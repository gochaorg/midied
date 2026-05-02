use super::*;
use async_trait::async_trait;
use reqwest::{Method, StatusCode};
use serde::Serialize;
use std::sync::Arc;

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

#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl FsClient for FsHttpClient {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String> {
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

  async fn write(&self, path: &str, bytes: &[u8]) -> Result<(), String> {
    let bytes = bytes.as_ref().to_vec();
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

  async fn read(&self, path: &str) -> Result<Vec<u8>, String> {
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

  async fn mkdir(&self, path: &str) -> Result<(), String> {
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

  async fn delete(&self, path: &str) -> Result<(), String> {
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

  async fn rename(&self, source_path: &str, target_path: &str) -> Result<(), String> {
    #[derive(Debug, Serialize)]
    struct Body {
      source: String,
      target: String,
    }

    let body = serde_json::to_string(&Body {
      source: source_path.to_string(),
      target: target_path.to_string(),
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
          s = source_path,
          t = target_path,
        )
      })
      .and_then(|r| match r.status() {
        StatusCode::OK => Ok(r),
        _ => Err(format!("expect status 200, actual {}", r.status())),
      })?;

    Ok(())
  }
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

      //let res = client.read_dir("/").await;
      let res = client.list("/").await;
      println!("result {res:?}");
    });
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

      let res = client.write("/test123", b"abc").await;
      println!("result {res:?}");
    });
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
