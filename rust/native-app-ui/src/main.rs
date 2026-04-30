pub mod test;

use base_ui::MyApp;
use base_ui::app_params::*;
use std::collections::HashMap;
use std::env;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug)]
struct SimpleError(String);

impl std::fmt::Display for SimpleError {
  fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
    write!(f, "{}", self.0)
  }
}

impl std::error::Error for SimpleError {}

#[cfg(not(target_arch = "wasm32"))]
fn main() -> eframe::Result {
  env_logger::init(); // Log to stderr (if you run with `RUST_LOG=debug`).
  log::info!("start native");

  let rt = tokio::runtime::Runtime::new();
  if let Ok(rt) = rt {
    let _ = rt.enter();

    let local = tokio::task::LocalSet::new();
    return local.block_on(&rt, async {
      use std::collections::{HashMap, HashSet};

      let mut bld = egui::ViewportBuilder::default();
      bld.maximized = Some(true);

      let native_options = eframe::NativeOptions {
        viewport: bld,
        ..Default::default()
      };

      // Копируем значения из env
      let mut app_params: HashMap<String, String> = Default::default();
      let mut copy_env_vars: HashSet<String> = Default::default();
      for k in COPY_PARAMS_FROM_ENV {
        copy_env_vars.insert(k.to_string());
      }

      for (k, v) in env::vars() {
        if copy_env_vars.contains(&k) {
          app_params.insert(k.clone(), v.clone());
        }
      }

      // Инициализируем домашний каталог
      init_home_dir(&mut app_params);

      eframe::run_native(
        "midied",
        native_options,
        Box::new(|_cc| Ok(Box::new(MyApp::new(&app_params)))),
      )
    });
  } else {
    //anyhow!("Не удалось инициализировать Tokio runtime");
    log::error!("Не удалось инициализировать Tokio runtime");
    return Err(eframe::Error::AppCreation(Box::new(SimpleError(format!(
      "Failed to create Tokio runtime"
    )))));
  }

  // let mut bld = egui::ViewportBuilder::default();
  // bld.maximized = Some(true);

  // let native_options = eframe::NativeOptions {
  //   viewport: bld
  //     //.with_inner_size([1000.0, 500.0])
  //     //.with_min_inner_size([300.0, 220.0]),
  //     // .with_maximized(true)
  //     // .with_inner_size([600.0, 400.0])
  //     //.with_clamp_size_to_monitor_size(true),
  //     ,
  //   // .with_icon(
  //   //   // NOTE: Adding an icon is optional
  //   //   eframe::icon_data::from_png_bytes(&include_bytes!("../assets/icon-256.png")[..])
  //   //     .expect("Failed to load icon"),
  //   // ),
  //   ..Default::default()
  // };
  // eframe::run_native(
  //   "eframe template",
  //   native_options,
  //   Box::new(|_cc| Ok(Box::new(MyApp::default()))),
  // )
}

fn init_home_dir(app_params: &mut HashMap<String, String>) {
  if !app_params.contains_key(ENV_HOME) {
    match find_exists_home_dir() {
      Some(dir) => {
        log::info!("found home at {dir:?}");
        if let Some(dir) = dir.to_str() {
          app_params.insert(ENV_HOME.to_string(), dir.to_string());
        } else {
          log::error!("can't extract name from {dir:?} for ENV_HOME");
        }
      }
      None => match preferred_home_dir() {
        Some(dir) => {
          log::info!("use {dir:?} as home");
          if let Some(dir) = dir.to_str() {
            app_params.insert(ENV_HOME.to_string(), dir.to_string());
          } else {
            log::error!("can't extract name from {dir:?} for ENV_HOME");
          }
        }
        None => {
          log::warn!("preferred_home_dir() return none");
        }
      },
    }
  } else {
    if let Some(dir) = app_params.get(ENV_HOME) {
      if let Err(e) = create_home_dir_if_not_exists(Path::new(dir)) {
        log::error!("can't check exists/mkdir home dir '{dir}': {e}");
      }
    }
  }

  if !app_params.contains_key(PARAM_FS_HOME_MOUNT_STRING)
    && !app_params.contains_key(PARAM_FS_HOME_TYPE)
    && let Some(home) = app_params.get(ENV_HOME)
  {
    app_params.insert(PARAM_FS_HOME_MOUNT_STRING.to_string(), home.to_string());
    app_params.insert(
      PARAM_FS_HOME_TYPE.to_string(),
      PARAM_FS_HOME_TYPE_NATIVE.to_string(),
    );
  }
}

fn create_home_dir_if_not_exists(dir: &Path) -> Result<(), String> {
  let exists = fs::exists(dir).map_err(|e| format!("{e}"))?;
  if !exists {
    fs::create_dir_all(dir).map_err(|e| format!("{e}"))?;
  }
  Ok(())
}

fn preferred_home_dir() -> Option<PathBuf> {
  std::env::current_dir().ok().and_then(|dir| {
    let target = dir.join(".midied");
    if let Ok(exists) = fs::exists(&target) {
      if exists {
        Some(target)
      } else {
        if fs::create_dir_all(target.clone()).is_ok() {
          Some(target)
        } else {
          None
        }
      }
    } else {
      None
    }
  })
}

fn find_exists_home_dir() -> Option<PathBuf> {
  let from_cd = std::env::current_dir()
    .ok()
    .and_then(|dir| find_exists_dir_up_from(".midied", &dir));
  let from_home = std::env::home_dir().and_then(|dir| find_exists_dir_up_from(".midied", &dir));
  from_cd.or(from_home)
}

fn find_exists_dir_up_from(dir_name: &str, from: &Path) -> Option<PathBuf> {
  let mut dir = from.to_path_buf();
  loop {
    let target = dir.join(dir_name);
    match fs::exists(&target) {
      Err(_e) => return None,
      Ok(exists) => {
        if exists {
          return Some(target);
        } else {
          match dir.parent() {
            Some(p) => {
              dir = p.to_path_buf();
            }
            None => {
              return None;
            }
          }
        }
      }
    }
  }
}
