use super::*;
use async_trait::async_trait;
use futures::lock::Mutex;
use std::sync::Arc;

// #[cfg(not(target_arch = "wasm32"))]
// pub trait FsClientAdopt {
//   fn read_dir<A: Into<ListArgs>>(
//     &self,
//     dir: A,
//   ) -> Pin<Box<dyn Future<Output = Result<Vec<DirEntry>, String>> + Send + '_>>;
// }

// #[cfg(not(target_arch = "wasm32"))]
// impl<C: FsClient> FsClientAdopt for C {
//   fn read_dir<A: Into<ListArgs>>(
//     &self,
//     dir: A,
//   ) -> Pin<Box<dyn Future<Output = Result<Vec<DirEntry>, String>> + Send + '_>> {
//     Box::pin(self.list(dir.into()))
//   }
// }

// impl From<&str> for ListArgs {
//   fn from(value: &str) -> Self {
//     Self {
//       dir: value.to_string(),
//     }
//   }
// }

// impl From<String> for ListArgs {
//   fn from(value: String) -> Self {
//     Self { dir: value }
//   }
// }

#[cfg(target_arch = "wasm32")]
#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl FsClient for Arc<Mutex<dyn FsClient>> {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String> {
    self.lock().await.list(dir).await
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

  async fn delete(&self, path: &str) -> Result<(), String> {
    self.lock().await.delete(path).await
  }

  async fn rename(&self, source_path: &str, target_path: &str) -> Result<(), String> {
    self.lock().await.rename(source_path, target_path).await
  }
}

#[cfg(not(target_arch = "wasm32"))]
#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl FsClient for Arc<Mutex<dyn FsClient + Send>> {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String> {
    self.lock().await.list(dir).await
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

  async fn delete(&self, path: &str) -> Result<(), String> {
    self.lock().await.delete(path).await
  }

  async fn rename(&self, source_path: &str, target_path: &str) -> Result<(), String> {
    self.lock().await.rename(source_path, target_path).await
  }
}
