use chrono::TimeDelta;
use egui::{Pos2, Rect, Ui};
use midi_model::{EventTime, Pitch, pitches};
use std::{cell::RefCell, rc::Rc};

use crate::music_grid::GridPoint;
use crate::music_grid::{ChronoTimeDeltaExt, GridRect, PixelPos, PixelRect};

/// Функция линейной интерполяции между двумя значениями
///
/// - x - входное значение
/// - min_x, max_x - диапазон входных значений
/// - y_min, y_max - диапазон выходных значений
fn interpolation(x: f64, min_x: f64, max_x: f64, y_min: f64, y_max: f64) -> Option<f64> {
  let x_w = max_x - min_x;
  let y_w = y_max - y_min;
  if y_w == 0f64 {
    return Some(y_min);
  }
  if x_w == 0f64 {
    return Some(y_min);
  }

  let k = y_w / x_w;

  let base_x = x - min_x;
  let base_y = base_x * k;

  Some(base_y + y_min)
}

/// Структура ViewPort представляет собой область просмотра для отображения музыкальных данных
/// Она управляет временными и высотными координатами на экране
pub struct ViewPort {
  /// Начальное время отображения
  start_time: chrono::TimeDelta,

  /// Список обработчиков изменения времени
  start_time_changed: Vec<Rc<dyn Fn(chrono::TimeDelta) + 'static>>,

  /// Количество пикселей на секунду
  pixels_per_second: f64,
  pixels_per_second_changed: Vec<Rc<dyn Fn(f64) + 'static>>,

  /// Нижняя граница высоты тона в пикселях
  lower_pitch: f64,
  lower_pitch_changed: Vec<Rc<dyn Fn(f64) + 'static>>,

  /// Верхняя граница высоты тона в пикселях
  higher_pitch: f64,
  higher_pitch_changed: Vec<Rc<dyn Fn(f64) + 'static>>,
}

impl Default for ViewPort {
  fn default() -> Self {
    Self {
      start_time: chrono::TimeDelta::milliseconds(0),
      start_time_changed: Default::default(),
      pixels_per_second: 500.0,
      pixels_per_second_changed: Default::default(),
      lower_pitch: 1000.0,
      lower_pitch_changed: Default::default(),
      higher_pitch: 0.0,
      higher_pitch_changed: Default::default(),
    }
  }
}

impl ViewPort {
  /// Получить начальное время отображения
  pub fn get_start_time(&self) -> chrono::TimeDelta {
    self.start_time
  }

  /// Установить начальное время отображения и вызвать обработчики
  pub fn set_start_time(&mut self, t: chrono::TimeDelta) {
    self.start_time = t;
    self.fire_start_time_changed(t);
  }

  /// Добавить обработчик изменения времени
  pub fn on_start_time_changed<F: Fn(chrono::TimeDelta) + 'static>(&mut self, f: Rc<F>) {
    self.start_time_changed.push(f.clone());
  }

  /// Вызвать все обработчики изменения времени
  fn fire_start_time_changed(&self, t: chrono::TimeDelta) {
    for ls in self.start_time_changed.iter() {
      (*ls)(t.clone());
    }
  }

  ////////////////////////////////////////////////////////

  /// Получить количество пикселей на секунду
  pub fn get_pixels_per_second(&self) -> f64 {
    self.pixels_per_second
  }

  /// Установить количество пикселей на секунду и вызвать обработчики
  pub fn set_pixels_per_second(&mut self, v: f64) {
    self.pixels_per_second = v;
    self.fire_pixels_per_second_changed(v);
  }

  /// Добавить обработчик изменения масштаба
  pub fn on_pixels_per_second_changed<F: Fn(f64) + 'static>(&mut self, f: Rc<F>) {
    self.pixels_per_second_changed.push(f.clone());
  }

  /// Вызвать все обработчики изменения масштаба
  fn fire_pixels_per_second_changed(&self, t: f64) {
    for ls in self.pixels_per_second_changed.iter() {
      (*ls)(t.clone());
    }
  }

  ////////////////////////////////////////////////////////

  /// Получить нижнюю границу высоты тона
  pub fn get_lower_pitch(&self) -> f64 {
    self.lower_pitch
  }

  /// Установить нижнюю границу высоты тона и вызвать обработчики
  pub fn set_lower_pitch(&mut self, v: f64) {
    self.lower_pitch = v;
    self.fire_lower_pitch_changed(v);
  }

  /// Добавить обработчик изменения нижней границы
  pub fn on_lower_pitch_changed<F: Fn(f64) + 'static>(&mut self, f: Rc<F>) {
    self.lower_pitch_changed.push(f.clone());
  }

  /// Вызвать все обработчики изменения нижней границы
  fn fire_lower_pitch_changed(&self, t: f64) {
    for ls in self.lower_pitch_changed.iter() {
      (*ls)(t.clone());
    }
  }

  ////////////////////////////////////////////////////////

  /// Получить верхнюю границу высоты тона
  pub fn get_higher_pitch(&self) -> f64 {
    self.higher_pitch
  }

  /// Установить верхнюю границу высоты тона и вызвать обработчики
  pub fn set_higher_pitch(&mut self, v: f64) {
    self.higher_pitch = v;
    self.fire_higher_pitch_changed(v);
  }

  /// Добавить обработчик изменения верхней границы
  pub fn on_higher_pitch_changed<F: Fn(f64) + 'static>(&mut self, f: Rc<F>) {
    self.higher_pitch_changed.push(f.clone());
  }

  /// Вызвать все обработчики изменения верхней границы
  fn fire_higher_pitch_changed(&self, t: f64) {
    for ls in self.higher_pitch_changed.iter() {
      (*ls)(t.clone());
    }
  }
}

impl ViewPort {
  /// Преобразовать время в координату X на экране
  pub fn x_of_time<T: Into<chrono::TimeDelta>>(&self, time: T) -> f64 {
    let t: chrono::TimeDelta = time.into();

    let x = (t.num_milliseconds() as f64) / 1_000.0; // Преобразование миллисекунд в секунды
    let x = x * self.pixels_per_second; // Масштабирование по времени

    let x_off = (self.start_time.num_milliseconds() as f64) / 1_000.0; // Смещение начального времени
    let x_off = x_off * self.pixels_per_second; // Масштабирование смещения

    x - x_off // Возврат смещенной координаты
  }

  /// Преобразовать координату X на экране в время
  pub fn time_of_x<X: Into<f64>>(&self, x: X) -> chrono::TimeDelta {
    if self.pixels_per_second == 0.0 {
      return chrono::TimeDelta::zero(); // Защита от деления на ноль
    }
    let second = x.into() / self.pixels_per_second; // Обратное масштабирование
    let t = chrono::TimeDelta::milliseconds((second * 1000.0) as i64); // Преобразование в миллисекунды
    t + self.start_time // Добавление смещения
  }

  /// Преобразовать высоту тона в координату Y на экране
  pub fn y_of_pitch_f64(&self, pitch: f64) -> f64 {
    // Интерполяция из диапазона MIDI (0-128) в диапазон экрана (lower_pitch - higher_pitch)
    interpolation(pitch, 0.0, 128.0, self.lower_pitch, self.higher_pitch)
      .unwrap_or((self.higher_pitch - self.lower_pitch) / 2.0 + self.lower_pitch) // Значение по умолчанию при ошибке
  }

  /// Преобразовать координату Y на экране в высоту тона
  pub fn pitch_f64_of_y(&self, y: f64) -> f64 {
    if self.lower_pitch == self.higher_pitch {
      return self.lower_pitch; // Защита от деления на ноль
    }

    match interpolation(y, self.lower_pitch, self.higher_pitch, 0.0, 128.0) {
      None => (self.higher_pitch - self.lower_pitch) / 2.0 + self.lower_pitch, // Значение по умолчанию
      Some(a) => a,
    }
  }

  ////////////////////////

  /// Преобразовать высоту тона в диапазон координат Y (учитывая целую ноту)
  pub fn y_of_pitch<P: Into<Pitch>>(&self, pitch: P) -> (f64, f64) {
    let p: Pitch = pitch.into();
    let y0 = self.y_of_pitch_f64(p.number() as f64); // Y-координата начала ноты
    let y1 = self.y_of_pitch_f64(p.number() as f64 + 1.0); // Y-координата конца ноты
    (y0.min(y1), y0.max(y1)) // Возвращаем минимальную и максимальную координаты
  }

  /// Преобразовать координату Y в высоту тона (с проверкой корректности)
  pub fn try_pitch_of(&self, y: f64) -> Option<Pitch> {
    let p = interpolation(y, self.lower_pitch, self.higher_pitch, 0.0, 128.0); // Интерполяция
    if p.is_none() {
      return None; // Ошибка интерполяции
    }
    let p = p.unwrap();

    // Проверка диапазона значения
    if p < u8::MIN as f64 || p > u8::MAX as f64 {
      return None;
    }
    let p = p as u8;

    // Проверка диапазона допустимой высоты тона
    if p < Pitch::MIN.number() || p > Pitch::MAX.number() {
      return None;
    }
    Pitch::new(p).ok() // Создание и возврат объекта Pitch
  }

  ////////////////////////

  /// Преобразовать точку сетки в пиксельную позицию
  pub fn try_pixelpos_of(&self, grid_point: GridPoint) -> Option<PixelPos> {
    let x = self.x_of_time(grid_point.time); // Преобразование времени в X

    let y = interpolation(
      grid_point.pitch.number() as f64, // Номер высоты тона
      0.0,                              // Минимальное значение в MIDI
      128.0,                            // Максимальное значение в MIDI
      self.lower_pitch,                 // Минимальная Y-координата
      self.higher_pitch,                // Максимальная Y-координата
    );

    y.map(|y| PixelPos { x: x, y: y }) // Возврат пиксельной позиции
  }

  /// Преобразовать прямоугольник сетки в прямоугольник на экране
  pub fn try_rect_of(&self, grid_rect: GridRect) -> Option<Rect> {
    let p0 = self.try_pixelpos_of(grid_rect.first_point); // Первая точка
    let p1 = self.try_pixelpos_of(grid_rect.second_point); // Вторая точка

    p0.and_then(|p0| {
      p1.map(|p1| {
        let x_min = p0.x.min(p1.x); // Минимальная X-координата
        let x_max = p0.x.max(p1.x); // Максимальная X-координата

        let y_min = p0.y.min(p1.y); // Минимальная Y-координата
        let y_max = p0.y.max(p1.y); // Максимальная Y-координата

        Rect {
          min: Pos2 {
            x: x_min as f32, // Преобразование в f32 для egui
            y: y_min as f32,
          },
          max: Pos2 {
            x: x_max as f32, // Преобразование в f32 для egui
            y: y_max as f32,
          },
        }
      })
    })
  }

  /// Преобразовать X-координату в время события
  pub fn try_eventtime_of(&self, x: f64) -> Option<EventTime> {
    self.time_of_x(x).try_into().ok() // Преобразование TimeDelta в EventTime
  }

  /// Преобразовать пиксельную позицию в точку сетки
  pub fn try_gridpoint_of<P: Into<PixelPos>>(&self, p: P) -> Option<GridPoint> {
    let pixel = p.into();
    self.try_pitch_of(pixel.y).and_then(|p| {
      // Преобразование Y в высоту тона
      self
        .try_eventtime_of(pixel.x) // Преобразование X во время
        .map(|t| GridPoint { pitch: p, time: t }) // Создание точки сетки
    })
  }

  /// Преобразовать прямоугольник пикселей в прямоугольник сетки
  pub fn try_gridrect_of<R: Into<PixelRect>>(&self, rect: R) -> Option<GridRect> {
    let rect: PixelRect = rect.into();

    let left_top = self.try_gridpoint_of(rect.left_top()); // Левый верхний угол
    let right_bottom = self.try_gridpoint_of(rect.right_bottom()); // Правый нижний угол

    left_top.and_then(|lt| {
      right_bottom.map(|rb| GridRect {
        first_point: lt,  // Первый угол
        second_point: rb, // Второй угол
      })
    })
  }

  /// Определить видимую область сетки по прямоугольнику клиента
  pub fn try_visible_gridrect_of<R: Into<PixelRect>>(&self, client_rect: R) -> Option<GridRect> {
    let pixel_rect: PixelRect = client_rect.into();

    let t0 = self.time_of_x(pixel_rect.left()); // Время левой границы
    let t1 = self.time_of_x(pixel_rect.right()); // Время правой границы

    if t1.num_milliseconds() < 0 {
      // Если правая граница до нуля
      return None;
    }

    // Ограничение левой границы нулем
    let t0 = if t0.num_milliseconds() < 0 {
      chrono::TimeDelta::zero()
    } else {
      t0
    };

    let p0 = self.pitch_f64_of_y(pixel_rect.top()); // Высота тона верхней границы
    let p1 = self.pitch_f64_of_y(pixel_rect.bottom()); // Высота тона нижней границы

    let (p0, p1) = if p0 > p1 { (p1, p0) } else { (p0, p1) }; // Упорядочивание высот
    let (t0, t1) = if t0 > t1 { (t1, t0) } else { (t0, t1) }; // Упорядочивание времени

    EventTime::try_from(t0).ok().and_then(|t0| {
      EventTime::try_from(t1).ok().map(|t1| GridRect {
        first_point: GridPoint {
          time: t0,                  // Время начала
          pitch: Pitch::bounded(p0), // Ограниченная высота тона
        },
        second_point: GridPoint {
          time: t1,                  // Время окончания
          pitch: Pitch::bounded(p1), // Ограниченная высота тона
        },
      })
    })
  }
}

pub struct Scaling {
  pub pitch_height: f64,
  pub pixels_per_second: f64,
}

impl From<&ViewPort> for Scaling {
  fn from(view_port: &ViewPort) -> Self {
    let pitch_h = view_port.y_of_pitch(pitches::C_4);
    Scaling {
      pitch_height: (pitch_h.0 - pitch_h.1).abs(),
      pixels_per_second: view_port.pixels_per_second,
    }
  }
}

impl Scaling {
  pub fn apply<C: Into<Pos2>>(&self, view_port: &mut ViewPort, center: C) {
    let center = center.into();
    let x = center.x as f64;
    let y = center.y as f64;

    // ===== ВРЕМЯ (ось X) =====
    // 1. Получаем логическое время, соответствующее экранной координате x
    let time = view_port.time_of_x(x);
    let time_seconds = time.num_milliseconds() as f64 / 1000.0;

    // 2. Вычисляем новое start_time так, чтобы `time` снова отобразился в `x`
    //    Формула: x = (time_seconds - start_seconds) * pixels_per_second
    //    => start_seconds = time_seconds - x / pixels_per_second
    let new_start_seconds = time_seconds - x / self.pixels_per_second;
    let new_start_time =
      chrono::TimeDelta::milliseconds((new_start_seconds * 1000.0).round() as i64);

    // 3. Применяем новые параметры времени (через сеттеры с уведомлением)
    view_port.set_pixels_per_second(self.pixels_per_second);
    view_port.set_start_time(new_start_time);

    // ===== ВЫСОТА ТОНА (ось Y) =====
    let cur_pitch_h = view_port.y_of_pitch(pitches::C_4);
    let cur_pitch_h = (cur_pitch_h.0 - cur_pitch_h.1).abs();

    let y_factor = self.pitch_height / cur_pitch_h;

    let lower_pitch = view_port.get_lower_pitch();
    let higher_pitch = view_port.get_higher_pitch();

    let l_delta_y = (lower_pitch - y) * y_factor;
    let h_delta_y = (y - higher_pitch) * y_factor;

    let lower_pitch = y + l_delta_y;
    let higher_pitch = y + h_delta_y;

    // 3. Применяем новые границы высоты тона
    view_port.set_lower_pitch(lower_pitch);
    view_port.set_higher_pitch(higher_pitch);
  }
}

pub struct ViewPortControls {
  time_start: Rc<RefCell<String>>,
  pixels_per_second: Rc<RefCell<String>>,
  lower_pitch: Rc<RefCell<String>>,
  higher_pitch: Rc<RefCell<String>>,
  grid_visible_rect: Option<Rc<RefCell<Rect>>>,
  pitch_size_str: String,
  recompute_pitch_size_str: Rc<RefCell<bool>>,
}

impl ViewPortControls {
  pub fn new(view_port: &mut ViewPort) -> Self {
    let recompute_pitch_size_str = Rc::new(RefCell::new(true));
    let recompute_pitch_size_str0 = recompute_pitch_size_str.clone();
    let recompute_pitch_size_str1 = recompute_pitch_size_str.clone();

    let time_start_str = Rc::new(RefCell::new(view_port.get_start_time().to_human_string()));
    let time_start_str2 = time_start_str.clone();

    view_port.on_start_time_changed(Rc::new(move |t: chrono::TimeDelta| {
      let mut str = time_start_str2.borrow_mut(); // todo use try
      str.clear();
      str.push_str(&t.to_human_string());
    }));

    let pixels_per_second_str =
      Rc::new(RefCell::new(view_port.get_pixels_per_second().to_string()));
    let pixels_per_second_str2 = pixels_per_second_str.clone();
    view_port.on_pixels_per_second_changed(Rc::new(move |pps: f64| {
      let mut str = pixels_per_second_str2.borrow_mut();
      str.clear();
      str.push_str(&pps.to_string());
    }));

    let lower_pitch_str = Rc::new(RefCell::new(view_port.get_lower_pitch().to_string()));
    let lower_pitch_str2 = lower_pitch_str.clone();
    view_port.on_lower_pitch_changed(Rc::new(move |pps: f64| {
      let mut str = lower_pitch_str2.borrow_mut();
      str.clear();
      str.push_str(&pps.to_string());
      *recompute_pitch_size_str0.borrow_mut() = true;
    }));

    let higher_pitch_str = Rc::new(RefCell::new(view_port.get_higher_pitch().to_string()));
    let higher_pitch_str2 = higher_pitch_str.clone();
    view_port.on_higher_pitch_changed(Rc::new(move |pps: f64| {
      let mut str = higher_pitch_str2.borrow_mut();
      str.clear();
      str.push_str(&pps.to_string());
      *recompute_pitch_size_str1.borrow_mut() = true;
    }));

    Self {
      time_start: time_start_str,
      pixels_per_second: pixels_per_second_str,
      lower_pitch: lower_pitch_str,
      higher_pitch: higher_pitch_str,
      grid_visible_rect: None,
      pitch_size_str: "".to_string(),
      recompute_pitch_size_str: recompute_pitch_size_str,
    }
  }

  pub fn set_grid_visible_rect(&mut self, rect: Rc<RefCell<Rect>>) {
    self.grid_visible_rect = Some(rect);
  }

  pub fn ui(&mut self, view_port: &mut ViewPort, ui: &mut Ui) {
    {
      let mut flag = self.recompute_pitch_size_str.borrow_mut();
      if *flag {
        let (pitch0y, pitch1y) = view_port.y_of_pitch(pitches::C_4);
        *flag = false;
        self.pitch_size_str = format!("{}", (pitch0y - pitch1y).abs());
      }
    }

    egui::Grid::new("view_port_ctrl1")
      .num_columns(4)
      .min_col_width(100.0)
      .max_col_width(100.0)
      .striped(true)
      .show(ui, |ui| {
        if let Some(grid_rect) = &self.grid_visible_rect {
          let grid_rect = {
            grid_rect
              .try_borrow()
              .expect("grid_visible_rect try_borrow fail at view_port")
              .clone()
          };

          let pitch0 = view_port.pitch_f64_of_y(grid_rect.min.y as f64);
          let pitch1 = view_port.pitch_f64_of_y(grid_rect.max.y as f64);

          let pitch_min = Pitch::bounded(pitch0.min(pitch1));
          let pitch_max = Pitch::bounded(pitch0.max(pitch1));
          let pitch_len = pitch_max.number() - pitch_min.number();

          ui.label("visible pitch min");
          ui.label(format!("{pitch_min}"));
          ui.end_row();

          ui.label("visible pitch max");
          ui.label(format!("{pitch_max}"));
          ui.end_row();

          ui.label("visible pitch total");
          ui.label(format!("{pitch_len}"));
          if ui.button("- zoom in").clicked() {
            // zoom in
            let total_pitch = (view_port.get_lower_pitch() - view_port.get_higher_pitch()).abs();
            let next_total_pitch = total_pitch * 1.10;
            let pitch_delta = next_total_pitch - total_pitch;

            if view_port.get_lower_pitch() < view_port.get_higher_pitch() {
              view_port.set_lower_pitch(view_port.get_lower_pitch() - pitch_delta / 2.0);
              view_port.set_higher_pitch(view_port.get_higher_pitch() + pitch_delta / 2.0);
            } else {
              view_port.set_lower_pitch(view_port.get_lower_pitch() + pitch_delta / 2.0);
              view_port.set_higher_pitch(view_port.get_higher_pitch() - pitch_delta / 2.0);
            }
          }
          if ui.button("+ zoom out").clicked() {
            // zoom out
            let total_pitch = (view_port.get_lower_pitch() - view_port.get_higher_pitch()).abs();
            let next_total_pitch = total_pitch * 0.9;
            let pitch_delta = next_total_pitch - total_pitch;

            if view_port.get_lower_pitch() < view_port.get_higher_pitch() {
              view_port.set_lower_pitch(view_port.get_lower_pitch() - pitch_delta / 2.0);
              view_port.set_higher_pitch(view_port.get_higher_pitch() + pitch_delta / 2.0);
            } else {
              view_port.set_lower_pitch(view_port.get_lower_pitch() + pitch_delta / 2.0);
              view_port.set_higher_pitch(view_port.get_higher_pitch() - pitch_delta / 2.0);
            }
          }
          ui.end_row();

          ui.label("pitch size");
          ui.text_edit_singleline(&mut self.pitch_size_str);
          if ui.button("set new size").clicked() {
            if let Ok(new_pitch_size) = self.pitch_size_str.parse::<f64>() {
              let (pitch0y, pitch1y) = view_port.y_of_pitch(pitches::C_4);
              let cur_pitch_size = (pitch1y - pitch0y).abs();
              if cur_pitch_size > 0.0 && new_pitch_size > 0.0 && new_pitch_size != cur_pitch_size {
                let factor = new_pitch_size / cur_pitch_size;

                let total_pitch =
                  (view_port.get_lower_pitch() - view_port.get_higher_pitch()).abs();
                let next_total_pitch = total_pitch * factor;
                let pitch_delta = next_total_pitch - total_pitch;

                if view_port.get_lower_pitch() < view_port.get_higher_pitch() {
                  view_port.set_lower_pitch(view_port.get_lower_pitch() - pitch_delta / 2.0);
                  view_port.set_higher_pitch(view_port.get_higher_pitch() + pitch_delta / 2.0);
                } else {
                  view_port.set_lower_pitch(view_port.get_lower_pitch() + pitch_delta / 2.0);
                  view_port.set_higher_pitch(view_port.get_higher_pitch() - pitch_delta / 2.0);
                }
              }
            }
          }
          ui.end_row();
        }

        //
        ui.label("time start");
        {
          let mut time_start = self
            .time_start
            .try_borrow_mut()
            .expect("self.time_start.try_borrow_mut at view_port");
          egui::TextEdit::singleline(&mut (*time_start))
            .desired_width(100.0)
            .show(ui);
        }
        ui.end_row();

        ui.label("pixels per second");
        {
          let mut pxls_per_sec = self
            .pixels_per_second
            .try_borrow_mut()
            .expect("self.pixels_per_second.try_borrow_mut at view_port");
          egui::TextEdit::singleline(&mut (*pxls_per_sec))
            .desired_width(100.0)
            .show(ui);
        }
        ui.end_row();

        ui.label("lower pitch");
        {
          let mut num = self
            .lower_pitch
            .try_borrow_mut()
            .expect("self.lower_pitch.try_borrow_mut at view_port");
          egui::TextEdit::singleline(&mut (*num))
            .desired_width(100.0)
            .show(ui);
        }
        ui.end_row();

        ui.label("higer pitch");
        {
          let mut num = self
            .higher_pitch
            .try_borrow_mut()
            .expect("self.higher_pitch.try_borrow_mut at view_port");
          egui::TextEdit::singleline(&mut (*num))
            .desired_width(100.0)
            .show(ui);
        }
        ui.end_row();
      });

    if ui.button(format!("reset")).clicked() {
      let def_vp: ViewPort = Default::default();

      view_port.set_start_time(def_vp.get_start_time());
      view_port.set_pixels_per_second(def_vp.get_pixels_per_second());
      view_port.set_lower_pitch(def_vp.get_lower_pitch());
      view_port.set_higher_pitch(def_vp.get_higher_pitch());
    }

    if ui.button("set").clicked() {
      if let Ok(t) = {
        TimeDelta::parse_human_string(
          &self
            .time_start
            .try_borrow()
            .expect("self.time_start.try_borrow at view_port / set"),
        )
      } {
        view_port.set_start_time(t);
      }
      if let Ok(n) = {
        self
          .pixels_per_second
          .try_borrow()
          .expect("self.time_start.pixels_per_second at view_port / set")
          .parse::<f64>()
      } {
        view_port.set_pixels_per_second(n);
      }
      if let Ok(n) = {
        self
          .lower_pitch
          .try_borrow()
          .expect("self.time_start.lower_pitch at view_port / set")
          .parse::<f64>()
      } {
        view_port.set_lower_pitch(n);
      }
      if let Ok(n) = {
        self
          .higher_pitch
          .try_borrow()
          .expect("self.higher_pitch.try_borrow at view_port / set")
          .parse::<f64>()
      } {
        view_port.set_higher_pitch(n);
      }
    }
  }
}
