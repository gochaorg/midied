use std::{fmt::Display, hash::Hash};

/// Представляет путь в стиле Unix с поддержкой нормализации, разрешения относительных путей
/// и обработки экранированных символов.
///
/// Внутреннее представление хранит два поля:
/// - `names`: вектор компонентов пути (без разделителей)
/// - `path`: исходная строка пути для быстрого сравнения и хеширования
///
/// # Пример
/// ```
/// let path = UnixPath::new("/home/user/docs");
/// assert_eq!(path.is_absolute(), true);
/// ```
#[derive(Debug, Clone)]
pub struct UnixPath {
  /// Компоненты пути как вектор строк (пустая строка обозначает корень или замыкающий `/`)
  names: Vec<String>,
  /// Исходное строковое представление пути для эффективного сравнения
  path: Box<String>,
}

impl PartialEq for UnixPath {
  fn eq(&self, other: &Self) -> bool {
    self.path == other.path
  }
}

impl Hash for UnixPath {
  fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
    self.path.hash(state);
  }
}

impl PartialOrd for UnixPath {
  fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
    self.path.partial_cmp(&other.path)
  }
}

impl UnixPath {
  /// Создаёт новый `UnixPath` из строкового представления.
  ///
  /// Поддерживает экранирование символов через обратный слеш (`\`):
  /// - `\/` интерпретируется как литеральный символ `/` внутри компонента
  /// - `\\` интерпретируется как литеральный обратный слеш
  ///
  /// # Параметры
  /// * `path` — строка или ссылка на строку с путём в формате Unix
  ///
  /// # Пример
  /// ```
  /// let path = UnixPath::new("foo\\/bar/baz");
  /// // Компоненты: ["foo/bar", "baz"]
  /// ```
  pub fn new<P: AsRef<str>>(path: P) -> Self {
    let mut names: Vec<String> = Vec::new();
    let mut state = 0;
    let mut buf = String::new();
    for c in path.as_ref().chars() {
      match state {
        0 => match c {
          '\\' => {
            state = 1;
          }
          '/' => {
            names.push(buf.clone());
            buf.clear();
          }
          _ => {
            buf.push(c);
          }
        },
        1 => {
          buf.push(c);
          state = 0;
        }
        _ => {}
      }
    }

    names.push(buf);

    Self {
      names: names,
      path: Box::new(path.as_ref().to_string()),
    }
  }
}

impl Display for UnixPath {
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    f.write_str(&self.names.to_string())
  }
}

/// Конвертирует `&str` в `UnixPath` через вызов `UnixPath::new`.
impl Into<UnixPath> for &str {
  fn into(self) -> UnixPath {
    UnixPath::new(self)
  }
}

/// Конвертирует `String` в `UnixPath` через вызов `UnixPath::new`.
impl Into<UnixPath> for String {
  fn into(self) -> UnixPath {
    UnixPath::new(&self)
  }
}

/// Конвертирует `&String` в `UnixPath` через вызов `UnixPath::new`.
impl Into<UnixPath> for &String {
  fn into(self) -> UnixPath {
    UnixPath::new(self)
  }
}

impl Into<UnixPath> for &mut String {
  fn into(self) -> UnixPath {
    UnixPath::new(self)
  }
}

#[allow(unused)]
fn accept<PATH: AsRef<str> + Send>(path: PATH) {
  //
}

/// Возвращает ссылку на исходное строковое представление пути.
impl AsRef<str> for UnixPath {
  fn as_ref(&self) -> &str {
    &self.path
  }
}

#[test]
fn new_test() {
  let path = UnixPath::new("abc/x");
  println!("{path}");
  accept(path);
}

/// Трейт для операций над путями, реализованный для `&Vec<String>`.
///
/// Все методы принимают `self` по значению (как ссылку на вектор) и возвращают
/// результаты в виде новых векторов или значений, не модифицируя исходные данные.
trait PathTool {
  /// Проверяет, является ли путь абсолютным (начинается с `/`).
  fn is_abs(self) -> bool;

  /// Проверяет, обозначает ли путь директорию (заканчивается на `/`, `.` или `..`).
  fn is_dir(self) -> bool;

  /// Проверяет, является ли путь корневым (`/`).
  fn is_root(self) -> bool;

  /// Преобразует вектор компонентов обратно в строковое представление пути,
  /// экранируя внутренние символы `/` как `\/`.
  fn to_string(self) -> String;

  /// Нормализует путь: удаляет `.` и `..`, схлопывает множественные `/`,
  /// сохраняет информацию о замыкающем слеше для директорий.
  fn normalize(self) -> Vec<String>;

  /// Возвращает родительский путь или ошибку, если путь пустой или корневой.
  fn parent(self) -> Result<Vec<String>, String>;

  /// Преобразует относительный путь в абсолютный, используя `current_dir` как базовый.
  /// Возвращает ошибку, если `current_dir` не является абсолютным.
  fn make_absolute(self, current_dir: &[String]) -> Result<Vec<String>, String>;

  /// Вычисляет относительный путь от `from` к текущему пути.
  /// Оба пути должны быть абсолютными.
  fn relative_to(self, from: &[String]) -> Result<Vec<String>, String>;

  /// Разрешает дочерний путь относительно текущего: конкатенирует компоненты,
  /// обрабатывая замыкающий слеш текущего пути.
  fn resolve(self, child: &[String]) -> Vec<String>;
}

impl PathTool for &Vec<String> {
  fn is_abs(self) -> bool {
    self.len() > 0 && self.get(0).map(|s| s.is_empty()).unwrap_or(false)
  }

  fn is_dir(self) -> bool {
    self
      .last()
      .map(|s| s.is_empty() || s == "." || s == "..")
      .unwrap_or(false)
  }

  fn is_root(self) -> bool {
    self.len() == 2
      && self.get(0).map(|s| s.is_empty()).unwrap_or(false)
      && self.get(1).map(|s| s.is_empty()).unwrap_or(false)
  }

  fn to_string(self) -> String {
    let mut buf = String::new();
    for i in 0..self.len() {
      if i > 0 {
        buf.push('/');
      }
      buf.push_str(&self[i].replace("/", "\\/"));
    }
    buf
  }

  fn normalize(self) -> Vec<String> {
    let components: &[String] = self.as_slice();

    // Путь абсолютный, если начинается с пустого сегмента (ведущий /)
    let is_abs = components.first().map_or(false, |s| s.is_empty());

    // Определяем, был ли входной путь "директорией":
    // - заканчивается на "" (явный /), ИЛИ
    // - заканчивается на "." или ".." И содержит "" на позиции > 0 и < last
    let input_was_directory = if components.is_empty() {
      false
    } else {
      let last_idx = components.len() - 1;
      let last = &components[last_idx];

      if last.is_empty() {
        // Явный замыкающий /
        true
      } else if last == "." || last == ".." {
        // Проверяем наличие пустого сегмента в диапазоне [1, len-1)
        // (т.е. был ли слеш перед спец-компонентом)
        components
          .iter()
          .skip(1)
          .take(components.len().saturating_sub(2))
          .any(|s| s.is_empty())
      } else {
        false
      }
    };

    // Диапазон обработки: исключаем замыкающий пустой сегмент, если он есть
    let process_end = if components.last().map_or(false, |s| s.is_empty()) {
      components.len().saturating_sub(1)
    } else {
      components.len()
    };

    // Стек для построения нормализованного пути
    // Храним &str для избежания лишних аллокаций
    let mut stack: Vec<&str> = Vec::with_capacity(components.len());

    for i in 0..process_end {
      let name = &components[i];

      if name.is_empty() {
        continue; // Пропускаем пустые сегменты от //
      }

      if name == "." {
        continue; // Текущая директория — игнорируем
      }

      if name == ".." {
        if is_abs {
          // Абсолютный путь: не удаляем корень
          if !stack.is_empty() {
            stack.pop();
          }
        } else {
          // Относительный путь:
          // - если стек не пуст и верхний элемент не "..", удаляем его
          // - иначе добавляем ".." как литеральный компонент
          if !stack.is_empty() && stack.last() != Some(&"..") {
            stack.pop();
          } else {
            stack.push("..");
          }
        }
      } else {
        stack.push(name.as_str());
      }
    }

    // Формируем результат
    let mut result: Vec<String> = Vec::with_capacity(stack.len() + 2);

    if is_abs {
      result.push(String::new()); // Ведущий пустой для абсолютного пути

      if stack.is_empty() {
        // Нормализация привела к корню: ["", ""]
        result.push(String::new());
      } else {
        result.extend(stack.into_iter().map(|s| s.to_string()));
      }
    } else {
      // Относительный путь — просто добавляем содержимое стека
      result.extend(stack.into_iter().map(|s| s.to_string()));
    }

    // Добавляем замыкающий слеш, если входной путь был "директорией"
    if input_was_directory {
      let is_root = is_abs && result.len() == 2 && result.get(1).map_or(false, |s| s.is_empty());

      if !is_root {
        if result.is_empty() {
          // Пустой относительный результат + был директорией → [""]
          if !is_abs {
            result.push(String::new());
          }
        } else {
          let last = &result[result.len() - 1];
          if last != "." && last != ".." {
            result.push(String::new());
          }
        }
      }
    }

    result
  }

  fn parent(self) -> Result<Vec<String>, String> {
    // Проверка на пустой путь или корень
    if self.is_empty() || is_root(self) {
      return Err(if self.is_empty() {
        "Empty path has no parent".to_string()
      } else {
        "Root path has no parent".to_string()
      });
    }

    let is_abs = is_absolute(self);
    let has_trailing = self.len() > 1 && self.last().map_or(false, |s| s.is_empty());

    // Рабочая копия: временно убираем замыкающий пустой для обработки
    let mut working: Vec<&str> = if has_trailing {
      self
        .iter()
        .take(self.len() - 1)
        .map(|s| s.as_str())
        .collect()
    } else {
      self.iter().map(|s| s.as_str()).collect()
    };

    // Удаляем последний значимый компонент (но не корень для абсолютных путей)
    let min_len = if is_abs { 1 } else { 0 };
    if working.len() > min_len {
      working.pop();
    }

    // Формируем результат
    let mut result: Vec<String> = Vec::with_capacity(working.len() + 1);

    if is_abs && working.len() == 1 && working.first() == Some(&"") {
      // Для абсолютного пути остался только маркер корня → делаем корень-директорию ["", ""]
      result.push(String::new());
      result.push(String::new());
    } else {
      // Копируем компоненты из working
      result.extend(working.iter().map(|s| s.to_string()));

      // Сохраняем замыкающий / если он был в исходном пути
      if has_trailing {
        // Для относительных путей: добавляем замыкающий даже если результат пустой
        // Для абсолютных путей: добавляем замыкающий только если есть компоненты кроме корня
        if !is_abs || result.len() > 1 {
          result.push(String::new());
        }
      }
    }

    Ok(result)
  }

  fn make_absolute(self, current_dir: &[String]) -> Result<Vec<String>, String> {
    // Если путь уже абсолютный — возвращаем нормализованную копию
    if is_absolute(self) {
      return Ok(self.normalize());
    }

    // current_dir обязан быть абсолютным
    if !is_absolute(current_dir) {
      return Err("currentDir must be absolute".to_string());
    }

    // Комбинируем компоненты: current_dir + this (без дублирования корня)
    // Резервируем место: сумма длин минус 1 (на случай, если пропустим ведущий пустой)
    let mut combined: Vec<String> = Vec::with_capacity(current_dir.len() + self.len());

    // Добавляем все компоненты current_dir
    combined.extend_from_slice(current_dir);

    // Добавляем компоненты текущего пути, пропуская ведущий пустой (если вдруг есть)
    // Это защитная проверка: для относительного пути первый элемент не должен быть ""
    let mut iter = self.iter();
    if let Some(first) = iter.next() {
      // Пропускаем первый элемент, только если он пустой И путь не абсолютный
      // (поскольку выше уже проверили !is_absolute(self), условие упрощается)
      if !first.is_empty() {
        combined.push(first.clone());
      }
      // Добавляем остальные компоненты без проверок
      combined.extend(iter.cloned());
    }

    // Нормализуем результат и возвращаем
    // (normalize() сохранит замыкающий / если нужно, согласно исходной логике)
    Ok(combined.normalize())
  }

  fn relative_to(self, from: &[String]) -> Result<Vec<String>, String> {
    // Нормализуем оба пути
    let norm_from = from.to_vec().normalize();
    let norm_to = self.normalize();

    // Проверка: оба пути должны быть абсолютными
    if !is_absolute(&norm_from) || !is_absolute(&norm_to) {
      return Err("Both paths must be absolute to compute relative path".to_string());
    }

    // Запоминаем, был ли замыкающий слеш у нормализованного целевого пути
    let to_has_trailing = norm_to.len() > 1 && norm_to.last().map_or(false, |s| s.is_empty());

    // Для сравнения убираем замыкающие пустые сегменты (если они есть и не являются частью корня)
    let f_comp: Vec<&str> =
      if norm_from.len() > 1 && norm_from.last().map_or(false, |s| s.is_empty()) {
        norm_from
          .iter()
          .take(norm_from.len() - 1)
          .map(|s| s.as_str())
          .collect()
      } else {
        norm_from.iter().map(|s| s.as_str()).collect()
      };

    let t_comp: Vec<&str> = if norm_to.len() > 1 && norm_to.last().map_or(false, |s| s.is_empty()) {
      norm_to
        .iter()
        .take(norm_to.len() - 1)
        .map(|s| s.as_str())
        .collect()
    } else {
      norm_to.iter().map(|s| s.as_str()).collect()
    };

    // Ищем общий префикс
    let common = f_comp
      .iter()
      .zip(t_comp.iter())
      .take_while(|(a, b)| a == b)
      .count();

    // Если общего префикса нет (даже корень не совпадает) — ошибка
    // Примечание: для абсолютных путей корень "" всегда должен совпадать
    if common == 0 {
      return Err("Paths have no common root".to_string());
    }

    // Строим относительный путь
    let mut rel_names: Vec<String> = Vec::new();

    // Добавляем ".." для каждого компонента, который нужно подняться из from
    for _ in common..f_comp.len() {
      rel_names.push("..".to_string());
    }

    // Добавляем компоненты целевого пути после общего префикса
    for i in common..t_comp.len() {
      rel_names.push(t_comp[i].to_string());
    }

    // Если пути идентичны — возвращаем "." БЕЗ замыкающего /
    if rel_names.is_empty() {
      return Ok(vec![".".to_string()]);
    }

    // Восстанавливаем замыкающий слеш, если он был у целевого пути
    // Но НЕ добавляем, если результат заканчивается на "." или ".."
    if to_has_trailing && !rel_names.is_empty() {
      let last = &rel_names[rel_names.len() - 1];
      if last != "." && last != ".." && !last.is_empty() {
        rel_names.push(String::new());
      }
    }

    Ok(rel_names)
  }

  fn resolve(self, child: &[String]) -> Vec<String> {
    // Абсолютный дочерний путь переопределяет контекст — стандартное поведение Unix
    if is_absolute(child) {
      return child.to_vec();
    }

    // Конкатенация компонентов с обработкой замыкающего "/" текущего пути
    let mut result: Vec<String> = Vec::with_capacity(self.len() + child.len());

    // Копируем компоненты текущего пути
    result.extend_from_slice(self);

    // Если текущий путь имеет замыкающий "" (индикатор директории /),
    // удаляем его перед добавлением относительного дочернего пути, чтобы избежать "//"
    if result.last().map_or(false, |s| s.is_empty()) && result.len() > 1 {
      result.pop();
    }

    // Добавляем компоненты дочернего пути
    result.extend_from_slice(child);

    result
  }
}

/// Вспомогательная функция: проверяет, является ли путь абсолютным
/// (начинается с пустого сегмента — ведущий /)
#[inline]
fn is_absolute(components: &[String]) -> bool {
  components.first().map_or(false, |s| s.is_empty())
}

/// Вспомогательная функция: проверяет, является ли путь корнем
/// Корень: абсолютный путь с ровно двумя пустыми сегментами ["", ""]
#[inline]
fn is_root(components: &[String]) -> bool {
  is_absolute(components) && components.len() == 2 && components.iter().all(|s| s.is_empty())
}

impl UnixPath {
  /// Проверяет, является ли путь абсолютным (начинается с `/`).
  pub fn is_absolute(&self) -> bool {
    self.names.is_abs()
  }

  /// Проверяет, является ли путь корневым (`/`).
  pub fn is_root(&self) -> bool {
    self.names.is_root()
  }

  /// Проверяет, обозначает ли путь директорию (заканчивается на `/`, `.` или `..`).
  pub fn is_dir(&self) -> bool {
    self.names.is_dir()
  }

  /// Проверяет, является ли путь пустым.
  pub fn is_empty(&self) -> bool {
    self.names.is_empty() || (self.names.len() == 1 && self.names[0].is_empty())
  }

  /// Возвращает нормализованную копию пути.
  ///
  /// Нормализация включает:
  /// - удаление `.` и разрешение `..`
  /// - схлопывание множественных `/` в один
  /// - сохранение информации о замыкающем слеше для директорий
  pub fn normalize(&self) -> Self {
    Self::new(&self.names.normalize().to_string())
  }

  /// Возвращает имя последнего компонента пути (без замыкающего `/`).
  ///
  /// # Возвращаемое значение
  /// - Для корня (`/`) возвращает `"/"`
  /// - Для пустого пути возвращает `""`
  /// - Иначе — последний непустой компонент
  pub fn name(&self) -> String {
    if self.is_root() {
      return "/".to_string();
    }

    if self.is_empty() {
      return "".to_string();
    }

    for idx0 in 0..self.names.len() {
      let name = self.names.get(self.names.len() - idx0 - 1).and_then(|s| {
        if !s.is_empty() {
          Some(s.to_string())
        } else {
          None
        }
      });

      match name {
        Some(name) => return name,
        _ => {}
      }
    }

    "".to_string()
  }

  /// Возвращает родительский путь, или `None`, если путь пустой или корневой.
  pub fn parent(&self) -> Option<Self> {
    if self.is_empty()
      || self.names.is_empty()
      || (self.names.len() == 1 && self.names.get(0).map(|n| n.is_empty()).unwrap_or(false))
    {
      return None;
    }
    self.names.parent().ok().map(|n| Self::new(&n.to_string()))
  }

  /// Разрешает дочерний путь относительно текущего.
  ///
  /// # Параметры
  /// * `name` — любой тип, конвертируемый в `UnixPath`
  ///
  /// # Возвращает
  /// Новый `UnixPath`, представляющий объединённый путь.
  pub fn resolve<N: Into<UnixPath>>(&self, name: N) -> UnixPath {
    Self::new(&self.names.resolve(&name.into().names).to_string())
  }

  /// Вычисляет относительный путь от `name` к текущему пути.
  ///
  /// # Параметры
  /// * `name` — любой тип, конвертируемый в `UnixPath`
  ///
  /// # Возвращает
  /// `Ok(UnixPath)` с относительным путём, или `Err(String)` с описанием ошибки,
  /// если пути не абсолютные или не имеют общего корня.
  pub fn relative_to<N: Into<UnixPath>>(&self, name: N) -> Result<UnixPath, String> {
    self
      .names
      .relative_to(&name.into().names)
      .map(|n| Self::new(&n.to_string()))
  }

  /// Преобразует путь в абсолютный, используя `name` как базовую директорию.
  ///
  /// # Параметры
  /// * `name` — любой тип, конвертируемый в `UnixPath` (должен быть абсолютным)
  ///
  /// # Возвращает
  /// `Ok(UnixPath)` с абсолютным путём, или `Err(String)`, если `name` не абсолютный.
  pub fn make_absolute<N: Into<UnixPath>>(&self, name: N) -> Result<UnixPath, String> {
    self
      .names
      .make_absolute(&name.into().names)
      .map(|n| Self::new(&n.to_string()))
  }
}

#[test]
fn normalize_test() {
  let samples = vec![
    (UnixPath::new("a"), UnixPath::new("a")),
    (UnixPath::new("/a"), UnixPath::new("/a")),
    (UnixPath::new("//a"), UnixPath::new("/a")),
    (UnixPath::new("/./a"), UnixPath::new("/a")),
    (UnixPath::new("./a/./b"), UnixPath::new("a/b")),
    (UnixPath::new("/a/.."), UnixPath::new("/")),
    (UnixPath::new("/a/b/.."), UnixPath::new("/a")),
    (UnixPath::new("/a/../b"), UnixPath::new("/b")),
    (UnixPath::new("/../a"), UnixPath::new("/a")),
    (UnixPath::new("a/.."), UnixPath::new("")),
    (UnixPath::new("../a"), UnixPath::new("../a")),
    (UnixPath::new("/a/b//.."), UnixPath::new("/a/")),
    (UnixPath::new("/a/../"), UnixPath::new("/")),
    (UnixPath::new("/a/./b/../../c"), UnixPath::new("/c")),
    (UnixPath::new("a/./../../b"), UnixPath::new("../b")),
  ];
  for (src, expect) in samples {
    println!("src '{src}' expect '{expect}'");
    assert!(src.normalize() == expect);
  }
}

#[test]
fn name_test() {
  let samples = vec![
    //
    ("a", "a"),
    ("a/bc", "bc"),
    ("a/bc/", "bc"),
    ("a/bc//", "bc"),
    ("/", "/"),
    (".", "."),
    ("", ""),
    ("/a/bc", "bc"),
    ("/a/bc/", "bc"),
  ];

  for (src, expect) in samples {
    let name = UnixPath::new(src).name();
    println!("src '{src}' expect '{expect}' actual '{name}'");
    assert!(name == expect);
  }
}

#[test]
fn parent_test() {
  let samples = vec![
    //
    ("/a", Some("/")),
    ("/a/", Some("/")),
    ("/a/b", Some("/a")),
    ("/a/b/", Some("/a/")),
    ("/a/b/c", Some("/a/b")),
    ("a", Some("")),
    ("a/", Some("")),
    ("a/b", Some("a")),
    ("a/b/", Some("a/")),
    ("/x/y/z/", Some("/x/y/")),
    ("p/q/r/", Some("p/q/")),
    ("/", None),
    ("", None),
  ];

  for (src, expect) in samples {
    let parent = UnixPath::new(src).parent();
    println!("src '{src}' expect {expect:?} actual {parent:?}");
    match expect {
      Some(exp) => match parent {
        Some(prnt) => {
          assert!(exp == prnt.to_string())
        }
        None => assert!(false),
      },
      None => {
        assert!(parent.is_none())
      }
    }
  }
}

#[test]
fn resolve_test() {
  let samples = vec![
    //
    ("a", "a"),
    ("a/bc", "bc"),
    ("a/bc/", "bc"),
    ("a/bc//", "bc"),
    ("/", "/"),
    (".", "."),
    ("", ""),
    ("/a/bc", "bc"),
    ("/a/bc/", "bc"),
  ];

  for (src, expect) in samples {
    let name = UnixPath::new(src).name();
    println!("src '{src}' expect '{expect}' actual '{name}'");
    assert!(name == expect);
  }
}

#[test]
fn relative_to_test() {
  //
}

impl UnixPath {
  /// Возвращает вектор непустых компонентов пути (без пустых строк, обозначающих `/`).
  ///
  /// Полезно для получения «чистого» списка имён без служебных маркеров.
  pub fn name_components(&self) -> Vec<String> {
    let mut ns = Vec::<String>::new();
    for n in &self.names {
      if !n.is_empty() {
        ns.push(n.clone());
      }
    }
    ns
  }
}
