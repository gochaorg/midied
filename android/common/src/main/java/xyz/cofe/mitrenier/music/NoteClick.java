package xyz.cofe.mitrenier.music;

import xyz.cofe.mitrenier.midi.MidiEvent.*;
import xyz.cofe.mitrenier.midi.MidiNote;

import java.time.Instant;
import java.util.Optional;

/**
 * "Клик" клавиши midi/пианино.
 * <ul>
 *   <li>
 *     Клик - это либо нажатие и отпускание одной клавиши {@link FullClick}
 *   </li>
 *   <li>
 *       Либо только нажатие {@link HalfClick}
 *   </li>
 *   </ul>
 */
public sealed interface NoteClick extends MidiNote,
                                          Channel {
    /**
     * Возвращает номер канала
     */
    int channel();

    /**
     * Номер midi ноты
     */
    int note();

    /**
     * Время начала нажатия
     */
    Instant startAt();

    /**
     * Время конца нажатия
     */
    Optional<Instant> endAt();

    /**
     * Событие нажатия
     */
    NoteOn noteOn();

    /**
     * Полный клик клавиши
     *
     * @param noteOn  Нажатие клавиши
     * @param noteOff Отпускание клавиши
     */
    record FullClick(NoteOn noteOn, NoteOff noteOff) implements NoteClick {
        @Override
        public int channel() {
            return noteOn.channel();
        }

        @Override
        public int note() {
            return noteOn.note();
        }

        @Override
        public Instant startAt() {
            return noteOn.time();
        }

        @Override
        public Optional<Instant> endAt() {
            return Optional.of(noteOff.time());
        }

        public Instant endAtTime() {
            return noteOff.time();
        }
    }

    /**
     * Частичное клик клавиши
     *
     * @param noteOn Нажатие клавиши
     */
    record HalfClick(NoteOn noteOn) implements NoteClick {
        @Override
        public Instant startAt() {
            return noteOn.time();
        }

        @Override
        public Optional<Instant> endAt() {
            return Optional.empty();
        }

        @Override
        public int channel() {
            return noteOn.channel();
        }

        @Override
        public int note() {
            return noteOn.note();
        }
    }
}
