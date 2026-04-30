use super::*;
use crate::fs::unix_path::UnixPath;
use futures::lock::Mutex;
use std::fs;
use std::io::{ErrorKind, Write};
use std::{
  fmt::Display,
  path::{Path, PathBuf},
  sync::Arc,
};

#[cfg(not(target_arch = "wasm32"))]
#[derive(Debug, Clone)]
pub struct FsNativeStore {
  root: PathBuf,
}

impl FsNativeStore {
  pub fn new<P: AsRef<Path>>(root: P) -> Self {
    Self {
      root: root.as_ref().to_path_buf(),
    }
  }

  pub fn boxed(self) -> Fs {
    Arc::new(futures::lock::Mutex::new(self)).boxed()
  }
}

impl Display for FsNativeStore {
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    f.write_str(&format!("native store {}", self.root.to_string_lossy()))
  }
}

fn map_physical<PATH: AsRef<str>>(root: &Path, path: PATH) -> Result<PathBuf, String> {
  let path = UnixPath::new(path.as_ref());
  if path.is_empty() {
    return Err("path is empty".to_string());
  }
  if !path.is_absolute() {
    return Err("path is not absolute".to_string());
  }
  let path = path.normalize();
  if path.is_root() || path.is_empty() {
    return Ok(root.to_path_buf());
  }

  let mut target = root.to_path_buf();
  for name in path.name_components() {
    target.push(name);
  }

  Ok(target)
}

impl FsClient for Arc<Mutex<FsNativeStore>> {
  async fn list<PATH: AsRef<str> + Send>(&self, dir: PATH) -> Result<Vec<super::DirEntry>, String> {
    let mut entries: Vec<super::DirEntry> = Vec::new();
    let me = self.lock().await;
    let target = map_physical(&me.root, dir)?;
    for entry in fs::read_dir(target).map_err(|e| e.to_string())? {
      let entry = entry.map_err(|e| e.to_string())?;
      let file_type = entry.file_type().map_err(|e| e.to_string())?;
      let entry_meta = entry.metadata().map_err(|e| e.to_string())?;

      if file_type.is_dir() {
        entries.push(super::DirEntry::Dir {
          name: entry
            .file_name()
            .into_string()
            .map_err(|e| format!("{e:?}"))?,
        });
      } else if file_type.is_file() {
        entries.push(super::DirEntry::File {
          name: entry
            .file_name()
            .into_string()
            .map_err(|e| format!("{e:?}"))?,
          size: entry_meta.len(),
        });
      }
    }

    Ok(entries)
  }

  async fn delete<PATH: AsRef<str> + Send>(&self, path: PATH) -> Result<(), String> {
    let me = self.lock().await;
    let target = map_physical(&me.root, path)?;

    let remove_res = match fs::remove_file(&target) {
      Ok(()) => Ok(()),
      Err(e) if e.kind() == ErrorKind::IsADirectory => fs::remove_dir(&target),
      Err(e) => Err(e),
    };
    let _ = remove_res.map_err(|e| format!("{e}"))?;
    Ok(())
  }

  async fn write<PATH: AsRef<str> + Send, BYTES: AsRef<[u8]> + Send>(
    &self,
    path: PATH,
    bytes: BYTES,
  ) -> Result<(), String> {
    let me = self.lock().await;
    let target = map_physical(&me.root, path)?;
    if let Some(parent) = target.parent() {
      if let Err(e) = fs::metadata(parent)
        && e.kind() == ErrorKind::NotFound
      {
        fs::create_dir_all(parent).map_err(|e| format!("{e:?}"))?;
      }
    }

    let mut file = fs::OpenOptions::new()
      .create(true)
      .read(true)
      .write(true)
      .open(&target)
      .map_err(|e| format!("{e:?}"))?;

    file
      .write_all(bytes.as_ref())
      .map_err(|e| format!("{e:?}"))?;

    Ok(())
  }

  async fn read<PATH: AsRef<str> + Send>(&self, path: PATH) -> Result<Vec<u8>, String> {
    let me = self.lock().await;
    let target = map_physical(&me.root, path)?;

    Ok(fs::read(target).map_err(|e| format!("{e:?}"))?)
  }

  async fn mkdir<PATH: AsRef<str> + Send>(&self, path: PATH) -> Result<(), String> {
    let me = self.lock().await;
    let target = map_physical(&me.root, path)?;

    fs::create_dir_all(target).map_err(|e| format!("{e:?}"))?;
    Ok(())
  }

  async fn abilities(&self) -> Result<FsAbilities, String> {
    Ok(
      FsAbilities::empty()
        .with_read(true)
        .with_delete(true)
        .with_mkdir(true)
        .with_upload(true),
    )
  }
}

#[cfg(not(target_arch = "wasm32"))]
#[test]
fn test_cycle() {
  let temp_dir = "/home/user/code/midi/tmp";

  if !fs::exists(temp_dir).ok().unwrap_or(false) {
    return;
  }

  use fastrand;
  use tokio::runtime::Builder;
  let _runtime = Builder::new_multi_thread()
    .worker_threads(4)
    .enable_all()
    .build()
    .unwrap()
    .block_on(async {
      let store = FsNativeStore::new(temp_dir);
      println!("store: {store}");

      let mut exists_file: Option<String> = None;
      let store = Arc::new(Mutex::new(store));
      for de in store.list("/").await.map_err(|e| format!("{e}")).unwrap() {
        println!("entry {de:?}");
        match &de {
          DirEntry::File { name, .. } => {
            exists_file = Some(name.clone());
          }
          _ => {}
        }
      }

      if let Some(exists_file) = exists_file {
        println!("delete {exists_file}");
        match store.delete(format!("/{exists_file}")).await {
          Ok(_) => {
            println!("delete success");
          }
          Err(e) => {
            println!("delete fail: {e}");
          }
        }
      }

      let mut file_name: String = (0..5).map(|_| fastrand::alphanumeric()).collect();
      file_name.insert_str(0, "/file-");

      println!("write file {file_name}");
      match store.write(&file_name, b"content\n").await {
        Ok(_) => println!("success"),
        Err(e) => println!("fail {e}"),
      }
    });
}
