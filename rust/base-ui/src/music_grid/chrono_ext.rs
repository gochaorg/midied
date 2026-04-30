use chrono::TimeDelta;
use lazy_regex::regex;

pub trait ChronoTimeDeltaExt {
  fn truncate_ms(&self) -> Self;
  fn truncate_second(&self) -> Self;
  fn to_human_string(&self) -> String;

  type ParseOut;
  fn parse_human_string(src: &str) -> Self::ParseOut;
}

impl ChronoTimeDeltaExt for TimeDelta {
  fn truncate_ms(&self) -> Self {
    let nano2ms = self.subsec_nanos() % 1_000_000;
    *self - TimeDelta::milliseconds(nano2ms as i64)
  }

  fn truncate_second(&self) -> Self {
    //let nano2ms = self.subsec_nanos() % 1_000_000_000;
    *self - TimeDelta::nanoseconds(self.subsec_nanos() as i64)
  }

  fn to_human_string(&self) -> String {
    if self.is_zero() {
      format!("0")
    } else {
      let (_, sign) = if self.num_milliseconds() != 0 {
        if self.num_milliseconds() > 0 {
          (self.clone(), "")
        } else {
          (self.abs(), "-")
        }
      } else {
        if let Some(nano) = self.num_nanoseconds() {
          if nano > 0 {
            (self.clone(), "")
          } else {
            (self.abs(), "-")
          }
        } else {
          (self.abs(), "")
        }
      };

      let mut str = sign.to_string();

      let hours_total = self.num_hours().abs();
      let seconds_total = self.num_seconds().abs();
      let minutes_fraction = (seconds_total / 60 % 24).abs();
      let second_fraction = (seconds_total % 60).abs();
      let millisecond = self.subsec_millis().abs();
      let nano_fraction = (self.subsec_nanos() % 1_000_000).abs();

      if hours_total > 0 {
        str.push_str(&format!("{}:", hours_total));
      }

      let pad = |str: String, len: u8| {
        let mut str = str;
        if len as usize > str.len() {
          let diff = len - str.len() as u8;
          for _ in 0..diff {
            str.insert_str(0, "0");
          }
        }

        str
      };

      if minutes_fraction > 0 {
        if minutes_fraction > 9 {
          str.push_str(&format!("{minutes_fraction}:"));
        } else {
          str.push_str(&format!("0{minutes_fraction}:"));
        }
      } else {
        if hours_total > 0 {
          str.push_str("00:");
        }
      }

      if second_fraction > 9 {
        str.push_str(&format!("{second_fraction}"));
      } else {
        str.push_str(&format!("0{second_fraction}"));
      }

      ///////////

      if millisecond > 0 && nano_fraction > 0 {
        str.push_str(".");
        str.push_str(&pad(format!("{millisecond}"), 3));

        let nano = pad(format!("{nano_fraction}"), 6);
        str.push_str(nano.trim_end_matches('0'));
      } else if millisecond > 0 {
        str.push_str(".");
        str.push_str(&pad(format!("{millisecond}"), 3));
      } else if nano_fraction > 0 {
        str.push_str(".");
        str.push_str(&format!(".000"));

        let nano = pad(format!("{nano_fraction}"), 6);
        str.push_str(nano.trim_end_matches('0'));
      }

      str
    }
  }

  type ParseOut = Result<TimeDelta, String>;

  fn parse_human_string(src: &str) -> Self::ParseOut {
    let parse_nano = |s: &str| match s.len() {
      0 => Ok(0),
      1 => s
        .parse::<i32>()
        .map_err(|e| e.to_string())
        .map(|n| n * 100_000_000),
      2 => s
        .parse::<i32>()
        .map_err(|e| e.to_string())
        .map(|n| n * 10_000_000),
      3 => s
        .parse::<i32>()
        .map_err(|e| e.to_string())
        .map(|n| n * 1_000_000),
      4 => s
        .parse::<i32>()
        .map_err(|e| e.to_string())
        .map(|n| n * 100_000),
      5 => s
        .parse::<i32>()
        .map_err(|e| e.to_string())
        .map(|n| n * 10_000),
      6 => s
        .parse::<i32>()
        .map_err(|e| e.to_string())
        .map(|n| n * 1_000),
      7 => s.parse::<i32>().map_err(|e| e.to_string()).map(|n| n * 100),
      8 => s.parse::<i32>().map_err(|e| e.to_string()).map(|n| n * 10),
      9 => s.parse::<i32>().map_err(|e| e.to_string()),
      _ => Err("".to_string()),
    };

    let sum = |sign: i32, hours: i64, minutes: i64, seconds: i64, nano: i64| {
      let t = TimeDelta::hours(hours)
        + TimeDelta::minutes(minutes)
        + TimeDelta::seconds(seconds)
        + TimeDelta::nanoseconds(nano);
      if sign >= 0 { t } else { -t }
    };

    if let Some(caps) =
      regex!(r"(?<sign>\-)?(?<h>\d+):(?<m>\d{1,2}):(?<s>\d{1,2})\.(?<n>\d+)").captures(src)
    {
      let sign = match caps.name("sign") {
        None => 1,
        Some(_) => -1,
      };

      let hours = caps["h"].parse::<i64>().ok().unwrap_or(0);
      let minutes = caps["m"].parse::<i64>().ok().unwrap_or(0);
      let seconds = caps["s"].parse::<i64>().ok().unwrap_or(0);
      let nano = parse_nano(&caps["n"]).unwrap_or_default() as i64;

      return Ok(sum(sign, hours, minutes, seconds, nano));
    }

    if let Some(caps) = regex!(r"(?<sign>\-)?(?<m>\d{1,2}):(?<s>\d{1,2})\.(?<n>\d+)").captures(src)
    {
      let sign = match caps.name("sign") {
        None => 1,
        Some(_) => -1,
      };

      let hours = 0i64;
      let minutes = caps["m"].parse::<i64>().ok().unwrap_or(0);
      let seconds = caps["s"].parse::<i64>().ok().unwrap_or(0);
      let nano = parse_nano(&caps["n"]).unwrap_or_default() as i64;

      return Ok(sum(sign, hours, minutes, seconds, nano));
    }

    if let Some(caps) = regex!(r"(?<sign>\-)?(?<s>\d{1,2})\.(?<n>\d+)").captures(src) {
      let sign = match caps.name("sign") {
        None => 1,
        Some(_) => -1,
      };

      let hours = 0i64;
      let minutes = 0i64;
      let seconds = caps["s"].parse::<i64>().ok().unwrap_or(0);
      let nano = parse_nano(&caps["n"]).unwrap_or_default() as i64;

      return Ok(sum(sign, hours, minutes, seconds, nano));
    }

    if let Some(caps) = regex!(r"(?<sign>\-)?(?<s>\d+)\.(?<n>\d+)").captures(src) {
      let sign = match caps.name("sign") {
        None => 1,
        Some(_) => -1,
      };

      let hours = 0i64;
      let minutes = 0i64;
      let seconds = caps["s"].parse::<i64>().ok().unwrap_or(0);
      let nano = parse_nano(&caps["n"]).unwrap_or_default() as i64;

      return Ok(sum(sign, hours, minutes, seconds, nano));
    }

    if let Some(caps) = regex!(r"^(?<sign>\-)?(?<s>[0123456789]+)").captures(src) {
      let sign = match caps.name("sign") {
        None => 1,
        Some(_) => -1,
      };

      let hours = 0i64;
      let minutes = 0i64;
      let seconds = caps["s"].parse::<i64>().ok().unwrap_or(0);
      let nano = 0i64;

      return Ok(sum(sign, hours, minutes, seconds, nano));
    };

    if let Some(caps) = regex!(r"(?<sign>-)?\.(?<n>\d+)").captures(src) {
      let sign = match caps.name("sign") {
        None => 1,
        Some(_) => -1,
      };

      let hours = 0i64;
      let minutes = 0i64;
      let seconds = 0i64;
      let nano = parse_nano(&caps["n"]).unwrap_or_default() as i64;

      return Ok(sum(sign, hours, minutes, seconds, nano));
    }

    Err("format error".to_string())
  }
}

#[test]
fn chrono_str() {
  let t = TimeDelta::minutes(20)
    + TimeDelta::seconds(10)
    + TimeDelta::milliseconds(023)
    + TimeDelta::microseconds(34);
  println!("{}", t.to_human_string());

  let result = TimeDelta::parse_human_string("20:10.023034");
  println!("{result:?}");

  assert!(result.is_ok());
  let result = result.unwrap();
  assert!(result.num_seconds() % 60 == 10);
  assert!(result.num_seconds() / 60 == 20);
  assert!(result.subsec_nanos() == 23034000);

  let result = TimeDelta::parse_human_string("10.023");
  println!("{result:?}");

  assert!(result.is_ok());
  let result = result.unwrap();
  assert!(result.num_seconds() > 0);
  assert!((result.num_seconds() % 60).abs() == 10);
  assert!(result.subsec_millis() == 23);

  let result = TimeDelta::parse_human_string("-10.023");
  println!("{result:?}");

  assert!(result.is_ok());
  let result = result.unwrap();

  println!("{s}", s = result.num_seconds());
  assert!(result.num_seconds() < 0);
  assert!((result.num_seconds() % 60).abs() == 10);

  println!("{ms}", ms = result.subsec_millis());
  assert!(result.subsec_millis().abs() == 23);

  let result = TimeDelta::parse_human_string(".023");
  println!("{result:?}");
  assert!(result.is_ok());

  let result = result.unwrap();
  println!(
    "{s} {ms}",
    s = result.num_seconds(),
    ms = result.subsec_millis()
  );
  assert!(result.subsec_millis() == 23);
}
