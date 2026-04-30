use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct Id(pub(crate) usize);

pub struct ChangedByIdx<A> {
  pub old_value: A,
  pub old_id: Id,
  pub new_id: Id,
}

pub struct ChangedById<A> {
  pub old_value: A,
  pub old_id: Id,
  pub index: usize,
}

pub trait IdList<A> {
  fn len(&self) -> usize;

  fn exists_id(&self, id: Id) -> bool {
    self.read_by_id(id, |opt| opt.is_some())
  }
  fn index_of_id(&self, id: Id) -> Option<usize> {
    self.read_by_id(id, |opt| opt.map(|opt| opt.0))
  }

  fn read_by_idx<R: Sized>(&self, idx: usize, f: impl FnMut(Option<&(Id, A)>) -> R) -> R;
  fn write_by_idx<R: Sized>(&mut self, idx: usize, f: impl FnMut(Option<(Id, &mut A)>) -> R) -> R;

  fn read_by_id<R: Sized>(&self, id: Id, f: impl FnMut(Option<(usize, &A)>) -> R) -> R;
  fn write_by_id<R: Sized>(&mut self, id: Id, f: impl FnMut(Option<(usize, &mut A)>) -> R) -> R;

  fn write_by_ids<R: Sized, IDS: IntoIterator<Item = Id>>(
    &mut self,
    state: R,
    ids: IDS,
    mut modifier: impl FnMut(usize, Id, &mut A, &mut R),
  ) -> R {
    let mut state = state;
    for id in ids.into_iter() {
      let ref_state = &mut state;
      self.write_by_id(id.clone(), |opt| {
        if let Some((idx, ref_item)) = opt {
          (modifier)(idx, id, ref_item, ref_state);
        }
      });
    }
    state
  }

  fn push(&mut self, v: A) -> (Id, usize);
  fn insert(&mut self, idx: usize, v: A) -> (Id, usize);

  fn remove_by_idx(&mut self, idx: usize) -> Option<(Id, A)>;

  fn remove_by_id(&mut self, id: Id) -> Option<(usize, A)>;
  fn remove_by_ids<IDS: IntoIterator<Item = Id>>(&mut self, ids: IDS) -> Vec<(usize, Id, A)> {
    let mut ls: Vec<(usize, Id, A)> = Vec::new();
    for id in ids.into_iter() {
      if let Some((idx, x)) = self.remove_by_id(id) {
        ls.push((idx, id, x));
      }
    }
    ls
  }

  fn clear(&mut self) -> Vec<(usize, Id, A)>;

  fn changed_by_idx(&mut self, idx: usize, v: A) -> Option<ChangedByIdx<A>>;
  fn changed_by_id(&mut self, id: Id, v: A) -> Option<ChangedById<A>>;

  fn find_by_type<B>(&self) -> Vec<(B, Id, usize)>
  where
    B: for<'a> TryFrom<&'a A>,
  {
    let mut result = Vec::new();

    for idx in 0..self.len() {
      // read_by_idx возвращает R, который выводится как Option<B>
      if let Some(converted) = self.read_by_idx(idx, |opt| {
        opt.and_then(|(id, item)| B::try_from(item).ok().map(|b| (b, id.clone(), idx)))
      }) {
        result.push(converted);
      }
    }

    result
  }
}
