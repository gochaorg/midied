use std::sync::{Arc, Mutex};

use super::super::GridObjects;
use super::super::fs::FsClient;
use super::super::plugin::Panel;
use super::super::*;
use crate::{
  coll::IdList,
  fs::{Fs, files_dialog::OpenDialog, unix_path::UnixPath},
  music_grid::{GridObj, pitch_lines::PitchLines, time_lines::TimeLines},
  store::file_content::{MidiedJson, restore, store},
};
use derive_more::Display;
use egui::{Id, Modal, Ui};
use futures::{SinkExt, TryFutureExt};
use futures_channel::mpsc::{self, UnboundedReceiver, UnboundedSender};
use log;

pub struct StorePanel {
  fs: Fs,
  filename: String,
  grid_objects: GridObjects,
  egui_context: Option<egui::Context>,
  save_progress: Arc<Mutex<RWProgress>>,
  load_progress: Arc<Mutex<RWProgress>>,
  open_dialog: Option<OpenDialog>,
  async_sender: UnboundedSender<AsyncMessage>,
  async_reciver: UnboundedReceiver<AsyncMessage>,
}

enum AsyncMessage {
  ErrorMessage(String),
  LoadingSucess {
    grid_objs: Vec<Box<dyn GridObj>>,
    file_name: UnixPath,
  },
  SavingSuccess,
}

#[derive(Display, Clone, PartialEq, Debug)]
enum RWProgress {
  #[display("not started")]
  NotStarted,

  #[display("progress")]
  Progress,

  #[display("success")]
  Success,

  #[display("fail: {_0}")]
  Fail(String),
}

trait StateChange {
  fn set_state(self, state: RWProgress);
}

impl StateChange for &Arc<Mutex<RWProgress>> {
  fn set_state(self, new_state: RWProgress) {
    if let Ok(mut state) = self.try_lock() {
      *state = new_state;
    }
  }
}

impl StateChange for (&Arc<Mutex<RWProgress>>, &Option<egui::Context>) {
  fn set_state(self, new_state: RWProgress) {
    let (state, ctx) = self;
    {
      match state.lock() {
        Ok(mut state) => {
          *state = new_state.clone();
        }
        Err(e) => {
          log::error!("can't lock state {e}");
        }
      }
    }

    if let Some(ctx) = ctx {
      ctx.request_repaint();
    }
  }
}

impl StorePanel {
  pub fn new(fs: Fs, grid_obj: GridObjects) -> Self {
    let (tx, rx) = mpsc::unbounded::<AsyncMessage>();
    Self {
      fs: fs,
      filename: "/unnamed".to_string(),
      grid_objects: grid_obj,
      egui_context: None,
      save_progress: Arc::new(Mutex::new(RWProgress::NotStarted)),
      load_progress: Arc::new(Mutex::new(RWProgress::NotStarted)),
      open_dialog: None,
      async_reciver: rx,
      async_sender: tx,
    }
  }
}

impl Panel for StorePanel {
  fn name(&self) -> String {
    "Store".to_string()
  }

  fn update_egui_context(&mut self, _ctx: egui::Context) {
    self.egui_context = Some(_ctx);
  }

  fn ui(&mut self, ui: &mut Ui) {
    self.process_async_input();

    ui.horizontal_wrapped(|ui| {
      ui.label("filename");
      ui.text_edit_singleline(&mut self.filename);
    });

    if ui.button("save").clicked() {
      self.save_file(UnixPath::new(&self.filename));
    }

    {
      if let Ok(state) = self.save_progress.lock() {
        if *state != RWProgress::NotStarted {
          let st = state.to_string();
          ui.label(st);
        }
      }
    }

    ui.horizontal(|ui| {
      if ui.button("load").clicked() {
        self.load_file(UnixPath::new(&self.filename));
      }

      if ui.button("open dialog").clicked()
        && self.egui_context.is_some()
        && self.open_dialog.is_none()
      {
        self.open_dialog = Some(OpenDialog::new(self.fs.clone()));
      }

      if let Some(dlg) = &mut self.open_dialog
        && let Some(ctx) = &self.egui_context
      {
        let modal = Modal::new(Id::new("open_dlg")).show(&ctx, |ui| {
          ui.set_width(400.0);
          ui.set_height_range(200.0..=600.0);
          ui.heading("Open dialog");

          dlg.show(ui);

          if ui.button("close").clicked() || !dlg.selection.is_empty() {
            ui.close();
          }

          dlg.selection.get(0).map(|x| x.clone())
        });

        if modal.should_close() {
          self.open_dialog = None;
        }

        if let Some(sel_file) = modal.inner {
          self.load_file(sel_file);
        }
      }
    });

    {
      if let Ok(state) = self.load_progress.lock() {
        if *state != RWProgress::NotStarted {
          ui.label(state.to_string());
        }
      }
    }
  }
}

impl StorePanel {
  fn process_async_input(&mut self) {
    while let Ok(action) = self.async_reciver.try_recv() {
      match action {
        AsyncMessage::ErrorMessage(_message) => {}
        AsyncMessage::LoadingSucess {
          grid_objs,
          file_name,
        } => match self.grid_objects.lock() {
          Err(err) => {
            log::error!("can't lock consumer grid_objects for loading {err}");
          }
          Ok(mut target_objs) => {
            target_objs.clear();
            for obj in grid_objs {
              target_objs.push(obj);
            }

            if target_objs.find_by_type::<TimeLines>().is_empty() {
              target_objs.push(Box::new(TimeLines::default()));
            }

            if target_objs.find_by_type::<PitchLines>().is_empty() {
              target_objs.push(Box::new(PitchLines::default()));
            }

            self.filename = file_name.to_string();
          }
        },
        AsyncMessage::SavingSuccess => {}
      }
    }
  }

  fn load_file(&self, file_name: UnixPath) {
    let ctx = self.egui_context.clone();
    let fs = self.fs.clone();
    let mut sender = self.async_sender.clone();

    let load_impl = async move || -> Result<AsyncMessage, String> {
      let bytes = fs.read(file_name.clone()).await?;
      let content = String::from_utf8_lossy(&bytes);
      let content = restore(&content)?;

      let mut obj_consumer = Vec::<Box<dyn GridObj>>::new();

      obj_consumer.clear();

      for item in &content.content {
        match item {
          MidiedJson::Note(note) => {
            let note = Box::new(note.clone());
            obj_consumer.push(note);
          }
        }
      }

      Ok(AsyncMessage::LoadingSucess {
        grid_objs: obj_consumer,
        file_name: file_name,
      })
    };

    spawn(async move {
      if let Err(e) = {
        match load_impl().await.map_err(|e| AsyncMessage::ErrorMessage(e)) {
          Ok(r) => sender.send(r).await,
          Err(e) => sender.send(e).await,
        }
      } {
        log::error!("send to async_sender fail: {e}");
      }

      if let Some(ctx) = ctx {
        ctx.request_repaint();
      }
    });
  }

  fn save_file(&self, file_name: UnixPath) {
    let obj = self.grid_objects.clone();
    let ctx = self.egui_context.clone();
    let fs = self.fs.clone();
    let mut sender = self.async_sender.clone();

    let save_impl = async move || -> Result<AsyncMessage, String> {
      let content = store(obj)?;
      fs.write(file_name.to_string(), content.into_bytes())
        .await?;

      Ok(AsyncMessage::SavingSuccess)
    };

    spawn(async move {
      if let Err(e) = {
        match save_impl()
          .await
          .map_err(|e| AsyncMessage::ErrorMessage(format!("{e}")))
        {
          Ok(r) => sender.send(r).await,
          Err(r) => sender.send(r).await,
        }
      } {
        log::error!("send to async_sender fail: {e}");
      }

      if let Some(ctx) = ctx {
        ctx.request_repaint();
      }
    });
  }
}
