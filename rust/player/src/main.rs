use rustysynth::*;
use std::{fs::File, sync::Arc};

use cpal::{
  FromSample, Sample, SampleFormat, SizedSample,
  traits::{DeviceTrait, HostTrait, StreamTrait},
};
use rustysynth::*;
use std::sync::Mutex;

const BANK_0: &str = "/usr/share/sounds/sf2/TimGM6mb.sf2";
const BANK_1: &str = "/usr/share/sounds/sf2/FluidR3_GM.sf2";

fn main() -> Result<(), Box<dyn std::error::Error>> {
  // let mut sf2 = File::open(BANK_0).unwrap();
  // let sound_font = Arc::new(SoundFont::new(&mut sf2).unwrap());

  // // Create the synthesizer.
  // let settings = SynthesizerSettings::new(44100);
  // let mut synthesizer = Synthesizer::new(&sound_font, &settings).unwrap();

  // // Play some notes (middle C, E, G).
  // synthesizer.note_on(0, 60, 100);
  // synthesizer.note_on(0, 64, 100);
  // synthesizer.note_on(0, 67, 100);

  // // The output buffer (3 seconds).
  // let sample_count = (3 * settings.sample_rate) as usize;
  // let mut left: Vec<f32> = vec![0_f32; sample_count];
  // let mut right: Vec<f32> = vec![0_f32; sample_count];

  // === Инициализация rustysynth ===
  let mut sf2 = File::open(BANK_0)?;
  let sound_font = Arc::new(SoundFont::new(&mut sf2)?);
  let settings = SynthesizerSettings::new(44100);
  let synthesizer = Arc::new(Mutex::new(Synthesizer::new(&sound_font, &settings)?));

  // === Настройка cpal ===
  let host = cpal::default_host();
  let device = host
    .default_output_device()
    .ok_or("Нет устройства вывода")?;

  let config = device.default_output_config()?;
  let sample_rate = config.sample_rate();
  let channels = config.channels() as usize;

  println!(
    "Устройство: {:?}, Sample rate: {}, Каналы: {}",
    device.name()?,
    sample_rate,
    channels
  );

  // === Создаём поток ===
  let stream = match config.sample_format() {
    SampleFormat::F32 => build_stream::<f32>(&device, config.into(), synthesizer.clone()),
    SampleFormat::I16 => build_stream::<i16>(&device, config.into(), synthesizer.clone()),
    SampleFormat::I32 => build_stream::<i32>(&device, config.into(), synthesizer.clone()),
    SampleFormat::U16 => build_stream::<u16>(&device, config.into(), synthesizer.clone()),
    fmt => return Err(format!("Неподдерживаемый формат: {:?}", fmt).into()),
  }?;

  // === Запускаем воспроизведение ===
  stream.play()?;

  // === Играем аккорд ===
  {
    let mut synth = synthesizer.lock().unwrap();
    synth.note_on(0, 60, 100); // C4
    synth.note_on(0, 64, 100); // E4
    synth.note_on(0, 67, 100); // G4
  }

  // Держим программу запущенной
  println!("Воспроизведение... Нажмите Enter для остановки");
  let mut input = String::new();
  std::io::stdin().read_line(&mut input)?;

  // Останавливаем и завершаем
  stream.pause()?;
  drop(stream);

  Ok(())
}

fn build_stream<T>(
  device: &cpal::Device,
  config: cpal::StreamConfig,
  synthesizer: Arc<Mutex<Synthesizer>>,
) -> Result<cpal::Stream, cpal::BuildStreamError>
where
  T: SizedSample + FromSample<f32>,
{
  let channels = config.channels as usize;

  device.build_output_stream(
    &config,
    move |output: &mut [T], _: &cpal::OutputCallbackInfo| {
      // Заполняем буфер через rustysynth
      let mut synth = synthesizer.lock().unwrap();

      // Временные буферы для left/right каналов
      let frames = output.len() / channels;
      let mut left = vec![0f32; frames];
      let mut right = vec![0f32; frames];

      // Рендерим аудио
      synth.render(&mut left, &mut right);

      // Интерливируем и конвертируем в целевой тип
      for (i, frame) in output.chunks_mut(channels).enumerate() {
        let l = T::from_sample(left[i]);
        let r = T::from_sample(right[i]);

        frame[0] = l;
        if channels > 1 {
          frame[1] = r;
        }
        // Для >2 каналов можно дублировать или расширить логику
      }
    },
    |err| eprintln!("Ошибка в аудио-потоке: {}", err),
    None, // blocking callback
  )
}
