use crate::music_grid::MusicGrid;
use crate::{coll::IdList, music_grid::GridObj};
use egui::{Response, Ui};

pub trait Tool<L: IdList<Box<dyn GridObj>>> {
  #[allow(unused)]
  fn render_ui(&mut self, ui: &mut Ui) {}

  #[allow(unused)]
  fn handle_input(&mut self, grid: &mut MusicGrid<L>, ui: &mut Ui, response: &Response);

  #[allow(unused)]
  fn on_attached_to_grid(&mut self, grid: &mut MusicGrid<L>) {}

  #[allow(unused)]
  fn on_detached_from_grid(&mut self, grid: &mut MusicGrid<L>) {}
}

// #[derive(Default)]
// pub struct Sample {
//   last_mouse: Option<Pos2>,
// }
//
// impl Tool for Sample {
//   fn handle_input(&mut self, _grid: &mut MusicGrid, ui: &mut Ui, _response: &Response) {
//     let pos = _response.interact_pointer_pos();
//     if let Some(_) = pos {
//       self.last_mouse = pos;
//     }
//     ui.input(|input| {
//       for event in &input.events {
//         if let egui::Event::Key {
//           key,
//           physical_key: _,
//           pressed,
//           repeat,
//           modifiers: _,
//         } = event
//         {
//           // Обрабатываем только момент нажатия (KeyDown), игнорируем отпускание (KeyUp)
//           if *key == egui::Key::A && *pressed && !repeat {
//             //println!("Клавиша A нажата (один раз) {p:?}", p = self.last_mouse);
//             if let Some(last_pos) = self.last_mouse {
//               if let Some(gp) = _grid.view_port.try_gridpoint_of(last_pos) {
//                 let note1 = Note {
//                   time: gp.time,
//                   pitch: gp.pitch,
//                   velocity: Velocity::V100,
//                   duration: 250u64.ms(),
//                 };
//                 _grid.objects.push(Box::new(note1));
//               }
//             }
//           }
//         }
//       }
//     });
//   }
// }
