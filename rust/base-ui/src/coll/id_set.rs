use std::collections::{HashMap, HashSet};

use super::*;

#[derive(Default)]
pub struct IdSet {
  set: HashSet<Id>,

  insert_listeners: HashMap<Id, Box<dyn Fn(Id)>>,
  insert_ls_id_next: usize,

  delete_listeners: HashMap<Id, Box<dyn Fn(Id)>>,
  delete_ls_id_next: usize,
}

impl IdSet {
  pub fn new() -> Self {
    Self::default()
  }

  fn fire_insert(&self, id: Id) {
    for ls in self.insert_listeners.iter() {
      (*ls.1)(id)
    }
  }

  pub fn on_insert(&mut self, ls: impl Fn(Id) + 'static) -> Id {
    let id = Id(self.insert_ls_id_next);
    self.insert_ls_id_next += 1;
    self.insert_listeners.insert(id, Box::new(ls));
    id
  }

  pub fn delete_listener_on_insert(&mut self, id: Id) {
    self.insert_listeners.remove(&id);
  }

  pub fn on_delete(&mut self, ls: impl Fn(Id) + 'static) -> Id {
    let id = Id(self.delete_ls_id_next);
    self.delete_ls_id_next += 1;
    self.delete_listeners.insert(id, Box::new(ls));
    id
  }

  pub fn delete_listener_on_delete(&mut self, id: Id) {
    self.delete_listeners.remove(&id);
  }

  fn fire_delete(&self, id: Id) {
    for ls in self.delete_listeners.iter() {
      (*ls.1)(id)
    }
  }

  pub fn len(&self) -> usize {
    self.set.len()
  }

  pub fn is_empty(&self) -> bool {
    self.set.is_empty()
  }

  pub fn contains(&self, id: Id) -> bool {
    self.set.contains(&id)
  }

  pub fn push(&mut self, id: Id) -> bool {
    let modified = self.set.insert(id);
    if modified {
      self.fire_insert(id);
    }
    modified
  }

  pub fn push_all<IDS: IntoIterator<Item = Id>>(&mut self, ids: IDS) {
    for id in ids.into_iter() {
      self.push(id);
    }
  }

  pub fn remove(&mut self, id: Id) -> bool {
    let modified = self.set.remove(&id);
    if modified {
      self.fire_delete(id);
    }
    modified
  }

  pub fn remove_all<IDS: IntoIterator<Item = Id>>(&mut self, ids: IDS) {
    for id in ids.into_iter() {
      self.remove(id);
    }
  }

  // Добавим метод iter для удобства итерации по ссылке
  pub fn iter(&self) -> std::collections::hash_set::Iter<'_, Id> {
    self.set.iter()
  }

  pub fn clear(&mut self) {
    if self.is_empty() {
      return;
    }

    let id_set = self.set.clone();
    for id in id_set {
      self.remove(id);
    }
  }
}

// Реализуем IntoIterator для итерации по значению (владеющая итерация)
impl IntoIterator for IdSet {
  type Item = Id;
  type IntoIter = std::collections::hash_set::IntoIter<Id>;

  /// Метод возвращает итератор, который потребляет (take) содержимое IdSet
  fn into_iter(self) -> Self::IntoIter {
    self.set.into_iter()
  }
}

// Реализуем IntoIterator для &IdSet, чтобы можно было итерировать по ссылке
// impl<'a> IntoIterator for &'a IdSet {
//   type Item = &'a Id;
//   type IntoIter = std::collections::hash_set::Iter<'a, Id>;

//   /// Метод возвращает итератор по ссылкам на элементы IdSet
//   fn into_iter(self) -> Self::IntoIter {
//     self.set.iter()
//   }
// }

impl IntoIterator for &IdSet {
  type Item = Id;
  type IntoIter = std::collections::hash_set::IntoIter<Id>;

  /// Метод возвращает итератор по ссылкам на элементы IdSet
  fn into_iter(self) -> Self::IntoIter {
    self.set.clone().into_iter()
  }
}
