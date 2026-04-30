use crate::music_grid::*;
use egui::{Align2, Color32, FontId, Pos2, Stroke};
use midi_model::{EventTime, NumToPitch, Pitch, Velocity};
use serde::{Deserialize, Serialize};
use std::any::Any;

/// Представляет музыкальную ноту в сетке секвенсора.
///
/// Содержит основные параметры MIDI-ноты: время начала, высоту,
/// громкость (velocity) и длительность. Структура поддерживает
/// сериализацию/десериализацию через `serde` для сохранения состояния.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
pub struct Note {
  /// Время начала ноты в единиках `EventTime`.
  pub time: EventTime,
  /// Высота ноты (pitch) — MIDI-номер (0–127).
  pub pitch: Pitch,
  /// Сила нажатия (velocity) — громкость ноты (0–127).
  pub velocity: Velocity,
  /// Длительность ноты в единиках `EventTime`.
  pub duration: EventTime,
}

pub trait ModifyNote {
  fn as_note<F, R>(self, f: F) -> Option<R>
  where
    R: Sized,
    F: FnMut(&mut Note) -> R;
}

impl ModifyNote for &mut Box<dyn GridObj> {
  fn as_note<F, R>(self, mut f: F) -> Option<R>
  where
    R: Sized,
    F: FnMut(&mut Note) -> R,
  {
    let any_ref: &mut dyn Any = self.as_mut();
    let note_opt = any_ref.downcast_mut::<Note>();
    if let Some(note_ref) = note_opt {
      return Some(f(note_ref));
    }

    None
  }
}

/// Попытка преобразовать `&Box<dyn GridObj>` в `Note`.
///
/// # Errors
/// Возвращает ошибку, если объект не может быть приведён к типу `Note`.
impl TryFrom<&Box<dyn GridObj>> for Note {
  type Error = String;

  fn try_from(value: &Box<dyn GridObj>) -> Result<Self, Self::Error> {
    let any_ref: &dyn Any = value.as_ref();
    let note_opt = any_ref.downcast_ref::<Note>();
    if note_opt.is_none() {
      return Err(format!("can't cast to note"));
    }
    Ok(note_opt.unwrap().clone())
  }
}

/// Попытка преобразовать `&Box<dyn GridObj + Send + Sync>` в `Note`.
///
/// Аналогично предыдущей реализации, но для потокобезопасных объектов.
///
/// # Errors
/// Возвращает ошибку, если объект не может быть приведён к типу `Note`.
impl TryFrom<&Box<dyn GridObj + Send + Sync>> for Note {
  type Error = String;

  fn try_from(value: &Box<dyn GridObj + Send + Sync>) -> Result<Self, Self::Error> {
    let any_ref: &dyn Any = value.as_ref();
    let note_opt = any_ref.downcast_ref::<Note>();
    if note_opt.is_none() {
      return Err(format!("can't cast to note"));
    }
    Ok(note_opt.unwrap().clone())
  }
}

/// Реализация отрисовки и геометрических параметров ноты для сетки.
impl GridObj for Note {
  fn clone_boxed(&self) -> Box<dyn GridObj> {
    Box::new(self.clone())
  }

  /// Ноты можно выбирать
  fn is_selectable(&self) -> bool {
    true
  }

  fn render_location(&self, ctx: &ObjContext) -> Option<egui::Rect> {
    ctx.view_port.try_rect_of(GridRect {
      first_point: GridPoint {
        time: self.time,
        pitch: self.pitch,
      },
      second_point: GridPoint {
        time: self.time + self.duration,
        // TODO это фиксить надо
        pitch: (self.pitch + 1.pitch().unwrap()).unwrap_or(self.pitch),
      },
    })
  }

  fn render(&self, painter: &egui::Painter, ctx: &ObjContext) {
    let rect = self.render_location(ctx);
    if rect.is_none() {
      return;
    }
    let rect = rect.unwrap();

    let factor = (self.velocity.number() as f32) / (Velocity::MAX.number() as f32);
    let fill_color = Color32::from_rgb(
      (60.0 * factor) as u8,
      (180.0 * factor) as u8,
      (240.0 * factor) as u8,
    );

    painter.rect_filled(rect, 2.0, fill_color);

    //if _ctx.mouse_hover {
    painter.text(
      Pos2 {
        x: rect.min.x,
        y: rect.max.y,
      },
      Align2::LEFT_BOTTOM,
      format!(
        "{name}/{vel}",
        name = self.pitch,
        vel = self.velocity.number()
      ),
      FontId::new(12.0, egui::FontFamily::Proportional),
      Color32::WHITE,
    );
    //}

    if ctx.selected {
      painter.rect_stroke(
        rect,
        2.0,
        Stroke::new(1.0, Color32::from_gray(255)),
        egui::StrokeKind::Middle,
      );
    }
  }
}

/// Реализация перемещения ноты по сетке.
impl Movable for Note {
  /// Возвращает текущую опорную точку ноты (начало по времени и высоте).
  fn get_origin(&self) -> Option<GridPoint> {
    Some(GridPoint {
      time: self.time,
      pitch: self.pitch,
    })
  }

  /// Устанавливает новую опорную точку для ноты.
  ///
  /// # Параметры
  /// * `p` — новая точка (`GridPoint`) с координатами времени и высоты.
  ///
  /// # Returns
  /// `Ok(())` при успешном обновлении координат.
  fn set_origin(&mut self, p: GridPoint) -> Result<(), String> {
    self.time = p.time;
    self.pitch = p.pitch;
    Ok(())
  }
}

/// Расширяет типы, связанные со временем, методами для работы с длительностями
/// и сдвигами в контексте музыкальных нот.
///
/// Позволяет эргономично выполнять арифметические операции с нотами,
/// разделяя понятия «изменение длительности» и «сдвиг во времени».
pub trait NoteTime {
  /// Тип, представляющий длительность для данного времени.
  type Duration;
  /// Возвращает обёртку для операций с длительностью.
  fn duration(&self) -> Self::Duration;

  /// Тип, представляющий временной сдвиг для данного времени.
  type Shift;
  /// Возвращает обёртку для операций со сдвигом.
  fn shift(&self) -> Self::Shift;
}

/// Реализация `NoteTime` для `EventTime`.
impl NoteTime for EventTime {
  type Duration = NoteTimeDuration;

  fn duration(&self) -> Self::Duration {
    NoteTimeDuration(*self)
  }

  type Shift = NoteTimeShift;

  fn shift(&self) -> Self::Shift {
    NoteTimeShift(*self)
  }
}

/// Обёртка над `EventTime` для операций изменения длительности ноты.
///
/// Используется в перегрузке оператора `+` для `Note`.
pub struct NoteTimeDuration(pub EventTime);

/// Обёртка над `EventTime` для операций сдвига ноты во времени.
///
/// Используется в перегрузке оператора `+` для `Note`.
pub struct NoteTimeShift(pub EventTime);

/// Увеличивает длительность ноты на заданное значение.
///
/// # Пример
/// ```
/// let note = Note { time: 0, pitch: 60, velocity: 100, duration: 10 };
/// let new_note = note + NoteTimeDuration(5); // duration станет 15
/// ```
impl std::ops::Add<NoteTimeDuration> for Note {
  type Output = Note;

  fn add(self, rhs: NoteTimeDuration) -> Self::Output {
    Note {
      duration: rhs.0 + self.duration,
      ..self
    }
  }
}

/// Сдвигает ноту во времени на заданное значение.
///
/// # Пример
/// ```
/// let note = Note { time: 10, pitch: 60, velocity: 100, duration: 5 };
/// let new_note = note + NoteTimeShift(3); // time станет 13
/// ```
impl std::ops::Add<NoteTimeShift> for Note {
  type Output = Note;

  fn add(self, rhs: NoteTimeShift) -> Self::Output {
    Note {
      time: rhs.0 + self.time,
      ..self
    }
  }
}

/// Повышает высоту ноты на заданное количество полутонов.
///
/// # Returns
/// `Some(Note)` при успешном изменении высоты, `None` если результат
/// выходит за допустимый диапазон `Pitch`.
impl std::ops::Add<Pitch> for Note {
  type Output = Option<Note>;

  fn add(self, rhs: Pitch) -> Self::Output {
    (self.pitch + rhs).map(|p| Note { pitch: p, ..self })
  }
}

/// Понижает высоту ноты на заданное количество полутонов.
///
/// # Returns
/// `Some(Note)` при успешном изменении высоты, `None` если результат
/// выходит за допустимый диапазон `Pitch`.
impl std::ops::Sub<Pitch> for Note {
  type Output = Option<Note>;

  fn sub(self, rhs: Pitch) -> Self::Output {
    (self.pitch - rhs).map(|p| Note { pitch: p, ..self })
  }
}
