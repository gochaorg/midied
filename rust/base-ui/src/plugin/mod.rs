use log;
use std::{
  cell::RefCell,
  rc::Rc,
  sync::{Arc, Mutex},
};

use egui::Ui;

pub trait Panel {
  fn name(&self) -> String {
    "unnamed Panel".to_string()
  }

  fn update_egui_context(&mut self, _ctx: egui::Context) {}

  fn ui(&mut self, ui: &mut Ui);
}

impl Panel for Box<dyn Panel> {
  fn ui(&mut self, ui: &mut Ui) {
    self.as_mut().ui(ui);
  }
}

impl Panel for Rc<RefCell<dyn Panel>> {
  fn ui(&mut self, ui: &mut Ui) {
    match self.try_borrow_mut() {
      Ok(mut p) => p.ui(ui),
      Err(e) => {
        log::error!("can't borrow_mut for Panel, {e}")
      }
    }
  }
}

impl Panel for Arc<Mutex<dyn Panel>> {
  fn ui(&mut self, ui: &mut Ui) {
    match self.try_lock() {
      Ok(mut p) => p.ui(ui),
      Err(e) => {
        log::error!("can't lock for Panel, {e}")
      }
    }
  }
}
