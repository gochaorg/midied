use super::*;
use std::sync::Arc;

use reqwest::{Method, StatusCode};
use serde::Serialize;

#[derive(Debug, Clone)]
pub struct FsHttpClient {
  pub base_addr: Arc<String>,
  pub http_client: Arc<reqwest::Client>,
}

#[derive(Debug, Default)]
pub struct FsHttpClientBuilder {
  pub base_addr: String,
}

impl FsHttpClient {
  pub fn new(addr: &str) -> FsHttpClientBuilder {
    FsHttpClientBuilder {
      base_addr: addr.to_string(),
      ..FsHttpClientBuilder::default()
    }
  }

  pub fn boxed(self) -> Fs {
    Arc::new(futures::lock::Mutex::new(self)).boxed()
  }
}

impl FsHttpClientBuilder {
  pub fn build(&mut self) -> Result<FsHttpClient, String> {
    let client = reqwest::Client::builder()
      .build()
      .map_err(|e| e.to_string())?;

    Ok(FsHttpClient {
      base_addr: Arc::new(self.base_addr.clone()),
      http_client: Arc::new(client),
    })
  }
}

impl FsHttpClient {
  pub async fn list<PATH: AsRef<str>>(&self, dir: PATH) -> Result<Vec<DirEntry>, String> {
    let dir = dir.as_ref();
    let res = self
      .http_client
      .get(format!("{base}{dir}", base = self.base_addr, dir = dir))
      .header("Accept", "application/json")
      .send()
      .await
      .map_err(|e| format!("network: {e}"))
      .and_then(|r| match r.status() {
        StatusCode::OK => Ok(r),
        _ => Err(format!("expect status 200, actual {}", r.status())),
      })?;

    let text = res
      .text()
      .await
      .map_err(|e| format!("text of response: {e}"))?;

    let res = serde_json::from_str::<Vec<DirEntry>>(&text).map_err(|e| format!("json: {e}"))?;
    Ok(res)
  }
}

#[test]
fn dir_entry_json_test() {
  let json = r#"[
    {
      "type": "dir",
      "name": "compileTestJava",
      "createTime": "2026-04-05T10:29:18.168586683Z",
      "modifyTime": "2026-04-05T10:29:18.168586683Z"
    },
    {
      "type": "file",
      "name": "test123",
      "createTime": "2026-04-09T19:21:31.637536977Z",
      "modifyTime": "2026-04-09T19:21:31.637536977Z",
      "size": 3
    }]
    "#;

  let entries = serde_json::from_str::<Vec<DirEntry>>(json).unwrap();

  println!("{entries:?}");
}

#[cfg(not(target_arch = "wasm32"))]
#[test]
fn list_test() {
  use tokio::runtime::Builder;
  let _runtime = Builder::new_multi_thread()
    .worker_threads(4)
    .enable_all()
    .build()
    .unwrap()
    .block_on(async {
      let client = FsHttpClient::new("http://localhost:8899/static")
        .build()
        .unwrap();

      let res = client.list("/").await;
      println!("result {res:?}");
    });
}

impl FsHttpClient {
  pub async fn delete<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String> {
    let path = path.as_ref();
    self
      .http_client
      .delete(format!("{base}{path}", base = self.base_addr))
      .header("fs-op-recursive", "true")
      .header("fs-op-follow-links", "true")
      .send()
      .await
      .map_err(|e| format!("network, io, at write(path={path}): {e}"))
      .and_then(|r| match r.status() {
        StatusCode::OK => Ok(r),
        _ => Err(format!("expect status 200, actual {}", r.status())),
      })?;
    Ok(())
  }
}

#[cfg(not(target_arch = "wasm32"))]
#[test]
fn delete_test() {
  use tokio::runtime::Builder;
  let _runtime = Builder::new_multi_thread()
    .worker_threads(4)
    .enable_all()
    .build()
    .unwrap()
    .block_on(async {
      let client = FsHttpClient::new("http://localhost:8899/static")
        .build()
        .unwrap();

      let res = client.delete("/test123").await;
      println!("result {res:?}");
    });
}

impl FsHttpClient {
  pub async fn write<PATH: AsRef<str>, BYTES: AsRef<[u8]>>(
    &self,
    path: PATH,
    bytes: BYTES,
  ) -> Result<(), String> {
    let bytes = bytes.as_ref().to_vec();
    let path = path.as_ref();
    let _res = self
      .http_client
      .post(format!("{base}{path}", base = self.base_addr))
      .body(bytes)
      .send()
      .await
      .map_err(|e| format!("network, io, at write(path={path}): {e}"))
      .and_then(|r| match r.status() {
        StatusCode::OK => Ok(r),
        _ => Err(format!("expect status 200, actual {}", r.status())),
      })?;

    Ok(())
  }
}

#[cfg(not(target_arch = "wasm32"))]
#[test]
fn write_test() {
  use tokio::runtime::Builder;
  let _runtime = Builder::new_multi_thread()
    .worker_threads(4)
    .enable_all()
    .build()
    .unwrap()
    .block_on(async {
      let client = FsHttpClient::new("http://localhost:8899/static")
        .build()
        .unwrap();

      let res = client.write("/test123", "abc").await;
      println!("result {res:?}");
    });
}

impl FsHttpClient {
  pub async fn read<PATH: AsRef<str>>(&self, path: PATH) -> Result<Vec<u8>, String> {
    let path = path.as_ref();
    let res = self
      .http_client
      .get(format!("{base}{path}", base = self.base_addr))
      .send()
      .await
      .map_err(|e| format!("network, io, at read(path={path}): {e}"))
      .and_then(|r| match r.status() {
        StatusCode::OK => Ok(r),
        _ => Err(format!("expect status 200, actual {}", r.status())),
      })?;

    let bytes = res
      .bytes()
      .await
      .map_err(|e| format!("network, extract body: {e}"))?
      .to_vec();

    Ok(bytes)
  }
}

#[cfg(not(target_arch = "wasm32"))]
#[test]
fn read_test() {
  use tokio::runtime::Builder;
  let _runtime = Builder::new_multi_thread()
    .worker_threads(4)
    .enable_all()
    .build()
    .unwrap()
    .block_on(async {
      let client = FsHttpClient::new("http://localhost:8899/static")
        .build()
        .unwrap();

      let res = client.read("/test123").await;
      println!("result {res:?}");

      let bytes = res.unwrap();
      let res = String::from_utf8(bytes).unwrap();
      println!("result text {res}");
    });
}

impl FsHttpClient {
  pub async fn abilities(&self) -> Result<FsAbilities, String> {
    let res = self
      .http_client
      .request(
        Method::from_bytes(b"abilities").unwrap(),
        format!("{base}/", base = self.base_addr),
      )
      .send()
      .await
      .map_err(|e| format!("network, io, at abilities(): {e}"))
      .and_then(|r| match r.status() {
        StatusCode::OK => Ok(r),
        _ => Err(format!("expect status 200, actual {}", r.status())),
      })?;

    let text = res
      .text()
      .await
      .map_err(|e| format!("network, extract body: {e}"))?;

    let arr =
      serde_json::from_str::<Vec<String>>(&text).map_err(|e| format!("extract body json: {e}"))?;

    Ok(FsAbilities { abilities: arr })
  }
}

#[cfg(not(target_arch = "wasm32"))]
#[test]
fn abilities_test() {
  use tokio::runtime::Builder;
  let _runtime = Builder::new_multi_thread()
    .worker_threads(4)
    .enable_all()
    .build()
    .unwrap()
    .block_on(async {
      let client = FsHttpClient::new("http://localhost:8899/static")
        .build()
        .unwrap();

      let res = client.abilities().await;
      println!("result {res:?}");

      // let bytes = res.unwrap();
      // let res = String::from_utf8(bytes).unwrap();
      // println!("result text {res}");
    });
}

impl FsHttpClient {
  pub async fn mkdir<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String> {
    let path = path.as_ref();
    let _res = self
      .http_client
      .request(
        Method::from_bytes(b"mkdir").unwrap(),
        format!("{base}{path}", base = self.base_addr),
      )
      .send()
      .await
      .map_err(|e| format!("network, io, at mkdir(path={path}): {e}"))
      .and_then(|r| match r.status() {
        StatusCode::OK => Ok(r),
        _ => Err(format!("expect status 200, actual {}", r.status())),
      })?;

    Ok(())
  }
}

impl FsHttpClient {
  pub async fn rename<PATH1: AsRef<str>, PATH2: AsRef<str>>(
    &self,
    source_path: PATH1,
    target_path: PATH2,
  ) -> Result<(), String> {
    #[derive(Debug, Serialize)]
    struct Body {
      source: String,
      target: String,
    };

    let body = serde_json::to_string(&Body {
      source: source_path.as_ref().to_string(),
      target: target_path.as_ref().to_string(),
    })
    .map(|json| json.into_bytes())
    .map_err(|e| format!("json encode: {e}"))?;

    let _res = self
      .http_client
      .request(
        Method::from_bytes(b"rename").unwrap(),
        format!("{base}/", base = self.base_addr),
      )
      .body(body)
      .send()
      .await
      .map_err(|e| {
        format!(
          "network, io, at rename(source={s}, target={t}): {e}",
          s = source_path.as_ref(),
          t = target_path.as_ref(),
        )
      })
      .and_then(|r| match r.status() {
        StatusCode::OK => Ok(r),
        _ => Err(format!("expect status 200, actual {}", r.status())),
      })?;

    Ok(())
  }
}

#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl FsClient for FsHttpClient {
  async fn list<PATH: AsRef<str>>(&self, dir: PATH) -> Result<Vec<DirEntry>, String> {
    let res = self.list(dir);
    let res = res.await;
    res
  }

  async fn delete<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String> {
    self.delete(path).await
  }

  async fn write<PATH: AsRef<str>, BYTES: AsRef<[u8]>>(
    &self,
    path: PATH,
    bytes: BYTES,
  ) -> Result<(), String> {
    self.write(path, bytes).await
  }

  async fn read<PATH: AsRef<str>>(&self, path: PATH) -> Result<Vec<u8>, String> {
    self.read(path).await
  }

  async fn mkdir<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String> {
    self.mkdir(path).await
  }

  async fn abilities(&self) -> Result<FsAbilities, String> {
    self.abilities().await
  }
}

#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl FsClient for Arc<futures::lock::Mutex<FsHttpClient>> {
  async fn list<PATH: AsRef<str>>(&self, dir: PATH) -> Result<Vec<DirEntry>, String> {
    self.lock().await.list(dir).await
  }

  async fn delete<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String> {
    self.lock().await.delete(path).await
  }

  async fn write<PATH: AsRef<str>, BYTES: AsRef<[u8]>>(
    &self,
    path: PATH,
    bytes: BYTES,
  ) -> Result<(), String> {
    self.lock().await.write(path, bytes).await
  }

  async fn read<PATH: AsRef<str>>(&self, path: PATH) -> Result<Vec<u8>, String> {
    self.lock().await.read(path).await
  }

  async fn mkdir<PATH: AsRef<str>>(&self, path: PATH) -> Result<(), String> {
    self.lock().await.mkdir(path).await
  }

  async fn abilities(&self) -> Result<FsAbilities, String> {
    self.lock().await.abilities().await
  }
}
