use std::future::Future;

#[cfg(not(target_arch = "wasm32"))]
#[allow(unused)]
use tokio::sync::oneshot;

#[cfg(target_arch = "wasm32")]
use std::cell::RefCell;

#[cfg(target_arch = "wasm32")]
use std::rc::Rc;

#[cfg(target_arch = "wasm32")]
use futures_channel::oneshot;

// Определяем Handle
pub struct SpawnHandle<T> {
  #[cfg(not(target_arch = "wasm32"))]
  join_handle: tokio::task::JoinHandle<T>,

  #[cfg(target_arch = "wasm32")]
  shared_state: Rc<RefCell<SharedState<T>>>,
}

#[cfg(target_arch = "wasm32")]
struct SharedState<T> {
  result: Option<Result<T, String>>,
  receiver: Option<oneshot::Receiver<T>>,
  cancel_flag: bool,
}

#[cfg(target_arch = "wasm32")]
impl<T> SharedState<T> {
  fn new(receiver: oneshot::Receiver<T>) -> Self {
    Self {
      result: None,
      receiver: Some(receiver),
      cancel_flag: false,
    }
  }
}

impl<T> SpawnHandle<T>
where
  T: Send + 'static + Clone,
{
  /// Дождаться завершения задачи и получить результат
  pub async fn join(self) -> Result<T, Box<dyn std::error::Error + Send + Sync>> {
    #[cfg(not(target_arch = "wasm32"))]
    {
      match self.join_handle.await {
        Ok(result) => Ok(result),
        Err(e) if e.is_cancelled() => Err("Task was cancelled".into()),
        Err(e) => Err(e.into()),
      }
    }
    #[cfg(target_arch = "wasm32")]
    {
      let mut state = self.shared_state.borrow_mut();
      if let Some(ref result) = state.result {
        match result {
          Ok(v) => Ok(v.clone()),
          Err(e) => Err(e.clone().into()),
        }
      } else {
        if let Some(receiver) = state.receiver.take() {
          let res = receiver.await;
          let result = match res {
            Ok(v) => Ok(v),
            Err(_) => Err("Task was cancelled or dropped".to_string()),
          };
          state.result = Some(result.clone());
          match result {
            Ok(v) => Ok(v),
            Err(e) => Err(e.into()),
          }
        } else {
          // Если receiver уже забран, но результат ещё не сохранён — ждать нечего
          if let Some(ref result) = state.result {
            match result {
              Ok(v) => Ok(v.clone()),
              Err(e) => Err(e.clone().into()),
            }
          } else {
            // Это может произойти, если вызов происходит после отмены
            Err("Task was cancelled or dropped".into())
          }
        }
      }
    }
  }

  pub fn abort(&self) {
    #[cfg(not(target_arch = "wasm32"))]
    {
      self.join_handle.abort();
    }
    #[cfg(target_arch = "wasm32")]
    {
      self.shared_state.borrow_mut().cancel_flag = true;
    }
  }

  pub fn is_finished(&self) -> bool {
    #[cfg(not(target_arch = "wasm32"))]
    {
      self.join_handle.is_finished()
    }
    #[cfg(target_arch = "wasm32")]
    {
      self.shared_state.borrow().result.is_some()
    }
  }

  pub fn is_running(&self) -> bool {
    !self.is_finished()
  }
}

/*
// А теперь функция spawn, возвращающая SpawnHandle
pub fn spawn<F, T>(future: F) -> SpawnHandle<T>
where
  F: Future<Output = T> + Send + 'static,
  T: Send + 'static,
{
  #[cfg(not(target_arch = "wasm32"))]
  {
    let join_handle = tokio::spawn(future);
    SpawnHandle { join_handle }
  }

  #[cfg(target_arch = "wasm32")]
  {
    use futures_channel::oneshot;
    let (sender, receiver) = oneshot::channel();

    let shared_state = Rc::new(RefCell::new(SharedState::new(receiver)));

    let shared_clone = shared_state.clone();
    wasm_bindgen_futures::spawn_local(async move {
      let state = shared_clone.borrow();
      if state.cancel_flag {
        drop(sender); // отмена
        return;
      }
      drop(state);

      let result = future.await;

      // отправляем результат
      let _ = sender.send(result);
    });

    SpawnHandle { shared_state }
  }
}
*/

#[cfg(not(target_arch = "wasm32"))]
pub fn spawn<F, T>(future: F) -> SpawnHandle<T>
where
  F: Future<Output = T> + Send + 'static,
  T: Send + 'static,
{
  let join_handle = tokio::spawn(future);
  SpawnHandle { join_handle }
}

#[cfg(target_arch = "wasm32")]
pub fn spawn<F, T>(future: F) -> SpawnHandle<T>
where
  F: Future<Output = T> + 'static, // Убрано Send для WASM
  T: Send + 'static,
{
  use futures_channel::oneshot;
  let (sender, receiver) = oneshot::channel();

  let shared_state = Rc::new(RefCell::new(SharedState::new(receiver)));

  let shared_clone = shared_state.clone();
  wasm_bindgen_futures::spawn_local(async move {
    let state = shared_clone.borrow();
    if state.cancel_flag {
      drop(sender);
      return;
    }
    drop(state);

    let result = future.await;
    let _ = sender.send(result);
  });

  SpawnHandle { shared_state }
}
