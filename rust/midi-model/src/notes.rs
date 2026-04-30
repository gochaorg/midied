use serde::{Deserialize, Serialize};

use crate::{events::*, EventTime, Pitch};

/// Нажатие и отпускание
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub struct FullClick {
  /// Канал
  pub channel: Channel,

  /// Тон
  pub pitch: Pitch,

  /// Время нажатия
  pub begin_time: EventTime,

  /// Громкость в начале
  pub begin_velocity: Velocity,

  /// Время отпускания
  pub end_time: EventTime,

  /// Громкость в конце
  pub end_velocity: Velocity,
}

/// Только нажатие без отпускания
#[allow(dead_code)]
pub struct HalfClick {
  /// Канал
  channel: Channel,

  /// Тон
  pitch: Pitch,

  /// Время нажатия
  begin_time: EventTime,

  /// Громкость в начале
  begin_velocity: Velocity,
}

impl FullClick {
  pub fn try_time_shift_micro(self, micro_sec: i64) -> Option<FullClick> {
    if (self.begin_time.stamp_microseconds as i128) < (micro_sec as i128) && micro_sec < 0 {
      return None;
    }

    Some(FullClick {
      begin_time: EventTime {
        stamp_microseconds: ((self.begin_time.stamp_microseconds as i128) + (micro_sec as i128))
          as u64,
      },
      end_time: EventTime {
        stamp_microseconds: ((self.end_time.stamp_microseconds as i128) + (micro_sec as i128))
          as u64,
      },
      ..self
    })
  }

  pub fn try_time_shift_ms(self, ms: i64) -> Option<FullClick> {
    Self::try_time_shift_micro(self, ms * 1000)
  }
}
