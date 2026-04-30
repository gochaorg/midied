package xyz.cofe.mitrenier.midi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Декодированное midi событие, см {@link RawMidiEvent}
 * <p>
 * <a href="https://www.music.mcgill.ca/~ich/classes/mumt306/StandardMIDIfileformat.html">Описание возможных событий</a>
 *
 * см:
 * <ul>
 *     <li> {@link NoteOn} </li>
 *     <li> {@link NoteOff} </li>
 *     <li> {@link PolyphonicKeyPressure} </li>
 *     <li> {@link ControlChange} </li>
 *     <li> {@link ProgramChange} </li>
 *     <li> {@link ChannelPressure} </li>
 *     <li> {@link PitchWheelChange} </li>
 *     <li> {@link ChannelModeMessages} </li>
 * </ul>
 */
public sealed interface MidiEvent<SELF extends  MidiEvent<SELF>> {
    /**
     * Конвертация в RAW событие
     * @return RAW событие
     */
    RawMidiEvent toRawMidiEvent();

    /**
     * Возвращает номер канала midi
     */
    interface Channel {
        /**
         * Номер канала
         * @return Номер канала
         */
        int channel();
    }

    /**
     * Время события, когда получено
     * @return время
     */
    Instant time();

    /**
     * Время события (нано секунды) - создано пианино
     * @return время (нано секунды)
     */
    long timestampNano();

    /**
     * Создать клон с новым временем
     * @param time Время события, когда получено
     * @param timestamp  Время события (микро секунды) - создано пианино
     * @return клон
     */
    SELF withTime(Instant time, long timestamp);

    /**
     * Указатель на позицию в исходнике
     *
     * @param data данные
     * @param ptr  указатель
     */
    record SrcPtr(byte[] data, int ptr) {
        public static SrcPtr from(byte[] data) {
            if( data == null ) throw new IllegalArgumentException("data==null");
            return new SrcPtr(data, 0);
        }

        public SrcPtr {
            if( data == null ) throw new IllegalArgumentException("data==null");
        }

        public boolean isEOF() {return ptr >= data.length;}

        public Optional<Integer> get() {
            if( ptr < 0 ) return Optional.empty();
            if( ptr >= data.length ) return Optional.empty();
            return Optional.of(data[ptr] & 0xFF);
        }

        public Optional<Integer> get(int off) {
            int o = (ptr + off);
            if( o < 0 ) return Optional.empty();
            if( o >= data.length ) return Optional.empty();
            return Optional.of(data[o] & 0xFF);
        }

        public SrcPtr jump(int off) {
            return new SrcPtr(data, ptr + off);
        }

        /**
         * Кло-во доступных байтов включая текущий
         *
         * @return кол-во байт
         */
        public int available() {
            return Math.max(data.length - ptr, 0);
        }

        public Optional<SrcPtr> minAvailable(int minBytes) {
            var a = available();
            if( a < minBytes ) return Optional.empty();
            return Optional.of(this);
        }

        /**
         * Верхние 4 бита
         *
         * @return значение от 0 до (включительно) 15 или -1
         */
        public int hi4bit() {
            return get().map(v -> (v & 0xF0) >> 4).orElse(-1);
        }

        /**
         * Нижние 4 бита
         *
         * @return значение от 0 до (включительно) 15 или -1
         */
        public int lo4bit() {
            return get().map(v -> (v & 0xF)).orElse(-1);
        }

        /**
         * Нижние 7 бита
         *
         * @return значение от 0 до (включительно) 127 или -1
         */
        public int lo7bit() {
            return get().map(v -> (v & 0b0111_1111)).orElse(-1);
        }
    }

    /**
     * Результат парсинга
     *
     * @param value значение
     * @param next  указатель на следующее значение после этого
     * @param <A>   тип значения
     */
    record Parsed<A>(A value, SrcPtr next) {}

    interface Parser<A> extends Function<SrcPtr, Optional<Parsed<A>>> {
        @SafeVarargs
        public static <A> Parser<A> or(Parser<? extends A>... parsers) {
            return ptr -> {
                for( var parser : parsers ){
                    var p = parser.apply(ptr);
                    if( p.isPresent() ){
                        var parsed = p.get();
                        A value = parsed.value;
                        return Optional.of(new Parsed<>(value, parsed.next()));
                    }
                }
                return Optional.empty();
            };
        }

        default <B> Parser<B> flatMap(Function<A, Optional<B>> mapper) {
            if( mapper == null ) throw new IllegalArgumentException("mapper==null");
            var self = this;
            return ptr -> self.apply(ptr).flatMap(res -> mapper.apply(res.value()).map(res2 -> new Parsed<B>(res2, res.next())));
        }

        default <B> Parser<B> map(Function<A, B> mapper) {
            if( mapper == null ) throw new IllegalArgumentException("mapper==null");
            return flatMap(res1 -> Optional.ofNullable(mapper.apply(res1)));
        }
    }

    sealed interface Atom {
        MidiEvent<?> withTime(Instant time, long timestamp);
    }

    /**
     * Включение ноты
     *
     * @param channel  номер MIDI-канала (0–15).
     * @param note     (0–127) — номер ноты.
     * @param velocity (0–127) — сила нажатия.
     */
    record NoteOnAtom(int channel, int note, int velocity) implements MidiNote,
                                                                      Atom {
        public static final Parser<NoteOnAtom> parser = ptr ->
            ptr.minAvailable(3).flatMap(p0 -> p0.hi4bit() == 0b1001
                ? Optional.of(new Parsed<>(new NoteOnAtom(p0.lo4bit(), p0.jump(1).lo7bit(), p0.jump(2).lo7bit()), p0.jump(3)))
                : Optional.empty());

        @Override
        public NoteOn withTime(Instant time, long timestamp) {
            return new NoteOn(time, timestamp, channel, note, velocity);
        }
    }

    public sealed interface NoteOnOrOff extends Channel, MidiNote {
        int note();
        int velocity();
        Instant time();
    }

    /**
     * Включение ноты
     *
     * @param time      Время события когда было получено
     * @param timestampNano Время события от устройства midi
     * @param channel   номер MIDI-канала (0–15).
     * @param note      (0–127) — номер ноты.
     * @param velocity  (0–127) — сила нажатия.
     */
    record NoteOn(Instant time, long timestampNano, int channel, int note, int velocity) implements MidiEvent<NoteOn>,
                                                                                                    MidiNote,
                                                                                                    Channel,
                                                                                                    NoteOnOrOff
    {
        @Override
        public NoteOn withTime(Instant time, long timestamp) {
            return new NoteOn(time, timestamp, channel, note, velocity);
        }

        public NoteOn withChannel(int channel){
            return new NoteOn(time, timestampNano, channel, note, velocity);
        }

        @Override
        public RawMidiEvent toRawMidiEvent() {
            return new RawMidiEvent(
                time, timestampNano,
                new byte[] {
                    (byte)(((0b1001) << 4) | (channel & 0x0F)),
                    (byte) note,
                    (byte) velocity
                }
            );
        }
    }

    /**
     * Выключение ноты
     *
     * @param channel  номер MIDI-канала (0–15).
     * @param note     (0–127) — номер ноты.
     * @param velocity (0–127) — сила нажатия.
     */
    record NoteOffAtom(int channel, int note, int velocity) implements Atom,
                                                                       MidiNote {
        public static final Parser<NoteOffAtom> parser = ptr ->
            ptr.minAvailable(3).flatMap(p0 -> p0.hi4bit() == 0b1000
                ? Optional.of(new Parsed<>(new NoteOffAtom(p0.lo4bit(), p0.jump(1).lo7bit(), p0.jump(2).lo7bit()), p0.jump(3)))
                : Optional.empty());

        @Override
        public NoteOff withTime(Instant time, long timestamp) {
            return new NoteOff(time, timestamp, channel, note, velocity);
        }
    }

    /**
     * Выключение ноты
     *
     * @param time      Время события когда было получено
     * @param timestampNano Время события от устройства midi
     * @param channel   номер MIDI-канала (0–15).
     * @param note      (0–127) — номер ноты.
     * @param velocity  (0–127) — сила нажатия.
     */
    record NoteOff(Instant time, long timestampNano, int channel, int note, int velocity) implements MidiEvent<NoteOff>,
                                                                                                     MidiNote,
                                                                                                     Channel,
                                                                                                     NoteOnOrOff
    {
        @Override
        public NoteOff withTime(Instant time, long timestamp) {
            return new NoteOff(time, timestamp, channel, note, velocity);
        }

        @Override
        public RawMidiEvent toRawMidiEvent() {
            return new RawMidiEvent(
                time, timestampNano,
                new byte[] {
                    (byte)(((0b1000) << 4) | (channel & 0x0F)),
                    (byte) note,
                    (byte) velocity
                }
            );
        }

        public NoteOff withChannel(int channel){
            return new NoteOff(time, timestampNano, channel, note, velocity);
        }
    }

    /**
     * Передаёт давление, приложенное к отдельной ноте (note_number) после её нажатия на канале n.
     * Pressure (0–127) указывает величину давления.
     * Используется для модуляции звука (например, вибрато).
     * <p>
     * Пример: 0xA0, 60, 50 — давление 50 на ноту C4 на канале Ascending (0xC0, 60, 127)` — нажать ноту C4 на канале 13 с максимальной громкостью.
     *
     * @param channel       номер MIDI-канала (0–15).
     * @param note          (0–127) — номер ноты.
     * @param pressureValue давление
     */
    record PolyphonicKeyPressureAtom(int channel, int note, int pressureValue) implements Atom {
        public static final Parser<PolyphonicKeyPressureAtom> parser = ptr ->
            ptr.minAvailable(3).flatMap(p0 -> p0.hi4bit() == 0b1010
                ? Optional.of(new Parsed<>(new PolyphonicKeyPressureAtom(p0.lo4bit(), p0.jump(1).lo7bit(), p0.jump(2).lo7bit()), p0.jump(3)))
                : Optional.empty());

        @Override
        public PolyphonicKeyPressure withTime(Instant time, long timestamp) {
            return new PolyphonicKeyPressure(time, timestamp, channel, note, pressureValue);
        }
    }

    /**
     * Передаёт давление, приложенное к отдельной ноте (note_number) после её нажатия на канале n.
     * pressure (0–127) указывает величину давления.
     * Используется для модуляции звука (например, вибрато).
     * <p>
     * <br/> Пример: 0xA0, 60, 50 — давление 50 на ноту C4 на канале Ascending (0xC0, 60, 127)` — нажать ноту C4 на канале 13 с максимальной громкостью.
     *
     * @param time          Время события когда было получено
     * @param timestampNano     Время события от устройства midi
     * @param channel       номер MIDI-канала (0–15).
     * @param note          (0–127) — номер ноты.
     * @param pressureValue давление
     */
    record PolyphonicKeyPressure(Instant time, long timestampNano, int channel, int note, int pressureValue)
        implements MidiEvent<PolyphonicKeyPressure>,
                   MidiNote,
                   Channel
    {
        @Override
        public PolyphonicKeyPressure withTime(Instant time, long timestamp) {
            return new PolyphonicKeyPressure(time, timestamp, channel, note, pressureValue);
        }

        @Override
        public RawMidiEvent toRawMidiEvent() {
            return new RawMidiEvent(
                time, timestampNano,
                new byte[] {
                    (byte)(((0b1010) << 4) | (channel & 0x0F)),
                    (byte) note,
                    (byte) pressureValue
                }
            );
        }
    }

    /**
     * Изменение параметров (например, громкости, панорамы).
     * <p>
     * <p/>
     * Управляет параметрами синтезатора или эффектов на канале n. controller_number (0–127)
     * определяет тип контроллера (например, громкость, панорама, модуляция), а value (0–127) — его значение.
     * <p>
     * <p/>
     * Пример: 0xB0, 7, 100 — установить громкость канала 1 на 100.
     * <p>
     * <br/> Популярные контроллеры:
     * <pre>
     *     1: Modulation Depth
     *     7: Channel Volume
     *     10: Pan
     *     64: Sustain Pedal (0 = выкл, 127 = вкл).
     * </pre>
     *
     * @param channel    номер MIDI-канала (0–15).
     * @param controller параметр
     * @param value      значение
     */
    record ControlChangeAtom(int channel, int controller, int value) implements Atom {
        public static final Parser<ControlChangeAtom> parser = ptr ->
            ptr.minAvailable(3).flatMap(p0 -> p0.hi4bit() == 0b1010
                ? Optional.of(new Parsed<>(new ControlChangeAtom(p0.lo4bit(), p0.jump(1).lo7bit(), p0.jump(2).lo7bit()), p0.jump(3)))
                : Optional.empty());

        @Override
        public ControlChange withTime(Instant time, long timestamp) {
            return new ControlChange(time, timestamp, channel, controller, value);
        }
    }

    /**
     * Изменение параметров (например, громкости, панорамы).
     * <p>
     * <p/>
     * Управляет параметрами синтезатора или эффектов на канале n. controller_number (0–127)
     * определяет тип контроллера (например, громкость, панорама, модуляция), а value (0–127) — его значение.
     * <p>
     * <p/>
     * Пример: 0xB0, 7, 100 — установить громкость канала 1 на 100.
     * <p>
     * <br/> Популярные контроллеры:
     * <pre>
     *     1: Modulation Depth
     *     7: Channel Volume
     *     10: Pan
     *     64: Sustain Pedal (0 = выкл, 127 = вкл).
     * </pre>
     *
     * @param time       Время события когда было получено
     * @param timestampNano  Время события от устройства midi
     * @param channel    номер MIDI-канала (0–15).
     * @param controller параметр
     * @param value      значение
     */
    record ControlChange(Instant time, long timestampNano, int channel, int controller, int value)
        implements MidiEvent<ControlChange>, Channel
    {
        @Override
        public ControlChange withTime(Instant time, long timestamp) {
            return new ControlChange(time, timestamp, channel, controller, value);
        }

        @Override
        public RawMidiEvent toRawMidiEvent() {
            return new RawMidiEvent(
                time, timestampNano,
                new byte[] {
                    (byte)(((0b1010) << 4) | (channel & 0x0F)),
                    (byte) controller,
                    (byte) value
                }
            );
        }
    }

    /**
     * Выключить все ноты
     * @param channel канал
     * @return команда
     */
    public static ControlChange stopAllNotes(int channel){
        return new ControlChange(Instant.now(), System.nanoTime(), channel, 123, 0);
    }

    /**
     * Смена инструмента
     *
     * @param channel  номер MIDI-канала (0–15).
     * @param programm инструмент
     */
    record ProgramChangeAtom(int channel, int programm) implements Atom {
        public static final Parser<ProgramChangeAtom> parser = ptr ->
            ptr.minAvailable(2).flatMap(p0 -> p0.hi4bit() == 0b1100
                ? Optional.of(new Parsed<>(new ProgramChangeAtom(p0.lo4bit(), p0.jump(1).lo7bit()), p0.jump(2)))
                : Optional.empty());

        @Override
        public ProgramChange withTime(Instant time, long timestamp) {
            return new ProgramChange(time, timestamp, channel, programm);
        }
    }

    /**
     * Смена инструмента
     *
     * @param time      Время события когда было получено
     * @param timestampNano Время события от устройства midi
     * @param channel   номер MIDI-канала (0–15).
     * @param programm  инструмент
     */
    record ProgramChange(Instant time, long timestampNano, int channel, int programm)
        implements MidiEvent<ProgramChange>, Channel
    {
        @Override
        public ProgramChange withTime(Instant time, long timestamp) {
            return new ProgramChange(time, timestamp, channel, programm);
        }

        @Override
        public RawMidiEvent toRawMidiEvent() {
            return new RawMidiEvent(
                time, timestampNano,
                new byte[] {
                    (byte)(((0b1010) << 4) | (channel & 0x0F)),
                    (byte) programm
                }
            );
        }
    }

    /**
     * Передаёт общее давление, приложенное ко всем нотам на канале n.
     * pressure (0–127) влияет на параметры, такие как громкость или тембр.
     * В отличие от Polyphonic Key Pressure, применяется ко всему каналу.
     *
     * @param channel номер MIDI-канала (0–15).
     * @param value   общее давление
     */
    record ChannelPressureAtom(int channel, int value) implements Atom {
        public static final Parser<ChannelPressureAtom> parser = ptr ->
            ptr.minAvailable(2).flatMap(p0 -> p0.hi4bit() == 0b1101
                ? Optional.of(new Parsed<>(new ChannelPressureAtom(p0.lo4bit(), p0.jump(1).lo7bit()), p0.jump(2)))
                : Optional.empty());

        @Override
        public ChannelPressure withTime(Instant time, long timestamp) {
            return new ChannelPressure(time, timestamp, channel, value);
        }
    }

    /**
     * Передаёт общее давление, приложенное ко всем нотам на канале n.
     * pressure (0–127) влияет на параметры, такие как громкость или тембр.
     * В отличие от Polyphonic Key Pressure, применяется ко всему каналу.
     *
     * @param time      Время события когда было получено
     * @param timestampNano Время события от устройства midi
     * @param channel   номер MIDI-канала (0–15).
     * @param value     общее давление
     */
    record ChannelPressure(Instant time, long timestampNano, int channel, int value)
        implements MidiEvent<ChannelPressure>, Channel
    {
        @Override
        public ChannelPressure withTime(Instant time, long timestamp) {
            return new ChannelPressure(time, timestamp, channel, value);
        }

        @Override
        public RawMidiEvent toRawMidiEvent() {
            return new RawMidiEvent(
                time, timestampNano,
                new byte[] {
                    (byte)(((0b1010) << 4) | (channel & 0x0F)),
                    (byte) value
                }
            );
        }
    }

    /**
     * Эта команда используется для изменения высоты тона всех нот на канале n
     * (0–15, соответствует каналам 1–16) с помощью колеса модуляции (pitch wheel).
     * Значение высоты тона представлено 14-битным числом, где:
     *
     * <pre>
     *     lllllll — младшие 7 бит (LSB, least significant bits).
     *     mmmmmmm — старшие 7 бит (MSB, most significant bits).
     *     Диапазон значений: 0 (0x0000) — максимальное понижение, 8192 (0x2000) — нейтральное положение (без изменения высоты), 16383 (0x3FFF) — максимальное повышение.
     *     Чувствительность (диапазон изменения высоты, например, ±2 полутона) зависит от настроек передатчика (синтезатора или контроллера).
     * </pre>
     * <p>
     * Пример: 0xE0, 0, 64 (эквивалент 8192) — нейтральное положение колеса модуляции на канале 1.
     * <p></p> Применение: Используется для плавного изменения высоты звука, например, для эффекта вибрато или глиссандо.
     *
     * @param channel номер MIDI-канала (0–15).
     * @param value   значение
     */
    record PitchWheelChangeAtom(int channel, int value) implements Atom {
        public static final Parser<PitchWheelChangeAtom> parser = ptr ->
            ptr.minAvailable(3).flatMap(p0 -> p0.hi4bit() == 0b1110
                ? Optional.of(new Parsed<>(
                new PitchWheelChangeAtom(p0.lo4bit(),
                    p0.jump(1).lo7bit() | (p0.jump(2).lo7bit() << 7)
                ), p0.jump(3)))
                : Optional.empty());

        @Override
        public PitchWheelChange withTime(Instant time, long timestamp) {
            return new PitchWheelChange(time, timestamp, channel, value);
        }
    }

    /**
     * Эта команда используется для изменения высоты тона всех нот на канале n
     * (0–15, соответствует каналам 1–16) с помощью колеса модуляции (pitch wheel).
     * Значение высоты тона представлено 14-битным числом, где:
     *
     * <pre>
     *     lllllll — младшие 7 бит (LSB, least significant bits).
     *     mmmmmmm — старшие 7 бит (MSB, most significant bits).
     *     Диапазон значений: 0 (0x0000) — максимальное понижение, 8192 (0x2000) — нейтральное положение (без изменения высоты), 16383 (0x3FFF) — максимальное повышение.
     *     Чувствительность (диапазон изменения высоты, например, ±2 полутона) зависит от настроек передатчика (синтезатора или контроллера).
     * </pre>
     * <p>
     * Пример: 0xE0, 0, 64 (эквивалент 8192) — нейтральное положение колеса модуляции на канале 1.
     * <p></p> Применение: Используется для плавного изменения высоты звука, например, для эффекта вибрато или глиссандо.
     *
     * @param time      Время события когда было получено
     * @param timestampNano Время события от устройства midi
     * @param channel   номер MIDI-канала (0–15).
     * @param value     значение
     */
    record PitchWheelChange(Instant time, long timestampNano, int channel, int value)
        implements MidiEvent<PitchWheelChange>, Channel
    {
        @Override
        public PitchWheelChange withTime(Instant time, long timestamp) {
            return new PitchWheelChange(time, timestamp, channel, value);
        }

        @Override
        public RawMidiEvent toRawMidiEvent() {
            return new RawMidiEvent(
                time, timestampNano,
                new byte[] {
                    (byte)(((0b1010) << 4) | (channel & 0x0F)),
                    (byte) (value & 0b01111111),
                    (byte) ((value >> 7) & 0b01111111)
                }
            );
        }
    }

    /**
     * Эти сообщения имеют тот же формат, что и Control Change (0xBn, controller_number, value),
     * но используют зарезервированные номера контроллеров (120–127) для управления режимами работы MIDI-устройства на канале n.
     * Они определяют, как устройство реагирует на MIDI-данные или локальные действия (например, нажатия клавиш).
     *
     * @param channel    номер MIDI-канала (0–15).
     * @param controller
     * @param value
     */
    record ChannelModeMessagesAtom(int channel, int controller, int value) implements Atom {
        public static final Parser<ChannelModeMessagesAtom> parser = ptr ->
            ptr.minAvailable(3).flatMap(p0 -> p0.hi4bit() == 0b1011
                ? Optional.of(new Parsed<>(
                new ChannelModeMessagesAtom(
                    p0.lo4bit(),
                    p0.jump(1).lo7bit(),
                    p0.jump(2).lo7bit()
                ), p0.jump(3)))
                : Optional.empty());

        @Override
        public ChannelModeMessages withTime(Instant time, long timestamp) {
            return new ChannelModeMessages(time, timestamp, channel, controller, value);
        }
    }

    /**
     * Эти сообщения имеют тот же формат, что и Control Change (0xBn, controller_number, value),
     * но используют зарезервированные номера контроллеров (120–127) для управления режимами работы MIDI-устройства на канале n.
     * Они определяют, как устройство реагирует на MIDI-данные или локальные действия (например, нажатия клавиш).
     *
     * @param time       Время события когда было получено
     * @param timestampNano  Время события от устройства midi
     * @param channel    номер MIDI-канала (0–15).
     * @param controller
     * @param value
     */
    record ChannelModeMessages(Instant time, long timestampNano, int channel, int controller, int value)
        implements MidiEvent<ChannelModeMessages>, Channel
    {
        @Override
        public ChannelModeMessages withTime(Instant time, long timestamp) {
            return new ChannelModeMessages( time, timestamp, channel, controller, value );
        }

        @Override
        public RawMidiEvent toRawMidiEvent() {
            return new RawMidiEvent(
                time, timestampNano,
                new byte[] {
                    (byte)(((0b1010) << 4) | (channel & 0x0F)),
                    (byte) controller,
                    (byte) value
                }
            );
        }
    }

    public static final Parser<Atom> parser = Parser.or(
        NoteOnAtom.parser,
        NoteOffAtom.parser,
        PolyphonicKeyPressureAtom.parser,
        ControlChangeAtom.parser,
        ProgramChangeAtom.parser,
        ChannelPressureAtom.parser,
        PitchWheelChangeAtom.parser,
        ChannelModeMessagesAtom.parser
    );

    public static List<MidiEvent<?>> parse(SrcPtr ptr, Instant time, long timestamp) {
        if( ptr == null ) throw new IllegalArgumentException("ptr==null");
        var lst = new ArrayList<MidiEvent<?>>();
        while( !ptr.isEOF() ){
            var res = parser.apply(ptr);
            if( res.isEmpty() ) break;

            ptr = res.get().next();
            lst.add(res.get().value().withTime(time, timestamp));
        }
        return lst;
    }

    public static List<MidiEvent<?>> parse(RawMidiEvent event) {
        if( event == null ) throw new IllegalArgumentException("event==null");
        return parse(SrcPtr.from(event.data()), event.time(), event.timestamp());
    }
}
