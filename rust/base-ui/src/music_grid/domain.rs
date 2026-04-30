use std::any::Any;
use std::{fmt::Display, u64};

use egui::{Painter, Pos2, Rect};
use midi_model::*;

use crate::music_grid::view_port::ViewPort;

/// Точка на музыкальной сетке
#[derive(Debug, Clone, Copy)]
pub struct GridPoint {
  pub time: EventTime,
  pub pitch: Pitch,
}

impl GridPoint {
  pub fn new(time: EventTime, pitch: Pitch) -> Self {
    Self {
      time: time,
      pitch: pitch,
    }
  }

  pub fn max() -> Self {
    Self {
      time: EventTime {
        stamp_microseconds: u64::MAX,
      },
      pitch: Pitch::MAX,
    }
  }

  pub fn min() -> Self {
    Self {
      time: EventTime {
        stamp_microseconds: u64::MIN,
      },
      pitch: Pitch::MIN,
    }
  }
}

impl Display for GridPoint {
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    write!(f, "{t} {p}", t = self.time, p = self.pitch)
  }
}

impl From<(EventTime, Pitch)> for GridPoint {
  fn from(value: (EventTime, Pitch)) -> Self {
    Self {
      time: value.0,
      pitch: value.1,
    }
  }
}

impl Default for GridPoint {
  fn default() -> Self {
    Self {
      time: EventTime {
        stamp_microseconds: 0,
      },
      pitch: pitches::C_4,
    }
  }
}

impl std::ops::Add<chrono::TimeDelta> for GridPoint {
  type Output = Option<GridPoint>;

  fn add(self, rhs: chrono::TimeDelta) -> Self::Output {
    if rhs.num_milliseconds() > 0 {
      Some(GridPoint {
        time: self.time + EventTime::from_ms(rhs.num_milliseconds() as u64),
        pitch: self.pitch,
      })
    } else if rhs.num_milliseconds() == 0 {
      Some(self)
    } else if rhs.num_milliseconds() < 0 {
      let mcs = (rhs.num_milliseconds().abs() * 1000) as u64;

      if self.time.stamp_microseconds > mcs {
        Some(GridPoint {
          time: EventTime {
            stamp_microseconds: self.time.stamp_microseconds - mcs,
          },
          pitch: self.pitch,
        })
      } else {
        None
      }
    } else {
      None
    }
  }
}

impl std::ops::Add<Pitch> for GridPoint {
  type Output = Option<GridPoint>;

  fn add(self, rhs: Pitch) -> Self::Output {
    Pitch::new(self.pitch.number() + rhs.number())
      .ok()
      .map(|p| GridPoint {
        time: self.time,
        pitch: p,
      })
  }
}

impl std::ops::Sub<Pitch> for GridPoint {
  type Output = Option<GridPoint>;

  fn sub(self, rhs: Pitch) -> Self::Output {
    Pitch::new(self.pitch.number() - rhs.number())
      .ok()
      .map(|p| GridPoint {
        time: self.time,
        pitch: p,
      })
  }
}

////////////////////////////////////////////////////////////////////////////

/// Прямоугольник на музыкальной сетке
#[derive(Debug, Clone, Copy, Default)]
pub struct GridRect {
  pub first_point: GridPoint,
  pub second_point: GridPoint,
}

impl GridRect {
  pub fn new(first: GridPoint, second: GridPoint) -> Self {
    Self {
      first_point: first,
      second_point: second,
    }
  }

  pub fn max() -> Self {
    Self {
      first_point: GridPoint::min(),
      second_point: GridPoint::max(),
    }
  }

  pub fn begin(&self) -> EventTime {
    self.first_point.time.min(self.second_point.time)
  }

  pub fn end(&self) -> EventTime {
    self.first_point.time.max(self.second_point.time)
  }

  pub fn length(&self) -> EventTime {
    let min = self.begin();
    let max = self.end();
    EventTime {
      stamp_microseconds: max.stamp_microseconds - min.stamp_microseconds,
    }
  }

  pub fn higher_pitch(&self) -> Pitch {
    Pitch::new(
      self
        .first_point
        .pitch
        .number()
        .max(self.second_point.pitch.number()),
    )
    .unwrap_or(Pitch::MIN)
  }

  pub fn lower_pitch(&self) -> Pitch {
    Pitch::new(
      self
        .first_point
        .pitch
        .number()
        .min(self.second_point.pitch.number()),
    )
    .unwrap_or(Pitch::MIN)
  }

  pub fn contains<P: Into<GridPoint>>(&self, p: P) -> bool {
    let p: GridPoint = p.into();
    self.begin() <= p.time
      && self.end() > p.time
      && self.lower_pitch() <= p.pitch
      && self.higher_pitch() > p.pitch
  }
}

////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, Copy)]
pub struct PixelPos {
  pub x: f64,
  pub y: f64,
}

impl From<Pos2> for PixelPos {
  fn from(value: Pos2) -> Self {
    PixelPos {
      x: value.x as f64,
      y: value.y as f64,
    }
  }
}

impl From<(f64, f64)> for PixelPos {
  fn from(value: (f64, f64)) -> Self {
    PixelPos {
      x: value.0,
      y: value.1,
    }
  }
}

impl From<(f32, f32)> for PixelPos {
  fn from(value: (f32, f32)) -> Self {
    PixelPos {
      x: value.0 as f64,
      y: value.1 as f64,
    }
  }
}

impl From<(i32, i32)> for PixelPos {
  fn from(value: (i32, i32)) -> Self {
    PixelPos {
      x: value.0 as f64,
      y: value.1 as f64,
    }
  }
}

impl Into<Pos2> for PixelPos {
  fn into(self) -> Pos2 {
    Pos2 {
      x: self.x as f32,
      y: self.y as f32,
    }
  }
}

////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, Copy)]
pub struct PixelRect {
  pub first: PixelPos,
  pub second: PixelPos,
}

impl PixelRect {
  pub fn left(&self) -> f64 {
    self.first.x.min(self.second.x)
  }

  pub fn right(&self) -> f64 {
    self.first.x.max(self.second.x)
  }

  pub fn top(&self) -> f64 {
    self.first.y.min(self.second.y)
  }

  pub fn bottom(&self) -> f64 {
    self.first.y.max(self.second.y)
  }

  pub fn left_top(&self) -> PixelPos {
    PixelPos {
      x: self.left(),
      y: self.top(),
    }
  }

  pub fn right_top(&self) -> PixelPos {
    PixelPos {
      x: self.right(),
      y: self.top(),
    }
  }

  pub fn left_bottom(&self) -> PixelPos {
    PixelPos {
      x: self.left(),
      y: self.bottom(),
    }
  }

  pub fn right_bottom(&self) -> PixelPos {
    PixelPos {
      x: self.right(),
      y: self.bottom(),
    }
  }
}

impl From<Rect> for PixelRect {
  fn from(rect: Rect) -> Self {
    PixelRect {
      first: PixelPos {
        x: rect.min.x as f64,
        y: rect.min.y as f64,
      },
      second: PixelPos {
        x: rect.max.x as f64,
        y: rect.max.y as f64,
      },
    }
  }
}

////////////////////////////////////////////////////////////////////////////

pub trait Movable {
  fn get_origin(&self) -> Option<GridPoint> {
    None
  }

  fn set_origin(&mut self, _p: GridPoint) -> Result<(), String> {
    Err("not impl".to_string())
  }
}

pub struct ObjContext<'a> {
  pub selected: bool,
  pub mouse_hover: bool,
  pub view_port: &'a ViewPort,
  pub visible_rect: Rect,
}

pub trait GridObj: Any + Movable + Send + Sync {
  /// Возвращает полное имя конкретного типа, реализующего трейт
  fn type_name(&self) -> &'static str {
    std::any::type_name::<Self>()
  }

  /// Возможно выбирать объект пользователем
  fn is_selectable(&self) -> bool {
    false
  }

  #[allow(unused)]
  fn render_location(&self, ctx: &ObjContext) -> Option<Rect> {
    None
  }

  #[allow(unused)]
  fn render(&self, painter: &Painter, ctx: &ObjContext) {}

  fn clone_boxed(&self) -> Box<dyn GridObj>;
}
