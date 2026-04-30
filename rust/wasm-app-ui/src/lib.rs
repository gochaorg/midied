#[allow(unused)]
use base_ui::{coll::IdListImpl, music_grid::GridObj};

#[allow(unused)]
use std::collections::HashMap;

#[allow(unused)]
use std::sync::{Arc, Mutex};

#[allow(unused)]
use base_ui::MyApp;

#[allow(unused)]
use eframe::egui;

#[cfg(target_arch = "wasm32")]
use wasm_bindgen::JsCast;

#[cfg(target_arch = "wasm32")]
use web_sys::{Document, Element};

// // When compiling to web using trunk:
#[cfg(target_arch = "wasm32")]
#[wasm_bindgen::prelude::wasm_bindgen(start)]
pub fn main() {
  use eframe::wasm_bindgen::JsCast as _;

  // Redirect `log` message to `console.log` and friends:
  eframe::WebLogger::init(log::LevelFilter::Debug).ok();

  let web_options = eframe::WebOptions::default();

  wasm_bindgen_futures::spawn_local(async {
    let document = web_sys::window()
      .expect("No window")
      .document()
      .expect("No document");

    let params = fetch_params(&document);

    let canvas = document
      .get_element_by_id("the_canvas_id")
      .expect("Failed to find the_canvas_id")
      .dyn_into::<web_sys::HtmlCanvasElement>()
      .expect("the_canvas_id was not a HtmlCanvasElement");

    let start_result = eframe::WebRunner::new()
      .start(
        canvas,
        web_options,
        Box::new(move |_cc| Ok(Box::new(MyApp::new(&params)))),
      )
      .await;

    // Remove the loading text and spinner:
    if let Some(loading_text) = document.get_element_by_id("loading_text") {
      match start_result {
        Ok(_) => {
          loading_text.remove();
        }
        Err(e) => {
          loading_text
            .set_inner_html("<p> The app has crashed. See the developer console for details. </p>");
          panic!("Failed to start eframe: {e:?}");
        }
      }
    }
  });
}

#[cfg(target_arch = "wasm32")]
fn fetch_params(doc: &Document) -> HashMap<String, String> {
  let mut map: HashMap<String, String> = Default::default();
  if let Ok(lst) = doc.query_selector_all("config") {
    for idx in 0..lst.length() {
      if let Some(config_el) = lst.item(idx) {
        if let Some(config_el) = config_el.dyn_ref::<Element>() {
          if let Some(key) = config_el.get_attribute("key")
            && let Some(value) = config_el.get_attribute("value")
          {
            map.insert(key, value);
          }
        }
      }
    }
  }
  map
}
