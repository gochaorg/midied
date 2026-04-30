use crate::coll::Id;
use crate::music_grid::note::Note;
use crate::{coll::IdList, music_grid::GridObj};
use midi_model::{Channel, Event, EventTime, Pitch};
use std::any::Any;
use std::collections::HashMap;

pub struct IdListWriterOfMidi<L> {
  target: L,
  open_notes: HashMap<Channel, HashMap<Pitch, Id>>,
  pub time_shift: EventTime,
}

impl<L> IdListWriterOfMidi<L>
where
  L: IdList<Box<dyn GridObj>>,
{
  pub fn new(target: L) -> Self {
    Self {
      target: target,
      open_notes: Default::default(),
      time_shift: EventTime {
        stamp_microseconds: 0,
      },
    }
  }

  pub fn write(&mut self, event: Event) {
    match event {
      Event::NoteOn {
        time,
        channel,
        pitch,
        velocity,
      } => {
        let note = Note {
          time: time + self.time_shift,
          pitch,
          velocity,
          duration: EventTime::from_ms(100),
        };

        // has open ?
        match self.open_notes.get_mut(&channel) {
          None => {
            // для канала еще нет map
            let mut pitch_map: HashMap<Pitch, Id> = Default::default();
            let (id, _) = self.target.push(Box::new(note));
            pitch_map.insert(pitch, id);
            self.open_notes.insert(channel, pitch_map);
          }
          Some(pitch_map) => match pitch_map.get(&pitch) {
            None => {
              // канал есть, но pitch отсуствует
              let (id, _) = self.target.push(Box::new(note));
              pitch_map.insert(pitch, id);
            }
            Some(_obj_id) => {
              //let res = self.target.read_by_id(*obj_id, |_| 1);
            }
          },
        }
      }
      Event::NoteOff {
        time,
        channel,
        pitch,
        velocity: _,
      } => match self.open_notes.get_mut(&channel) {
        None => {}
        Some(pitch_map) => match pitch_map.get_mut(&pitch) {
          None => {}
          Some(note_id) => {
            self.target.write_by_id(*note_id, |opt| {
              if let Some((_idx, obj)) = opt {
                // update duration
                let any_ref: &mut dyn Any = obj.as_mut();
                if let Some(note) = any_ref.downcast_mut::<Note>() {
                  let t0 = note.time;
                  let t1 = time + self.time_shift;
                  if t0 < t1 {
                    note.duration = EventTime {
                      stamp_microseconds: t1.stamp_microseconds - t0.stamp_microseconds,
                    }
                  }
                  //
                }
              }
            });

            // remove ref
            pitch_map.remove(&pitch);
          }
        },
      },
      _ => {}
    }
  }
}

#[test]
fn test_write() {
  use crate::coll::IdListImpl;
  use midi_model::Velocity;
  use midi_model::pitches;
  use std::sync::{Arc, Mutex};

  let objects: IdListImpl<Box<dyn GridObj>> = IdListImpl::new();
  let objects = Arc::new(Mutex::new(objects));

  let mut writer = IdListWriterOfMidi::new(objects.clone());

  let t0 = EventTime::from_ms(0);
  let t1 = EventTime::from_ms(250);
  let ch = Channel::CHANNEL_0;
  let ptch = pitches::C_4;
  let vel = Velocity::V100;

  writer.write(Event::NoteOn {
    time: t0,
    channel: ch,
    pitch: ptch,
    velocity: vel,
  });

  writer.write(Event::NoteOff {
    time: t1,
    channel: ch,
    pitch: ptch,
    velocity: Velocity::MIN,
  });

  assert!(objects.len() == 1);

  {
    let objs = objects.lock().unwrap();
    for i in 0..objs.len() {
      if let Some((_, obj)) = objs.get_by_idx(i) {
        if let Ok(note) = Note::try_from(obj) {
          println!("{note:?}");
        }
      }
    }
  }
}
