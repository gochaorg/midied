use crate::{events_json::JsonMidiEvent, EventTime};

use super::super::Event;
use reqwest::StatusCode;
use std::sync::{Arc, Mutex};

#[derive(Clone)]
pub struct MidiInput {
  pub base_addr: Arc<String>,
  pub http_client: Arc<reqwest::Client>,
  pub new_reader_timestamp_shift: TimestampShift,
}

#[derive(Debug, Clone, Copy)]
pub enum TimestampShift {
  NoShift,
  Now,
  Nanos(u64),
}

#[derive(Debug, Clone)]
pub enum MidiErr {
  Custom(String),
  ClientNotFound { id: String },
}

impl MidiErr {
  pub fn network(err: reqwest::Error) -> Self {
    MidiErr::Custom(format!("network {}", err))
  }

  pub fn un_expected_status() -> Self {
    MidiErr::Custom(format!("un_expected_status"))
  }

  pub fn expect_text(err: reqwest::Error) -> Self {
    MidiErr::Custom(format!("expect_text {}", err))
  }

  pub fn bad_response(err: reqwest::Error) -> Self {
    MidiErr::Custom(format!("bad_response {}", err))
  }

  pub fn service_unavailable() -> Self {
    MidiErr::Custom(format!("service_unavailable"))
  }

  pub fn json(err: serde_json::Error) -> Self {
    MidiErr::Custom(format!("json {}", err))
  }

  pub fn build_http_client(err: reqwest::Error) -> Self {
    MidiErr::Custom(format!("build_http_client {}", err))
  }
}

impl MidiInput {
  pub fn new(addr: &str) -> MidiInputBuilder {
    MidiInputBuilder::new(addr)
  }
}

pub struct MidiInputBuilder {
  pub base_addr: String,
  pub new_reader_timestamp_shift: TimestampShift,
}

impl MidiInputBuilder {
  pub fn new(addr: &str) -> Self {
    MidiInputBuilder {
      base_addr: addr.to_string(),
      new_reader_timestamp_shift: TimestampShift::Now,
    }
  }

  pub fn timestamp_shift_none(mut self) -> Self {
    self.new_reader_timestamp_shift = TimestampShift::NoShift;
    self
  }

  pub fn timestamp_shift_now(mut self) -> Self {
    self.new_reader_timestamp_shift = TimestampShift::Now;
    self
  }

  pub fn build(&self) -> Result<MidiInput, MidiErr> {
    let client = reqwest::Client::builder()
      .build()
      .map_err(|e| MidiErr::build_http_client(e))?;

    Ok(MidiInput {
      base_addr: Arc::new(self.base_addr.clone()),
      http_client: Arc::new(client),
      new_reader_timestamp_shift: TimestampShift::Now,
    })
  }
}

#[allow(dead_code)]
#[derive(serde::Deserialize, Debug)]
struct NewReaderResponse {
  pub id: String,
  pub millis: u64,
  pub nano: u64,
  pub time: chrono::DateTime<chrono::Utc>,
}

#[test]
fn test_new_reader_response() {
  let json = r#"
    {
      "id": "l1dla",
      "millis": 1772486734667,
      "nano": 58315447059156,
      "time": "2026-03-02T21:25:34.667370183Z"
    }
    "#;

  let resp: NewReaderResponse = serde_json::from_str(json).unwrap();
  println!("{resp:?}");
  assert_eq!(resp.id, "l1dla");
  assert_eq!(resp.millis, 1772486734667u64);
  assert_eq!(resp.nano, 58315447059156u64);
}

impl MidiInput {
  pub async fn new_reader(&self) -> Result<MidiInputReader, MidiErr> {
    let mut opts: Vec<String> = Vec::new();

    match self.new_reader_timestamp_shift {
      TimestampShift::Nanos(n) => {
        opts.push(format!("timestamp-shift={n}"));
      }
      TimestampShift::Now => {
        opts.push(format!("timestamp-shift=now"));
      }
      TimestampShift::NoShift => {}
    }

    let result = self
      .http_client
      .post(format!(
        "{a}/new{qs}",
        a = self.base_addr,
        qs = {
          if !opts.is_empty() {
            let mut s = opts.join("&");
            s.insert_str(0, "?");
            s
          } else {
            "".to_string()
          }
        }
      ))
      .send()
      .await
      .map_err(|e| MidiErr::network(e))?;

    let status = result.status();
    match status {
      StatusCode::SERVICE_UNAVAILABLE => Err(MidiErr::service_unavailable()),
      StatusCode::OK => {
        let text = result.text().await.map_err(|e| MidiErr::bad_response(e))?;

        let resp: NewReaderResponse = serde_json::from_str(&text).map_err(|e| MidiErr::json(e))?;
        Ok(MidiInputReader::new(self.clone(), resp.id))
      }
      _ => Err(MidiErr::un_expected_status()),
    }
  }
}

#[test]
fn new_reader() {
  //use std::time::Duration;

  let rt = tokio::runtime::Runtime::new().unwrap();
  rt.block_on(async {
    let client = MidiInput::new("http://localhost:8899/client")
      .build()
      .unwrap();

    println!("new reader");
    let rd = client.new_reader().await.unwrap();

    //tokio::time::sleep(Duration::from_millis(500)).await;
    println!("id {:?}", rd.reader_id());
  })
}

pub struct PositiveMillisec(pub u32);

impl From<EventTime> for PositiveMillisec {
  fn from(value: EventTime) -> Self {
    let ms = value.stamp_microseconds / 1000;
    let ms = if ms > u32::MAX as u64 {
      u32::MAX
    } else {
      ms as u32
    };
    PositiveMillisec(ms)
  }
}

#[derive(Clone)]
pub struct MidiInputReader {
  client: Arc<MidiInput>,
  id: Arc<String>,
  already_closed: Arc<Mutex<bool>>,
  wait_if_empty_ms: u32,
  remove_after_read: bool,
}

impl MidiInputReader {
  pub fn new(input: MidiInput, id: String) -> Self {
    Self {
      client: Arc::new(input),
      id: Arc::new(id),
      already_closed: Arc::new(Mutex::new(false)),
      wait_if_empty_ms: 0,
      remove_after_read: true,
    }
  }

  pub fn reader_id(&self) -> Arc<String> {
    self.id.clone()
  }

  pub fn set_await_if_empty<D: Into<PositiveMillisec>>(&mut self, duration: D) {
    let ms: PositiveMillisec = duration.into();
    self.wait_if_empty_ms = ms.0
  }

  pub fn set_remove_after_read(&mut self, removing: bool) {
    self.remove_after_read = removing;
  }

  pub async fn poll(&self) -> Result<Vec<Event>, MidiErr> {
    let mut opts: Vec<String> = Vec::new();
    if self.wait_if_empty_ms > 0 {
      opts.push(format!("wait-if-empty-ms={}", self.wait_if_empty_ms));
    }
    if self.remove_after_read {
      opts.push(format!("remove-after-read=1"));
    }

    let http_res = self
      .client
      .http_client
      .get(format!(
        "{a}/{id}/events{qs}",
        a = self.client.base_addr,
        id = self.id,
        qs = {
          if !opts.is_empty() {
            let mut s = opts.join("&");
            s.insert_str(0, "?");
            s
          } else {
            "".to_string()
          }
        }
      ))
      .send()
      .await
      .map_err(|e| MidiErr::network(e))?;

    match http_res.status() {
      StatusCode::BAD_REQUEST => Err(MidiErr::ClientNotFound {
        id: self.id.to_string(),
      }),
      StatusCode::OK => {
        let text = http_res.text().await.map_err(|e| MidiErr::expect_text(e))?;

        let events: Vec<JsonMidiEvent> =
          serde_json::from_str(&text).map_err(|e| MidiErr::json(e))?;

        let base_time = EventTime::from_ms(0);

        let events: Vec<Event> = events
          .iter()
          .flat_map(|js_event| {
            Some(js_event.as_event(|t| EventTime {
              stamp_microseconds: t.timestamp() / 1000,
            }))
            .and_then(|ev| (ev.time() - base_time.to_timedelta()).map(|t| ev.with_time(t)))
          })
          .collect();

        Ok(events)
      }
      _ => Err(MidiErr::un_expected_status()),
    }
  }

  pub async fn close(&self) -> Result<(), MidiErr> {
    let http_res = self
      .client
      .http_client
      .delete(format!("{a}/{id}", a = self.client.base_addr, id = self.id))
      .send()
      .await
      .map_err(|e| MidiErr::network(e))?;

    match http_res.status() {
      StatusCode::BAD_REQUEST => Err(MidiErr::ClientNotFound {
        id: self.id.to_string(),
      }),
      StatusCode::OK => {
        match self.already_closed.lock() {
          Ok(mut x) => *x = true,
          _ => {}
        };

        Ok(())
      }
      _ => Err(MidiErr::un_expected_status()),
    }
  }
}

//#[test]
// fn test_poll() {
//   let mut rt = tokio::runtime::Runtime::new().unwrap();
//   rt.block_on(async {
//     let client = MidiInput::new("http://localhost:8899/client")
//       .build()
//       .unwrap();

//     println!("new reader");
//     let mut reader = client.new_reader().await.unwrap();

//     println!("id {:?}", reader.reader_id());

//     reader.set_await_if_empty(EventTime::from_seconds(20));
//     reader.set_remove_after_read(true);

//     println!("pool");
//     match reader.poll().await {
//       Err(err) => {
//         println!("error {err:?}")
//       }
//       Ok(events) => {
//         println!("accept {} events", events.len());
//         events.iter().for_each(|ev| {
//           println!("event {:?}", ev);
//         });
//       }
//     }

//     let _ = reader.close();
//   })
// }
