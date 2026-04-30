use std::collections::HashSet;

use crate::fs::{DirEntry, FsClient, unix_path::UnixPath};
use crate::fs::{Fs, unix_path};
use crate::spawn;
use egui::Ui;
use egui::{CentralPanel, Image, Panel, ScrollArea, TextEdit, Vec2, include_image};
use egui_extras::{Column, TableBuilder};
use futures::SinkExt;
use futures_channel::mpsc::{self, UnboundedReceiver, UnboundedSender};

pub struct OpenDialog {
  fs_client: Fs,
  current_directory: String,
  dir_content: Vec<DirEntry>,
  initial_navigate: bool,
  initialized: bool,
  egui_ctx: Option<egui::Context>,
  async_sender: UnboundedSender<AsyncMessage>,
  async_reciver: UnboundedReceiver<AsyncMessage>,
  error_message: Option<String>,
  user_file_name: String,
  mkdir_name: String,
  pub selection: Vec<UnixPath>,
}

impl OpenDialog {
  pub fn new(fs: Fs) -> Self {
    let (tx, rx) = mpsc::unbounded::<AsyncMessage>();
    Self {
      fs_client: fs,
      current_directory: "/".to_string(),
      dir_content: Default::default(),
      initial_navigate: true,
      initialized: false,
      egui_ctx: None,
      async_reciver: rx,
      async_sender: tx,
      error_message: None,
      user_file_name: "".to_string(),
      mkdir_name: "".to_string(),
      selection: Default::default(),
    }
  }

  pub fn update_egui_context(&mut self, ctx: egui::Context) {
    self.egui_ctx = Some(ctx);
  }
}

pub struct SaveDialog {
  fs_client: Fs,
  current_directory: String,
  dir_content: Vec<DirEntry>,
  initial_navigate: bool,
  initialized: bool,
  egui_ctx: Option<egui::Context>,
  async_sender: UnboundedSender<AsyncMessage>,
  async_reciver: UnboundedReceiver<AsyncMessage>,
  error_message: Option<String>,
  user_file_name: String,
  mkdir_name: String,
  mkdir_visible: bool,
  pub selection: Option<UnixPath>,
}

impl SaveDialog {
  pub fn new(fs: Fs) -> Self {
    let (tx, rx) = mpsc::unbounded::<AsyncMessage>();
    Self {
      fs_client: fs,
      current_directory: "/".to_string(),
      dir_content: Default::default(),
      initial_navigate: true,
      initialized: false,
      egui_ctx: None,
      async_reciver: rx,
      async_sender: tx,
      error_message: None,
      user_file_name: "enter name".to_string(),
      selection: None,
      mkdir_name: "dir name".to_string(),
      mkdir_visible: false,
    }
  }

  pub fn update_egui_context(&mut self, ctx: egui::Context) {
    self.egui_ctx = Some(ctx);
  }
}

#[derive(Clone)]
enum AsyncMessage {
  NavigateIntoDirSuccess {
    path: String,
    content: Vec<DirEntry>,
  },
  MkDirSuccess,
  ErrorMessage(String),
}

enum UiAction {
  EnterInto(UnixPath),
  SelectFile(UnixPath),
  OkButton(UnixPath),
  MkDir(UnixPath),
}

impl SaveDialog {
  fn process_async_input(&mut self) {
    while let Ok(action) = self.async_reciver.try_recv() {
      if let Err(e) = self.try_recieve(&action) {
        match action {
          AsyncMessage::MkDirSuccess => {
            self.mkdir_visible = false;
          }
          _ => {
            log::error!("can't process_async_input {e}");
          }
        }
      }
    }
  }

  pub fn show(&mut self, ui: &mut Ui) {
    if !self.initialized {
      self.initialize();
    }

    self.process_async_input();

    let actions = FileDialogRender {
      current_directory: &mut self.current_directory,
      dir_content: &mut self.dir_content,
      error_message: &mut self.error_message,
      user_file_name: &mut self.user_file_name,
      user_file_name_visible: true,
      mkdir_available: true,
      mkdir_name: &mut self.mkdir_name,
      mkdir_visible: &mut self.mkdir_visible,
      delete_avaiable: true,
    }
    .render(ui);

    for action in actions {
      match action {
        UiAction::EnterInto(unix_path) => {
          self.navigate_into(&unix_path.to_string());
        }
        UiAction::SelectFile(unix_path) => {
          self.user_file_name = unix_path.name();
        }
        UiAction::OkButton(unix_path) => self.selection = Some(unix_path.clone()),
        UiAction::MkDir(unix_path) => {
          self.mkdir(&unix_path.to_string());
        }
      }
    }
  }

  fn initialize(&mut self) {
    self.initialized = true;
    if self.initial_navigate {
      self.navigate_into(&self.current_directory.clone());
    }
  }
}

impl OpenDialog {
  fn process_async_input(&mut self) {
    while let Ok(action) = self.async_reciver.try_recv() {
      if let Err(e) = self.try_recieve(&action) {
        log::error!("can't process_async_input {e}");
      }
    }
  }

  pub fn show(&mut self, ui: &mut Ui) {
    if !self.initialized {
      self.initialize();
    }

    self.process_async_input();

    let mut temp: bool = false;

    let actions = FileDialogRender {
      current_directory: &mut self.current_directory,
      dir_content: &mut self.dir_content,
      error_message: &mut self.error_message,
      user_file_name: &mut self.user_file_name,
      user_file_name_visible: false,
      mkdir_name: &mut self.mkdir_name,
      mkdir_available: false,
      mkdir_visible: &mut temp,
      delete_avaiable: false,
    }
    .render(ui);

    for action in actions {
      match action {
        UiAction::EnterInto(unix_path) => {
          self.navigate_into(&unix_path.to_string());
        }
        UiAction::SelectFile(unix_path) => {
          self.selection.push(unix_path);
        }
        _ => {}
      }
    }
  }

  fn initialize(&mut self) {
    self.initialized = true;
    if self.initial_navigate {
      self.navigate_into(&self.current_directory.clone());
    }
  }
}

trait DirNavigate {
  fn get_fs_client(&self) -> Fs;
  fn get_egui_ctx(&self) -> Option<egui::Context>;
  fn get_send_pipe(&self) -> UnboundedSender<AsyncMessage>;

  fn set_current_dir(&mut self, dir: &str);
  fn set_dir_conent(&mut self, content: Vec<DirEntry>);
  fn set_error_message(&mut self, message: Option<String>);

  fn navigate_into(&self, dir: &str) {
    log::info!("start navigate into {dir}");

    let dir_path = dir.to_string();

    let fs = self.get_fs_client();
    let ctx = self.get_egui_ctx();
    let mut sender = self.get_send_pipe();

    spawn(async move {
      match fs.list(&dir_path).await {
        Ok(entries) => {
          log::info!(
            "end navigate into {dir_path}, count {cnt}",
            cnt = entries.len()
          );

          if let Err(err) = sender
            .send(AsyncMessage::NavigateIntoDirSuccess {
              path: dir_path,
              content: entries,
            })
            .await
          {
            log::error!("send dir content fail: {err}");
          }
        }
        Err(err) => {
          log::error!("end navigate into {dir_path}: {err}");

          let _ = sender
            .send(AsyncMessage::ErrorMessage(format!(
              "can't read dir: {dir_path}: {err}"
            )))
            .await;
        }
      };

      if let Some(ctx) = ctx {
        ctx.request_repaint();
      }
    });
  }

  fn try_recieve(&mut self, msg: &AsyncMessage) -> Result<(), String> {
    match msg {
      AsyncMessage::NavigateIntoDirSuccess { path, content } => {
        self.set_current_dir(path);
        self.set_dir_conent({
          let mut content = content.clone();
          content.sort_by(|a, b| {
            //std::cmp::Ordering::Equal
            let ta = match a {
              DirEntry::Dir { .. } => 0,
              DirEntry::File { .. } => 1,
            };

            let tb = match b {
              DirEntry::Dir { .. } => 0,
              DirEntry::File { .. } => 1,
            };

            if ta != tb {
              return if ta < tb {
                std::cmp::Ordering::Less
              } else {
                std::cmp::Ordering::Greater
              };
            }

            let na = match a {
              DirEntry::Dir { name } => name,
              DirEntry::File { name, .. } => name,
            };

            let nb = match b {
              DirEntry::Dir { name } => name,
              DirEntry::File { name, .. } => name,
            };

            if na == nb {
              std::cmp::Ordering::Equal
            } else {
              if na < nb {
                std::cmp::Ordering::Less
              } else {
                std::cmp::Ordering::Greater
              }
            }
          });
          content
        });
        self.set_error_message(None);
        Ok(())
      }
      AsyncMessage::ErrorMessage(msg) => {
        self.set_error_message(Some(msg.to_string()));
        Ok(())
      }
      AsyncMessage::MkDirSuccess => Err("not impl".to_string()),
    }
  }

  fn mkdir(&self, dir: &str) {
    log::info!("start mkdir {dir}");

    let dir_path = dir.to_string();

    let fs: Fs = self.get_fs_client();
    let ctx = self.get_egui_ctx();
    let mut sender = self.get_send_pipe();

    let mkdir_impl =
      async move |fs: Fs, sender: &mut UnboundedSender<AsyncMessage>| -> Result<(), String> {
        log::info!("mkdir {dir_path} async");
        fs.mkdir(&dir_path).await?;

        log::info!("list {dir_path} async");
        let dir_content = fs.list(&dir_path).await?;

        let _ = sender
          .send(AsyncMessage::NavigateIntoDirSuccess {
            path: dir_path,
            content: dir_content,
          })
          .await
          .map_err(|e| format!("{e}"))?;

        Ok(())
      };

    spawn(async move {
      match mkdir_impl(fs, &mut sender).await {
        Ok(_) => {
          let _ = sender.send(AsyncMessage::MkDirSuccess).await;
        }
        Err(e) => {
          if let Err(e) = sender
            .send(AsyncMessage::ErrorMessage(format!("{e}")))
            .await
          {
            log::error!("mkdir fail {e}");
          }
        }
      }

      if let Some(ctx) = ctx {
        ctx.request_repaint();
      }
    });
  }
}

struct FileDialogRender<'a> {
  pub current_directory: &'a mut String,
  pub dir_content: &'a mut Vec<DirEntry>,
  pub error_message: &'a mut Option<String>,
  pub user_file_name_visible: bool,
  pub user_file_name: &'a mut String,
  pub mkdir_name: &'a mut String,
  pub mkdir_available: bool,
  pub mkdir_visible: &'a mut bool,
  pub delete_avaiable: bool,
}

impl<'a> FileDialogRender<'a> {
  pub fn render(&mut self, ui: &mut Ui) -> Vec<UiAction> {
    let id_source = ui.id().with("open_dialog");
    let mut actions = Vec::<UiAction>::new();

    Panel::top(id_source.with("top"))
      .resizable(true)
      .show_separator_line(false)
      .show_inside(ui, |ui| {
        Panel::right(id_source.with("top_nav_but"))
          .resizable(true)
          .show_inside(ui, |ui| {
            ui.horizontal(|ui| {
              if ui
                .button((
                  Image::new(include_image!("assets/go-up.png"))
                    .max_width(14.0)
                    .max_height(14.0),
                  "up",
                ))
                .clicked()
                && !self.current_directory.is_empty()
                && let dir = UnixPath::new(&self.current_directory)
                && dir.is_absolute()
                && !dir.is_root()
                && let Some(parent) = dir.parent()
              {
                actions.push(UiAction::EnterInto(parent));
              }

              if ui
                .button((
                  Image::new(include_image!("assets/go-next.png"))
                    .max_width(14.0)
                    .max_height(14.0),
                  "enter",
                ))
                .clicked()
                && !self.current_directory.is_empty()
                && let dir = UnixPath::new(&self.current_directory)
                && dir.is_absolute()
              {
                actions.push(UiAction::EnterInto(dir));
              }

              if self.mkdir_available {
                ui.checkbox(self.mkdir_visible, "mkdir");
              }
            })
          });

        CentralPanel::no_frame().show_inside(ui, |ui| {
          let avail_width = ui.available_width();
          ui.add_sized(
            Vec2::new(avail_width, 20.0),
            TextEdit::singleline(self.current_directory),
          );

          if *self.mkdir_visible {
            ui.horizontal(|ui| {
              if ui.button("mkdir:").clicked() {
                actions.push(UiAction::MkDir(
                  UnixPath::new(self.current_directory.to_string())
                    .resolve(self.mkdir_name.to_string()),
                ));
              }
              ui.text_edit_singleline(self.mkdir_name);
            });
          }

          if let Some(msg) = &self.error_message {
            ui.label(msg);
          }
        })
      });

    if self.user_file_name_visible {
      Panel::bottom(id_source.with("bottom"))
        .show_separator_line(false)
        .show_inside(ui, |ui| {
          Panel::right(id_source.with("bottom_right")).show_inside(ui, |ui| {
            if ui.button("ok").clicked() {
              actions.push(UiAction::OkButton(
                UnixPath::new(self.current_directory.clone()).resolve(self.user_file_name.clone()),
              ));
            }
          });

          CentralPanel::no_frame().show_inside(ui, |ui| {
            TextEdit::singleline(self.user_file_name)
              .desired_width(ui.available_width())
              .show(ui);
          });
        });
    }

    CentralPanel::default().show_inside(ui, |ui| {
      ScrollArea::vertical()
        //.auto_shrink([false, false])
        .show(ui, |ui| {
          let table = TableBuilder::new(ui)
            //.auto_shrink([false, false])
            .resizable(true)
            .striped(true)
            .column(
              Column::remainder(),
              // Column::exact(w),
              // Column::initial(w),
            )
            .column(Column::auto().at_least(80.0))
            .column(Column::remainder().at_least(80.0));

          table
            .header(20.0, |mut header| {
              header.col(|ui| {
                ui.strong("Name");
              });
              header.col(|ui| {
                ui.strong("Size");
              });
              header.col(|ui| {
                ui.strong("Action");
              });
            })
            .body(|mut body| {
              for idx in 0..self.dir_content.len() {
                if let Some(dir_entry) = self.dir_content.get(idx) {
                  body.row(18f32, |mut row| {
                    // column 0
                    let (_, resp) = row.col(|ui| {
                      ui.horizontal(|ui| match dir_entry {
                        DirEntry::File { name, .. } => {
                          ui.image(include_image!("assets/text-x-preview.png"));
                          ui.label(name)
                        }
                        DirEntry::Dir { name } => {
                          ui.image(include_image!("assets/folder.png"));
                          ui.label(name)
                        }
                      });
                    });

                    if resp.clicked() {
                      //
                    }

                    // column 1
                    row.col(|ui| match dir_entry {
                      DirEntry::File { size, .. } => {
                        ui.label(format!("{size}"));
                      }
                      DirEntry::Dir { .. } => {}
                    });

                    // column 2
                    row.col(|ui| match dir_entry {
                      DirEntry::File { name, .. } => {
                        if ui.button("select").clicked() {
                          actions.push(UiAction::SelectFile(
                            UnixPath::new(&self.current_directory).resolve(name),
                          ));
                        }
                      }
                      DirEntry::Dir { name, .. } => {
                        if ui.button("enter").clicked() {
                          actions.push(UiAction::EnterInto(
                            UnixPath::new(&self.current_directory).resolve(name),
                          ));
                        }
                      }
                    });
                  })
                }
              }
            });
        });
    });

    actions
  }
}

impl DirNavigate for OpenDialog {
  fn get_fs_client(&self) -> Fs {
    self.fs_client.clone()
  }

  fn get_egui_ctx(&self) -> Option<egui::Context> {
    self.egui_ctx.clone()
  }

  fn get_send_pipe(&self) -> UnboundedSender<AsyncMessage> {
    self.async_sender.clone()
  }

  fn set_current_dir(&mut self, dir: &str) {
    self.current_directory = dir.to_string();
  }

  fn set_dir_conent(&mut self, content: Vec<DirEntry>) {
    self.dir_content = content;
  }

  fn set_error_message(&mut self, message: Option<String>) {
    self.error_message = message;
  }
}

impl DirNavigate for SaveDialog {
  fn get_fs_client(&self) -> Fs {
    self.fs_client.clone()
  }

  fn get_egui_ctx(&self) -> Option<egui::Context> {
    self.egui_ctx.clone()
  }

  fn get_send_pipe(&self) -> UnboundedSender<AsyncMessage> {
    self.async_sender.clone()
  }

  fn set_current_dir(&mut self, dir: &str) {
    self.current_directory = dir.to_string();
  }

  fn set_dir_conent(&mut self, content: Vec<DirEntry>) {
    self.dir_content = content;
  }

  fn set_error_message(&mut self, message: Option<String>) {
    self.error_message = message;
  }
}
