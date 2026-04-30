package xyz.cofe.mitrenier.midi;

import java.time.Instant;

/**
 * События midi port
 */
public sealed interface MidiPortOutEvent {
    /**
     * Начало воспроизведения
     * @param at время регистрации события
     */
    record Start(Instant at) implements MidiPortOutEvent {}

    /**
     * Конец воспроизведения
     * @param at время регистрации события
     */
    record Stop(Instant at) implements MidiPortOutEvent {}

    /**
     * Посылка события midi
     * @param at время регистрации события
     * @param event событие midi
     */
    record Send(Instant at, MidiEvent<?> event) implements MidiPortOutEvent {}

    /**
     * Посылка события midi
     * @param at время регистрации события
     * @param event событие midi
     * @param nano когда событие должно быть воспроизведено {@link System#nanoTime()}
     */
    record SendAt(Instant at, MidiEvent<?> event, long nano) implements MidiPortOutEvent {}
}
