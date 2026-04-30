use egui::{CursorIcon, Rect, Sense, Ui, UiBuilder};

pub struct SplitView {
  pub fraction: f32, // 0.0 - 1.0
  pub min_width: f32,
  pub separator_width: f32,
}

impl SplitView {
  pub fn new(fraction: f32) -> Self {
    Self {
      fraction,
      min_width: 100.0,
      separator_width: 4.0, // ширина разделителя по умолчанию
    }
  }

  pub fn min_wdith(mut self, w: f32) -> Self {
    self.min_width = w;
    self
  }

  pub fn separator_width(mut self, w: f32) -> Self {
    self.separator_width = w;
    self
  }

  pub fn show<R>(
    &mut self,
    ui: &mut Ui,
    add_left_right: impl FnOnce(&mut Ui, &mut Ui) -> R,
  ) -> egui::InnerResponse<R> {
    let available = ui.available_rect_before_wrap();

    // Вычисляем координаты разделителя
    let total_width = available.width();
    let left_width = (total_width * self.fraction).max(self.min_width);
    let split_x = available.min.x + left_width;

    // Рисуем разделитель
    let separator_rect = Rect::from_min_max(
      egui::pos2(split_x - self.separator_width / 2.0, available.min.y),
      egui::pos2(split_x + self.separator_width / 2.0, available.max.y),
    );

    let separator_response = ui.interact(separator_rect, ui.id().with("separator"), Sense::drag());

    // Изменяем курсор при наведении на разделитель
    if separator_response.hovered() {
      ui.output_mut(|o| o.cursor_icon = CursorIcon::ResizeColumn);
    }

    // Обновляем позицию разделителя при перетаскивании
    if separator_response.dragged() {
      let new_x = separator_response.interact_pointer_pos().unwrap().x;
      let clamped_x = new_x.clamp(
        available.min.x + self.min_width + self.separator_width / 2.0,
        available.max.x - self.min_width - self.separator_width / 2.0,
      );
      self.fraction = (clamped_x - available.min.x) / total_width;
    }

    // Левая панель (до разделителя)
    let left_rect = Rect::from_min_max(
      available.min,
      egui::pos2(separator_rect.min.x, available.max.y),
    );

    // Правая панель (после разделителя)
    let right_rect = Rect::from_min_max(
      egui::pos2(separator_rect.max.x, available.min.y),
      available.max,
    );

    // Создаем дочерние UI для левой и правой панелей
    let mut left_ui = ui.new_child(UiBuilder::new().max_rect(left_rect));
    let mut right_ui = ui.new_child(UiBuilder::new().max_rect(right_rect));

    // Вызываем пользовательскую функцию с двумя UI
    let inner = add_left_right(&mut left_ui, &mut right_ui);

    // Рисуем сам разделитель
    ui.painter()
      .rect_filled(separator_rect, 0.0, egui::Color32::GRAY);

    egui::InnerResponse::new(
      inner,
      left_ui.response() | separator_response | right_ui.response(),
    )
  }
}
