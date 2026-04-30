use std::{cell::RefCell, rc::Rc};

use crate::{
  coll::{Id, IdList, IdSet},
  music_grid::{note::NoteTime, pitch_lines::PitchLines, time_lines::TimeLines, tool::Tool},
};

use super::*;
use egui::{Pos2, Rect, Response, Sense, Ui};
use midi_model::{NumToEventTime, NumToPitch, Velocity};
use view_port::*;

pub struct MusicGrid<L>
where
  L: IdList<Box<dyn GridObj>>,
{
  pub objects: L,

  pub visible_rect: Rc<RefCell<Rect>>,
  pub view_port: ViewPort,
  pub selection: IdSet,

  pub tool: Option<Rc<RefCell<dyn Tool<L>>>>,
  pub last_mouse_ptr: Option<Pos2>,
}

impl<L> MusicGrid<L>
where
  L: IdList<Box<dyn GridObj>>,
{
  pub fn new(mut objs: L) -> Self {
    //let mut objs: IdListImpl<Box<dyn GridObj>> = IdListImpl::new();

    objs.push(Box::new(PitchLines::default()));
    objs.push(Box::new(TimeLines::default()));

    let note1 = note::Note {
      time: 0u64.ms(),
      pitch: 60.pitch().unwrap(),
      velocity: Velocity::V100,
      duration: 250u64.ms(),
    };

    let note2 = note1 + 250u64.ms().shift();
    let note2 = (note2 + 2.pitch().unwrap()).unwrap();

    objs.push(Box::new(note1));
    objs.push(Box::new(note2));

    // objs.push(Box::new(cursor_time_line::CursorTimeLine {
    //   time: EventTime::from_ms(1600),
    // }));

    Self {
      objects: objs,
      selection: Default::default(),
      visible_rect: Rc::new(RefCell::new(Rect {
        min: Pos2 { x: 0.0, y: 0.0 },
        max: Pos2 { x: 0.0, y: 0.0 },
      })),
      view_port: Default::default(),
      tool: None,
      last_mouse_ptr: None,
    }
  }
}

pub fn rect_intersect(a: Rect, b: Rect, allow_zero: bool) -> Option<Rect> {
  let min_x = a.min.x.min(b.min.x).min(a.max.x).min(b.max.x);
  let max_x = a.max.x.max(b.max.x).max(a.min.x).max(b.min.x);

  let min_y = a.min.y.min(b.min.y).min(a.max.y).min(b.max.y);
  let max_y = a.max.y.max(b.max.y).max(a.min.y).max(b.min.y);

  let sum_len = a.width() + b.width();
  let sum_height = a.height() + b.height();

  let max_len = max_x - min_x;
  let max_height = max_y - min_y;

  if (max_len > sum_len) || (max_len >= sum_len && !allow_zero) {
    return None;
  }
  if (max_height > sum_height) || (max_height >= sum_height && !allow_zero) {
    return None;
  }

  let x0 = a.min.x.max(b.min.x);
  let y0 = a.min.y.max(b.min.y);

  let x1 = a.max.x.min(b.max.x);
  let y1 = a.max.y.min(b.max.y);

  Some(Rect {
    min: Pos2 { x: x0, y: y0 },
    max: Pos2 { x: x1, y: y1 },
  })
}

// find_objs_at_pixel_collect
// assign_tool
impl<L> MusicGrid<L>
where
  L: IdList<Box<dyn GridObj>>,
{
  pub fn find_objs_at_pixel_collect<P, F, R>(&self, p: P, r: R, f: F) -> R
  where
    P: Into<PixelPos>,
    F: Fn(&Box<dyn GridObj>, usize, Id, &mut R),
    R: Clone,
  {
    let mut res: R = r;
    let visible_rect = {
      self
        .visible_rect
        .try_borrow()
        .expect("can't try_borrow  find_objs_at_pixel_collect")
        .clone()
    };

    let pos: Pos2 = p.into().into();

    for i in 0..self.objects.len() {
      res = self.objects.read_by_idx(i, |obj| {
        if let Some((obj_id, obj)) = obj {
          let has_selection = self.selection.contains(*obj_id);

          let obj_ctx = ObjContext {
            selected: has_selection,
            mouse_hover: false,
            visible_rect: visible_rect,
            view_port: &self.view_port,
          };

          if let Some(rect) = obj.render_location(&obj_ctx)
            && rect.contains(pos)
          {
            f(obj, i, *obj_id, &mut res)
          }
        }

        res.clone()
      });
    }
    res
  }

  pub fn assign_tool(&mut self, tool: Rc<RefCell<dyn Tool<L>>>) {
    if let Some(exists_tool) = self.tool.clone() {
      exists_tool.borrow_mut().on_detached_from_grid(self);
    }

    self.tool = Some(tool.clone());

    tool.borrow_mut().on_attached_to_grid(self);
  }
}

// ui
impl<L> MusicGrid<L>
where
  L: IdList<Box<dyn GridObj>>,
{
  pub fn ui(&mut self, ui: &mut Ui) -> Response {
    let available_rect = ui.available_rect_before_wrap();

    let response = ui.allocate_rect(available_rect, Sense::click_and_drag());
    let visible_rect = available_rect;

    {
      *self.visible_rect.borrow_mut() = visible_rect;
    }
    let painter = ui.painter();

    let mouse_ptr = response.interact_pointer_pos();
    if let Some(p) = mouse_ptr {
      self.last_mouse_ptr = Some(p);
    }

    for idx in 0..self.objects.len() {
      self.objects.read_by_idx(idx, |obj| {
        if let Some((obj_id, obj)) = obj {
          let has_selection = self.selection.contains(*obj_id);

          let obj_ctx = ObjContext {
            selected: has_selection,
            mouse_hover: false,
            visible_rect: visible_rect,
            view_port: &self.view_port,
          };

          if let Some(rect) = obj.render_location(&obj_ctx)
            && rect_intersect(visible_rect, rect, true).is_some()
          {
            obj.render(painter, &obj_ctx);
          }
        } else {
          log::warn!("can't read self.objects.read_by_idx(idx={idx}, ...");
        }
      });
    }

    let x = self.tool.clone();
    if let Some(tool) = x {
      let mut tool = tool.borrow_mut();
      tool.handle_input(self, ui, &response);
    }

    response
  }
}
