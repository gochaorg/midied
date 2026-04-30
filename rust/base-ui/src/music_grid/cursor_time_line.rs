use egui::Color32;
use egui::Pos2;
use egui::Stroke;
use midi_model::{EventTime, Pitch};

use super::GridPoint;
use super::GridRect;
use crate::music_grid::{GridObj, Movable};

#[derive(Debug, Clone)]
pub struct CursorTimeLine {
  pub time: EventTime,
}

impl GridObj for CursorTimeLine {
  fn clone_boxed(&self) -> Box<dyn GridObj> {
    Box::new(self.clone())
  }

  fn render_location(&self, ctx: &super::ObjContext) -> Option<egui::Rect> {
    ctx.view_port.try_rect_of(GridRect::new(
      GridPoint {
        time: self.time,
        pitch: Pitch::MIN,
      },
      GridPoint {
        time: self.time + EventTime::from_ms(10),
        pitch: Pitch::MAX,
      },
    ))
  }

  fn render(&self, painter: &egui::Painter, ctx: &super::ObjContext) {
    let rect = self.render_location(ctx);
    if rect.is_none() {
      return;
    }
    let rect = rect.unwrap();

    let stroke = Stroke::new(1.0, Color32::from_rgb(255, 0, 0));

    let start = Pos2::new(rect.min.x, ctx.visible_rect.min.y);
    let end = Pos2::new(rect.min.x, ctx.visible_rect.max.y);
    painter.line_segment([start, end], stroke);

    let start = Pos2::new(rect.max.x, ctx.visible_rect.min.y);
    let end = Pos2::new(rect.max.x, ctx.visible_rect.max.y);
    painter.line_segment([start, end], stroke);
  }
}

impl Movable for CursorTimeLine {
  fn get_origin(&self) -> Option<GridPoint> {
    Some(GridPoint {
      time: self.time,
      pitch: Pitch::MAX,
    })
  }

  fn set_origin(&mut self, p: GridPoint) -> Result<(), String> {
    self.time = p.time;
    Ok(())
  }
}
