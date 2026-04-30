use std::collections::HashSet;

use super::tool::*;
use crate::{
  coll::{Id, IdList},
  music_grid::{
    GridObj, GridPoint, MusicGrid,
    note::{ModifyNote, Note},
    view_port::Scaling,
  },
};
use egui::{
  Modifiers, Pos2, Response, Ui,
  ahash::{HashMap, HashMapExt},
};
use midi_model::{NumToEventTime, Pitch, Velocity};

pub struct Move {
  drag_state: GridDragState,
  add_note_on_click: bool,
  last_mouse_ptr: Option<Pos2>,
}

impl Default for Move {
  fn default() -> Self {
    Self {
      drag_state: GridDragState::Non,
      add_note_on_click: true,
      last_mouse_ptr: None,
    }
  }
}

#[derive(Debug, Clone)]
pub enum GridDragState {
  Non,
  GridDragging {
    mouse_start: Pos2,
    start_time: chrono::TimeDelta,
    lower_pitch: f64,
    higher_pitch: f64,
  },
  ObjsDragging {
    mouse_start: Pos2,
    objects: HashMap<Id, GridPoint>,
  },
}

impl GridDragState {
  pub fn is_none(&self) -> bool {
    match self {
      GridDragState::Non => true,
      _ => false,
    }
  }
}

struct PressedKeys {
  pub keys: HashSet<egui::Key>,

  #[allow(unused)]
  pub keys_with_mods: HashSet<(egui::Key, egui::Modifiers)>,
}

impl PressedKeys {
  pub fn from_input_of(ui: &Ui) -> Self {
    let (keys, keys_with_mods) = ui.input(|input| {
      let mut keys = HashSet::<egui::Key>::new();
      let mut keys_with_mods: HashSet<(egui::Key, egui::Modifiers)> =
        HashSet::<(egui::Key, egui::Modifiers)>::new();
      for event in &input.events {
        if let egui::Event::Key {
          key,
          physical_key: _,
          pressed,
          repeat,
          modifiers,
        } = event
        {
          if *pressed && !repeat {
            keys.insert(*key);
            keys_with_mods.insert((*key, *modifiers));
          }
        }
      }
      (keys, keys_with_mods)
    });
    Self {
      keys: keys,
      keys_with_mods: keys_with_mods,
    }
  }
}

impl<L: IdList<Box<dyn GridObj>>> Tool<L> for Move {
  fn render_ui(&mut self, ui: &mut Ui) {
    ui.collapsing("move tool", |ui| {
      ui.checkbox(&mut self.add_note_on_click, "add note on click");
    });
  }

  fn handle_input(&mut self, grid: &mut MusicGrid<L>, ui: &mut Ui, response: &Response) {
    let pressed_keys = PressedKeys::from_input_of(ui);

    if let Some(mouse_ptr) = response.interact_pointer_pos() {
      self.last_mouse_ptr = Some(mouse_ptr);
    }

    if !response.hovered() {
      if !self.drag_state.is_none() {
        self.drag_state = GridDragState::Non;
      }
      return;
    }

    if pressed_keys.keys.contains(&egui::Key::Delete) {
      grid.objects.remove_by_ids(&grid.selection);
      grid.selection.clear();
    }

    let modifiers = ui.input(|input| input.modifiers);

    if response.clicked()
      && let Some(mouse_ptr) = response.interact_pointer_pos()
    {
      self.handle_left_click(grid, mouse_ptr, modifiers);
    }

    if response.secondary_clicked()
      && let Some(mouse_ptr) = response.interact_pointer_pos()
    {
      self.handle_right_click(grid, mouse_ptr, modifiers);
    }

    if response.drag_started() && self.drag_state.is_none() {
      if let Some(mouse_ptr) = response.interact_pointer_pos() {
        self.handle_dragging_start(grid, mouse_ptr);
      }
    }

    if response.dragged()
      && let Some(mouse_ptr) = response.interact_pointer_pos()
    {
      self.handle_dragging_continue(grid, mouse_ptr);
    }

    if response.drag_stopped() && !self.drag_state.is_none() {
      self.drag_state = GridDragState::Non;
    }

    ////////////////////////

    self.handle_mouse_wheel(grid, ui);
  }
}

impl Move {
  fn handle_left_click<L: IdList<Box<dyn GridObj>>>(
    &mut self,
    grid: &mut MusicGrid<L>,
    mouse_ptr: Pos2,
    modifiers: Modifiers,
  ) {
    if self.add_note_on_click {
      let selected_click: Vec<Id> = Vec::new();
      let non_selected_click: Vec<Id> = Vec::new();

      let (selected_click, non_selected_click) = grid.find_objs_at_pixel_collect(
        mouse_ptr,
        (selected_click, non_selected_click),
        |obj, _idx, obj_id, (sel, non_sel)| {
          if !obj.is_selectable() {
            return;
          }

          if grid.selection.contains(obj_id) {
            sel.push(obj_id);
          } else {
            non_sel.push(obj_id);
          }
        },
      );

      let gp = grid.view_port.try_gridpoint_of(mouse_ptr);
      if non_selected_click.is_empty() && selected_click.is_empty() {
        if let Some(gp) = gp {
          let note1 = Note {
            time: gp.time,
            pitch: gp.pitch,
            velocity: Velocity::V100,
            duration: 250u64.ms(),
          };
          grid.objects.push(Box::new(note1));
        }
      } else if selected_click.is_empty() && !non_selected_click.is_empty() {
        if !modifiers.shift {
          grid.selection.clear();
        }
        grid.selection.push_all(non_selected_click);
      }
    } else {
      let add_sel: Vec<Id> = Vec::new();
      let del_sel: Vec<Id> = Vec::new();

      let (add_ids, del_ids) = grid.find_objs_at_pixel_collect(
        mouse_ptr,
        (add_sel, del_sel),
        |obj, _idx, obj_id, (add, del)| {
          if !obj.is_selectable() {
            return;
          }

          let has_selection = grid.selection.contains(obj_id);
          if has_selection {
            del.push(obj_id);
          } else {
            add.push(obj_id);
          }
        },
      );

      for i in add_ids {
        grid.selection.push(i);
      }
      for i in del_ids {
        grid.selection.remove(i);
      }
    }
  }

  fn handle_right_click<L: IdList<Box<dyn GridObj>>>(
    &mut self,
    _grid: &mut MusicGrid<L>,
    _mouse_ptr: Pos2,
    _modifiers: Modifiers,
  ) {
    //
  }

  fn handle_mouse_wheel<L: IdList<Box<dyn GridObj>>>(
    &mut self,
    grid: &mut MusicGrid<L>,
    ui: &mut Ui,
  ) {
    ui.input(|i| {
      let delta = i.events.iter().find_map(|e| match e {
        egui::Event::MouseWheel {
          unit: _,
          delta,
          modifiers,
          phase: _,
        } => Some((delta.y, modifiers.clone())),
        _ => None,
      });

      if let Some(mouse_ptr) = self.last_mouse_ptr {
        let selected_click: Vec<Id> = Vec::new();
        let non_selected_click: Vec<Id> = Vec::new();

        let (selected_click, _non_selected_click) = grid.find_objs_at_pixel_collect(
          mouse_ptr,
          (selected_click, non_selected_click),
          |obj, _idx, obj_id, (sel, non_sel)| {
            if !obj.is_selectable() {
              return;
            }

            if grid.selection.contains(obj_id) {
              sel.push(obj_id);
            } else {
              non_sel.push(obj_id);
            }
          },
        );

        if !selected_click.is_empty()
          && let Some((delta, _)) = delta
          && delta != 0.0
        {
          grid
            .objects
            .write_by_ids((), selected_click, |_idx, _id, obj, _state| {
              obj.as_note(move |note| {
                if let Some(vel) = if delta > 0.0 {
                  note
                    .velocity
                    .number()
                    .checked_add(1)
                    .and_then(|n| Velocity::new(n).ok())
                } else {
                  note
                    .velocity
                    .number()
                    .checked_sub(1)
                    .and_then(|n| Velocity::new(n).ok())
                } {
                  // println!(
                  //   "change velocity of {id:?} from {v0:?} to {v1:?}",
                  //   v0 = note.velocity,
                  //   v1 = vel
                  // );
                  note.velocity = vel;
                }
                //note.velocity
              });
            });

          return;
        }
      }

      // wheel
      // Масштабирование view port
      if let Some((delta, mods)) = delta {
        if mods.ctrl {
          if delta > 0.0 {
            // pitch zoom out
            if let Some(mouse_pos) = self.last_mouse_ptr {
              let mut scale = Scaling::from(&grid.view_port);
              scale.pitch_height *= 0.9;
              scale.pitch_height = scale.pitch_height.max(0.2);
              scale.apply(&mut grid.view_port, mouse_pos);
            } else {
              grid
                .view_port
                .set_lower_pitch(grid.view_port.get_lower_pitch() - 100.0);
            }
          } else if delta < 0.0 {
            // pitch zoom in
            if let Some(mouse_pos) = self.last_mouse_ptr {
              let mut scale = Scaling::from(&grid.view_port);
              scale.pitch_height *= 1.1;
              scale.apply(&mut grid.view_port, mouse_pos);
            } else {
              if (grid.view_port.get_lower_pitch() - grid.view_port.get_higher_pitch()) > 100.0 {
                grid
                  .view_port
                  .set_lower_pitch(grid.view_port.get_lower_pitch() + 100.0);
              }
            }
          }
        } else if mods.shift {
          if delta > 0.0 {
            // time zoom out
            if let Some(mouse_pos) = self.last_mouse_ptr {
              let mut scale = Scaling::from(&grid.view_port);
              scale.pixels_per_second *= 0.9;
              scale.pixels_per_second = scale.pixels_per_second.max(5.0);
              scale.apply(&mut grid.view_port, mouse_pos);
            } else {
              if grid.view_port.get_pixels_per_second() > 100.0 {
                grid
                  .view_port
                  .set_pixels_per_second(grid.view_port.get_pixels_per_second() - 50.0);
              } else if grid.view_port.get_pixels_per_second() > 5.0 {
                grid
                  .view_port
                  .set_pixels_per_second(grid.view_port.get_pixels_per_second() - 5.0);
              }
            }
          } else if delta < 0.0 {
            // time zoom in
            if let Some(mouse_pos) = self.last_mouse_ptr {
              let mut scale = Scaling::from(&grid.view_port);
              scale.pixels_per_second *= 1.1;
              scale.apply(&mut grid.view_port, mouse_pos);
            } else {
              if grid.view_port.get_pixels_per_second() < 100.0 {
                grid
                  .view_port
                  .set_pixels_per_second(grid.view_port.get_pixels_per_second() + 5.0);

                if grid.view_port.get_pixels_per_second() >= 100.0 {
                  grid.view_port.set_pixels_per_second(100.0);
                }
              } else {
                grid
                  .view_port
                  .set_pixels_per_second(grid.view_port.get_pixels_per_second() + 50.0);
              }
            }
          }
        }
      }
    });
  }

  fn handle_dragging_start<L: IdList<Box<dyn GridObj>>>(
    &mut self,
    grid: &mut MusicGrid<L>,
    mouse_ptr: Pos2,
  ) {
    let (selected_objs_at_point, _non_selected_objs_at_point) = grid.find_objs_at_pixel_collect(
      mouse_ptr,
      (
        HashMap::<Id, GridPoint>::new(),
        HashMap::<Id, GridPoint>::new(),
      ),
      |obj, _idx, obj_id, (sel, non_sel)| {
        if let Some(origin) = obj.get_origin() {
          if grid.selection.contains(obj_id) {
            sel.insert(obj_id, origin);
          } else {
            non_sel.insert(obj_id, origin);
          }
        }
      },
    );

    if !selected_objs_at_point.is_empty() {
      let mut sel_obj: HashMap<Id, GridPoint> = Default::default();

      for sel_id in grid.selection.iter() {
        grid.objects.read_by_id(*sel_id, |obj| {
          if let Some((_, obj)) = obj {
            if let Some(pt) = obj.get_origin() {
              sel_obj.insert(*sel_id, pt);
            }
          }
        });
      }

      self.drag_state = GridDragState::ObjsDragging {
        mouse_start: mouse_ptr,
        objects: sel_obj,
      };
    } else if !_non_selected_objs_at_point.is_empty() {
      self.drag_state = GridDragState::ObjsDragging {
        mouse_start: mouse_ptr,
        objects: _non_selected_objs_at_point,
      };
    } else {
      self.drag_state = GridDragState::GridDragging {
        mouse_start: mouse_ptr,
        start_time: grid.view_port.get_start_time(),
        higher_pitch: grid.view_port.get_higher_pitch(),
        lower_pitch: grid.view_port.get_lower_pitch(),
      };
    }
  }

  fn handle_dragging_continue<L: IdList<Box<dyn GridObj>>>(
    &mut self,
    grid: &mut MusicGrid<L>,
    mouse_ptr: Pos2,
  ) {
    if let GridDragState::GridDragging {
      mouse_start,
      start_time,
      lower_pitch,
      higher_pitch,
    } = self.drag_state
    {
      let dx = mouse_ptr.x - mouse_start.x;
      let dy = mouse_ptr.y - mouse_start.y;

      if grid.view_port.get_pixels_per_second() != 0.0 {
        let dt = (dx as f64) / grid.view_port.get_pixels_per_second();
        let dt = chrono::TimeDelta::milliseconds((dt * 1000.0) as i64);
        grid.view_port.set_start_time(start_time - dt);
        grid.view_port.set_lower_pitch(lower_pitch + dy as f64);
        grid.view_port.set_higher_pitch(higher_pitch + dy as f64);
      }
    }

    if let GridDragState::ObjsDragging {
      mouse_start,
      objects,
    } = &self.drag_state
    {
      let dx = mouse_ptr.x - mouse_start.x;
      let dy = mouse_ptr.y - mouse_start.y;

      let pitch_height_pixels = {
        let (py0, py1) = grid.view_port.y_of_pitch(Pitch::MIN);
        (py0 - py1).abs()
      };

      let pitch_delta = if pitch_height_pixels != 0.0 {
        Some(dy as f64 / pitch_height_pixels)
      } else {
        None
      };

      let pitch_delta = pitch_delta.and_then(|n| if n.abs() < 1.0 { None } else { Some(n) });

      let pitch_delta = pitch_delta
        .map(|n| (n as i32, n.abs()))
        .map(|(n, a)| {
          if a > Pitch::MAX.number() as f64 {
            (n, Pitch::MAX.number())
          } else {
            (n, a as u8)
          }
        })
        .and_then(|(sign, n)| Pitch::new(n).ok().map(|p| (-(sign), p)));

      if grid.view_port.get_pixels_per_second() != 0.0 {
        let dt = (dx as f64) / grid.view_port.get_pixels_per_second();
        let dt = chrono::TimeDelta::milliseconds((dt * 1000.0) as i64);

        objects.iter().for_each(|(id, pt)| {
          grid.objects.write_by_id(*id, |obj| {
            if let Some((_, obj)) = obj {
              if let Some(pt) = *pt + dt {
                let pt = pitch_delta
                  .and_then(
                    |(sign, d_pitch)| {
                      if sign < 0 { pt - d_pitch } else { pt + d_pitch }
                    },
                  )
                  .unwrap_or(pt);

                let _ = obj.set_origin(pt);
              }
            }
          });
        });
      }
    }
  }
}
