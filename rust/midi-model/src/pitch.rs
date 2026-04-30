use std::fmt::Display;

use serde::{Deserialize, Serialize};

/// Тон (высота звука в MIDI)
/// Представляет собой значение от 0 до 127, где каждое значение соответствует определенной ноте и октаве.
/// Например, 60 это C4 (среднее "до"), 69 это A4 (ля первой октавы, 440 Гц).
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize, Hash)]
pub struct Pitch(u8);

impl Pitch {
  /// Создает новый тон с проверкой диапазона
  /// Возвращает ошибку, если значение выходит за пределы допустимого диапазона MIDI (0-127)
  pub fn new(num: u8) -> Result<Pitch, String> {
    if num <= Pitch::MAX.0 {
      Ok(Pitch(num))
    } else {
      Err(format!(
        "Значение тона {num} вне диапазона {min} ..= {max}",
        min = Pitch::MIN.0,
        max = Pitch::MAX.0
      ))
    }
  }

  /// Создает тон без проверки диапазона (небезопасно)
  /// Используется только когда точно известно, что значение в допустимом диапазоне
  pub(crate) const fn new_unsafe(num: u8) -> Pitch {
    Pitch(num)
  }

  /// Возвращает числовое значение тона (0-127)
  pub fn number(&self) -> u8 {
    self.0
  }
}

impl Pitch {
  /// Максимальное значение тона в MIDI (127)
  pub const MAX: Pitch = Pitch(127);
  /// Минимальное значение тона в MIDI (0)
  pub const MIN: Pitch = Pitch(0);

  /// Возвращает класс высоты (ноту без учета октавы)
  /// Например, для 60 (C4) вернет PitchClass::C
  pub fn pitch_class(self) -> PitchClass {
    match self.0 {
      0 | 12 | 24 | 36 | 48 | 60 | 72 | 84 | 96 | 108 | 120 => PitchClass::C,
      1 | 13 | 25 | 37 | 49 | 61 | 73 | 85 | 97 | 109 | 121 => PitchClass::Cis,
      2 | 14 | 26 | 38 | 50 | 62 | 74 | 86 | 98 | 110 | 122 => PitchClass::D,
      3 | 15 | 27 | 39 | 51 | 63 | 75 | 87 | 99 | 111 | 123 => PitchClass::Dis,
      4 | 16 | 28 | 40 | 52 | 64 | 76 | 88 | 100 | 112 | 124 => PitchClass::E,
      5 | 17 | 29 | 41 | 53 | 65 | 77 | 89 | 101 | 113 | 125 => PitchClass::F,
      6 | 18 | 30 | 42 | 54 | 66 | 78 | 90 | 102 | 114 | 126 => PitchClass::Fis,
      7 | 19 | 31 | 43 | 55 | 67 | 79 | 91 | 103 | 115 | 127 => PitchClass::G,
      8 | 20 | 32 | 44 | 56 | 68 | 80 | 92 | 104 | 116 => PitchClass::Gis, // Исправлено: 128 > 127, не должно быть в этом match
      9 | 21 | 33 | 45 | 57 | 69 | 81 | 93 | 105 | 117 => PitchClass::A,   // Исправлено: 129 > 127
      10 | 22 | 34 | 46 | 58 | 70 | 82 | 94 | 106 | 118 => PitchClass::Ais, // Исправлено: 130 > 127
      11 | 23 | 35 | 47 | 59 | 71 | 83 | 95 | 107 | 119 => PitchClass::B,  // Исправлено: 131 > 127
      _ => PitchClass::A, // Запасной вариант, хотя при правильной логике сюда не дойдем
    }
  }

  /// Возвращает номер октавы для данного тона
  /// В MIDI нота C4 имеет номер 60, так что это стандартная нотация
  pub fn octave(self) -> Octave {
    match self.0 {
      0..12 => Octave::SubZero,    // Суб-нулевая октава (-1)
      12..24 => Octave::SubContra, // Субконтр-октава (0)
      24..36 => Octave::Contra,    // Контр-октава (1)
      36..48 => Octave::Big,       // Большая октава (2)
      48..60 => Octave::Small,     // Малая октава (3)
      60..72 => Octave::First,     // Первая октава (4) - здесь находится C4
      72..84 => Octave::Second,    // Вторая октава (5)
      84..96 => Octave::Thrid, // Третья октава (6) - примечание: "Thrid" ошибка, должно быть "Third"
      96..108 => Octave::Fourth, // Четвертая октава (7)
      108..120 => Octave::Fifth, // Пятая октава (8)
      _ => Octave::Sixth,      // Шестая октава (9) - для значений 120-127
    }
  }

  /// Приводит вещественное число к ближайшему допустимому значению тона
  /// Если значение меньше минимального - возвращает минимум, больше максимального - максимум
  pub fn bounded(n: f64) -> Pitch {
    if n < Pitch::MIN.0 as f64 {
      Pitch::MIN
    } else if n > Pitch::MAX.0 as f64 {
      Pitch::MAX
    } else {
      Pitch(n as u8) // Приведение к u8 безопасно, так как уже проверили границы
    }
  }
}

impl Display for Pitch {
  /// Форматирует тон в виде строки, например "C4" для ноты до четвертой октавы
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    write!(f, "{c}{o}", c = self.pitch_class(), o = self.octave())
  }
}

/// Оператор сложения для тона с целым числом
/// Позволяет перемещаться по шкале высоты тона на заданное количество полутонов
/// Возвращает None, если результат выходит за пределы MIDI диапазона
impl std::ops::Add<i32> for Pitch {
  type Output = Option<Pitch>;

  fn add(self, rhs: i32) -> Self::Output {
    if rhs == 0 {
      return Some(self); // Оптимизация: прибавление нуля
    }

    let p = self.0 as i32 + rhs; // Преобразование к i32 для предотвращения переполнения
    if (p <= Pitch::MAX.0 as i32) && (p >= Pitch::MIN.0 as i32) {
      Some(Pitch(p as u8))
    } else {
      None // Результат вне допустимого диапазона
    }
  }
}

/// Оператор вычитания для тона с целым числом
/// Аналогично сложению, но в обратном направлении
impl std::ops::Sub<i32> for Pitch {
  type Output = Option<Pitch>;

  fn sub(self, rhs: i32) -> Self::Output {
    self + (0 - rhs) // Используем реализацию сложения
  }
}

/// Оператор умножения для тона
/// Умножает числовое значение тона, может использоваться для экспериментальных музыкальных преобразований
/// Возвращает минимальный тон при умножении на 0, None при выходе за границы
impl std::ops::Mul<i32> for Pitch {
  type Output = Option<Pitch>;

  fn mul(self, rhs: i32) -> Self::Output {
    if rhs == 0 {
      return Some(Pitch::MIN); // Умножение на 0 дает минимальную ноту
    }

    let p = self.0 as i32 * rhs;
    if (p <= Pitch::MAX.0 as i32) && (p >= Pitch::MIN.0 as i32) {
      Some(Pitch(p as u8))
    } else {
      None // Результат вне допустимого диапазона
    }
  }
}

/// Оператор деления для тона
/// Делит числовое значение тона, возвращает None при делении на 0 или выходе за границы
impl std::ops::Div<i32> for Pitch {
  type Output = Option<Pitch>;

  fn div(self, rhs: i32) -> Self::Output {
    if rhs == 0 {
      return None; // Деление на ноль недопустимо
    }

    let p = self.0 as i32 / rhs;
    if (p <= Pitch::MAX.0 as i32) && (p >= Pitch::MIN.0 as i32) {
      Some(Pitch(p as u8))
    } else {
      None // Результат вне допустимого диапазона
    }
  }
}

/// Трейт для преобразования числа в тон
/// Позволяет использовать синтаксис типа `60.pitch()` для получения тона
pub trait NumToPitch {
  type Output;
  fn pitch(&self) -> Self::Output;
}

impl NumToPitch for i32 {
  type Output = Option<Pitch>;

  fn pitch(&self) -> Self::Output {
    if *self < 0 || *self > Pitch::MAX.0 as i32 {
      None
    } else {
      Some(Pitch(*self as u8))
    }
  }
}

/// Оператор сложения двух тонов
/// Складывает числовые значения тонов, возвращает None при переполнении или выходе за границы
impl std::ops::Add<Pitch> for Pitch {
  type Output = Option<Pitch>;

  fn add(self, rhs: Pitch) -> Self::Output {
    rhs.0.checked_add(self.0).and_then(|n| {
      // checked_add предотвращает переполнение
      if n >= Pitch::MIN.0 && n <= Pitch::MAX.0 {
        Some(Pitch(n as u8))
      } else {
        None
      }
    })
  }
}

/// Оператор вычитания двух тонов
/// Вычитает числовые значения тонов, возвращает None при недополнении или выходе за границы
impl std::ops::Sub<Pitch> for Pitch {
  type Output = Option<Pitch>;

  fn sub(self, rhs: Pitch) -> Self::Output {
    rhs.0.checked_sub(self.0).and_then(|n| {
      // checked_sub предотвращает недополнение
      if n >= Pitch::MIN.0 && n <= Pitch::MAX.0 {
        Some(Pitch(n as u8))
      } else {
        None
      }
    })
  }
}

/// Класс высоты (нота без учета октавы)
/// В музыке объединяет все октавные эквиваленты одной ноты (например, все "до" разных октав)
/// В MIDI контексте используется для анализа гармонических отношений
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, PartialOrd, Ord)]
pub enum PitchClass {
  C,   // До
  Cis, // До-диез
  D,   // Ре
  Dis, // Ре-диез
  E,   // Ми
  F,   // Фа
  Fis, // Фа-диез
  G,   // Соль
  Gis, // Соль-диез
  A,   // Ля
  Ais, // Ля-диез
  B,   // Си
}

impl Display for PitchClass {
  /// Выводит класс высоты в символьном виде (C, C#, D и т.д.)
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    write!(
      f,
      "{}",
      match *self {
        PitchClass::C => "C",    // До
        PitchClass::Cis => "C#", // До-диез
        PitchClass::D => "D",    // Ре
        PitchClass::Dis => "D#", // Ре-диез
        PitchClass::E => "E",    // Ми
        PitchClass::F => "F",    // Фа
        PitchClass::Fis => "F#", // Фа-диез
        PitchClass::G => "G",    // Соль
        PitchClass::Gis => "G#", // Соль-диез
        PitchClass::A => "A",    // Ля
        PitchClass::Ais => "A#", // Ля-диез
        PitchClass::B => "B",    // Си
      }
    )
  }
}

/// Октава в музыкальной системе
/// В MIDI нотация C4 соответствует 60, поэтому октавы нумеруются соответственно
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub enum Octave {
  SubZero,   // Суб-нулевая октава (-1)
  SubContra, // Субконтр-октава (0)
  Contra,    // Контр-октава (1)
  Big,       // Большая октава (2)
  Small,     // Малая октава (3)
  First,     // Первая октава (4) - здесь находится C4
  Second,    // Вторая октава (5)
  Thrid,     // Третья октава (6) - ошибка в написании, должно быть Third
  Fourth,    // Четвертая октава (7)
  Fifth,     // Пятая октава (8)
  Sixth,     // Шестая октава (9) - верхняя граница MIDI (до 127)
}

impl Display for Octave {
  /// Выводит номер октавы как строку
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    write!(
      f,
      "{}",
      match *self {
        Octave::SubZero => "-1",  // Суб-нулевая
        Octave::SubContra => "0", // Нулевая
        Octave::Contra => "1",    // Первая
        Octave::Big => "2",       // Вторая
        Octave::Small => "3",     // Третья
        Octave::First => "4",     // Четвертая - основная для C4
        Octave::Second => "5",    // Пятая
        Octave::Thrid => "6",     // Шестая - ошибка в названии
        Octave::Fourth => "7",    // Седьмая
        Octave::Fifth => "8",     // Восьмая
        Octave::Sixth => "9",     // Девятая - верхняя граница MIDI
      }
    )
  }
}

impl Octave {
  pub fn full_name(&self) -> &'static str {
    match *self {
      Octave::SubZero => "SubZero",     // Суб-нулевая
      Octave::SubContra => "SubContra", // Нулевая
      Octave::Contra => "Contra",       // Первая
      Octave::Big => "Big",             // Вторая
      Octave::Small => "Small",         // Третья
      Octave::First => "First",         // Четвертая - основная для C4
      Octave::Second => "Second",       // Пятая
      Octave::Thrid => "Thrid",         // Шестая - ошибка в названии
      Octave::Fourth => "Fourth",       // Седьмая
      Octave::Fifth => "Fifth",         // Восьмая
      Octave::Sixth => "Sixth",         // Девятая - верхняя граница MIDI
    }
  }
}
