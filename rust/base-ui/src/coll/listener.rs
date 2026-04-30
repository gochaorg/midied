use std::collections::{HashMap, HashSet};
use std::sync::{Arc, RwLock, Weak};

use crate::coll::Id;

pub trait EventSource<A: 'static + Send + Sync>: Send + Sync {
  fn add_listener(&self, ls: impl Fn(A) + Send + Sync + 'static) -> Id;
  fn remove_listener(&self, id: Id);
}

pub trait EventSourceWeak<A: 'static + Send + Sync>: Send + Sync + EventSource<A> {
  fn add_weak_listener(&self, ls: &Arc<dyn Fn(A) + Send + Sync>) -> Id;
}

pub trait EventEmmiter<A> {
  fn emit(&self, a: A);
}

#[derive(Default)]
pub struct EventProducer<A> {
  state: RwLock<EventProducerState<A>>,
}

#[derive(Default)]
struct EventProducerState<A> {
  counter: usize,
  weak_listeners: HashMap<usize, Weak<dyn Fn(A) + Send + Sync>>,
  hard_listeners: HashMap<usize, Box<dyn Fn(A) + Send + Sync>>,
}

impl<A: 'static + Send + Sync> EventSource<A> for EventProducer<A> {
  fn add_listener(&self, ls: impl Fn(A) + Send + Sync + 'static) -> Id {
    if let Ok(mut state) = self.state.write() {
      let cnt = state.counter;
      state.counter += 1;
      state.hard_listeners.insert(cnt, Box::new(ls));
      return Id(cnt);
    }

    panic!("can't lock");
  }

  fn remove_listener(&self, id: Id) {
    if let Ok(mut state) = self.state.write() {
      state.hard_listeners.remove(&id.0);
    }
  }
}

impl<A: 'static + Send + Sync> EventSourceWeak<A> for EventProducer<A> {
  fn add_weak_listener(&self, ls: &Arc<dyn Fn(A) + Send + Sync>) -> Id {
    if let Ok(mut state) = self.state.write() {
      let cnt = state.counter;
      state.counter += 1;
      //state.hard_listeners.insert(cnt, Box::new(ls));
      state.weak_listeners.insert(cnt, Arc::downgrade(ls));
      return Id(cnt);
    }

    panic!("can't lock");
  }
}

impl<A: Clone> EventEmmiter<A> for EventProducer<A> {
  fn emit(&self, value: A) {
    let mut remove_set = HashSet::<usize>::new();
    if let Ok(state) = self.state.read() {
      for ls in state.hard_listeners.values().into_iter() {
        (ls)(value.clone())
      }
      for (id, ls) in state.weak_listeners.iter() {
        match ls.upgrade() {
          None => {
            remove_set.insert(*id);
          }
          Some(ls) => (ls)(value.clone()),
        }
      }
    }
    if !remove_set.is_empty()
      && let Ok(mut state) = self.state.write()
    {
      for id in remove_set.iter() {
        state.weak_listeners.remove(id);
      }
    }
  }
}

#[test]
fn test() {
  use std::sync::{Arc, Mutex};

  let accepted = Arc::new(Mutex::new(Vec::<i32>::new()));
  let accepted2 = accepted.clone();

  let prod: EventProducer<i32> = Default::default();

  prod.add_listener(move |ev| {
    println!("event {ev:?}");
    if let Ok(mut res) = accepted2.lock() {
      res.push(ev);
    }
  });

  prod.emit(12);
  prod.emit(23);

  {
    match accepted.lock() {
      Err(_err) => {
        assert!(false);
      }
      Ok(res) => {
        assert!(res.len() == 2);
        assert!(res.iter().filter(|i| **i == 12).count() > 0);
        assert!(res.iter().filter(|i| **i == 23).count() > 0);
      }
    }
  }
}
