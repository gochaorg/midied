use std::any::Any;

use crate::music_grid::{ChronoTimeDeltaExt, GridObj, Movable};
use egui::{Align2, Color32, Pos2, Stroke};

#[derive(Debug, Clone, Default, PartialEq)]
pub struct TimeLines {}

impl TryFrom<&Box<dyn GridObj>> for TimeLines {
  type Error = String;

  fn try_from(value: &Box<dyn GridObj>) -> Result<Self, Self::Error> {
    let any_ref: &dyn Any = value.as_ref();
    any_ref
      .downcast_ref::<TimeLines>()
      .ok_or(format!("can't cast to TimeLines"))
      .map(|succ| succ.clone())
  }
}

impl GridObj for TimeLines {
  fn render_location(&self, ctx: &super::ObjContext) -> Option<egui::Rect> {
    Some(ctx.visible_rect)
  }

  fn render(&self, painter: &egui::Painter, ctx: &super::ObjContext) {
    let min_x = ctx.visible_rect.min.x;
    let max_x = ctx.visible_rect.max.x;
    let inside = |x: f32| x >= min_x && x <= max_x;

    let stroke_c = Stroke::new(1.0, Color32::from_gray(200));
    let stroke_oth = Stroke::new(1.0, Color32::from_gray(100));
    let stroke_is = Stroke::new(0.5, Color32::from_gray(50));

    let t = ctx.view_port.time_of_x(min_x);
    let mut t = t.truncate_second();

    let pixels_per_second = ctx.view_port.get_pixels_per_second();
    let step_t = chrono::TimeDelta::milliseconds(if pixels_per_second > 50.0 {
      125
    } else if pixels_per_second >= 25.0 {
      250
    } else if pixels_per_second >= 15.0 {
      500
    } else if pixels_per_second >= 10.0 {
      1000
    } else if pixels_per_second >= 5.0 {
      2000
    } else {
      4000
    });

    loop {
      let x = ctx.view_port.x_of_time(t) as f32;
      if x > max_x {
        break;
      }

      if inside(x) {
        let (stroke, str) = match t.subsec_millis().abs() {
          0 => (stroke_c.clone(), Some(t.to_human_string())),
          250 | 500 | 750 => (
            stroke_oth.clone(),
            if pixels_per_second >= 250.0 {
              Some(t.to_human_string())
            } else {
              None
            },
          ),
          _ => (stroke_is.clone(), None),
        };

        let p_start = Pos2::new(x, ctx.visible_rect.min.y);
        let p_end = Pos2::new(x, ctx.visible_rect.max.y);

        painter.line_segment([p_start, p_end], stroke);

        if str.is_some() {
          painter.text(
            Pos2 {
              x: x,
              y: ctx.visible_rect.min.y,
            },
            Align2::LEFT_TOP,
            &str.unwrap(),
            egui::FontId::default(),
            egui::Color32::WHITE,
          );
        }
      }

      t = t + step_t;
    }
  }

  fn clone_boxed(&self) -> Box<dyn GridObj> {
    Box::new(self.clone())
  }
}

impl Movable for TimeLines {}
