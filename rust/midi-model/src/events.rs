use serde::{Deserialize, Serialize};

use crate::{EventTime, Pitch};

/// midi channel
/// всего 16 каналов, 0..=15
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize, Hash)]
pub struct Channel(u8);

impl Channel {
  pub const CHANNEL_0: Channel = Channel(0);
  pub const CHANNEL_1: Channel = Channel(1);
  pub const CHANNEL_2: Channel = Channel(2);
  pub const CHANNEL_3: Channel = Channel(3);
  pub const CHANNEL_4: Channel = Channel(4);
  pub const CHANNEL_5: Channel = Channel(5);
  pub const CHANNEL_6: Channel = Channel(6);
  pub const CHANNEL_7: Channel = Channel(7);
  pub const CHANNEL_8: Channel = Channel(8);
  pub const CHANNEL_9: Channel = Channel(9);
  pub const CHANNEL_10: Channel = Channel(10);
  pub const CHANNEL_11: Channel = Channel(11);
  pub const CHANNEL_12: Channel = Channel(12);
  pub const CHANNEL_13: Channel = Channel(13);
  pub const CHANNEL_14: Channel = Channel(14);
  pub const CHANNEL_15: Channel = Channel(15);

  pub fn new(no: u8) -> Result<Self, String> {
    if no > 15 {
      Err(format!("channel number {no} out of range 0..=15"))
    } else {
      Ok(Channel(no))
    }
  }

  pub(crate) fn new_unsafe(no: u8) -> Self {
    Channel(no)
  }

  pub fn number(&self) -> u8 {
    self.0
  }
}

impl TryFrom<u8> for Channel {
  type Error = String;

  fn try_from(value: u8) -> Result<Self, Self::Error> {
    if value < 16 {
      Ok(Channel(value))
    } else {
      Err("Invalid channel number, expected 0-15".to_string())
    }
  }
}

/// Музыкальный инструмент
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize, Hash)]
pub struct Instrument(pub u8);

/// Громкость 0..=127
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize, Hash)]
pub struct Velocity(u8);

impl Velocity {
  pub const MAX: Velocity = Velocity(127);
  pub const MIN: Velocity = Velocity(0);
  pub const V120: Velocity = Velocity(120);
  pub const V100: Velocity = Velocity(100);
  pub const V80: Velocity = Velocity(80);
  pub const V60: Velocity = Velocity(60);
}

impl Velocity {
  pub fn new(num: u8) -> Result<Self, String> {
    if num <= 127 {
      Ok(Self(num))
    } else {
      Err(format!("Velocity number {num} out of range 0..=127"))
    }
  }

  pub(crate) fn new_unsafe(num: u8) -> Self {
    Self(num)
  }

  pub fn number(&self) -> u8 {
    self.0
  }
}

/// События midi
#[derive(Debug, Clone, Copy)]
pub enum Event {
  /// События нажатия клавиши
  NoteOn {
    time: EventTime,
    channel: Channel,
    pitch: Pitch,
    velocity: Velocity,
  },

  /// События отжатия клавиши
  NoteOff {
    time: EventTime,
    channel: Channel,
    pitch: Pitch,
    velocity: Velocity,
  },

  /// Изменение параметров (например, громкости, панорамы).
  ///
  /// Управляет параметрами синтезатора или эффектов на канале n. controller_number (0–127)
  /// определяет тип контроллера (например, громкость, панорама, модуляция), а value (0–127) — его значение.
  ///
  /// Популярные контроллеры:
  ///
  ///     1: Modulation Depth
  ///     7: Channel Volume
  ///     10: Pan
  ///     64: Sustain Pedal (0 = выкл, 127 = вкл).
  ///
  ControlChange {
    time: EventTime,
    channel: Channel,
    control: u8,
    value: u8,
  },

  /// Смена инструмента
  ProgramChange {
    time: EventTime,
    channel: Channel,
    program: Instrument,
  },

  /// Эта команда используется для изменения высоты тона всех нот на канале n
  /// (0–15, соответствует каналам 1–16) с помощью колеса модуляции (pitch wheel).
  /// Значение высоты тона представлено 14-битным числом, где:
  ///
  ///     lllllll — младшие 7 бит (LSB, least significant bits).
  ///     mmmmmmm — старшие 7 бит (MSB, most significant bits).
  ///     Диапазон значений: 0 (0x0000) — максимальное понижение, 8192 (0x2000) — нейтральное положение (без изменения высоты), 16383 (0x3FFF) — максимальное повышение.
  ///     Чувствительность (диапазон изменения высоты, например, ±2 полутона) зависит от настроек передатчика (синтезатора или контроллера).
  ///
  /// Пример: 0xE0, 0, 64 (эквивалент 8192) — нейтральное положение колеса модуляции на канале 1.
  ///
  /// Применение: Используется для плавного изменения высоты звука, например, для эффекта вибрато или глиссандо.
  PitchBend {
    time: EventTime,
    channel: Channel,
    value: i16,
  },

  /// Передаёт общее давление, приложенное ко всем нотам на канале n.
  /// pressure (0–127) влияет на параметры, такие как громкость или тембр.
  /// В отличие от Polyphonic Key Pressure, применяется ко всему каналу.
  ChannelPressure {
    time: EventTime,
    channel: Channel,
    value: u8,
  },

  /// Передаёт давление, приложенное к отдельной ноте (note_number) после её нажатия на канале n.
  /// pressure (0–127) указывает величину давления.
  /// Используется для модуляции звука (например, вибрато).
  PolyPressure {
    time: EventTime,
    channel: Channel,
    pitch: Pitch,
    value: u8,
  },

  /// Эти сообщения имеют тот же формат, что и Control Change (0xBn, controller_number, value),
  /// но используют зарезервированные номера контроллеров (120–127) для управления режимами работы MIDI-устройства на канале n.
  /// Они определяют, как устройство реагирует на MIDI-данные или локальные действия (например, нажатия клавиш).
  ChannelMode {
    time: EventTime,
    channel: Channel,
    mode: u8,
    value: u8,
  },
}

impl Event {
  pub fn time(&self) -> EventTime {
    match self {
      Event::NoteOn {
        time,
        channel: _,
        pitch: _,
        velocity: _,
      } => *time,
      Event::NoteOff {
        time,
        channel: _,
        pitch: _,
        velocity: _,
      } => *time,
      Event::ControlChange {
        time,
        channel: _,
        control: _,
        value: _,
      } => *time,
      Event::ProgramChange {
        time,
        channel: _,
        program: _,
      } => *time,
      Event::PitchBend {
        time,
        channel: _,
        value: _,
      } => *time,
      Event::ChannelPressure {
        time,
        channel: _,
        value: _,
      } => *time,
      Event::PolyPressure {
        time,
        channel: _,
        pitch: _,
        value: _,
      } => *time,
      Event::ChannelMode {
        time,
        channel: _,
        mode: _,
        value: _,
      } => *time,
    }
  }

  pub fn with_time(self, t: EventTime) -> Event {
    match self {
      Event::NoteOn {
        time: _,
        channel,
        pitch,
        velocity,
      } => Event::NoteOn {
        time: t,
        channel: channel,
        pitch: pitch,
        velocity: velocity,
      },
      Event::NoteOff {
        time: _,
        channel,
        pitch,
        velocity,
      } => Event::NoteOff {
        time: t,
        channel: channel,
        pitch: pitch,
        velocity: velocity,
      },
      Event::ChannelMode {
        time: _,
        channel,
        mode,
        value,
      } => Event::ChannelMode {
        time: t,
        channel: channel,
        mode: mode,
        value: value,
      },
      Event::ChannelPressure {
        time: _,
        channel,
        value,
      } => Event::ChannelPressure {
        time: t,
        channel: channel,
        value: value,
      },
      Event::PolyPressure {
        time: _,
        channel,
        pitch,
        value,
      } => Event::PolyPressure {
        time: t,
        channel: channel,
        pitch: pitch,
        value: value,
      },
      Event::ControlChange {
        time: _,
        channel,
        control,
        value,
      } => Event::ControlChange {
        time: t,
        channel: channel,
        control: control,
        value: value,
      },
      Event::ProgramChange {
        time: _,
        channel,
        program,
      } => Event::ProgramChange {
        time: t,
        channel: channel,
        program: program,
      },
      Event::PitchBend {
        time: _,
        channel,
        value,
      } => Event::PitchBend {
        time: t,
        channel: channel,
        value: value,
      },
    }
  }
}
