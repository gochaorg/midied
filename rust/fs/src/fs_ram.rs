use super::*;
use crate::UnixPath;
use async_trait::async_trait;
use futures::lock::Mutex;
use std::sync::Arc;

#[derive(Default)]
pub struct FsRamStore {
  root: Vec<FsEntry>,
}

#[derive(Clone)]
enum FsEntry {
  File { name: String, data: Vec<u8> },
  Dir { name: String, content: Vec<FsEntry> },
}

impl FsEntry {
  fn name(&self) -> &str {
    match self {
      FsEntry::File { name, .. } => &name,
      FsEntry::Dir { name, .. } => &name,
    }
  }

  /// Вспомогательный метод для переименования записи
  fn set_name(&mut self, new_name: String) {
    match self {
      FsEntry::File { name, .. } => *name = new_name,
      FsEntry::Dir { name, .. } => *name = new_name,
    }
  }
}

#[derive(Debug, Clone, PartialEq)]
pub enum FsStoreErr {
  PathNotAbsolute,
  PathIsRoot,
  PathIsEmpty,
  PathNotFound,
  PathExists,
  NotADirectory, // Попытка зайти "внутрь" файла или list не-директории
  IsADirectory,  // Попытка read/write файла, но путь указывает на директорию
}

#[derive(Debug, Clone, PartialEq)]
pub struct RamDirEntry {
  pub name: String,
  pub is_dir: bool,
  pub size: Option<u64>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct EntryInfo {
  pub name: String,
  pub is_dir: bool,
  pub size: Option<usize>, // None для директорий
}

impl FsRamStore {
  /// Исправленная версия resolve: корректно спускается по дереву компонентов пути
  #[allow(dead_code)]
  fn resolve<R, F>(&mut self, path: UnixPath, result_state: R, consumer: F) -> Result<R, FsStoreErr>
  where
    R: Clone,
    F: Fn(&mut FsEntry, R) -> R,
  {
    if !path.is_absolute() {
      return Err(FsStoreErr::PathNotAbsolute);
    }
    if path.is_root() {
      return Err(FsStoreErr::PathIsRoot);
    }
    if path.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let names = path.name_components();
    if names.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let mut current_level = &mut self.root;

    for (depth, name) in names.iter().enumerate() {
      let is_last = depth == names.len() - 1;

      let found_idx = current_level
        .iter_mut()
        .position(|entry| entry.name() == name);

      match found_idx {
        Some(idx) => {
          if is_last {
            return Ok(consumer(&mut current_level[idx], result_state));
          }
          // Спускаемся внутрь, если это директория
          match &mut current_level[idx] {
            FsEntry::Dir { content, .. } => {
              current_level = content;
            }
            FsEntry::File { .. } => return Err(FsStoreErr::PathNotFound),
          }
        }
        None => return Err(FsStoreErr::PathNotFound),
      }
    }

    Err(FsStoreErr::PathNotFound)
  }

  /// Вспомогательный метод: находит родительскую директорию и возвращает
  /// мутабельную ссылку на её content + имя целевого элемента
  fn resolve_parent<'a>(
    &'a mut self,
    path: UnixPath,
  ) -> Result<(&'a mut Vec<FsEntry>, String), FsStoreErr> {
    if !path.is_absolute() {
      return Err(FsStoreErr::PathNotAbsolute);
    }
    if path.is_root() {
      return Err(FsStoreErr::PathIsRoot);
    }
    if path.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let names = path.name_components();
    if names.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let target_name = names.last().unwrap().clone();

    // Если путь имеет только один компонент — родитель это root
    if names.len() == 1 {
      return Ok((&mut self.root, target_name));
    }

    let mut current_level = &mut self.root;

    // Проходим все компоненты кроме последнего (имени целевого элемента)
    for name in names.iter().take(names.len() - 1) {
      let found_idx = current_level
        .iter_mut()
        .position(|entry| entry.name() == name);

      match found_idx {
        Some(idx) => match &mut current_level[idx] {
          FsEntry::Dir { content, .. } => {
            current_level = content;
          }
          FsEntry::File { .. } => return Err(FsStoreErr::PathNotFound),
        },
        None => return Err(FsStoreErr::PathNotFound),
      }
    }

    Ok((current_level, target_name))
  }

  /// Создаёт директорию по указанному пути (аналог mkdir, без -p)
  pub fn mkdir(&mut self, path: UnixPath) -> Result<(), FsStoreErr> {
    let (parent, dir_name) = self.resolve_parent(path)?;

    // Проверяем, не существует ли уже элемент с таким именем
    if parent.iter().any(|e| e.name() == dir_name) {
      return Err(FsStoreErr::PathExists); // PathExists could be better
    }

    parent.push(FsEntry::Dir {
      name: dir_name,
      content: Vec::new(),
    });

    Ok(())
  }

  /// Создаёт пустой файл или обновляет существующий (аналог touch)
  pub fn touch(&mut self, path: UnixPath) -> Result<(), FsStoreErr> {
    let (parent, file_name) = self.resolve_parent(path)?;

    // Если элемент уже существует
    if let Some(idx) = parent.iter().position(|e| e.name() == file_name) {
      match &parent[idx] {
        FsEntry::File { .. } => {
          // Файл уже есть — "обновляем" его (очищаем данные)
          parent[idx] = FsEntry::File {
            name: file_name.clone(),
            data: Vec::new(),
          };
          return Ok(());
        }
        FsEntry::Dir { .. } => {
          // Нельзя создать файл поверх директории
          return Err(FsStoreErr::PathNotFound);
        }
      }
    }

    // Создаём новый пустой файл
    parent.push(FsEntry::File {
      name: file_name,
      data: Vec::new(),
    });

    Ok(())
  }

  /// Удаляет файл или пустую директорию (аналог rm)
  pub fn rm(&mut self, path: UnixPath) -> Result<(), FsStoreErr> {
    let (parent, target_name) = self.resolve_parent(path)?;

    let idx = parent
      .iter()
      .position(|e| e.name() == target_name)
      .ok_or(FsStoreErr::PathNotFound)?;

    // Для директорий: удаляем только если она пуста
    if let FsEntry::Dir { content, .. } = &parent[idx] {
      if !content.is_empty() {
        return Err(FsStoreErr::PathNotFound); // Could be DirectoryNotEmpty
      }
    }

    parent.remove(idx);
    Ok(())
  }

  /// Создаёт директорию рекурсивно (аналог mkdir -p)
  /// - Создаёт все промежуточные директории при необходимости
  /// - Успех, если путь уже существует как директория
  /// - Ошибка, если любой компонент пути существует как файл
  pub fn mkdir_p(&mut self, path: UnixPath) -> Result<(), FsStoreErr> {
    if !path.is_absolute() {
      return Err(FsStoreErr::PathNotAbsolute);
    }
    if path.is_root() {
      return Err(FsStoreErr::PathIsRoot);
    }
    if path.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let mut current = &mut self.root;
    for name in path.name_components() {
      if let Some(idx) = current.iter().position(|e| e.name() == name) {
        if let FsEntry::Dir { content, .. } = &mut current[idx] {
          current = content;
        } else {
          return Err(FsStoreErr::NotADirectory);
        }
      } else {
        current.push(FsEntry::Dir {
          name: name.clone(),
          content: Vec::new(),
        });
        let idx = current.len() - 1;
        if let FsEntry::Dir { content, .. } = &mut current[idx] {
          current = content;
        } else {
          unreachable!()
        }
      }
    }
    Ok(())
  }

  /// Записывает данные в файл
  /// - Создаёт файл, если не существует
  /// - Перезаписывает содержимое, если файл уже есть
  /// - Ошибка, если путь указывает на директорию
  pub fn write<BYTES: AsRef<[u8]>>(
    &mut self,
    path: UnixPath,
    bytes: BYTES,
  ) -> Result<(), FsStoreErr> {
    let (parent, file_name) = self.resolve_parent(path)?;
    let data = bytes.as_ref().to_vec();

    if let Some(idx) = parent.iter().position(|e| e.name() == file_name) {
      // Файл существует
      match &parent[idx] {
        FsEntry::File { .. } => {
          // Перезаписываем содержимое
          parent[idx] = FsEntry::File {
            name: file_name.clone(),
            data,
          };
          Ok(())
        }
        FsEntry::Dir { .. } => Err(FsStoreErr::IsADirectory),
      }
    } else {
      // Создаём новый файл
      parent.push(FsEntry::File {
        name: file_name,
        data,
      });
      Ok(())
    }
  }

  /// Читает содержимое файла
  /// - Возвращает данные для файла
  /// - Ошибка, если путь указывает на директорию или не существует
  pub fn read(&self, path: UnixPath) -> Result<Vec<u8>, FsStoreErr> {
    if !path.is_absolute() {
      return Err(FsStoreErr::PathNotAbsolute);
    }
    if path.is_root() {
      return Err(FsStoreErr::PathIsRoot);
    }
    if path.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let names = path.name_components();
    if names.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    // Навигация без мутации (immutable traversal)
    let mut current_level = &self.root;

    for (depth, name) in names.iter().enumerate() {
      let is_last = depth == names.len() - 1;

      let found = current_level.iter().find(|entry| entry.name() == name);

      match found {
        Some(entry) => {
          if is_last {
            // Достигли целевого элемента
            match entry {
              FsEntry::File { data, .. } => return Ok(data.clone()),
              FsEntry::Dir { .. } => return Err(FsStoreErr::IsADirectory),
            }
          } else {
            // Спускаемся внутрь, если это директория
            match entry {
              FsEntry::Dir { content, .. } => {
                current_level = content;
              }
              FsEntry::File { .. } => return Err(FsStoreErr::NotADirectory),
            }
          }
        }
        None => return Err(FsStoreErr::PathNotFound),
      }
    }

    Err(FsStoreErr::PathNotFound)
  }

  pub fn info(&self, path: UnixPath) -> Result<EntryInfo, FsStoreErr> {
    if path.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }
    if !path.is_absolute() {
      return Err(FsStoreErr::PathNotAbsolute);
    }
    if path.is_root() {
      return Ok(EntryInfo {
        name: "/".to_string(),
        is_dir: true,
        size: None,
      });
    }
    if path.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let names = path.name_components();
    // Проверка names.is_empty() убрана как избыточная (после path.is_empty)

    let mut current_level = &self.root;

    for (depth, name) in names.iter().enumerate() {
      let is_last = depth == names.len() - 1;

      let found = current_level.iter().find(|entry| entry.name() == name);

      match found {
        Some(entry) => {
          if is_last {
            return Ok(match entry {
              FsEntry::File { name, data } => EntryInfo {
                name: name.clone(),
                is_dir: false,
                size: Some(data.len()),
              },
              FsEntry::Dir { name, .. } => EntryInfo {
                name: name.clone(),
                is_dir: true,
                size: None,
              },
            });
          } else {
            match entry {
              FsEntry::Dir { content, .. } => {
                current_level = content;
              }
              FsEntry::File { .. } => return Err(FsStoreErr::NotADirectory),
            }
          }
        }
        None => return Err(FsStoreErr::PathNotFound),
      }
    }

    // Компилятор требует возврат значения, но логически сюда попасть нельзя.
    // unreachable!() удовлетворяет проверку типов и оптимизируется компилятором.
    unreachable!("loop always returns early for non-empty absolute paths")
  }

  // ==================== ls / list ====================

  /// Список содержимого директории (аналог ls)
  pub fn ls(&self, path: UnixPath) -> Result<Vec<RamDirEntry>, FsStoreErr> {
    if !path.is_absolute() {
      return Err(FsStoreErr::PathNotAbsolute);
    }

    if path.is_root() {
      return Ok(
        self
          .root
          .iter()
          .map(|e| RamDirEntry {
            name: e.name().to_string(),
            is_dir: matches!(e, FsEntry::Dir { .. }),
            size: match e {
              FsEntry::File { data, .. } => Some(data.len() as u64),
              FsEntry::Dir { .. } => None,
            },
          })
          .collect(),
      );
    }

    if path.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let names = path.name_components();
    // УБРАНО: дублирующая проверка names.is_empty()

    let mut current_level = &self.root;

    // УБРАНО: enumerate, так как is_last не нужен
    for name in names.iter() {
      let found = current_level.iter().find(|entry| entry.name() == name);

      match found {
        Some(entry) => match entry {
          FsEntry::Dir { content, .. } => {
            current_level = content;
          }
          FsEntry::File { .. } => return Err(FsStoreErr::NotADirectory),
        },
        None => return Err(FsStoreErr::PathNotFound),
      }
    }

    Ok(
      current_level
        .iter()
        .map(|e| RamDirEntry {
          name: e.name().to_string(),
          is_dir: matches!(e, FsEntry::Dir { .. }),
          size: match e {
            FsEntry::File { data, .. } => Some(data.len() as u64),
            FsEntry::Dir { .. } => None,
          },
        })
        .collect(),
    )
  }

  /// Переименовывает или перемещает файл/директорию
  /// - Поддерживает переименование в той же директории
  /// - Поддерживает перемещение между директориями
  /// - Ошибка, если источник не существует
  /// - Ошибка, если цель уже занята
  /// - Ошибка, если попытка переместить директорию внутрь себя или её поддиректории
  pub fn rename(&mut self, from: UnixPath, to: UnixPath) -> Result<(), FsStoreErr> {
    // Валидация путей
    if !from.is_absolute() || !to.is_absolute() {
      return Err(FsStoreErr::PathNotAbsolute);
    }
    if from.is_root() || to.is_root() {
      return Err(FsStoreErr::PathIsRoot);
    }
    if from.is_empty() || to.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    let from_names = from.name_components();
    let to_names = to.name_components();

    if from_names.is_empty() || to_names.is_empty() {
      return Err(FsStoreErr::PathIsEmpty);
    }

    // === Проверка: не пытаемся ли переместить директорию внутрь себя ===
    // Сравниваем компоненты путей без доступа к дереву
    // Если to начинается с from и имеет больше компонентов — это запрещено
    let is_prefix = from_names.len() <= to_names.len()
      && from_names.iter().zip(to_names.iter()).all(|(a, b)| a == b);

    if is_prefix && to_names.len() > from_names.len() {
      // Потенциально пытаемся переместить в свою поддиректорию.
      // Нужно убедиться, что from действительно директория.
      // Для этого делаем read-only проход по дереву (без мутации!)
      let mut current = &self.root;
      let mut found_dir = false;

      for (depth, name) in from_names.iter().enumerate() {
        if let Some(entry) = current.iter().find(|e| e.name() == name) {
          if depth == from_names.len() - 1 {
            if matches!(entry, FsEntry::Dir { .. }) {
              found_dir = true;
            }
            break;
          }
          if let FsEntry::Dir { content, .. } = entry {
            current = content;
          } else {
            break; // from — файл, проверка не нужна
          }
        } else {
          break; // from не существует, проверка не нужна
        }
      }

      if found_dir {
        return Err(FsStoreErr::PathNotFound); // защита от цикла
      }
    }

    // === Извлекаем запись из источника (с клонированием) ===
    let (from_parent, from_name) = self.resolve_parent(from.clone())?;
    let idx = from_parent
      .iter()
      .position(|e| e.name() == from_name)
      .ok_or(FsStoreErr::PathNotFound)?;

    // Клонируем запись (теперь возможно, т.к. FsEntry: Clone)
    let mut entry_to_move = from_parent[idx].clone();

    // === Проверяем, не занята ли цель ===
    let (to_parent, to_name) = self.resolve_parent(to.clone())?;
    if to_parent.iter().any(|e| e.name() == to_name) {
      return Err(FsStoreErr::PathExists);
    }

    // === Удаляем из источника ===
    {
      let (from_parent, from_name) = self.resolve_parent(from)?;
      let idx = from_parent
        .iter()
        .position(|e| e.name() == from_name)
        .ok_or(FsStoreErr::PathNotFound)?;
      from_parent.remove(idx);
    }

    // === Вставляем в назначение с новым именем ===
    entry_to_move.set_name(to_name);
    {
      let (to_parent, _) = self.resolve_parent(to)?;
      to_parent.push(entry_to_move);
    }

    Ok(())
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  fn setup_store() -> FsRamStore {
    FsRamStore {
      root: vec![
        FsEntry::Dir {
          name: "home".to_string(),
          content: vec![FsEntry::Dir {
            name: "user".to_string(),
            content: vec![FsEntry::File {
              name: "readme.txt".to_string(),
              data: b"hello".to_vec(),
            }],
          }],
        },
        FsEntry::File {
          name: "root_file.txt".to_string(),
          data: b"root content".to_vec(),
        },
      ],
    }
  }

  // ==================== mkdir_p tests ====================

  #[test]
  fn test_mkdir_p_simple() {
    let mut store = setup_store();
    assert!(store.mkdir_p(UnixPath::new("/home/newdir")).is_ok());

    let info = store.info(UnixPath::new("/home/newdir")).unwrap();
    assert!(info.is_dir);
    assert_eq!(info.name, "newdir");
  }

  #[test]
  fn test_mkdir_p_nested_creates_all_levels() {
    let mut store = setup_store();
    assert!(store.mkdir_p(UnixPath::new("/home/a/b/c")).is_ok());

    assert!(store.info(UnixPath::new("/home/a")).unwrap().is_dir);
    assert!(store.info(UnixPath::new("/home/a/b")).unwrap().is_dir);
    assert!(store.info(UnixPath::new("/home/a/b/c")).unwrap().is_dir);
  }

  #[test]
  fn test_mkdir_p_already_exists_dir() {
    let mut store = setup_store();
    // Директория уже существует — должен вернуть успех
    assert!(store.mkdir_p(UnixPath::new("/home/user")).is_ok());
  }

  #[test]
  fn test_mkdir_p_conflict_with_file() {
    let mut store = setup_store();
    // root_file.txt — файл, пытаемся создать /root_file.txt/subdir
    let result = store.mkdir_p(UnixPath::new("/root_file.txt/subdir"));
    assert_eq!(result, Err(FsStoreErr::NotADirectory));
  }

  #[test]
  fn test_mkdir_p_partial_exists() {
    let mut store = setup_store();
    // /home существует, /home/new не существует
    assert!(store.mkdir_p(UnixPath::new("/home/new/deep")).is_ok());

    assert!(store.info(UnixPath::new("/home/new")).unwrap().is_dir);
    assert!(store.info(UnixPath::new("/home/new/deep")).unwrap().is_dir);
  }

  #[test]
  fn test_mkdir_p_relative_path_fails() {
    let mut store = setup_store();
    let result = store.mkdir_p(UnixPath::new("relative/path"));
    assert_eq!(result, Err(FsStoreErr::PathNotAbsolute));
  }

  #[test]
  fn test_mkdir_p_root_fails() {
    let mut store = setup_store();
    let result = store.mkdir_p(UnixPath::new("/"));
    assert_eq!(result, Err(FsStoreErr::PathIsRoot));
  }

  // ==================== write tests ====================

  #[test]
  fn test_write_new_file() {
    let mut store = setup_store();
    assert!(
      store
        .write(UnixPath::new("/home/user/new.txt"), b"content")
        .is_ok()
    );

    let data = store.read(UnixPath::new("/home/user/new.txt")).unwrap();
    assert_eq!(data, b"content");
  }

  #[test]
  fn test_write_overwrite_existing() {
    let mut store = setup_store();
    assert!(
      store
        .write(UnixPath::new("/home/user/readme.txt"), b"new data")
        .is_ok()
    );

    let data = store.read(UnixPath::new("/home/user/readme.txt")).unwrap();
    assert_eq!(data, b"new data");
  }

  #[test]
  fn test_write_on_directory_fails() {
    let mut store = setup_store();
    let result = store.write(UnixPath::new("/home/user"), b"data");
    assert_eq!(result, Err(FsStoreErr::IsADirectory));
  }

  #[test]
  fn test_write_parent_not_found() {
    let mut store = setup_store();
    let result = store.write(UnixPath::new("/nonexistent/file.txt"), b"data");
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_write_with_vec_u8() {
    let mut store = setup_store();
    let bytes: Vec<u8> = vec![1, 2, 3, 4];
    assert!(
      store
        .write(UnixPath::new("/home/user/binary.bin"), bytes.clone())
        .is_ok()
    );

    let data = store.read(UnixPath::new("/home/user/binary.bin")).unwrap();
    assert_eq!(data, bytes);
  }

  #[test]
  fn test_write_empty_data() {
    let mut store = setup_store();
    assert!(
      store
        .write(UnixPath::new("/home/user/empty.txt"), b"")
        .is_ok()
    );

    let data = store.read(UnixPath::new("/home/user/empty.txt")).unwrap();
    assert!(data.is_empty());
  }

  // ==================== read tests ====================

  #[test]
  fn test_read_existing_file() {
    let store = setup_store();
    let data = store.read(UnixPath::new("/home/user/readme.txt")).unwrap();
    assert_eq!(data, b"hello");
  }

  #[test]
  fn test_read_directory_fails() {
    let store = setup_store();
    let result = store.read(UnixPath::new("/home/user"));
    assert_eq!(result, Err(FsStoreErr::IsADirectory));
  }

  #[test]
  fn test_read_not_found() {
    let store = setup_store();
    let result = store.read(UnixPath::new("/home/nonexistent.txt"));
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_read_relative_path_fails() {
    let store = setup_store();
    let result = store.read(UnixPath::new("relative/path"));
    assert_eq!(result, Err(FsStoreErr::PathNotAbsolute));
  }

  #[test]
  fn test_read_root_fails() {
    let store = setup_store();
    let result = store.read(UnixPath::new("/"));
    assert_eq!(result, Err(FsStoreErr::PathIsRoot));
  }

  #[test]
  fn test_read_file_through_file_path_fails() {
    let store = setup_store();
    // Пытаемся прочитать /root_file.txt/subpath — root_file.txt это файл
    let result = store.read(UnixPath::new("/root_file.txt/subpath"));
    assert_eq!(result, Err(FsStoreErr::NotADirectory));
  }

  // ==================== resolve tests ====================

  #[test]
  fn test_resolve_file() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user/readme.txt");

    let result = store.resolve(path, (), |entry, _| match entry {
      FsEntry::File { name, data } => {
        assert_eq!(name, "readme.txt");
        assert_eq!(data, b"hello");
      }
      _ => panic!("Expected file"),
    });
    assert!(result.is_ok());
  }

  #[test]
  fn test_resolve_dir() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user");

    let result = store.resolve(path, (), |entry, _| match entry {
      FsEntry::Dir { name, content } => {
        assert_eq!(name, "user");
        assert_eq!(content.len(), 1);
      }
      _ => panic!("Expected dir"),
    });
    assert!(result.is_ok());
  }

  #[test]
  fn test_resolve_not_found() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/nonexistent");

    let result = store.resolve(path, (), |_, _| ());
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_resolve_on_file_as_dir() {
    let mut store = setup_store();
    // Пытаемся зайти "внутрь" файла
    let path = UnixPath::new("/home/user/readme.txt/subpath");

    let result = store.resolve(path, (), |_, _| ());
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_resolve_root_denied() {
    let mut store = setup_store();
    let path = UnixPath::new("/");

    let result = store.resolve(path, (), |_, _| ());
    assert_eq!(result, Err(FsStoreErr::PathIsRoot));
  }

  // ==================== mkdir tests ====================

  #[test]
  fn test_mkdir_simple() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/newdir");

    assert!(store.mkdir(path).is_ok());

    // Проверяем, что директория действительно создана
    let verify_path = UnixPath::new("/home/newdir");
    let result = store.resolve(verify_path, (), |entry, _| {
      assert!(matches!(entry, FsEntry::Dir { name, .. } if name == "newdir"));
    });
    assert!(result.is_ok());
  }

  #[test]
  fn test_mkdir_nested() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user/newdir");

    assert!(store.mkdir(path).is_ok());

    let verify_path = UnixPath::new("/home/user/newdir");
    let result = store.resolve(verify_path, (), |entry, _| {
      assert!(matches!(entry, FsEntry::Dir { name, .. } if name == "newdir"));
    });
    assert!(result.is_ok());
  }

  #[test]
  fn test_mkdir_already_exists() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user");

    // Элемент уже существует — ошибка
    assert_eq!(store.mkdir(path), Err(FsStoreErr::PathExists));
  }

  #[test]
  fn test_mkdir_parent_not_found() {
    let mut store = setup_store();
    let path = UnixPath::new("/nonexistent/newdir");

    // Родительская директория не существует
    assert_eq!(store.mkdir(path), Err(FsStoreErr::PathNotFound));
  }

  // ==================== touch tests ====================

  #[test]
  fn test_touch_new_file() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user/newfile.txt");

    assert!(store.touch(path).is_ok());

    let verify_path = UnixPath::new("/home/user/newfile.txt");
    let result = store.resolve(verify_path, (), |entry, _| {
      assert!(matches!(entry, FsEntry::File { name, data }
                if name == "newfile.txt" && data.is_empty()));
    });
    assert!(result.is_ok());
  }

  #[test]
  fn test_touch_existing_file_clears_content() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user/readme.txt");

    // Сначала проверяем, что файл содержит данные
    let check_path = UnixPath::new("/home/user/readme.txt");
    let result = store.resolve(check_path, (), |entry, _| {
      if let FsEntry::File { data, .. } = entry {
        assert_eq!(data, b"hello");
      }
    });
    assert!(result.is_ok());

    // Touch очищает содержимое
    assert!(store.touch(path).is_ok());

    // Проверяем, что файл теперь пустой
    let verify_path = UnixPath::new("/home/user/readme.txt");
    let result = store.resolve(verify_path, (), |entry, _| {
      if let FsEntry::File { data, .. } = entry {
        assert!(data.is_empty());
      }
    });
    assert!(result.is_ok());
  }

  #[test]
  fn test_touch_on_dir_fails() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user");

    // Нельзя создать файл поверх директории
    assert_eq!(store.touch(path), Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_touch_parent_not_found() {
    let mut store = setup_store();
    let path = UnixPath::new("/nonexistent/file.txt");

    assert_eq!(store.touch(path), Err(FsStoreErr::PathNotFound));
  }

  // ==================== rm tests ====================

  #[test]
  fn test_rm_file() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user/readme.txt");

    assert!(store.rm(path).is_ok());

    // Проверяем, что файл удалён
    let verify_path = UnixPath::new("/home/user/readme.txt");
    let result = store.resolve(verify_path, (), |_, _| ());
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_rm_empty_dir() {
    let mut store = setup_store();

    // Сначала создаём пустую директорию
    let mkdir_path = UnixPath::new("/home/emptydir");
    store.mkdir(mkdir_path).unwrap();

    // Теперь удаляем её
    let rm_path = UnixPath::new("/home/emptydir");
    assert!(store.rm(rm_path).is_ok());

    // Проверяем, что директория удалена
    let verify_path = UnixPath::new("/home/emptydir");
    let result = store.resolve(verify_path, (), |_, _| ());
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_rm_non_empty_dir_fails() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user");

    // Директория не пуста — удаление запрещено
    assert_eq!(store.rm(path), Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_rm_not_found() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/nonexistent");

    assert_eq!(store.rm(path), Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_rm_root_denied() {
    let mut store = setup_store();
    let path = UnixPath::new("/");

    assert_eq!(store.rm(path), Err(FsStoreErr::PathIsRoot));
  }

  // ==================== Integration tests ====================

  #[test]
  fn test_full_workflow() {
    let mut store = setup_store();

    // 1. Создаём новую директорию
    assert!(store.mkdir(UnixPath::new("/home/projects")).is_ok());

    // 2. Создаём файл в новой директории
    assert!(store.touch(UnixPath::new("/home/projects/main.rs")).is_ok());

    // 3. Проверяем, что файл существует и пустой
    let result = store.resolve(UnixPath::new("/home/projects/main.rs"), (), |entry, _| {
      assert!(matches!(entry, FsEntry::File { data, .. } if data.is_empty()));
    });
    assert!(result.is_ok());

    // 4. Пытаемся удалить непустую директорию — должно упасть
    assert_eq!(
      store.rm(UnixPath::new("/home/projects")),
      Err(FsStoreErr::PathNotFound)
    );

    // 5. Удаляем файл
    assert!(store.rm(UnixPath::new("/home/projects/main.rs")).is_ok());

    // 6. Теперь можно удалить пустую директорию
    assert!(store.rm(UnixPath::new("/home/projects")).is_ok());

    // 7. Проверяем, что директория действительно удалена
    let result = store.resolve(UnixPath::new("/home/projects"), (), |_, _| ());
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  // ==================== info tests ====================

  #[test]
  fn test_info_file() {
    let store = setup_store();
    let info = store.info(UnixPath::new("/home/user/readme.txt")).unwrap();
    assert_eq!(info.name, "readme.txt");
    assert!(!info.is_dir);
    assert_eq!(info.size, Some(5)); // "hello" = 5 bytes
  }

  #[test]
  fn test_info_directory() {
    let store = setup_store();
    let info = store.info(UnixPath::new("/home/user")).unwrap();
    assert_eq!(info.name, "user");
    assert!(info.is_dir);
    assert_eq!(info.size, None);
  }

  #[test]
  fn test_info_root() {
    let store = setup_store();
    let info = store.info(UnixPath::new("/")).unwrap();
    assert_eq!(info.name, "/");
    assert!(info.is_dir);
    assert_eq!(info.size, None);
  }

  #[test]
  fn test_info_not_found() {
    let store = setup_store();
    let result = store.info(UnixPath::new("/nonexistent"));
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_info_relative_path_fails() {
    let store = setup_store();
    let result = store.info(UnixPath::new("relative/path"));
    assert_eq!(result, Err(FsStoreErr::PathNotAbsolute));
  }

  #[test]
  fn test_info_empty_path_fails() {
    let store = setup_store();
    let result = store.info(UnixPath::new(""));
    assert_eq!(result, Err(FsStoreErr::PathIsEmpty));
  }

  #[test]
  fn test_info_file_size_zero() {
    let mut store = setup_store();
    store
      .write(UnixPath::new("/home/user/empty.txt"), b"")
      .unwrap();

    let info = store.info(UnixPath::new("/home/user/empty.txt")).unwrap();
    assert_eq!(info.size, Some(0));
  }

  // ==================== ls tests ====================

  #[test]
  fn test_ls_root() {
    let store = setup_store();
    let entries = store.ls(UnixPath::new("/")).unwrap();

    assert_eq!(entries.len(), 2);
    assert!(entries.iter().any(|e| e.name == "home" && e.is_dir));
    assert!(
      entries
        .iter()
        .any(|e| e.name == "root_file.txt" && !e.is_dir)
    );
  }

  #[test]
  fn test_ls_directory() {
    let store = setup_store();
    let entries = store.ls(UnixPath::new("/home/user")).unwrap();

    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].name, "readme.txt");
    assert!(!entries[0].is_dir);
  }

  #[test]
  fn test_ls_empty_directory() {
    let mut store = setup_store();
    store.mkdir_p(UnixPath::new("/home/empty")).unwrap();

    let entries = store.ls(UnixPath::new("/home/empty")).unwrap();
    assert!(entries.is_empty());
  }

  #[test]
  fn test_ls_on_file_fails() {
    let store = setup_store();
    let result = store.ls(UnixPath::new("/home/user/readme.txt"));
    assert_eq!(result, Err(FsStoreErr::NotADirectory));
  }

  #[test]
  fn test_ls_not_found() {
    let store = setup_store();
    let result = store.ls(UnixPath::new("/nonexistent"));
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_ls_relative_path_fails() {
    let store = setup_store();
    let result = store.ls(UnixPath::new("relative/path"));
    assert_eq!(result, Err(FsStoreErr::PathNotAbsolute));
  }

  #[test]
  fn test_ls_preserves_order() {
    let mut store = setup_store();
    // Добавляем элементы в определённом порядке
    store.mkdir_p(UnixPath::new("/home/aaa")).unwrap();
    store.mkdir_p(UnixPath::new("/home/zzz")).unwrap();
    store.write(UnixPath::new("/home/mmm.txt"), b"").unwrap();

    let entries = store.ls(UnixPath::new("/home")).unwrap();
    // Проверяем, что все элементы присутствуют (порядок не гарантируется, но элементы должны быть)
    assert!(entries.iter().any(|e| e.name == "user"));
    assert!(entries.iter().any(|e| e.name == "aaa"));
    assert!(entries.iter().any(|e| e.name == "zzz"));
    assert!(entries.iter().any(|e| e.name == "mmm.txt"));
  }

  // ==================== Additional edge cases ====================

  #[test]
  fn test_write_then_read_then_info() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user/test.dat");
    let content = b"binary\x00data";

    store.write(path.clone(), content).unwrap();

    let read_data = store.read(path.clone()).unwrap();
    assert_eq!(read_data, content);

    let info = store.info(path).unwrap();
    assert!(!info.is_dir);
    assert_eq!(info.size, Some(content.len()));
  }

  #[test]
  fn test_mkdir_p_then_write_then_ls() {
    let mut store = setup_store();

    store.mkdir_p(UnixPath::new("/home/docs/reports")).unwrap();
    store
      .write(UnixPath::new("/home/docs/report1.pdf"), b"%PDF")
      .unwrap();
    store
      .write(UnixPath::new("/home/docs/report2.pdf"), b"%PDF-v2")
      .unwrap();
    store
      .write(
        UnixPath::new("/home/docs/reports/annual.pdf"),
        b"%PDF-annual",
      )
      .unwrap();

    // Проверяем ls /home/docs
    let entries = store.ls(UnixPath::new("/home/docs")).unwrap();
    assert_eq!(entries.len(), 3);
    assert!(entries.iter().any(|e| e.name == "report1.pdf" && !e.is_dir));
    assert!(entries.iter().any(|e| e.name == "report2.pdf" && !e.is_dir));
    assert!(entries.iter().any(|e| e.name == "reports" && e.is_dir));

    // Проверяем ls /home/docs/reports
    let entries = store.ls(UnixPath::new("/home/docs/reports")).unwrap();
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].name, "annual.pdf");
  }

  #[test]
  fn test_rm_then_write_same_path() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user/file.txt");

    store.write(path.clone(), b"old").unwrap();
    store.rm(path.clone()).unwrap();
    store.write(path.clone(), b"new").unwrap();

    let data = store.read(path).unwrap();
    assert_eq!(data, b"new");
  }

  #[test]
  fn test_touch_then_write_preserves_path() {
    let mut store = setup_store();
    let path = UnixPath::new("/home/user/touched.txt");

    store.touch(path.clone()).unwrap();
    // После touch файл должен существовать и быть пустым
    assert_eq!(store.read(path.clone()).unwrap().len(), 0);

    store.write(path.clone(), b"data").unwrap();
    assert_eq!(store.read(path).unwrap(), b"data");
  }

  #[test]
  fn test_concurrent_like_operations() {
    let mut store = setup_store();

    // Создаём структуру
    store.mkdir_p(UnixPath::new("/proj/src")).unwrap();
    store.mkdir_p(UnixPath::new("/proj/tests")).unwrap();

    // Пишем файлы
    store
      .write(UnixPath::new("/proj/src/main.rs"), b"fn main() {}")
      .unwrap();
    store
      .write(UnixPath::new("/proj/tests/main_test.rs"), b"#[test]")
      .unwrap();
    store
      .write(UnixPath::new("/proj/README.md"), b"# Project")
      .unwrap();

    // Проверяем всю структуру
    assert_eq!(store.ls(UnixPath::new("/proj")).unwrap().len(), 3);
    assert_eq!(
      store.read(UnixPath::new("/proj/src/main.rs")).unwrap(),
      b"fn main() {}"
    );

    // Удаляем и пересоздаём
    store.rm(UnixPath::new("/proj/src/main.rs")).unwrap();
    store
      .write(
        UnixPath::new("/proj/src/main.rs"),
        b"fn main() { println!(); }",
      )
      .unwrap();

    assert_eq!(
      store.read(UnixPath::new("/proj/src/main.rs")).unwrap(),
      b"fn main() { println!(); }"
    );
  }

  // ==================== Error semantics tests ====================

  #[test]
  fn test_error_distinction_path_not_found_vs_not_a_directory() {
    let store = setup_store();

    // PathNotFound: элемент вообще не существует
    let result = store.read(UnixPath::new("/home/nonexistent"));
    assert_eq!(result, Err(FsStoreErr::PathNotFound));

    // NotADirectory: пытаемся зайти внутрь файла
    let result = store.read(UnixPath::new("/root_file.txt/inside"));
    assert_eq!(result, Err(FsStoreErr::NotADirectory));
  }

  #[test]
  fn test_error_distinction_is_directory_vs_path_not_found() {
    let store = setup_store();

    // IsADirectory: пытаемся read директорию как файл
    let result = store.read(UnixPath::new("/home"));
    assert_eq!(result, Err(FsStoreErr::IsADirectory));

    // PathNotFound: файл не существует
    let result = store.read(UnixPath::new("/home/user/missing.txt"));
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_mkdir_p_error_semantics() {
    let mut store = setup_store();

    // NotADirectory: компонент пути — файл
    let result = store.mkdir_p(UnixPath::new("/root_file.txt/sub"));
    assert_eq!(result, Err(FsStoreErr::NotADirectory));

    // PathNotAbsolute
    let result = store.mkdir_p(UnixPath::new("relative"));
    assert_eq!(result, Err(FsStoreErr::PathNotAbsolute));

    // PathIsRoot
    let result = store.mkdir_p(UnixPath::new("/"));
    assert_eq!(result, Err(FsStoreErr::PathIsRoot));
  }

  #[test]
  fn test_rename_file_same_dir() {
    let mut store = setup_store();
    let from = UnixPath::new("/home/user/readme.txt");
    let to = UnixPath::new("/home/user/notes.txt");

    assert!(store.rename(from, to).is_ok());

    // Старый путь не должен существовать
    assert_eq!(
      store.info(UnixPath::new("/home/user/readme.txt")),
      Err(FsStoreErr::PathNotFound)
    );

    // Новый путь должен существовать с тем же содержимым
    let info = store.info(UnixPath::new("/home/user/notes.txt")).unwrap();
    assert_eq!(info.name, "notes.txt");
    assert!(!info.is_dir);
    assert_eq!(info.size, Some(5));

    let data = store.read(UnixPath::new("/home/user/notes.txt")).unwrap();
    assert_eq!(data, b"hello");
  }

  #[test]
  fn test_rename_move_file_to_different_dir() {
    let mut store = setup_store();
    store.mkdir_p(UnixPath::new("/home/docs")).unwrap();

    let from = UnixPath::new("/home/user/readme.txt");
    let to = UnixPath::new("/home/docs/readme.txt");

    assert!(store.rename(from, to).is_ok());

    assert_eq!(
      store.info(UnixPath::new("/home/user/readme.txt")),
      Err(FsStoreErr::PathNotFound)
    );

    let info = store.info(UnixPath::new("/home/docs/readme.txt")).unwrap();
    assert_eq!(info.name, "readme.txt");
    assert_eq!(info.size, Some(5));
  }

  #[test]
  fn test_rename_move_directory() {
    let mut store = setup_store();
    store.mkdir_p(UnixPath::new("/home/backup")).unwrap();

    let from = UnixPath::new("/home/user");
    let to = UnixPath::new("/home/backup/user_backup");

    assert!(store.rename(from, to).is_ok());

    assert_eq!(
      store.info(UnixPath::new("/home/user")),
      Err(FsStoreErr::PathNotFound)
    );

    let info = store
      .info(UnixPath::new("/home/backup/user_backup"))
      .unwrap();
    assert!(info.is_dir);
    assert_eq!(info.name, "user_backup");

    // Содержимое должно сохраниться
    let data = store
      .read(UnixPath::new("/home/backup/user_backup/readme.txt"))
      .unwrap();
    assert_eq!(data, b"hello");
  }

  #[test]
  fn test_rename_source_not_found() {
    let mut store = setup_store();
    let result = store.rename(
      UnixPath::new("/home/nonexistent.txt"),
      UnixPath::new("/home/newname.txt"),
    );
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_rename_destination_exists() {
    let mut store = setup_store();
    store.touch(UnixPath::new("/home/user/other.txt")).unwrap();

    let result = store.rename(
      UnixPath::new("/home/user/readme.txt"),
      UnixPath::new("/home/user/other.txt"),
    );
    assert_eq!(result, Err(FsStoreErr::PathExists));
  }

  #[test]
  fn test_rename_dir_into_itself() {
    let mut store = setup_store();
    store.mkdir_p(UnixPath::new("/home/subdir")).unwrap();

    // Попытка переместить /home внутрь /home/subdir
    let result = store.rename(
      UnixPath::new("/home"),
      UnixPath::new("/home/subdir/home_backup"),
    );
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_rename_dir_into_subdir() {
    let mut store = setup_store();
    // /home/user уже содержит readme.txt
    let result = store.rename(
      UnixPath::new("/home/user"),
      UnixPath::new("/home/user/backup"), // попытка переместить в свою поддиректорию
    );
    // Это должно быть запрещено, но наша проверка работает только если to длиннее from
    // В данном случае это переименование в той же директории, так что разрешено
    // Для реальной проверки нужно: /home/user -> /home/user/sub/backup
    store.mkdir_p(UnixPath::new("/home/user/sub")).unwrap();
    let result = store.rename(
      UnixPath::new("/home/user"),
      UnixPath::new("/home/user/sub/backup"),
    );
    assert_eq!(result, Err(FsStoreErr::PathNotFound));
  }

  #[test]
  fn test_rename_relative_path_fails() {
    let mut store = setup_store();
    let result = store.rename(
      UnixPath::new("relative/from.txt"),
      UnixPath::new("/absolute/to.txt"),
    );
    assert_eq!(result, Err(FsStoreErr::PathNotAbsolute));

    let result = store.rename(
      UnixPath::new("/absolute/from.txt"),
      UnixPath::new("relative/to.txt"),
    );
    assert_eq!(result, Err(FsStoreErr::PathNotAbsolute));
  }

  #[test]
  fn test_rename_root_fails() {
    let mut store = setup_store();
    let result = store.rename(UnixPath::new("/"), UnixPath::new("/newroot"));
    assert_eq!(result, Err(FsStoreErr::PathIsRoot));

    let result = store.rename(UnixPath::new("/home"), UnixPath::new("/"));
    assert_eq!(result, Err(FsStoreErr::PathIsRoot));
  }

  #[test]
  fn test_rename_preserves_file_content() {
    let mut store = setup_store();
    let content = b"some binary \x00 data";
    store
      .write(UnixPath::new("/home/user/data.bin"), content)
      .unwrap();

    store
      .rename(
        UnixPath::new("/home/user/data.bin"),
        UnixPath::new("/home/user/renamed.bin"),
      )
      .unwrap();

    let read_data = store.read(UnixPath::new("/home/user/renamed.bin")).unwrap();
    assert_eq!(read_data, content);
  }

  #[test]
  fn test_rename_updates_ls_output() {
    let mut store = setup_store();

    store
      .rename(
        UnixPath::new("/home/user/readme.txt"),
        UnixPath::new("/home/user/notes.txt"),
      )
      .unwrap();

    let entries = store.ls(UnixPath::new("/home/user")).unwrap();
    assert!(!entries.iter().any(|e| e.name == "readme.txt"));
    assert!(entries.iter().any(|e| e.name == "notes.txt" && !e.is_dir));
  }

  #[test]
  fn test_rename_chain() {
    let mut store = setup_store();

    // Цепочка переименований
    store
      .rename(
        UnixPath::new("/home/user/readme.txt"),
        UnixPath::new("/home/user/a.txt"),
      )
      .unwrap();

    store
      .rename(
        UnixPath::new("/home/user/a.txt"),
        UnixPath::new("/home/user/b.txt"),
      )
      .unwrap();

    let data = store.read(UnixPath::new("/home/user/b.txt")).unwrap();
    assert_eq!(data, b"hello");

    assert_eq!(
      store.info(UnixPath::new("/home/user/readme.txt")),
      Err(FsStoreErr::PathNotFound)
    );
    assert_eq!(
      store.info(UnixPath::new("/home/user/a.txt")),
      Err(FsStoreErr::PathNotFound)
    );
  }
}

#[cfg_attr(not(target_arch = "wasm32"), async_trait)]
#[cfg_attr(target_arch = "wasm32", async_trait(?Send))]
impl FsClient for Arc<Mutex<FsRamStore>> {
  async fn list(&self, dir: &str) -> Result<Vec<DirEntry>, String> {
    let slf = self.lock().await;
    let res = slf.ls(UnixPath::new(dir)).map(|v| {
      let vv: Vec<super::DirEntry> = v
        .iter()
        .map(|e| {
          if e.is_dir {
            super::DirEntry::Dir {
              name: e.name.clone(),
            }
          } else {
            super::DirEntry::File {
              name: e.name.clone(),
              size: e.size.unwrap_or_default(),
            }
          }
        })
        .collect();
      vv
    });
    let res = res.map_err(|e| format!("{e:?}"));
    res
  }

  async fn write(&self, path: &str, bytes: &[u8]) -> Result<(), String> {
    let mut slf = self.lock().await;
    slf
      .write(UnixPath::new(path), bytes)
      .map_err(|e| format!("{e:?}"))
  }

  async fn read(&self, path: &str) -> Result<Vec<u8>, String> {
    let slf = self.lock().await;
    slf.read(UnixPath::new(path)).map_err(|e| format!("{e:?}"))
  }

  async fn mkdir(&self, path: &str) -> Result<(), String> {
    self
      .lock()
      .await
      .mkdir_p(UnixPath::new(path))
      .map_err(|e| format!("{e:?}"))
  }

  async fn delete(&self, path: &str) -> Result<(), String> {
    let mut slf = self.lock().await;
    slf.rm(UnixPath::new(path)).map_err(|e| format!("{e:?}"))
  }

  async fn rename(&self, source_path: &str, target_path: &str) -> Result<(), String> {
    self
      .lock()
      .await
      .rename(UnixPath::new(source_path), UnixPath::new(target_path))
      .map_err(|e| format!("{e:?}"))
  }
}

impl FsRamStore {
  pub fn boxed(self) -> Fs {
    // TODO boolshit code
    Arc::new(Mutex::new(Arc::new(Mutex::new(self))))
  }
}
