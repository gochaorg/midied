use async_trait::async_trait;
use std::{pin::Pin, sync::Arc};

use serde::Deserialize;

pub mod files_dialog;
pub mod fs_http;
pub mod fs_ram;

#[cfg(not(target_arch = "wasm32"))]
pub mod fs_native;

pub mod unix_path;

/// Содержание каталога - файл / каталог
#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "type")]
pub enum DirEntry {
  #[serde(rename = "file")]
  File { name: String, size: u64 },

  #[serde(rename = "dir")]
  Dir { name: String },
}

#[derive(Debug, Clone)]
pub struct FsAbilities {
  pub abilities: Vec<String>,
}

#[cfg(not(target_arch = "wasm32"))]
pub type Fs = Arc<futures::lock::Mutex<dyn FsClientDyn + Send>>;

#[cfg(target_arch = "wasm32")]
pub type Fs = Arc<futures::lock::Mutex<dyn FsClientDyn>>;

impl FsAbilities {
  pub fn empty() -> Self {
    Self {
      abilities: Vec::new(),
    }
  }

  pub fn with_ability(&self, ability: Option<String>) -> FsAbilities {
    let mut a: Vec<String> = self
      .abilities
      .iter()
      .filter(|s| !s.eq_ignore_ascii_case("read"))
      .map(|s| s.clone())
      .collect();
    if ability.is_some() {
      a.push(ability.unwrap().to_string());
    }
    FsAbilities { abilities: a }
  }

  pub fn read(&self) -> bool {
    self
      .abilities
      .iter()
      .find(|a| a.eq_ignore_ascii_case("read"))
      .is_some()
  }

  pub fn with_read(&self, use_read: bool) -> FsAbilities {
    self.with_ability(if use_read {
      Some("read".to_string())
    } else {
      None
    })
  }

  pub fn upload(&self) -> bool {
    self
      .abilities
      .iter()
      .find(|a| a.eq_ignore_ascii_case("upload"))
      .is_some()
  }

  pub fn with_upload(&self, use_read: bool) -> FsAbilities {
    self.with_ability(if use_read {
      Some("upload".to_string())
    } else {
      None
    })
  }

  pub fn delete(&self) -> bool {
    self
      .abilities
      .iter()
      .find(|a| a.eq_ignore_ascii_case("delete"))
      .is_some()
  }

  pub fn with_delete(&self, use_read: bool) -> FsAbilities {
    self.with_ability(if use_read {
      Some("delete".to_string())
    } else {
      None
    })
  }

  pub fn mkdir(&self) -> bool {
    self
      .abilities
      .iter()
      .find(|a| a.eq_ignore_ascii_case("mkdir"))
      .is_some()
  }

  pub fn with_mkdir(&self, use_read: bool) -> FsAbilities {
    self.with_ability(if use_read {
      Some("mkdir".to_string())
    } else {
      None
    })
  }
}

// ===== ТРЕЙТ С ASYNC_TRAIT =====
// ?Send автоматически: +Send для native, без Send для wasm32
#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
pub trait FsClient {
  async fn list<PATH: AsRef<str>>(&self, dir: PATH) -> Result<Vec<DirEntry>, String>;
  async fn delete<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String>;
  async fn write<PATH: AsRef<str>, BYTES: AsRef<[u8]>>(
    &self,
    path: PATH,
    bytes: BYTES,
  ) -> Result<(), String>;
  async fn read<PATH: AsRef<str>>(&self, path: PATH) -> Result<Vec<u8>, String>;
  async fn mkdir<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String>;
  async fn abilities(&self) -> Result<FsAbilities, String>;

  // Метод boxed остаётся, но упрощён
  #[cfg(target_arch = "wasm32")]
  fn boxed(self) -> Fs
  where
    Self: Sized + 'static,
  {
    Arc::new(futures::lock::Mutex::new(FsClientBoxed(self)))
  }

  #[cfg(not(target_arch = "wasm32"))]
  fn boxed(self) -> Fs
  where
    Self: Send + Sized + 'static,
  {
    Arc::new(futures::lock::Mutex::new(FsClientBoxed(self)))
  }
}

struct FsClientBoxed<T>(T);

#[cfg(target_arch = "wasm32")]
#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl<T: FsClient> FsClientDyn for FsClientBoxed<T> {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String> {
    self.0.list(dir).await
  }
  async fn delete(&self, path: &str) -> Result<(), String> {
    self.0.delete(path).await
  }
  async fn write(&self, path: &str, bytes: &[u8]) -> Result<(), String> {
    self.0.write(path, bytes).await
  }
  async fn read(&self, path: &str) -> Result<Vec<u8>, String> {
    self.0.read(path).await
  }
  async fn mkdir(&self, path: &str) -> Result<(), String> {
    self.0.mkdir(path).await
  }
  async fn abilities(&self) -> Result<FsAbilities, String> {
    self.0.abilities().await
  }
}

#[cfg(not(target_arch = "wasm32"))]
#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl<T: FsClient + Send> FsClientDyn for FsClientBoxed<T> {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String> {
    self.0.list(dir).await
  }
  async fn delete(&self, path: &str) -> Result<(), String> {
    self.0.delete(path).await
  }
  async fn write(&self, path: &str, bytes: &[u8]) -> Result<(), String> {
    self.0.write(path, bytes).await
  }
  async fn read(&self, path: &str) -> Result<Vec<u8>, String> {
    self.0.read(path).await
  }
  async fn mkdir(&self, path: &str) -> Result<(), String> {
    self.0.mkdir(path).await
  }
  async fn abilities(&self) -> Result<FsAbilities, String> {
    self.0.abilities().await
  }
}

// ===== ТРЕЙТ 2: FsClientDyn (динамический, object-safe) =====
#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
pub trait FsClientDyn {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String>;
  async fn delete(&self, path: &str) -> Result<(), String>;
  async fn write(&self, path: &str, bytes: &[u8]) -> Result<(), String>;
  async fn read(&self, path: &str) -> Result<Vec<u8>, String>;
  async fn mkdir(&self, path: &str) -> Result<(), String>;
  async fn abilities(&self) -> Result<FsAbilities, String>;
}

#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl FsClientDyn for Arc<futures::lock::Mutex<dyn FsClientDyn + Send>> {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String> {
    self.lock().await.list(dir).await
  }
  async fn delete(&self, path: &str) -> Result<(), String> {
    self.lock().await.delete(path).await
  }
  async fn write(&self, path: &str, bytes: &[u8]) -> Result<(), String> {
    self.lock().await.write(path, bytes).await
  }
  async fn read(&self, path: &str) -> Result<Vec<u8>, String> {
    self.lock().await.read(path).await
  }
  async fn mkdir(&self, path: &str) -> Result<(), String> {
    self.lock().await.mkdir(path).await
  }
  async fn abilities(&self) -> Result<FsAbilities, String> {
    self.lock().await.abilities().await
  }
}

// impl<FS: FsClient + Send> FsClientDyn for Arc<futures::lock::Mutex<FS>> {
//   fn list(
//     &self,
//     dir: &str,
//   ) -> Pin<Box<dyn Future<Output = Result<Vec<DirEntry>, String>> + Send + '_>> {
//     let path = dir.to_string();
//     Box::pin(async move {
//       let me = self.lock().await;
//       me.list(path).await
//     })
//   }

//   fn delete(&self, path: &str) -> Pin<Box<dyn Future<Output = Result<(), String>> + Send + '_>> {
//     let path = path.to_string();
//     Box::pin(async move {
//       let me = self.lock().await;
//       me.delete(path).await
//     })
//   }

//   fn write(
//     &self,
//     path: &str,
//     bytes: &[u8],
//   ) -> Pin<Box<dyn Future<Output = Result<(), String>> + Send + '_>> {
//     let path = path.to_string();
//     let bytes = bytes.to_vec();
//     Box::pin(async move {
//       let me = self.lock().await;
//       me.write(path, bytes).await
//     })
//   }

//   fn read(&self, path: &str) -> Pin<Box<dyn Future<Output = Result<Vec<u8>, String>> + Send + '_>> {
//     let path = path.to_string();
//     Box::pin(async move {
//       let me = self.lock().await;
//       me.read(path).await
//     })
//   }

//   fn mkdir(&self, path: &str) -> Pin<Box<dyn Future<Output = Result<(), String>> + Send + '_>> {
//     let path = path.to_string();
//     Box::pin(async move {
//       let me = self.lock().await;
//       me.mkdir(path).await
//     })
//   }

//   fn abilities(&self) -> Pin<Box<dyn Future<Output = Result<FsAbilities, String>> + Send + '_>> {
//     Box::pin(async move {
//       let me = self.lock().await;
//       me.abilities().await
//     })
//   }
// }

#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl FsClient for Arc<futures::lock::Mutex<dyn FsClientDyn>> {
  async fn list<PATH: AsRef<str>>(&self, dir: PATH) -> Result<Vec<DirEntry>, String> {
    self.lock().await.list(dir.as_ref()).await
  }

  async fn delete<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String> {
    self.lock().await.delete(path.as_ref()).await
  }

  async fn write<PATH: AsRef<str>, BYTES: AsRef<[u8]>>(
    &self,
    path: PATH,
    bytes: BYTES,
  ) -> Result<(), String> {
    self.lock().await.write(path.as_ref(), bytes.as_ref()).await
  }

  async fn read<PATH: AsRef<str>>(&self, path: PATH) -> Result<Vec<u8>, String> {
    self.lock().await.read(path.as_ref()).await
  }

  async fn mkdir<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String> {
    self.lock().await.mkdir(path.as_ref()).await
  }

  async fn abilities(&self) -> Result<FsAbilities, String> {
    self.lock().await.abilities().await
  }
}
