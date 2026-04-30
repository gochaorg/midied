#[allow(unused, non_snake_case)]
pub mod pitches {
  use crate::Pitch;

  // Octave -1 (subzero): MIDI notes 0-11
  pub const C_SUBZERO: Pitch = Pitch::new_unsafe(0);
  pub const CIS_SUBZERO: Pitch = Pitch::new_unsafe(1);
  pub const D_SUBZERO: Pitch = Pitch::new_unsafe(2);
  pub const DIS_SUBZERO: Pitch = Pitch::new_unsafe(3);
  pub const E_SUBZERO: Pitch = Pitch::new_unsafe(4);
  pub const F_SUBZERO: Pitch = Pitch::new_unsafe(5);
  pub const FIS_SUBZERO: Pitch = Pitch::new_unsafe(6);
  pub const G_SUBZERO: Pitch = Pitch::new_unsafe(7);
  pub const GIS_SUBZERO: Pitch = Pitch::new_unsafe(8);
  pub const A_SUBZERO: Pitch = Pitch::new_unsafe(9);
  pub const AIS_SUBZERO: Pitch = Pitch::new_unsafe(10);
  pub const B_SUBZERO: Pitch = Pitch::new_unsafe(11);

  // Octave 0: MIDI notes 12-23
  // Субконтроктава
  pub const C_0: Pitch = Pitch::new_unsafe(12);
  pub const CIS_0: Pitch = Pitch::new_unsafe(13);
  pub const D_0: Pitch = Pitch::new_unsafe(14);
  pub const DIS_0: Pitch = Pitch::new_unsafe(15);
  pub const E_0: Pitch = Pitch::new_unsafe(16);
  pub const F_0: Pitch = Pitch::new_unsafe(17);
  pub const FIS_0: Pitch = Pitch::new_unsafe(18);
  pub const G_0: Pitch = Pitch::new_unsafe(19);
  pub const GIS_0: Pitch = Pitch::new_unsafe(20);
  pub const A_0: Pitch = Pitch::new_unsafe(21);
  pub const AIS_0: Pitch = Pitch::new_unsafe(22);
  pub const B_0: Pitch = Pitch::new_unsafe(23);

  // Octave 1: MIDI notes 24-35
  // Контроктава
  pub const C_1: Pitch = Pitch::new_unsafe(24);
  pub const CIS_1: Pitch = Pitch::new_unsafe(25);
  pub const D_1: Pitch = Pitch::new_unsafe(26);
  pub const DIS_1: Pitch = Pitch::new_unsafe(27);
  pub const E_1: Pitch = Pitch::new_unsafe(28);
  pub const F_1: Pitch = Pitch::new_unsafe(29);
  pub const FIS_1: Pitch = Pitch::new_unsafe(30);
  pub const G_1: Pitch = Pitch::new_unsafe(31);
  pub const GIS_1: Pitch = Pitch::new_unsafe(32);
  pub const A_1: Pitch = Pitch::new_unsafe(33);
  pub const AIS_1: Pitch = Pitch::new_unsafe(34);
  pub const B_1: Pitch = Pitch::new_unsafe(35);

  // Octave 2: MIDI notes 36-47
  // Большая октава
  pub const C_2: Pitch = Pitch::new_unsafe(36);
  pub const CIS_2: Pitch = Pitch::new_unsafe(37);
  pub const D_2: Pitch = Pitch::new_unsafe(38);
  pub const DIS_2: Pitch = Pitch::new_unsafe(39);
  pub const E_2: Pitch = Pitch::new_unsafe(40);
  pub const F_2: Pitch = Pitch::new_unsafe(41);
  pub const FIS_2: Pitch = Pitch::new_unsafe(42);
  pub const G_2: Pitch = Pitch::new_unsafe(43);
  pub const GIS_2: Pitch = Pitch::new_unsafe(44);
  pub const A_2: Pitch = Pitch::new_unsafe(45);
  pub const AIS_2: Pitch = Pitch::new_unsafe(46);
  pub const B_2: Pitch = Pitch::new_unsafe(47);

  // Octave 3: MIDI notes 48-59
  // Малая октава
  pub const C_3: Pitch = Pitch::new_unsafe(48);
  pub const CIS_3: Pitch = Pitch::new_unsafe(49);
  pub const D_3: Pitch = Pitch::new_unsafe(50);
  pub const DIS_3: Pitch = Pitch::new_unsafe(51);
  pub const E_3: Pitch = Pitch::new_unsafe(52);
  pub const F_3: Pitch = Pitch::new_unsafe(53);
  pub const FIS_3: Pitch = Pitch::new_unsafe(54);
  pub const G_3: Pitch = Pitch::new_unsafe(55);
  pub const GIS_3: Pitch = Pitch::new_unsafe(56);
  pub const A_3: Pitch = Pitch::new_unsafe(57);
  pub const AIS_3: Pitch = Pitch::new_unsafe(58);
  pub const B_3: Pitch = Pitch::new_unsafe(59);

  // Octave 4: MIDI notes 60-71 (C4 = middle C)
  /// До - Первая октава
  pub const C_4: Pitch = Pitch::new_unsafe(60);
  pub const CIS_4: Pitch = Pitch::new_unsafe(61);
  pub const D_4: Pitch = Pitch::new_unsafe(62);
  pub const DIS_4: Pitch = Pitch::new_unsafe(63);
  pub const E_4: Pitch = Pitch::new_unsafe(64);
  pub const F_4: Pitch = Pitch::new_unsafe(65);
  pub const FIS_4: Pitch = Pitch::new_unsafe(66);
  pub const G_4: Pitch = Pitch::new_unsafe(67);
  pub const GIS_4: Pitch = Pitch::new_unsafe(68);
  pub const A_4: Pitch = Pitch::new_unsafe(69);
  pub const AIS_4: Pitch = Pitch::new_unsafe(70);
  pub const B_4: Pitch = Pitch::new_unsafe(71);

  // Octave 5: MIDI notes 72-83
  /// До - Вторая октава
  pub const C_5: Pitch = Pitch::new_unsafe(72);
  pub const CIS_5: Pitch = Pitch::new_unsafe(73);
  pub const D_5: Pitch = Pitch::new_unsafe(74);
  pub const DIS_5: Pitch = Pitch::new_unsafe(75);
  pub const E_5: Pitch = Pitch::new_unsafe(76);
  pub const F_5: Pitch = Pitch::new_unsafe(77);
  pub const FIS_5: Pitch = Pitch::new_unsafe(78);
  pub const G_5: Pitch = Pitch::new_unsafe(79);
  pub const GIS_5: Pitch = Pitch::new_unsafe(80);
  pub const A_5: Pitch = Pitch::new_unsafe(81);
  pub const AIS_5: Pitch = Pitch::new_unsafe(82);
  pub const B_5: Pitch = Pitch::new_unsafe(83);

  // Octave 6: MIDI notes 84-95
  // Третья октава
  pub const C_6: Pitch = Pitch::new_unsafe(84);
  pub const CIS_6: Pitch = Pitch::new_unsafe(85);
  pub const D_6: Pitch = Pitch::new_unsafe(86);
  pub const DIS_6: Pitch = Pitch::new_unsafe(87);
  pub const E_6: Pitch = Pitch::new_unsafe(88);
  pub const F_6: Pitch = Pitch::new_unsafe(89);
  pub const FIS_6: Pitch = Pitch::new_unsafe(90);
  pub const G_6: Pitch = Pitch::new_unsafe(91);
  pub const GIS_6: Pitch = Pitch::new_unsafe(92);
  pub const A_6: Pitch = Pitch::new_unsafe(93);
  pub const AIS_6: Pitch = Pitch::new_unsafe(94);
  pub const B_6: Pitch = Pitch::new_unsafe(95);

  // Octave 7: MIDI notes 96-107
  // Четвертая октава
  pub const C_7: Pitch = Pitch::new_unsafe(96);
  pub const CIS_7: Pitch = Pitch::new_unsafe(97);
  pub const D_7: Pitch = Pitch::new_unsafe(98);
  pub const DIS_7: Pitch = Pitch::new_unsafe(99);
  pub const E_7: Pitch = Pitch::new_unsafe(100);
  pub const F_7: Pitch = Pitch::new_unsafe(101);
  pub const FIS_7: Pitch = Pitch::new_unsafe(102);
  pub const G_7: Pitch = Pitch::new_unsafe(103);
  pub const GIS_7: Pitch = Pitch::new_unsafe(104);
  pub const A_7: Pitch = Pitch::new_unsafe(105);
  pub const AIS_7: Pitch = Pitch::new_unsafe(106);
  pub const B_7: Pitch = Pitch::new_unsafe(107);

  // Octave 8: MIDI notes 108-119
  pub const C_8: Pitch = Pitch::new_unsafe(108);
  pub const CIS_8: Pitch = Pitch::new_unsafe(109);
  pub const D_8: Pitch = Pitch::new_unsafe(110);
  pub const DIS_8: Pitch = Pitch::new_unsafe(111);
  pub const E_8: Pitch = Pitch::new_unsafe(112);
  pub const F_8: Pitch = Pitch::new_unsafe(113);
  pub const FIS_8: Pitch = Pitch::new_unsafe(114);
  pub const G_8: Pitch = Pitch::new_unsafe(115);
  pub const GIS_8: Pitch = Pitch::new_unsafe(116);
  pub const A_8: Pitch = Pitch::new_unsafe(117);
  pub const AIS_8: Pitch = Pitch::new_unsafe(118);
  pub const B_8: Pitch = Pitch::new_unsafe(119);

  // Octave 9: MIDI notes 120-127 (неполная октава)
  pub const C_9: Pitch = Pitch::new_unsafe(120);
  pub const CIS_9: Pitch = Pitch::new_unsafe(121);
  pub const D_9: Pitch = Pitch::new_unsafe(122);
  pub const DIS_9: Pitch = Pitch::new_unsafe(123);
  pub const E_9: Pitch = Pitch::new_unsafe(124);
  pub const F_9: Pitch = Pitch::new_unsafe(125);
  pub const FIS_9: Pitch = Pitch::new_unsafe(126);
  pub const G_9: Pitch = Pitch::new_unsafe(127);
}
