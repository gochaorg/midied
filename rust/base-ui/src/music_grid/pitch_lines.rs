use std::any::Any;

use super::*;
use egui::{Align2, Color32, FontId, Pos2, Stroke};
use midi_model::{Pitch, PitchClass};

#[derive(Debug, Clone, Copy, Default, PartialEq)]
pub struct PitchLines {}

impl TryFrom<&Box<dyn GridObj>> for PitchLines {
  type Error = String;

  fn try_from(value: &Box<dyn GridObj>) -> Result<Self, Self::Error> {
    let any_ref: &dyn Any = value.as_ref();
    any_ref
      .downcast_ref::<PitchLines>()
      .ok_or(format!("can't cast to PitchLines"))
      .map(|succ| succ.clone())
  }
}

impl GridObj for PitchLines {
  fn clone_boxed(&self) -> Box<dyn GridObj> {
    Box::new(self.clone())
  }

  fn render_location(&self, ctx: &ObjContext) -> Option<egui::Rect> {
    Some(ctx.visible_rect)
  }

  fn render(&self, painter: &egui::Painter, ctx: &ObjContext) {
    let rect = self.render_location(ctx);
    if rect.is_none() {
      return;
    }
    let rect = rect.unwrap();

    // Создаём стиль линии: толщина + цвет
    let stroke_c = Stroke::new(1.0, Color32::from_gray(200));
    let stroke_oth = Stroke::new(1.0, Color32::from_gray(100));
    let stroke_is = Stroke::new(0.5, Color32::from_gray(50));

    for (stroke, label, start, end) in (0..127)
      .into_iter()
      .map(|p| {
        let p = Pitch::new(p).unwrap_or(Pitch::MIN);
        let (_y, y) = ctx.view_port.y_of_pitch(p);
        (p, y as f32)
      })
      .filter_map(|(p, y)| {
        if y < rect.min.y || y > rect.max.y {
          return None;
        }

        let start = Pos2::new(ctx.visible_rect.min.x, y);
        let end = Pos2::new(ctx.visible_rect.max.x, y);

        let stroke = match p.pitch_class() {
          PitchClass::C => stroke_c.clone(),
          PitchClass::Cis
          | PitchClass::Dis
          | PitchClass::Fis
          | PitchClass::Gis
          | PitchClass::Ais => stroke_is.clone(),
          _ => stroke_oth.clone(),
        };

        let label = match p.pitch_class() {
          PitchClass::C => Some(format!("C{}", p.octave())),
          _ => None,
        };

        Some((stroke, label, start, end))
      })
    {
      if let Some(label) = label {
        painter.text(
          start,
          Align2::LEFT_BOTTOM,
          &label,
          FontId::default(),
          Color32::from_gray(200),
        );
      }
      painter.line_segment([start, end], stroke);
    }
  }
}

impl Movable for PitchLines {}
