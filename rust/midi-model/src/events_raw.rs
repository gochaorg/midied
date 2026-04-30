use crate::{events::*, EventTime, Pitch};

/// Источник необработанных MIDI данных для парсинга
/// Содержит байтовый срез с MIDI сообщениями, временную метку и позицию начала
#[derive(Debug, Clone, Copy)]
pub struct RawEventSource<'a> {
  /// Временная метка в микросекундах для текущего события
  pub stamp_microseconds: u64,
  /// Байтовый срез с необработанными MIDI данными
  pub source: &'a [u8],
  /// Индекс начала текущего события в исходном массиве
  pub from: usize,
}

impl<'a> RawEventSource<'a> {
  /// Создает новый источник необработанных MIDI данных
  pub fn new(stamp_microseconds: u64, source: &'a [u8], from: usize) -> Self {
    Self {
      stamp_microseconds,
      source,
      from,
    }
  }
}

impl Event {
  /// Парсит последовательность MIDI событий из необработанных байтовых данных
  /// Возвращает вектор распознанных MIDI событий
  pub fn parse_raw<'a>(ptr: RawEventSource<'a>) -> Vec<Event> {
    let mut events = Vec::new();

    // Локальная функция для разбора одного MIDI события
    // Проверяет каждый тип события по очереди
    let parse_one = |ptr2| {
      if let Some(res) = Event::parse_note_on(ptr2) {
        return Some(res);
      }
      if let Some(res) = Event::parse_note_off(ptr2) {
        return Some(res);
      }
      if let Some(res) = Event::parse_control_change(ptr2) {
        return Some(res);
      }
      if let Some(res) = Event::parse_pitch_bend(ptr2) {
        return Some(res);
      }
      if let Some(res) = Event::parse_channel_pressure(ptr2) {
        return Some(res);
      }
      if let Some(res) = Event::parse_poly_pressure(ptr2) {
        return Some(res);
      }
      if let Some(res) = Event::parse_channel_mode(ptr2) {
        return Some(res);
      }
      None
    };

    let mut p = ptr.clone();

    // Продолжаем разбор до тех пор, пока удается распознать события
    while let Some((event, next_ptr)) = parse_one(p) {
      events.push(event);
      p = next_ptr;
    }

    events
  }

  /// Разбирает сообщение Note On (включение ноты)
  /// Статус байт: 0x9n, где n - номер канала (0-15)
  /// Формат: [status, note, velocity]
  fn parse_note_on<'a>(ptr: RawEventSource<'a>) -> Option<(Event, RawEventSource<'a>)> {
    // Проверяем, достаточно ли байтов для сообщения Note On (3 байта)
    if ptr.source.len() < 3 {
      return None;
    }

    let status: u8 = ptr.source[0];
    // Проверяем, что старшие 4 бита равны 0x9 (Note On)
    if (status & 0xF0) != 0x90 {
      return None;
    }

    // Извлекаем номер канала из младших 4 битов статус байта
    let channel = status & 0x0F;

    let note = ptr.source[1];
    // Проверяем, что номер ноты в допустимом диапазоне (0-127)
    if note > Pitch::MAX.number() {
      return None;
    }

    let velocity = ptr.source[2];
    // Скорость также должна быть в диапазоне 0-127
    if velocity > 127 {
      return None;
    }

    Some((
      Event::NoteOn {
        time: EventTime {
          stamp_microseconds: ptr.stamp_microseconds,
        },
        // Создаем канал без проверки (предполагается, что проверка уже выполнена)
        channel: Channel::new_unsafe(channel),
        // Создаем высоту тона без проверки
        pitch: Pitch::new_unsafe(note),
        // Создаем скорость без проверки
        velocity: Velocity::new_unsafe(velocity),
      },
      // Возвращаем указатель на следующее событие (после 3 байт текущего события)
      RawEventSource::new(ptr.stamp_microseconds, ptr.source, ptr.from + 3),
    ))
  }

  /// Разбирает сообщение Note Off (выключение ноты)
  /// Статус байт: 0x8n, где n - номер канала (0-15)
  /// Также может быть представлено как Note On с нулевой скоростью
  fn parse_note_off<'a>(ptr: RawEventSource<'a>) -> Option<(Event, RawEventSource<'a>)> {
    if ptr.source.len() < 3 {
      return None;
    }

    let status: u8 = ptr.source[0];
    // Проверяем, что старшие 4 бита равны 0x8 (Note Off)
    if (status & 0xF0) != 0x80 {
      return None;
    }

    let channel = status & 0x0F;

    let note = ptr.source[1];
    if note > Pitch::MAX.number() {
      return None;
    }

    let velocity = ptr.source[2];

    // Скорость для Note Off должна быть в диапазоне 0-127
    if velocity >= 128 {
      return None;
    }

    Some((
      Event::NoteOff {
        time: EventTime {
          stamp_microseconds: ptr.stamp_microseconds,
        },
        channel: Channel::new_unsafe(channel),
        pitch: Pitch::new_unsafe(note),
        velocity: Velocity::new_unsafe(velocity),
      },
      RawEventSource::new(ptr.stamp_microseconds, ptr.source, ptr.from + 3),
    ))
  }

  /// Разбирает сообщение Control Change (изменение контроллера)
  /// Статус байт: 0xBn, где n - номер канала (0-15)
  /// Формат: [status, controller_number, value]
  fn parse_control_change<'a>(ptr: RawEventSource<'a>) -> Option<(Event, RawEventSource<'a>)> {
    if ptr.source.len() < 3 {
      return None;
    }

    let status: u8 = ptr.source[0];
    // Проверяем, что старшие 4 бита равны 0xA (1010 в двоичном) - это может быть ошибка
    // Правильно: 0xB (1011 в двоичном) для Control Change
    if ((status & 0xF0) >> 4) != 0b1010 {
      // Это ошибка! Должно быть 0b1011
      return None;
    }

    let channel = status & 0x0F;
    // Извлекаем номер контроллера (7 младших битов)
    let cntlr = ptr.source[1] & 0b0111_1111;
    // Извлекаем значение контроллера (7 младших битов)
    let value = ptr.source[2] & 0b0111_1111;

    Some((
      Event::ControlChange {
        time: EventTime {
          stamp_microseconds: ptr.stamp_microseconds,
        },
        channel: Channel::new_unsafe(channel),
        control: cntlr,
        value: value,
      },
      RawEventSource::new(ptr.stamp_microseconds, ptr.source, ptr.from + 3),
    ))
  }

  /// Разбирает сообщение Pitch Bend (изменение высоты тона)
  /// Статус байт: 0xEn, где n - номер канала (0-15)
  /// Формат: [status, lsb, msb] - 14-битное значение
  fn parse_pitch_bend<'a>(ptr: RawEventSource<'a>) -> Option<(Event, RawEventSource<'a>)> {
    if ptr.source.len() < 3 {
      return None;
    }

    let status: u8 = ptr.source[0];
    // Проверяем, что старшие 4 бита равны 0xE (1110 в двоичном) - Pitch Bend
    if ((status & 0xF0) >> 4) != 0b1110 {
      return None;
    }

    let channel = status & 0x0F;
    // Извлекаем младшие 7 битов (LSB)
    let lo = ptr.source[1] & 0b0111_1111;
    // Извлекаем старшие 7 битов (MSB)
    let hi = ptr.source[2] & 0b0111_1111;

    // Комбинируем в 14-битное значение: (msb << 7) | lsb
    // Значение может быть отрицательным (от -8192 до 8191)
    Some((
      Event::PitchBend {
        time: EventTime {
          stamp_microseconds: ptr.stamp_microseconds,
        },
        channel: Channel::new_unsafe(channel),
        value: (lo | (hi << 7)) as i16,
      },
      RawEventSource::new(ptr.stamp_microseconds, ptr.source, ptr.from + 3),
    ))
  }

  /// Разбирает сообщение Channel Pressure (давление на канал)
  /// Статус байт: 0xDn, где n - номер канала (0-15)
  /// Формат: [status, pressure_value] - только 2 байта
  fn parse_channel_pressure<'a>(ptr: RawEventSource<'a>) -> Option<(Event, RawEventSource<'a>)> {
    if ptr.source.len() < 2 {
      // Только 2 байта для Channel Pressure
      return None;
    }

    let status: u8 = ptr.source[0];
    // Проверяем, что старшие 4 бита равны 0xD (1101 в двоичном) - Channel Pressure
    // ВНИМАНИЕ: Здесь ошибка! Указано 0b1110, а должно быть 0b1101
    if ((status & 0xF0) >> 4) != 0b1110 {
      // ОШИБКА! Должно быть 0b1101
      return None;
    }

    let channel = status & 0x0F;
    // Извлекаем значение давления (7 младших битов)
    let value = ptr.source[1] & 0b0111_1111;

    Some((
      Event::ChannelPressure {
        time: EventTime {
          stamp_microseconds: ptr.stamp_microseconds,
        },
        channel: Channel::new_unsafe(channel),
        value: value,
      },
      // Обновляем позицию на 2 байта (Channel Pressure - 2-байтовое сообщение)
      RawEventSource::new(ptr.stamp_microseconds, ptr.source, ptr.from + 2),
    ))
  }

  /// Разбирает сообщение Poly Pressure (полифоническое давление/aftertouch)
  /// Статус байт: 0xAn, где n - номер канала (0-15)
  /// Формат: [status, note, pressure_value]
  fn parse_poly_pressure<'a>(ptr: RawEventSource<'a>) -> Option<(Event, RawEventSource<'a>)> {
    if ptr.source.len() < 3 {
      return None;
    }

    let status: u8 = ptr.source[0];
    // Проверяем, что старшие 4 бита равны 0xA (1010 в двоичном) - Poly Pressure
    // ВНИМАНИЕ: Здесь тоже ошибка! Указано 0b1010, но Control Change тоже использует 0b1010
    if ((status & 0xF0) >> 4) != 0b1010 {
      // ОШИБКА! Это совпадает с Control Change
      return None;
    }

    let channel = status & 0x0F;
    // Извлекаем номер ноты (7 младших битов)
    let a = ptr.source[1] & 0b0111_1111;
    if a > Pitch::MAX.number() {
      return None;
    }

    // Извлекаем значение давления (7 младших битов)
    let b = ptr.source[2] & 0b0111_1111;

    Some((
      Event::PolyPressure {
        time: EventTime {
          stamp_microseconds: ptr.stamp_microseconds,
        },
        channel: Channel::new_unsafe(channel),
        pitch: Pitch::new_unsafe(a),
        value: b,
      },
      RawEventSource::new(ptr.stamp_microseconds, ptr.source, ptr.from + 3),
    ))
  }

  /// Разбирает сообщение Channel Mode (режим канала)
  /// Статус байт: 0xBn, где n - номер канала (0-15)
  /// Используется для управления состоянием канала (Reset All Controllers, Local Control и т.д.)
  fn parse_channel_mode<'a>(ptr: RawEventSource<'a>) -> Option<(Event, RawEventSource<'a>)> {
    if ptr.source.len() < 3 {
      return None;
    }

    let status: u8 = ptr.source[0];
    // Проверяем, что старшие 4 бита равны 0xB (1011 в двоичном) - Channel Mode
    // ВНИМАНИЕ: Здесь тоже ошибка! Это совпадает с Control Change
    if ((status & 0xF0) >> 4) != 0b1011 {
      // Это правильно для Channel Mode
      return None;
    }

    let channel = status & 0x0F;
    // Первый параметр - тип режима
    let a = ptr.source[1] & 0b0111_1111;
    // Второй параметр - значение
    let b = ptr.source[2] & 0b0111_1111;

    Some((
      Event::ChannelMode {
        time: EventTime {
          stamp_microseconds: ptr.stamp_microseconds,
        },
        channel: Channel::new_unsafe(channel),
        mode: a,
        value: b,
      },
      RawEventSource::new(ptr.stamp_microseconds, ptr.source, ptr.from + 3),
    ))
  }
}
