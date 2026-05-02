use crate::{
  app_params::*,
  coll::{IdList, IdListImpl},
  midi_receiver::{collector::IdListWriterOfMidi, listener::MidiLisener},
  music_grid::{GridObj, MusicGrid, move_tool, tool, view_port::ViewPortControls},
  plugin::{self, Panel},
  spawn,
  split_view::SplitView,
  store::{self},
};
use egui::{Ui, Visuals};
use fs::*;
use futures::lock::Mutex as FLMutex;
use log::info as log_info;
use midi_model::midi_input::{MidiInput, PositiveMillisec, TimestampShift};
use std::{
  cell::RefCell,
  collections::HashMap,
  rc::Rc,
  sync::{Arc, Mutex},
};

pub type GridObjects = Arc<Mutex<IdListImpl<Box<dyn GridObj>>>>;

pub struct MyApp<L: IdList<Box<dyn GridObj>>> {
  maxi: bool,

  grid: MusicGrid<L>,
  view_port_ctl: ViewPortControls,
  split_view: SplitView,

  cur_tool: NamedTool<L>,
  tool1: NamedTool<L>,

  midi_input: Option<Arc<MidiInput>>,
  midi_listener: Arc<Mutex<Option<Arc<FLMutex<MidiLisener>>>>>,

  panels: Vec<Rc<RefCell<dyn plugin::Panel>>>,
}

#[derive(Clone)]
struct NamedTool<L: IdList<Box<dyn GridObj>>> {
  name: String,
  tool: Rc<RefCell<dyn tool::Tool<L>>>,
}

impl<L: IdList<Box<dyn GridObj>>> PartialEq for NamedTool<L> {
  fn eq(&self, other: &Self) -> bool {
    self.name == other.name
  }
}

impl MyApp<GridObjects> {
  pub fn new(params: &HashMap<String, String>) -> Self {
    log_info!("initial params ({})", params.len());
    for (k, v) in params.iter() {
      log_info!("init param {k} = {v}");
    }

    let mut grid: MusicGrid<_> =
      MusicGrid::new(Arc::new(Mutex::new(IdListImpl::<Box<dyn GridObj>>::new())));

    let mut vp = ViewPortControls::new(&mut grid.view_port);
    vp.set_grid_visible_rect(grid.visible_rect.clone());

    let tool1 = NamedTool {
      name: "sample".to_string(),
      tool: Rc::new(RefCell::new(move_tool::Move::default())),
    };

    let midi_input = params
      .get(&PARAM_MIDI_HTTP_URL.to_string())
      .and_then(|midi_client_api_url| {
        MidiInput::new(midi_client_api_url)
          .build()
          .map(|mut input| {
            input.new_reader_timestamp_shift = TimestampShift::Now;
            input
          })
          .ok()
          .map(|input| Arc::new(input))
      });

    let mut panels: Vec<Rc<RefCell<dyn plugin::Panel>>> = Vec::new();

    let store_panel = store::store_panel::StorePanel::new(init_fs(params), grid.objects.clone());
    let store_panel: Rc<RefCell<dyn Panel>> = Rc::new(RefCell::new(store_panel));
    panels.push(store_panel);

    Self {
      maxi: Default::default(),
      grid: grid,
      view_port_ctl: vp,
      split_view: SplitView::new(0.5).min_wdith(100.0).separator_width(8.0),
      tool1: tool1.clone(),
      cur_tool: tool1,
      midi_input: midi_input,
      midi_listener: Arc::new(Mutex::new(None)),
      panels: panels,
    }
  }
}

fn try_init_fs_http(params: &HashMap<String, String>) -> Option<Fs> {
  let fs_type = params.get(PARAM_FS_HOME_TYPE)?;
  if fs_type != PARAM_FS_HOME_TYPE_HTTP {
    return None;
  }

  let mount_path = params.get(PARAM_FS_HOME_MOUNT_STRING)?;
  let c = FsHttpClient::new(mount_path).build().ok()?;
  let c = Arc::new(futures::lock::Mutex::new(c));

  log::info!("use http fs");
  Some(c)
}

#[cfg(not(target_arch = "wasm32"))]
fn try_init_fs_native(params: &HashMap<String, String>) -> Option<Fs> {
  let fs_type = params.get(PARAM_FS_HOME_TYPE)?;
  if fs_type != PARAM_FS_HOME_TYPE_NATIVE {
    return None;
  }

  let mount_path = params.get(PARAM_FS_HOME_MOUNT_STRING)?;

  log::info!("use native fs");
  Some(Arc::new(futures::lock::Mutex::new(FsNativeStore::new(
    mount_path,
  ))))
}

fn init_ram_fs() -> Fs {
  log::info!("use ram fs");
  FsRamStore::default().boxed()
}

#[cfg(target_arch = "wasm32")]
fn init_fs(params: &HashMap<String, String>) -> Fs {
  let fs_cl_1 = try_init_fs_http(params);
  fs_cl_1.unwrap_or_else(|| init_ram_fs())
}

#[cfg(not(target_arch = "wasm32"))]
fn init_fs(params: &HashMap<String, String>) -> Fs {
  let fs_cl_1 = try_init_fs_http(params);
  let fs_cl_2 = try_init_fs_native(params);
  fs_cl_1
    .or_else(move || fs_cl_2)
    .unwrap_or_else(|| init_ram_fs())
}

impl eframe::App for MyApp<GridObjects> {
  //ctx.plugin_or_default::<egui_async::EguiAsyncPlugin>();

  fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
    ui.set_visuals(Visuals::dark());

    if !self.maxi {
      ui.send_viewport_cmd(egui::ViewportCommand::Maximized(true));
      self.maxi = true;
    }

    egui::Panel::top("id").show_inside(ui, |ui| {
      ui.horizontal(|ui| {
        if ui
          .selectable_value(&mut self.cur_tool, self.tool1.clone(), &self.tool1.name)
          .clicked()
        {
          self.grid.assign_tool(self.tool1.tool.clone());
        }
      })
    });

    egui::CentralPanel::default().show_inside(ui, |ui| {
      self.split_view.show(ui, |ui_left, ui_right| {
        self.grid.ui(ui_left);

        ui_right.collapsing("view port", |ui| {
          self.view_port_ctl.ui(&mut self.grid.view_port, ui);
        });

        if let Some(midi_input) = self.midi_input.clone() {
          let midi_input = midi_input.clone();
          ui_right.collapsing("midi recording", |ui| {
            let midi_ls_hold = self.midi_listener.clone();
            midi_ui(ui, self.grid.objects.clone(), midi_ls_hold, midi_input);
          });
        }

        for idx in 0..self.panels.len() {
          let panel = &self.panels[idx];
          match panel.try_borrow_mut() {
            Err(e) => {
              log::warn!("can't borrow mut panel [{idx}]: {e}");
            }
            Ok(mut panel) => {
              ui_right.collapsing(panel.name(), |ui| panel.ui(ui));
            }
          }
        }

        if let Some(tool) = &self.grid.tool {
          tool.borrow_mut().render_ui(ui_right);
        }
      });
    });
  }

  fn logic(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
    egui_extras::install_image_loaders(ctx);

    for idx in 0..self.panels.len() {
      let panel = &self.panels[idx];
      match panel.try_borrow_mut() {
        Err(e) => {
          log::warn!("can't borrow mut panel [{idx}]: {e}");
        }
        Ok(mut panel) => {
          panel.update_egui_context(ctx.clone());
        }
      }
    }
  }

  // fn save(&mut self, _storage: &mut dyn eframe::Storage) {}
  // fn on_exit(&mut self) {}
}

fn midi_ui(
  ui: &mut Ui,
  grid_objects: Arc<Mutex<IdListImpl<Box<dyn GridObj>>>>,
  midi_ls_hold: Arc<Mutex<Option<Arc<FLMutex<MidiLisener>>>>>,
  midi_input: Arc<MidiInput>,
) {
  let has_listener = {
    if let Ok(ls) = midi_ls_hold.lock() {
      ls.is_some()
    } else {
      false
    }
  };

  if has_listener {
    if ui.button("stop midi record").clicked() {
      log_info!("stopping midi record");
      spawn(async move {
        if let Ok(mut ls_hold) = midi_ls_hold.lock() {
          if ls_hold.is_some() {
            let ls = ls_hold.as_ref().unwrap();
            let ls = ls.clone();
            spawn(async move {
              ls.lock().await.stop();
            });

            *ls_hold = None;
          }
        }
      });
    }
  } else {
    if ui.button("start midi record").clicked() {
      log_info!("starting midi record");

      let objects_target = grid_objects.clone();
      let midi_collector = Arc::new(Mutex::new(IdListWriterOfMidi::new(objects_target)));

      spawn(async move {
        if let Ok(mut reader) = midi_input.new_reader().await {
          reader.set_await_if_empty(PositiveMillisec(20_000u32));
          reader.set_remove_after_read(true);

          let midi_listener = Arc::new(FLMutex::new(MidiLisener::new(reader)));
          {
            if let Ok(mut ls) = midi_ls_hold.lock() {
              *ls = Some(midi_listener.clone());
            }
          }

          let midi_ls = midi_listener.clone();
          spawn(async move {
            midi_ls.lock().await.start(move |ev| {
              log_info!("got {ev:?}");
              {
                if let Ok(mut midi_coll) = midi_collector.lock() {
                  log_info!("send event to midi collector");
                  midi_coll.write(ev.clone());
                }
              }
            });
          });
        }
      });
    }
  }
}
