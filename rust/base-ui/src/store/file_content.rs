use crate::{GridObjects, coll::IdList, music_grid::note::Note};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone)]
pub enum MidiedJson {
  Note(Note),
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct MidiedJsonContent {
  pub content: Vec<MidiedJson>,
}

pub fn store(grid_objs: GridObjects) -> Result<String, String> {
  let mut content = Vec::<MidiedJson>::new();
  for i in 0..grid_objs.len() {
    match grid_objs.read_by_idx(i, |obj| obj.and_then(|(_, obj)| Note::try_from(obj).ok())) {
      None => {}
      Some(note) => {
        content.push(MidiedJson::Note(note.clone()));
      }
    }
  }

  serde_json::to_string_pretty(
    //
    &MidiedJsonContent { content: content },
  )
  //
  .map_err(|e| e.to_string())
}

pub fn restore(json: &str) -> Result<MidiedJsonContent, String> {
  serde_json::from_str(json).map_err(|e| e.to_string())
}

#[test]
fn test_store() {
  use crate::{
    GridObjects,
    coll::{IdList, IdListImpl},
    music_grid::{GridObj, note::Note},
  };
  use midi_model::{EventTime, Velocity, pitches};
  use std::sync::{Arc, Mutex};

  let mut objs: GridObjects = Arc::new(Mutex::new(IdListImpl::<Box<dyn GridObj>>::new()));
  objs.push(Box::new(Note {
    time: EventTime::from_ms(0),
    pitch: pitches::C_4,
    velocity: Velocity::V100,
    duration: EventTime::from_ms(250),
  }));

  let json = store(objs.clone()).unwrap();
  println!("{json}");

  let restored = restore(&json).unwrap();
  println!("{restored:?}");
}

#[test]
fn body_test() {
  use serde_json::{Map, Value};

  let mut map = Map::new();
  map.insert("k".to_string(), Value::String("aa".to_string()));

  #[derive(Debug, Serialize, Deserialize)]
  struct Entry {
    pub a: String,
    pub b: i32,
  };

  let value2 = serde_json::to_value(Entry {
    a: "aaa".to_string(),
    b: 234,
  })
  .unwrap();

  map.insert("b".to_string(), value2);

  let json = serde_json::to_string_pretty(&map).unwrap();
  println!("{}", json);

  let map = serde_json::from_str::<Map<String, Value>>(&json).unwrap();
  println!("{map:?}");

  let entry = serde_json::from_value::<Entry>(map.get("b").unwrap().clone()).unwrap();
  println!("{entry:?}");
}

// use std::collections::BTreeMap;

// use crate::{GridObjects, coll::IdList, music_grid::note::Note};
// use serde::{Deserialize, Serialize};

// pub trait ContentEntries {
//   fn store(&self) -> Option<serde_json::Value>;
//   fn restore(&mut self, value: &serde_json::Value);
// }

// #[derive(Default)]
// pub struct ProjectFileSturcture {
//   sections: BTreeMap<String, Box<dyn ContentEntries>>,
// }

// impl ProjectFileSturcture {
//   pub fn section_registry(&mut self, name: &str, entry_mapper: Box<dyn ContentEntries>) {
//     self.sections.insert(name.to_string(), entry_mapper);
//   }

//   pub fn sections(&self) ->
// }

// pub trait FileContent {
//   fn entry_registry(&mut self, name: &str, entry_mapper: Box<dyn ContentEntries>);
//   fn write(&self) -> Result<String, String>;
//   fn read(&self, content: &str) -> Result<(), String>;
// }
