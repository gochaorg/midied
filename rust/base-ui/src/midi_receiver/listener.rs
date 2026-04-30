use crate::SpawnHandle;
use crate::spawn;
use futures::lock::Mutex;
use midi_model::Event;
use midi_model::midi_input::MidiInputReader;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering::SeqCst;

#[allow(dead_code)]
//#[test]
#[cfg(not(target_arch = "wasm32"))]
fn test1() {
  use midi_model::midi_input::{MidiInput, PositiveMillisec, TimestampShift};
  use tokio::runtime::Builder;

  let runtime = Builder::new_multi_thread()
    // 2. Configure specific settings
    .worker_threads(4)
    .enable_all()
    .build()
    .unwrap();

  runtime.block_on(async {
    let result = spawn(async {
      println!("async exec");

      let mut client = MidiInput::new("http://localhost:8080/client")
        .build()
        .unwrap();

      client.new_reader_timestamp_shift = TimestampShift::Now;

      println!("wait new reader");
      match client.new_reader().await {
        Ok(mut reader) => {
          println!("client created, id={id}", id = reader.reader_id().clone());

          reader.set_await_if_empty(PositiveMillisec(20_000u32));

          println!("poll");
          match reader.poll().await {
            Ok(events) => {
              println!("succ fetch events ({cnt})", cnt = events.len());
              for ev in events.iter() {
                println!("event {ev:?}");
              }
            }
            Err(err) => {
              println!("fail fetch events {err:?}")
            }
          }

          let _ = reader.close().await;
        }
        Err(err) => {
          println!("fail create reader {err:?}");
        }
      };

      1
    });

    let res = result.join().await;
    println!("result {res:?}")
  });
}

pub struct MidiLisener {
  stop_signal: Arc<AtomicBool>,
  midi_reader: Arc<Mutex<MidiInputReader>>,
  worker: Option<SpawnHandle<()>>,
}

impl MidiLisener {
  pub fn new(midi_reader: MidiInputReader) -> Self {
    Self {
      stop_signal: Arc::new(AtomicBool::new(false)),
      midi_reader: Arc::new(Mutex::new(midi_reader)),
      worker: None,
    }
  }

  pub fn is_running(&self) -> bool {
    if self.worker.is_some() {
      let h = self.worker.as_ref().unwrap();
      return h.is_running();
    } else {
      return false;
    }
  }

  pub fn start<F: Fn(&Event) + Send + Sync + 'static>(&mut self, f: F) {
    if self.worker.is_some() {
      let h = self.worker.as_ref().unwrap();
      if h.is_running() {
        return;
      }
    }

    self.stop_signal.store(false, SeqCst);

    let ls = MidiListenerWork {
      stop_signal: self.stop_signal.clone(),
      midi_reader: self.midi_reader.clone(),
      midi_event_consumer: f,
    };

    self.worker = Some(spawn(async move {
      ls.run().await;
    }));
  }

  pub fn stop(&self) {
    self.stop_signal.store(true, SeqCst);
  }
}

pub struct MidiListenerWork<F> {
  pub stop_signal: Arc<AtomicBool>,
  pub midi_reader: Arc<Mutex<MidiInputReader>>,
  pub midi_event_consumer: F,
}

impl<F: Fn(&Event)> MidiListenerWork<F> {
  pub async fn run(&self) {
    loop {
      let stop_now = self.stop_signal.load(SeqCst);

      if stop_now {
        return;
      }

      let reader = self.midi_reader.lock().await;
      let res = reader.poll().await;

      match res {
        Ok(events) => {
          for ev in events.iter() {
            (self.midi_event_consumer)(ev);
          }
        }
        Err(_err) => {
          //println!("poll got err {err:?}");
        }
      }
    }
  }
}

//#[allow(dead_code)]
#[test]
#[cfg(not(target_arch = "wasm32"))]
fn test2() {
  use midi_model::midi_input::{MidiInput, PositiveMillisec, TimestampShift};
  use std::time::Duration;
  use tokio::runtime::Builder;

  let runtime = Builder::new_multi_thread()
    .worker_threads(4)
    .enable_all()
    .build()
    .unwrap();

  runtime.block_on(async {
    let mut client = MidiInput::new("http://localhost:8080/client")
      .build()
      .unwrap();

    client.new_reader_timestamp_shift = TimestampShift::Now;

    let t = chrono::Utc::now();
    println!("{t:?} wait new reader");
    let mut reader = match client.new_reader().await {
      Err(err) => {
        println!("fail new client {err:?}");
        todo!()
      }
      Ok(reader) => reader,
    };

    let t = chrono::Utc::now();
    println!("{t:?} reader {id} created", id = reader.reader_id());
    reader.set_await_if_empty(PositiveMillisec(20_000u32));

    let listener = Arc::new(Mutex::new(MidiLisener::new(reader)));

    spawn(async move {
      let mut ls = listener.lock().await;
      ls.start(|ev| {
        let t = chrono::Utc::now();
        println!("{t:?} got {ev:?}");
      });
    });

    let t = chrono::Utc::now();
    println!("{t:?} wait 15 sec");
    tokio::time::sleep(Duration::from_secs(15)).await;
  });
}
