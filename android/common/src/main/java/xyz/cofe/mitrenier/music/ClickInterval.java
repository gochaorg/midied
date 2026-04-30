package xyz.cofe.mitrenier.music;

import org.jetbrains.annotations.NotNull;
import xyz.cofe.mitrenier.midi.MidiEvent;

import java.time.Instant;

public record ClickInterval(Instant time, MidiEvent.NoteOnOrOff note) implements Comparable<ClickInterval> {
    @Override
    public int compareTo(@NotNull ClickInterval other) {
        return time.compareTo(other.time());
    }
}
