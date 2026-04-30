use std::collections::BTreeMap;

/// Интервал с ассоциированным значением
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Interval<T, V> {
  /// Начало интервала (включительно)
  pub from: T,
  /// Конец интервала (исключительный)
  pub to: T,
  /// Значение, ассоциированное с интервалом
  pub value: V,
}

impl<T, V> Interval<T, V> {
  /// Создает новый интервал
  ///
  /// # Аргументы
  /// * `from` - начало интервала (включительно)
  /// * `to` - конец интервала (исключительный)
  /// * `value` - значение, ассоциированное с интервалом
  pub fn new(from: T, to: T, value: V) -> Self {
    Self { from, to, value }
  }
}

/// Интервальное дерево для хранения и поиска интервалов
///
/// Поддерживает несколько значений на одном интервале и несколько пересекающихся интервалов
#[allow(unused)]
pub struct IntervalTree<T, V>
where
  T: Ord + Clone,
  V: Clone + PartialEq,
{
  /// Хранит интервалы, сгруппированные по начальной точке
  intervals: BTreeMap<T, Vec<Interval<T, V>>>,
  /// Хранит конечные точки для быстрого поиска максимума/минимума
  end_points: BTreeMap<T, usize>,
}

#[allow(unused)]
impl<T, V> IntervalTree<T, V>
where
  T: Ord + Clone,
  V: Clone + PartialEq,
{
  /// Создает новое пустое интервальное дерево
  pub fn new() -> Self {
    Self {
      intervals: BTreeMap::new(),
      end_points: BTreeMap::new(),
    }
  }

  /// Добавляет новый интервал в дерево
  ///
  /// # Аргументы
  /// * `from` - начало интервала (включительно)
  /// * `to` - конец интервала (исключительный)
  /// * `value` - значение, ассоциированное с интервалом
  pub fn insert(&mut self, from: T, to: T, value: V) {
    let interval = Interval::new(from.clone(), to.clone(), value);

    // Добавляем интервал в основную карту
    self
      .intervals
      .entry(from.clone())
      .or_insert_with(Vec::new)
      .push(interval);

    // Обновляем счетчик конечных точек
    *self.end_points.entry(to.clone()).or_insert(0) += 1;
  }

  /// Удаляет интервал из дерева
  ///
  /// # Аргументы
  /// * `from` - начало интервала (включительно)
  /// * `to` - конец интервала (исключительный)
  /// * `value` - значение, ассоциированное с интервалом
  ///
  /// # Возвращает
  /// * `true` - если интервал был успешно удален
  /// * `false` - если интервал не найден
  pub fn remove(&mut self, from: &T, to: &T, value: &V) -> bool {
    let removed = if let Some(intervals) = self.intervals.get_mut(from) {
      let initial_len = intervals.len();
      intervals.retain(|interval| !(interval.to == *to && interval.value == *value));
      let removed_count = initial_len - intervals.len();

      if removed_count > 0 {
        // Обновляем счетчик конечных точек
        if let Some(count) = self.end_points.get_mut(to) {
          if *count <= removed_count {
            self.end_points.remove(to);
          } else {
            *count -= removed_count;
          }
        }

        // Удаляем пустой вектор
        if intervals.is_empty() {
          self.intervals.remove(from);
        }

        true
      } else {
        false
      }
    } else {
      false
    };

    removed
  }

  /// Возвращает все интервалы в дереве
  pub fn get_all_intervals(&self) -> Vec<Interval<T, V>> {
    self
      .intervals
      .values()
      .flat_map(|intervals| intervals.iter())
      .cloned()
      .collect()
  }

  /// Возвращает минимальную начальную точку среди всех интервалов
  ///
  /// # Возвращает
  /// * `Some(&T)` - минимальная начальная точка, если дерево не пустое
  /// * `None` - если дерево пустое
  pub fn get_min_start(&self) -> Option<&T> {
    self.intervals.keys().next()
  }

  /// Возвращает следующую ближайшую начальную точку после указанной
  ///
  /// # Аргументы
  /// * `from` - точка, от которой ищется следующая начальная точка
  ///
  /// # Возвращает
  /// * `Some(&T)` - следующая начальная точка, если она существует
  /// * `None` - если следующей точки нет
  pub fn get_next_start(&self, from: &T) -> Option<&T> {
    self.intervals.range(from..).skip(1).next().map(|(k, _)| k)
  }

  /// Возвращает максимальную конечную точку среди всех интервалов
  ///
  /// # Возвращает
  /// * `Some(&T)` - максимальная конечная точка, если дерево не пустое
  /// * `None` - если дерево пустое
  pub fn get_max_end(&self) -> Option<&T> {
    self.end_points.keys().last()
  }

  /// Возвращает предыдущую ближайшую конечную точку перед указанной
  ///
  /// # Аргументы
  /// * `to` - точка, от которой ищется предыдущая конечная точка
  ///
  /// # Возвращает
  /// * `Some(&T)` - предыдущая конечная точка, если она существует
  /// * `None` - если предыдущей точки нет
  pub fn get_prev_end(&self, to: &T) -> Option<&T> {
    self.end_points.range(..to).last().map(|(k, _)| k)
  }

  /// Возвращает все интервалы, содержащие указанную точку
  ///
  /// Точка p содержится в интервале [from, to), если p >= from и p < to
  ///
  /// # Аргументы
  /// * `p` - точка для поиска
  ///
  /// # Возвращает
  /// Вектор интервалов, содержащих точку p
  pub fn get_intervals_containing_point(&self, p: &T) -> Vec<Interval<T, V>> {
    self
      .intervals
      .iter()
      .filter(|(from, _)| *from <= p)
      .flat_map(|(_, intervals)| intervals.iter().filter(move |interval| &interval.to > p))
      .cloned()
      .collect()
  }

  /// Возвращает все интервалы, имеющие пересечение с указанным интервалом
  ///
  /// # Аргументы
  /// * `from` - начало интервала для поиска пересечений (включительно)
  /// * `to` - конец интервала для поиска пересечений (исключительный)
  ///
  /// # Возвращает
  /// Вектор интервалов, пересекающихся с [from, to)
  pub fn get_overlapping_intervals(&self, from: &T, to: &T) -> Vec<Interval<T, V>> {
    self
      .intervals
      .iter()
      .flat_map(|(_, intervals)| intervals.iter())
      .filter(|interval| {
        // Проверяем пересечение: [from, to) и [interval.from, interval.to)
        from < &interval.to && to > &interval.from
      })
      .cloned()
      .collect()
  }

  /// Находит все пересечения между интервалами в дереве
  ///
  /// Возвращает вектор пар пересекающихся интервалов
  ///
  /// # Возвращает
  /// Вектор пар интервалов, которые пересекаются друг с другом
  pub fn find_all_intersections(&self) -> Vec<(Interval<T, V>, Interval<T, V>)> {
    let all_intervals: Vec<_> = self.get_all_intervals();
    let mut intersections = Vec::new();

    for i in 0..all_intervals.len() {
      for j in (i + 1)..all_intervals.len() {
        let interval1 = &all_intervals[i];
        let interval2 = &all_intervals[j];

        // Проверяем пересечение
        if interval1.from < interval2.to && interval1.to > interval2.from {
          intersections.push((interval1.clone(), interval2.clone()));
        }
      }
    }

    intersections
  }
}

impl<T, V> Default for IntervalTree<T, V>
where
  T: Ord + Clone,
  V: Clone + PartialEq,
{
  fn default() -> Self {
    Self::new()
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn test_basic_operations() {
    let mut tree: IntervalTree<i32, String> = IntervalTree::new();

    tree.insert(1, 5, "first".to_string());
    tree.insert(3, 7, "second".to_string());
    tree.insert(6, 10, "third".to_string());

    // Проверяем получение всех интервалов
    let all_intervals = tree.get_all_intervals();
    assert_eq!(all_intervals.len(), 3);

    // Проверяем минимальное начало
    assert_eq!(tree.get_min_start(), Some(&1));

    // Проверяем следующее начало
    assert_eq!(tree.get_next_start(&1), Some(&3));
    assert_eq!(tree.get_next_start(&3), Some(&6));

    // Проверяем максимальный конец
    assert_eq!(tree.get_max_end(), Some(&10));

    // Проверяем предыдущий конец
    assert_eq!(tree.get_prev_end(&10), Some(&7));
    assert_eq!(tree.get_prev_end(&7), Some(&5));

    // Проверяем поиск по точке
    let containing_4 = tree.get_intervals_containing_point(&4);
    assert_eq!(containing_4.len(), 2); // интервалы [1,5) и [3,7)

    // Проверяем поиск пересечений
    let overlapping = tree.get_overlapping_intervals(&4, &8);
    assert_eq!(overlapping.len(), 3); // все три интервала пересекаются с [4,8)

    // Проверяем поиск всех пересечений
    let all_intersections = tree.find_all_intersections();
    assert_eq!(all_intersections.len(), 2); // [1,5) пересекается с [3,7), [3,7) пересекается с [6,10)

    // Проверяем удаление
    assert!(tree.remove(&1, &5, &"first".to_string()));
    assert_eq!(tree.get_all_intervals().len(), 2);
    assert_eq!(tree.get_min_start(), Some(&3));
  }
}
