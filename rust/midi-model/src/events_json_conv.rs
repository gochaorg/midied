use crate::events::*;
use crate::events_json::*;
use crate::EventTime;

use chrono::{DateTime, Utc};

#[allow(dead_code)]
pub(crate) struct RestoreEventsTime<'a, E> {
  pub collection: &'a [E],
  pub min_timestamp: Option<u64>,
  pub min_time: Option<DateTime<Utc>>,
}

pub(crate) trait RestoreEventTimeVec<E> {
  fn restore_events_time<'a>(&'a self) -> RestoreEventsTime<'a, E>;
}

impl<A: JsonEventTime> RestoreEventTimeVec<A> for Vec<A> {
  fn restore_events_time<'a>(&'a self) -> RestoreEventsTime<'a, A> {
    let min_timestamp = self
      .iter()
      .min_by_key(|e| e.timestamp())
      .map(|e| e.timestamp());

    let min_time = self.iter().min_by_key(|e| e.time()).map(|e| e.time());

    RestoreEventsTime {
      collection: self,
      min_timestamp: min_timestamp,
      min_time: min_time,
    }
  }
}

impl<'a> RestoreEventsTime<'a, JsonMidiEvent> {
  fn extract_event_time(&self, a: &JsonMidiEvent) -> EventTime {
    let dur_microsec = match self.min_time {
      Some(min_time) => min_time
        .signed_duration_since(a.time())
        .num_microseconds()
        .unwrap_or(0i64)
        .abs() as u64,
      None => 0u64,
    };

    // let dur_microsec = match self.min_timestamp {
    //     Some(min_timestamp) => {
    //         let min_t = min_timestamp.min(a.timestamp());
    //         let max_t = min_timestamp.max(a.timestamp());
    //         (max_t - min_t) as u64
    //     }
    //     None => 0u64,
    // };

    EventTime {
      stamp_microseconds: dur_microsec,
    }
  }

  pub fn as_events(&self) -> Vec<Event> {
    self
      .collection
      .iter()
      .flat_map(|je| {
        let time = self.extract_event_time(je);
        match je {
          JsonMidiEvent::JsonNoteOn(note) => Some((time, note).into()),
          JsonMidiEvent::JsonNoteOff(note) => Some((time, note).into()),
          JsonMidiEvent::JsonControlChange(note) => Some((time, note).into()),
          JsonMidiEvent::JsonProgramChange(note) => Some((time, note).into()),
          JsonMidiEvent::JsonChannelPressure(note) => Some((time, note).into()),
          JsonMidiEvent::JsonPolyPressure(note) => Some((time, note).into()),
          JsonMidiEvent::JsonPitchBend1(note) => Some((time, note).into()),
          JsonMidiEvent::JsonPitchBend2(note) => Some((time, note).into()),
          JsonMidiEvent::JsonChannelMode1(note) => Some((time, note).into()),
          JsonMidiEvent::JsonChannelMode2(note) => Some((time, note).into()),
        }
      })
      .collect()
  }
}
