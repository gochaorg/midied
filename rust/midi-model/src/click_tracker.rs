use std::collections::BTreeMap;

use crate::events::*;
use crate::notes::*;
use crate::{EventTime, Pitch};

/// Принимает события NoteOn / NoteOff и агренирует их в клики
#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct ClickTracker {
  pub note_on: BTreeMap<Channel, BTreeMap<Pitch, BTreeMap<EventTime, Velocity>>>,
  pub note_off: BTreeMap<Channel, BTreeMap<Pitch, BTreeMap<EventTime, Velocity>>>,
  pub full_clicks: Vec<FullClick>,
}

impl Default for ClickTracker {
  fn default() -> Self {
    Self {
      note_on: Default::default(),
      note_off: Default::default(),
      full_clicks: Default::default(),
    }
  }
}

#[allow(dead_code)]
impl ClickTracker {
  pub fn collect(&mut self, ev: &Event) {
    match ev {
      Event::NoteOn {
        time,
        channel,
        pitch,
        velocity,
      } => self.note_on(time, channel, pitch, velocity),
      Event::NoteOff {
        time,
        channel,
        pitch,
        velocity,
      } => self.note_off(time, channel, pitch, velocity),
      _ => (),
    }
  }

  fn note_on(&mut self, time: &EventTime, channel: &Channel, pitch: &Pitch, velocity: &Velocity) {
    self
      .note_on
      .entry(*channel)
      .or_insert_with(|| Default::default())
      .entry(*pitch)
      .or_insert_with(|| Default::default())
      .insert(*time, *velocity);

    self.process_intrevals();
  }

  fn note_off(&mut self, time: &EventTime, channel: &Channel, pitch: &Pitch, velocity: &Velocity) {
    self
      .note_off
      .entry(*channel)
      .or_insert_with(|| Default::default())
      .entry(*pitch)
      .or_insert_with(|| Default::default())
      .insert(*time, *velocity);

    self.process_intrevals();
  }

  fn process_intrevals(&mut self) {
    for (channel, pitch_map) in self.note_on.iter_mut() {
      for (pitch, times_on) in pitch_map.iter_mut() {
        let mut found_full_click: Option<FullClick> = None;
        loop {
          for (time_on, velocity_on) in times_on.iter_mut() {
            match self.note_off.get_mut(&channel) {
              None => {}
              Some(pitch_map) => {
                match pitch_map.get_mut(pitch) {
                  None => {}
                  Some(times_off) => {
                    found_full_click = None;
                    for (time_off, velocity_off) in times_off.iter() {
                      if time_off >= time_on {
                        found_full_click = Some(FullClick {
                          channel: *channel,
                          pitch: *pitch,
                          begin_time: *time_on,
                          begin_velocity: *velocity_on,
                          end_time: *time_off,
                          end_velocity: *velocity_off,
                        });
                        self.full_clicks.push(found_full_click.clone().unwrap());
                        break;
                      }
                    }
                    //////////////
                    match found_full_click {
                      None => {}
                      Some(found) => {
                        times_off.remove(&found.end_time);
                        continue;
                      }
                    }
                  }
                }
              }
            }
          }
          match found_full_click {
            None => {
              break;
            }
            Some(found) => {
              times_on.remove(&found.begin_time);
              found_full_click = None;
              continue;
            }
          }
        }
      }
    }

    Self::remove_empty_maps(&mut self.note_on);
    Self::remove_empty_maps(&mut self.note_off);
  }

  fn remove_empty_maps(
    map_map: &mut BTreeMap<Channel, BTreeMap<Pitch, BTreeMap<EventTime, Velocity>>>,
  ) {
    for (_ch, p_map) in map_map.iter_mut() {
      let remove_set: Vec<Pitch> = p_map
        .iter()
        .flat_map(|(k, v)| if !v.is_empty() { None } else { Some(*k) })
        .collect();

      for p in remove_set {
        p_map.remove(&p);
      }
    }

    let remove_set: Vec<Channel> = map_map
      .iter()
      .flat_map(|(k, v)| if !v.is_empty() { None } else { Some(*k) })
      .collect();

    for k in remove_set {
      map_map.remove(&k);
    }
  }
}

#[test]
fn tracker_test() {
  // use std::fs;
  use crate::events_json::JsonEvents;

  let sample_json = include_str!("samples/song1.midisongmt1b");
  let events: JsonEvents = serde_json::from_str(sample_json).unwrap();
  println!("events {}", events.events.len());

  let mut tracker = ClickTracker::default();
  for event in &events.events {
    tracker.collect(event);
  }

  println!("full clicks {fc}", fc = tracker.full_clicks.len());

  //println!("---------------");
  //fs::write("tracker_test.1.json", serde_json::to_string_pretty(&events.clone()).unwrap()).unwrap();
  //fs::write("tracker_test.2.json", serde_json::to_string_pretty(&tracker.full_clicks.clone()).unwrap()).unwrap();

  // println!("---------------");
  // println!("clicks:\n{}", );
}
