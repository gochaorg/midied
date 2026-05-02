use std::{io::Write, sync::Arc};

use fs::*;
use rhai::{Dynamic, Engine, EvalAltResult};
use tokio::runtime::Builder;

fn main() -> Result<(), String> {
  // let _runtime = Builder::new_current_thread()
  //   .worker_threads(4)
  //   .enable_all()
  //   .build()
  //   .unwrap()
  //   .block_on(async {
  //     let client = FsHttpClient::new("http://localhost:8899/static")
  //       .build()
  //       .unwrap();
  //     let res = client.list("/").await;
  //     println!("result {res:?}");
  //   });
  //

  let mut cli = CLI::default();
  cli.main()
}

#[test]
fn test_script() {
  let engine = Engine::new();
  let result = engine.eval::<Dynamic>("1 + 2");
  assert!(result.is_ok());

  let result = result.unwrap();
  println!("{result:?}");
}

struct CLI {
  exit: Arc<std::sync::Mutex<bool>>,
  promt: Arc<std::sync::Mutex<Prompt>>,
  engine: Engine,
}

impl Default for CLI {
  fn default() -> Self {
    let exit_flag = Arc::new(std::sync::Mutex::new(false));
    let exit_flag1 = exit_flag.clone();

    let prompt0: Arc<std::sync::Mutex<Prompt>> = Default::default();

    let mut engine = Engine::new();
    engine.register_fn("exit", move || {
      match exit_flag1.lock() {
        Ok(mut flag) => {
          *flag = true;
        }
        Err(e) => {
          println!("can't lock exit flag {e}");
        }
      };
    });

    Self {
      exit: exit_flag,
      promt: prompt0,
      engine: engine,
    }
  }
}

impl CLI {
  fn main(&mut self) -> Result<(), String> {
    while !{
      self
        .exit
        .lock()
        .map_err(|e| format!("can't lock exit flag {e}"))?
        .clone()
    } {
      let prompt = {
        match self.promt.lock() {
          Ok(p) => Some(p.get_prompt()),
          Err(e) => {
            println!("can't lock prompt {e}");
            None
          }
        }
      };

      std::io::stdout()
        .write(&(prompt.unwrap_or("(no prompt)> ".to_string())).into_bytes())
        .map_err(|e| format!("write stdout {e}"))?;

      std::io::stdout()
        .flush()
        .map_err(|e| format!("flush stdout {e}"))?;

      let mut input_line = String::new();
      std::io::stdin()
        .read_line(&mut input_line)
        .map_err(|e| format!("read stdin {e}"))?;

      if input_line.trim() == ":q" {
        break;
      }

      if let Err(e) = self.engine.run(&input_line) {
        println!("can't run {e}");
      }
    }

    {
      if let Ok(p) = self.promt.lock() {
        if let Some(t) = p.get_goodbie() {
          println!("{t}");
        }
      }
    }

    Ok(())
  }
}

#[derive(Default)]
struct Prompt {}

impl Prompt {
  fn get_prompt(&self) -> String {
    "> ".to_string()
  }

  fn get_goodbie(&self) -> Option<String> {
    Some("finished".to_string())
  }
}
