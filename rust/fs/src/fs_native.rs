use super::*;
use async_trait::async_trait;
use std::fs;
use std::io::{ErrorKind, Write};
use std::{
  fmt::Display,
  path::{Path, PathBuf},
};

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

#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
impl FsClient for FsNativeStore {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String> {
    let mut entries: Vec<DirEntry> = Vec::new();
    let target = map_physical(&self.root, dir)?;
    for entry in fs::read_dir(target).map_err(|e| e.to_string())? {
      let entry = entry.map_err(|e| e.to_string())?;
      let file_type = entry.file_type().map_err(|e| e.to_string())?;
      let entry_meta = entry.metadata().map_err(|e| e.to_string())?;

      if file_type.is_dir() {
        entries.push(DirEntry::Dir {
          name: entry
            .file_name()
            .into_string()
            .map_err(|e| format!("{e:?}"))?,
        });
      } else if file_type.is_file() {
        entries.push(DirEntry::File {
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

  async fn write(&self, path: &str, bytes: &[u8]) -> Result<(), String> {
    let target = map_physical(&self.root, path)?;
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

  async fn read(&self, path: &str) -> Result<Vec<u8>, String> {
    let target = map_physical(&self.root, path)?;
    Ok(fs::read(target).map_err(|e| format!("{e:?}"))?)
  }

  async fn mkdir(&self, path: &str) -> Result<(), String> {
    let target = map_physical(&self.root, path)?;
    fs::create_dir_all(target).map_err(|e| format!("{e:?}"))?;
    Ok(())
  }

  async fn delete(&self, path: &str) -> Result<(), String> {
    let target = map_physical(&self.root, path)?;
    let remove_res = match fs::remove_file(&target) {
      Ok(()) => Ok(()),
      Err(e) if e.kind() == ErrorKind::IsADirectory => fs::remove_dir(&target),
      Err(e) => Err(e),
    };
    let _ = remove_res.map_err(|e| format!("{e}"))?;
    Ok(())
  }

  async fn rename(&self, source_path: &str, target_path: &str) -> Result<(), String> {
    let source_path = map_physical(&self.root, source_path)?;
    let target_path = map_physical(&self.root, target_path)?;
    fs::rename(source_path, target_path).map_err(|e| format!("{e}"))
  }
}
