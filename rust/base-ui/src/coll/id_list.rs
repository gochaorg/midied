use super::*;
use log;
use std::{
  cell::RefCell,
  collections::HashMap,
  sync::{Arc, Mutex},
};

/// Потокобезопасная коллекция с уникальными идентификаторами для элементов.
///
/// `IdListImpl` хранит элементы типа `A`, каждому из которых присваивается
/// уникальный `Id` при добавлении. Коллекция поддерживает:
/// * Доступ по индексу (`O(1)`) и по ID (`O(1)` при наличии кэша индексов).
/// * Вставку, удаление и замену элементов с автоматическим обновлением внутренних структур.
/// * Итерацию с возможностью мутации элементов.
///
/// # Кэширование индексов
/// Для ускорения поиска по `Id` используется ленивый кэш `HashMap<Id, usize>`,
/// который инвалидируется при любой модификации коллекции через `drop_index()`.
///
/// # Потокобезопасность
/// Сама структура не реализует `Send + Sync`, но может быть обёрнута в
/// `Arc<Mutex<...>>` или `Mutex<...>` для многопоточного использования.
/// См. реализации трейта `IdList` для обёрток ниже.
///
/// # Типовые параметры
/// * `A` — тип хранимых элементов.
#[derive(Default)]
pub struct IdListImpl<A> {
  /// Внутренний вектор, хранящий пары `(Id, значение)`.
  /// Порядок элементов сохраняется, индексы стабильны до модификаций.
  list: Vec<(Id, A)>,
  /// Опциональный кэш: `Id -> индекс в векторе`.
  /// Хранится в `Mutex` для возможности ленивого пересчёта при чтении.
  /// `None` означает, что кэш устарел и требует перестроения.
  id2idx: Mutex<Option<HashMap<Id, usize>>>,
  /// Счётчик для генерации следующих уникальных `Id`.
  /// Гарантирует монотонный рост идентификаторов.
  id_next: usize,
}

impl<A> IdListImpl<A> {
  /// Создаёт новую пустую коллекцию `IdListImpl`.
  ///
  /// # Возвращает
  /// Экземпляр с пустым списком, отсутствующим кэшем индексов
  /// и начальным значением `id_next = 0`.
  ///
  /// # Пример
  /// ```
  /// let list: IdListImpl<String> = IdListImpl::new();
  /// assert_eq!(list.len(), 0);
  /// ```
  pub fn new() -> Self {
    Self {
      list: Default::default(),
      id2idx: Default::default(),
      id_next: Default::default(),
    }
  }
}

impl<A> IdListImpl<A> {
  /// Добавляет элемент в конец коллекции и возвращает его `Id` и индекс.
  ///
  /// # Параметры
  /// * `v` — значение для добавления.
  ///
  /// # Возвращает
  /// Кортеж `(Id, индекс)`, где:
  /// * `Id` — уникальный идентификатор нового элемента.
  /// * `индекс` — позиция элемента во внутреннем векторе.
  ///
  /// # Побочные эффекты
  /// * Инвалидирует кэш индексов (`id2idx`) через `drop_index()`.
  /// * Увеличивает `id_next` для обеспечения уникальности следующих ID.
  ///
  /// # Сложность
  /// * `O(1)` амортизированно для добавления в вектор.
  /// * `O(1)` для инвалидации кэша.
  pub fn push(&mut self, v: A) -> (Id, usize) {
    self.drop_index();

    let id = Id(self.id_next);
    self.id_next += 1;

    self.list.push((id, v));
    let idx = self.list.len() - 1;

    (id, idx)
  }

  /// Вставляет элемент по указанному индексу, сдвигая последующие элементы вправо.
  ///
  /// # Параметры
  /// * `idx` — позиция вставки (должна быть в диапазоне `0..=len()`).
  /// * `v` — значение для вставки.
  ///
  /// # Возвращает
  /// Кортеж `(Id, индекс)`, аналогично `push()`.
  ///
  /// # Паники
  /// Паникует в `Vec::insert`, если `idx > len()`.
  ///
  /// # Побочные эффекты
  /// Инвалидирует кэш индексов.
  pub fn insert_by_idx(&mut self, idx: usize, v: A) -> (Id, usize) {
    self.drop_index();

    let id = Id(self.id_next);
    self.id_next += 1;

    // self.fire_insert(id, &v);
    self.list.insert(idx, (id, v));
    (id, idx)
  }

  /// Заменяет элемент по индексу, генерируя новый `Id` для обновлённого значения.
  ///
  /// # Параметры
  /// * `idx` — индекс заменяемого элемента.
  /// * `v` — новое значение.
  ///
  /// # Возвращает
  /// * `Some(ChangedByIdx)` при успешной замене, содержащий:
  ///   * `old_value` — предыдущее значение.
  ///   * `old_id` — ID заменяемого элемента.
  ///   * `new_id` — ID нового элемента.
  /// * `None`, если `idx` выходит за границы коллекции.
  ///
  /// # Особенности
  /// * Старый элемент удаляется, новый вставляется на ту же позицию.
  /// * Индексы последующих элементов не меняются.
  /// * Кэш индексов инвалидируется.
  pub fn changed_by_idx(&mut self, idx: usize, v: A) -> Option<ChangedByIdx<A>> {
    if self.list.len() <= idx {
      return None;
    }

    self.drop_index();

    let id = Id(self.id_next);
    self.id_next += 1;

    let old = self.list.remove(idx);

    // self.fire_insert(id, &v);
    self.list.insert(idx, (id, v));

    // self.fire_delete(old.0, &old.1);

    Some(ChangedByIdx {
      old_value: old.1,
      old_id: old.0,
      new_id: id,
    })
  }

  /// Заменяет элемент по `Id`, генерируя новый `Id` для обновлённого значения.
  ///
  /// # Параметры
  /// * `id` — идентификатор заменяемого элемента.
  /// * `v` — новое значение.
  ///
  /// # Возвращает
  /// * `Some(ChangedById)` при успешной замене, содержащий:
  ///   * `old_value` — предыдущее значение.
  ///   * `old_id` — ID заменяемого элемента.
  ///   * `index` — позиция элемента в коллекции.
  /// * `None`, если элемент с таким `Id` не найден.
  ///
  /// # Сложность
  /// * `O(1)` при наличии актуального кэша индексов.
  /// * `O(n)` при необходимости перестроения кэша.
  pub fn changed_by_id(&mut self, id: Id, v: A) -> Option<ChangedById<A>> {
    let target_idx = self.id2idx_index(|index| {
      let y = index.get(&id);
      y.map(|a| a.clone())
    });

    if target_idx.is_none() {
      return None;
    }
    let target_idx = target_idx.unwrap();

    self.drop_index();

    let id = Id(self.id_next);
    self.id_next += 1;

    let old = self.list.remove(target_idx);

    // self.fire_insert(id, &v);
    self.list.insert(target_idx, (id, v));

    // self.fire_delete(old.0, &old.1);

    Some(ChangedById {
      old_value: old.1,
      old_id: old.0,
      index: target_idx,
    })
  }

  /// Удаляет все элементы
  ///
  /// # Возвращает
  /// Все удаленные элементы и их Id, index
  pub fn clear(&mut self) -> Vec<(usize, Id, A)> {
    let mut res: Vec<(usize, Id, A)> = Vec::new();

    let total = self.len();
    for idx0 in 0..total {
      let idx1 = total - idx0 - 1;
      if let Some((id, a)) = self.remove_by_idx(idx1) {
        res.push((idx1, id, a));
      }
    }

    res
  }

  /// Удаляет элемент по индексу и возвращает его `(Id, значение)`.
  ///
  /// # Параметры
  /// * `idx` — индекс удаляемого элемента.
  ///
  /// # Возвращает
  /// * `Some((Id, A))` при успешном удалении.
  /// * `None`, если `idx` выходит за границы коллекции.
  ///
  /// # Побочные эффекты
  /// * Элементы после `idx` сдвигаются влево.
  /// * Кэш индексов инвалидируется.
  pub fn remove_by_idx(&mut self, idx: usize) -> Option<(Id, A)> {
    if self.list.len() <= idx {
      return None;
    }

    self.drop_index();

    let (old_id, old_v) = self.list.remove(idx);
    // self.fire_delete(old_id, &old_v);

    Some((old_id, old_v))
  }

  /// Удаляет элемент по `Id` и возвращает его `(индекс, значение)`.
  ///
  /// # Параметры
  /// * `id` — идентификатор удаляемого элемента.
  ///
  /// # Возвращает
  /// * `Some((usize, A))` при успешном удалении.
  /// * `None`, если элемент с таким `Id` не найден.
  ///
  /// # Сложность
  /// Зависит от актуальности кэша индексов (см. `changed_by_id`).
  pub fn remove_by_id(&mut self, id: Id) -> Option<(usize, A)> {
    let target_idx = self.id2idx_index(|index| {
      let y = index.get(&id);
      y.map(|a| a.clone())
    });

    if target_idx.is_none() {
      return None;
    }
    let target_idx = target_idx.unwrap();

    self.remove_by_idx(target_idx).map(|(_, v)| (target_idx, v))
  }

  /// Возвращает неизменяемую ссылку на элемент по индексу.
  ///
  /// # Параметры
  /// * `idx` — индекс элемента.
  ///
  /// # Возвращает
  /// * `Some(&(Id, A))`, если элемент существует.
  /// * `None`, если `idx` выходит за границы.
  ///
  /// # Сложность
  /// `O(1)` — прямой доступ к вектору.
  pub fn get_by_idx(&self, idx: usize) -> Option<&(Id, A)> {
    self.list.get(idx)
  }

  /// Возвращает изменяемую ссылку на элемент по индексу вместе с его `Id`.
  ///
  /// # Параметры
  /// * `idx` — индекс элемента.
  ///
  /// # Возвращает
  /// * `Some((Id, &mut A))`, если элемент существует.
  /// * `None`, если `idx` выходит за границы.
  ///
  /// # Внимание
  /// Модификация элемента через эту ссылку не требует инвалидации кэша,
  /// так как `Id` и позиция не меняются.
  pub fn get_mut_by_idx(&mut self, idx: usize) -> Option<(Id, &mut A)> {
    let res = self.list.get_mut(idx);
    res.map(|x| (x.0.clone(), &mut x.1))
  }

  /// Возвращает количество элементов в коллекции.
  ///
  /// # Сложность
  /// `O(1)` — делегирует `Vec::len()`.
  pub fn len(&self) -> usize {
    self.list.len()
  }

  /// Выполняет операцию над кэшем индексов `Id -> usize`, пересчитывая его при необходимости.
  ///
  /// # Параметры
  /// * `f` — замыкание, принимающее ссылку на `HashMap<Id, usize>` и возвращающее результат типа `R`.
  ///
  /// # Возвращает
  /// Результат выполнения замыкания `f`.
  ///
  /// # Логика работы
  /// 1. Пытается захватить мьютекс `id2idx`.
  /// 2. Если кэш уже построен (`Some`) — применяет `f` к нему.
  /// 3. Если кэш отсутствует (`None`) — строит его заново за `O(n)`,
  ///    сохраняет в мьютекс и применяет `f`.
  /// 4. При ошибке блокировки мьютекса — паникует.
  ///
  /// # Паники
  /// * При невозможности захватить мьютекс (`lock()` возвращает `Err`).
  ///
  /// # Внутреннее использование
  /// Приватный метод, используемый для реализации поиска по `Id`.
  fn id2idx_index<R, F: FnOnce(&HashMap<Id, usize>) -> R>(&self, f: F) -> R {
    if let Ok(mut index) = self.id2idx.lock() {
      if index.is_some() {
        let idx = index.as_mut();
        if let Some(idx) = idx {
          return f(idx);
        }
      }

      let mut map: HashMap<Id, usize> = Default::default();
      for (idx, (id, _)) in self.list.iter().enumerate() {
        map.insert(id.clone(), idx);
      }

      let res = f(&map);
      *index = Some(map);
      return res;
    }

    panic!()
  }

  /// Инвалидирует кэш индексов `Id -> usize`, устанавливая его в `None`.
  ///
  /// # Побочные эффекты
  /// * Следующий запрос по `Id` вызовет перестроение кэша за `O(n)`.
  ///
  /// # Когда вызывается
  /// Автоматически перед любой операцией, изменяющей структуру коллекции:
  /// `push`, `insert`, `remove`, `changed_*`.
  fn drop_index(&self) {
    if let Ok(mut idx) = self.id2idx.lock() {
      *idx = None;
    }
  }

  /// Возвращает неизменяемую ссылку на элемент по `Id` вместе с его индексом.
  ///
  /// # Параметры
  /// * `id` — идентификатор элемента.
  ///
  /// # Возвращает
  /// * `Some((usize, &A))`, если элемент найден.
  /// * `None`, если элемент с таким `Id` отсутствует.
  ///
  /// # Сложность
  /// * `O(1)` при актуальном кэше.
  /// * `O(n)` при перестроении кэша.
  pub fn get_by_id(&self, id: Id) -> Option<(usize, &A)> {
    self.id2idx_index(|index| {
      index
        .get(&id)
        .and_then(|i| self.get_by_idx(*i).map(|vv| (*i, &vv.1)))
    })
  }

  /// Возвращает изменяемую ссылку на элемент по `Id` вместе с его индексом.
  ///
  /// # Параметры
  /// * `id` — идентификатор элемента.
  ///
  /// # Возвращает
  /// * `Some((usize, &mut A))`, если элемент найден.
  /// * `None`, если элемент с таким `Id` отсутствует.
  ///
  /// # Внимание
  /// Требует `&mut self`, так как возвращает мутабельную ссылку.
  /// Кэш индексов не инвалидируется, так как позиция элемента не меняется.
  pub fn get_mut_by_id(&mut self, id: Id) -> Option<(usize, &mut A)> {
    let target_idx = self.id2idx_index(|index| {
      let y = index.get(&id);
      y.map(|a| a.clone())
    });

    if target_idx.is_none() {
      return None;
    }
    let target_idx = target_idx.unwrap();

    let result = self.list.get_mut(target_idx);
    if result.is_none() {
      return None;
    }
    let result = result.unwrap();
    Some((target_idx, &mut result.1))
  }

  /// Применяет замыкание ко всем элементам.
  ///
  /// * `f` — замыкание, принимающее `&(Id, A)`.
  ///
  /// # Сложность
  /// `O(n)` — итерация по всем элементам.
  pub fn each<F, R>(&self, mut f: F)
  where
    F: FnMut(&(Id, A)),
  {
    for i in 0..self.list.len() {
      if let Some(e) = self.get_by_idx(i) {
        f(e);
      }
    }
  }

  /// Применяет замыкание ко всем элементам коллекции с возможностью мутации.
  ///
  /// # Параметры
  /// * `r` — мутабельная ссылка на внешнее состояние, передаваемое в замыкание.
  /// * `f` — замыкание, принимающее `&mut R` и `(Id, &mut A)`.
  ///
  /// # Внимание
  /// Требует `&mut self`. Изменения элементов применяются немедленно.
  ///
  /// # Сложность
  /// `O(n)` — итерация по всем элементам.
  pub fn each_mut<F, R>(&mut self, r: &mut R, f: F)
  where
    F: Fn(&mut R, (Id, &mut A)),
  {
    for i in 0..self.list.len() {
      if let Some(e) = self.get_mut_by_idx(i) {
        f(r, e);
      }
    }
  }
}

#[cfg(test)]
mod testing {
  use super::*;

  #[test]
  fn basic() {
    let mut lst: IdListImpl<i32> = Default::default();
    assert!(lst.len() == 0);

    // lst.on_insert(|id, v| println!("inserted {id:?} {v}"));
    // lst.on_delete(|id, v| println!("deleted {id:?} {v}"));

    let (id1, idx1) = lst.push(10);
    assert!(lst.len() == 1);
    assert!(idx1 == 0);

    let (_, _) = lst.insert_by_idx(0, 20);
    assert!(lst.len() == 2);

    let v = lst.get_by_idx(0);
    assert!(v.is_some());
    let (_, v) = v.unwrap();
    assert!(*v == 20);

    let v = lst.get_by_id(id1);
    assert!(v.is_some());
    let (idx1, v) = v.unwrap();
    assert!(*v == 10);
    assert!(idx1 == 1);

    let removed = lst.remove_by_id(id1);
    assert!(removed.is_some());
    let (_, v) = removed.unwrap();
    assert!(v == 10);

    //lst.change(id2, 22);
  }
}

impl<A> IdList<A> for IdListImpl<A> {
  fn remove_by_idx(&mut self, idx: usize) -> Option<(Id, A)> {
    self.remove_by_idx(idx)
  }

  fn remove_by_id(&mut self, id: Id) -> Option<(usize, A)> {
    self.remove_by_id(id)
  }

  fn clear(&mut self) -> Vec<(usize, Id, A)> {
    self.clear()
  }

  fn push(&mut self, v: A) -> (Id, usize) {
    self.push(v)
  }

  fn insert(&mut self, idx: usize, v: A) -> (Id, usize) {
    self.insert_by_idx(idx, v)
  }

  fn write_by_idx<R: Sized>(
    &mut self,
    idx: usize,
    mut f: impl FnMut(Option<(Id, &mut A)>) -> R,
  ) -> R {
    f(self.get_mut_by_idx(idx))
  }

  fn read_by_id<R: Sized>(&self, id: Id, mut f: impl FnMut(Option<(usize, &A)>) -> R) -> R {
    f(self.get_by_id(id))
  }

  fn write_by_id<R: Sized>(
    &mut self,
    id: Id,
    mut f: impl FnMut(Option<(usize, &mut A)>) -> R,
  ) -> R {
    f(self.get_mut_by_id(id))
  }

  fn len(&self) -> usize {
    self.len()
  }

  fn changed_by_idx(&mut self, idx: usize, v: A) -> Option<ChangedByIdx<A>> {
    self.changed_by_idx(idx, v)
  }

  fn changed_by_id(&mut self, id: Id, v: A) -> Option<ChangedById<A>> {
    self.changed_by_id(id, v)
  }

  fn read_by_idx<R: Sized>(&self, idx: usize, mut f: impl FnMut(Option<&(Id, A)>) -> R) -> R {
    f(self.get_by_idx(idx))
  }
}

impl<A, L: IdList<A>> IdList<A> for Arc<Mutex<L>> {
  fn remove_by_idx(&mut self, idx: usize) -> Option<(Id, A)> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> remove_by_idx")
      .remove_by_idx(idx)
  }

  fn remove_by_id(&mut self, id: Id) -> Option<(usize, A)> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> remove_by_id")
      .remove_by_id(id)
  }

  fn clear(&mut self) -> Vec<(usize, Id, A)> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> clear")
      .clear()
  }

  fn len(&self) -> usize {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> len")
      .len()
  }

  fn read_by_idx<R: Sized>(&self, idx: usize, f: impl FnMut(Option<&(Id, A)>) -> R) -> R {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> read_by_idx")
      .read_by_idx(idx, f)
  }

  fn write_by_idx<R: Sized>(&mut self, idx: usize, f: impl FnMut(Option<(Id, &mut A)>) -> R) -> R {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> write_by_idx")
      .write_by_idx(idx, f)
  }

  fn read_by_id<R: Sized>(&self, id: Id, f: impl FnMut(Option<(usize, &A)>) -> R) -> R {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> read_by_id")
      .read_by_id(id, f)
  }

  fn write_by_id<R: Sized>(&mut self, id: Id, f: impl FnMut(Option<(usize, &mut A)>) -> R) -> R {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> write_by_id")
      .write_by_id(id, f)
  }

  fn push(&mut self, v: A) -> (Id, usize) {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> push")
      .push(v)
  }

  fn insert(&mut self, idx: usize, v: A) -> (Id, usize) {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> insert")
      .insert(idx, v)
  }

  fn changed_by_idx(&mut self, idx: usize, v: A) -> Option<ChangedByIdx<A>> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> changed_by_idx")
      .changed_by_idx(idx, v)
  }

  fn changed_by_id(&mut self, id: Id, v: A) -> Option<ChangedById<A>> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> changed_by_id")
      .changed_by_id(id, v)
  }
}

impl<A, L: IdList<A>> IdList<A> for Mutex<L> {
  fn remove_by_idx(&mut self, idx: usize) -> Option<(Id, A)> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> remove_by_idx")
      .remove_by_idx(idx)
  }

  fn remove_by_id(&mut self, id: Id) -> Option<(usize, A)> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> remove_by_id")
      .remove_by_id(id)
  }

  fn clear(&mut self) -> Vec<(usize, Id, A)> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> clear")
      .clear()
  }

  fn len(&self) -> usize {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> len")
      .len()
  }

  fn read_by_idx<R: Sized>(&self, idx: usize, f: impl FnMut(Option<&(Id, A)>) -> R) -> R {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> read_by_idx")
      .read_by_idx(idx, f)
  }

  fn write_by_idx<R: Sized>(&mut self, idx: usize, f: impl FnMut(Option<(Id, &mut A)>) -> R) -> R {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> write_by_idx")
      .write_by_idx(idx, f)
  }

  fn read_by_id<R: Sized>(&self, id: Id, f: impl FnMut(Option<(usize, &A)>) -> R) -> R {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> read_by_id")
      .read_by_id(id, f)
  }

  fn write_by_id<R: Sized>(&mut self, id: Id, f: impl FnMut(Option<(usize, &mut A)>) -> R) -> R {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> write_by_id")
      .write_by_id(id, f)
  }

  fn push(&mut self, v: A) -> (Id, usize) {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> push")
      .push(v)
  }

  fn insert(&mut self, idx: usize, v: A) -> (Id, usize) {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> insert")
      .insert(idx, v)
  }

  fn changed_by_idx(&mut self, idx: usize, v: A) -> Option<ChangedByIdx<A>> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> changed_by_idx")
      .changed_by_idx(idx, v)
  }

  fn changed_by_id(&mut self, id: Id, v: A) -> Option<ChangedById<A>> {
    self
      .lock()
      .expect("can't lock Arc<Mutex<IdList<A>>> changed_by_id")
      .changed_by_id(id, v)
  }
}

impl<A, L: IdList<A>> IdList<A> for RefCell<L> {
  fn remove_by_idx(&mut self, idx: usize) -> Option<(Id, A)> {
    match self.try_borrow_mut() {
      Ok(mut x) => x.remove_by_idx(idx),
      Err(e) => {
        log::error!("can't borrow_mut remove_by_idx: {e}");
        None
      }
    }
  }

  fn remove_by_id(&mut self, id: Id) -> Option<(usize, A)> {
    match self.try_borrow_mut() {
      Ok(mut x) => x.remove_by_id(id),
      Err(e) => {
        log::error!("can't borrow_mut remove_by_id: {e}");
        None
      }
    }
  }

  fn clear(&mut self) -> Vec<(usize, Id, A)> {
    match self.try_borrow_mut() {
      Ok(mut x) => x.clear(),
      Err(e) => {
        log::error!("can't borrow_mut clear: {e}");
        Vec::new()
      }
    }
  }

  fn len(&self) -> usize {
    match self.try_borrow() {
      Ok(x) => x.len(),
      Err(e) => {
        log::error!("can't borrow_mut len: {e}");
        0usize
      }
    }
  }

  fn read_by_idx<R: Sized>(&self, idx: usize, mut f: impl FnMut(Option<&(Id, A)>) -> R) -> R {
    match self.try_borrow() {
      Err(e) => {
        log::error!("can't borrow_mut read_by_idx: {e}");
        f(None)
      }
      Ok(x) => x.read_by_idx(idx, f),
    }
  }

  fn write_by_idx<R: Sized>(
    &mut self,
    idx: usize,
    mut f: impl FnMut(Option<(Id, &mut A)>) -> R,
  ) -> R {
    match self.try_borrow_mut() {
      Err(e) => {
        log::error!("can't borrow_mut write_by_idx: {e}");
        f(None)
      }
      Ok(mut x) => x.write_by_idx(idx, f),
    }
  }

  fn read_by_id<R: Sized>(&self, id: Id, mut f: impl FnMut(Option<(usize, &A)>) -> R) -> R {
    match self.try_borrow() {
      Err(e) => {
        log::error!("can't borrow_mut read_by_id: {e}");
        f(None)
      }
      Ok(x) => x.read_by_id(id, f),
    }
  }

  fn write_by_id<R: Sized>(
    &mut self,
    id: Id,
    mut f: impl FnMut(Option<(usize, &mut A)>) -> R,
  ) -> R {
    match self.try_borrow_mut() {
      Err(e) => {
        log::error!("can't borrow_mut write_by_id: {e}");
        f(None)
      }
      Ok(mut x) => x.write_by_id(id, f),
    }
    //self.borrow_mut().write_by_id(id, f)
  }

  fn push(&mut self, v: A) -> (Id, usize) {
    match self.try_borrow_mut() {
      Err(e) => {
        log::error!("can't borrow_mut push: {e}");
        (Id(123459999), 999999)
      }
      Ok(mut x) => x.push(v),
    }
    //self.borrow_mut().push(v)
  }

  fn insert(&mut self, idx: usize, v: A) -> (Id, usize) {
    match self.try_borrow_mut() {
      Err(e) => {
        log::error!("can't borrow_mut insert: {e}");
        (Id(123459999), 999999)
      }
      Ok(mut x) => x.insert(idx, v),
    }
    //self.borrow_mut().insert(idx, v)
  }

  fn changed_by_idx(&mut self, idx: usize, v: A) -> Option<ChangedByIdx<A>> {
    match self.try_borrow_mut() {
      Err(e) => {
        log::error!("can't borrow_mut changed_by_idx: {e}");
        None
      }
      Ok(mut x) => x.changed_by_idx(idx, v),
    }
    //self.borrow_mut().changed_by_idx(idx, v)
  }

  fn changed_by_id(&mut self, id: Id, v: A) -> Option<ChangedById<A>> {
    match self.try_borrow_mut() {
      Err(e) => {
        log::error!("can't borrow_mut changed_by_id: {e}");
        None
      }
      Ok(mut x) => x.changed_by_id(id, v),
    }
    //self.borrow_mut().changed_by_id(id, v)
  }
}
