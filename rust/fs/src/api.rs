use std::sync::Arc;

use async_trait::async_trait;
use futures::lock::Mutex;
use serde::Deserialize;

#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
pub trait FsClient {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String>;

  async fn delete(&self, path: &str) -> Result<(), String>;

  async fn write(&self, path: &str, bytes: &[u8]) -> Result<(), String>;

  async fn read(&self, path: &str) -> Result<Vec<u8>, String>;

  async fn mkdir(&self, path: &str) -> Result<(), String>;

  async fn rename(&self, source_path: &str, target_path: &str) -> Result<(), String>;
}

/// Содержание каталога - файл / каталог
#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "type")]
pub enum DirEntry {
  #[serde(rename = "file")]
  File { name: String, size: u64 },

  #[serde(rename = "dir")]
  Dir { name: String },
}

#[cfg(target_arch = "wasm32")]
pub type Fs = Arc<Mutex<dyn FsClient>>;

#[cfg(not(target_arch = "wasm32"))]
pub type Fs = Arc<Mutex<dyn FsClient + Send>>;
