use std::fmt::Display;

use serde::{Deserialize, Serialize};

/// midi event time
///
/// Фактически имеет значение только отнсительно другого значения
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub struct EventTime {
  pub stamp_microseconds: u64,
}

impl EventTime {
  pub fn from_micro(mcs: u64) -> Self {
    Self {
      stamp_microseconds: mcs,
    }
  }

  pub fn from_ms(ms: u64) -> Self {
    Self::from_micro(ms * 1000)
  }

  pub fn from_seconds(sec: u64) -> Self {
    Self::from_ms(sec * 1000)
  }

  pub fn to_timedelta(&self) -> chrono::TimeDelta {
    let t = chrono::TimeDelta::seconds((self.stamp_microseconds / 1_000_000) as i64);
    let t2 = chrono::TimeDelta::nanoseconds(((self.stamp_microseconds % 1_000_000) * 1_000) as i64);
    t + t2
  }

  pub fn millis_fraction(&self) -> u64 {
    (self.stamp_microseconds % 1_000_000) / 1000
  }

  pub fn second_fraction(&self) -> u64 {
    let seconds = self.stamp_microseconds / 1_000_000;
    seconds % 60
  }

  pub fn minute_fraction(&self) -> u64 {
    let seconds = self.stamp_microseconds / 1_000_000;
    let minutes = seconds / 60;
    minutes % 24
  }

  pub fn hours_total(&self) -> u64 {
    let seconds = self.stamp_microseconds / 1_000_000;
    let minutes = seconds / 60;
    minutes / 24
  }
}

impl TryFrom<chrono::TimeDelta> for EventTime {
  type Error = String;

  fn try_from(value: chrono::TimeDelta) -> Result<Self, Self::Error> {
    if value.num_milliseconds() < 0 {
      return Err("TimeDelta is negative".to_string());
    }

    Ok(EventTime {
      stamp_microseconds: (value.num_seconds() * 1_000_000) as u64 + value.subsec_micros() as u64,
    })
  }
}

impl From<EventTime> for chrono::TimeDelta {
  fn from(value: EventTime) -> Self {
    chrono::TimeDelta::microseconds(value.stamp_microseconds as i64)
  }
}

impl std::ops::Add<EventTime> for EventTime {
  type Output = EventTime;

  fn add(self, rhs: EventTime) -> Self::Output {
    Self::Output {
      stamp_microseconds: self.stamp_microseconds + rhs.stamp_microseconds,
    }
  }
}

impl std::ops::Add<chrono::TimeDelta> for EventTime {
  type Output = Option<EventTime>;

  fn add(self, rhs: chrono::TimeDelta) -> Self::Output {
    if rhs.num_milliseconds() >= 0 {
      return Some(EventTime {
        stamp_microseconds: self.stamp_microseconds
          + ((rhs.num_seconds() * 1_000_000) as u64)
          + (rhs.subsec_micros() as u64),
      });
    }

    let positive = rhs.abs();
    let positive_micro = (positive.num_seconds() * 1_000_000) as u64;
    let positive_micro = (positive.subsec_micros() as u64) + positive_micro;

    if positive_micro > self.stamp_microseconds {
      return None;
    }

    let rest_micro = self.stamp_microseconds - positive_micro;

    Some(EventTime {
      stamp_microseconds: rest_micro,
    })
  }
}

impl std::ops::Sub<chrono::TimeDelta> for EventTime {
  type Output = Option<EventTime>;

  fn sub(self, rhs: chrono::TimeDelta) -> Self::Output {
    let r = -rhs;
    self + r
  }
}

impl std::ops::Div<f64> for EventTime {
  type Output = Option<EventTime>;

  fn div(self, rhs: f64) -> Self::Output {
    if rhs <= 0.0 {
      return None;
    }

    Some(EventTime {
      stamp_microseconds: ((self.stamp_microseconds as f64 / rhs) as u64),
    })
  }
}

impl std::ops::Div<EventTime> for EventTime {
  type Output = Option<f64>;

  fn div(self, rhs: EventTime) -> Self::Output {
    if rhs.stamp_microseconds == 0 {
      return None;
    }

    Some((self.stamp_microseconds as f64) / (rhs.stamp_microseconds as f64))
  }
}

impl std::ops::Mul<f64> for EventTime {
  type Output = Option<EventTime>;

  fn mul(self, rhs: f64) -> Self::Output {
    if rhs < 0.0 {
      return None;
    }

    Some(EventTime {
      stamp_microseconds: (self.stamp_microseconds as f64 * rhs) as u64,
    })
  }
}

pub trait NumToEventTime {
  type Ms;
  fn ms(&self) -> Self::Ms;

  type Seconds;
  fn seconds(&self) -> Self::Seconds;
}

impl NumToEventTime for i32 {
  type Ms = Option<EventTime>;

  fn ms(&self) -> Self::Ms {
    if (*self) < 0 {
      return None;
    }

    Some(EventTime {
      stamp_microseconds: ((*self) * 1_000) as u64,
    })
  }

  type Seconds = Option<EventTime>;

  fn seconds(&self) -> Self::Seconds {
    if (*self) < 0 {
      return None;
    }

    Some(EventTime {
      stamp_microseconds: ((*self) * 1_000_000) as u64,
    })
  }
}

impl NumToEventTime for u64 {
  type Ms = EventTime;

  fn ms(&self) -> Self::Ms {
    EventTime {
      stamp_microseconds: ((*self) * 1_000) as u64,
    }
  }

  type Seconds = EventTime;

  fn seconds(&self) -> Self::Seconds {
    EventTime {
      stamp_microseconds: ((*self) * 1_000_000) as u64,
    }
  }
}

impl Display for EventTime {
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    let h = self.hours_total();
    let h_str = format!("{h}");

    let m = self.minute_fraction();
    let m_str = if m < 10 {
      format!("0{m}")
    } else {
      format!("{m}")
    };

    let s = self.second_fraction();
    let s_str = if s < 10 {
      format!("0{s}")
    } else {
      format!("{s}")
    };

    let ms = self.millis_fraction();
    let ms_str = if ms < 10 {
      format!("00{ms}")
    } else if ms < 100 {
      format!("0{ms}")
    } else {
      format!("{ms}")
    };

    let mut str = String::new();
    str.push_str(&format!(".{ms_str}"));
    str.insert_str(0, &format!("{s_str}"));

    if h > 0 || m > 0 {
      str.insert_str(0, &format!("{m_str}:"));
    }

    if h > 0 {
      str.insert_str(0, &format!("{h_str}:"));
    }

    write!(f, "{}", str)
  }
}

impl EventTime {
  pub fn tuncate_ms(&self) -> Self {
    Self {
      stamp_microseconds: self.stamp_microseconds - (self.stamp_microseconds % 1000),
    }
  }

  pub fn tuncate_second(&self) -> Self {
    Self {
      stamp_microseconds: self.stamp_microseconds - (self.stamp_microseconds % 1_000_000),
    }
  }

  pub fn tuncate_minute(&self) -> Self {
    Self {
      stamp_microseconds: self.stamp_microseconds - (self.stamp_microseconds % (1_000_000 * 60)),
    }
  }

  pub fn tuncate_hour(&self) -> Self {
    Self {
      stamp_microseconds: self.stamp_microseconds
        - (self.stamp_microseconds % (1_000_000 * 60 * 60)),
    }
  }
}

#[test]
fn test_trunck() {
  let t = 1234u64.ms();
  println!("{t}");

  let t = t.tuncate_second();
  println!("{t}");
}

// impl FromStr for EventTime {
//   type Err = String;

//   fn from_str(src: &str) -> Result<Self, Self::Err> {
//     src.
//     todo!()
//   }
// }
