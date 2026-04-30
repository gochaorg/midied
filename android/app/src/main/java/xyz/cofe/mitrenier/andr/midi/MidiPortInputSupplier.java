package xyz.cofe.mitrenier.andr.midi;

import xyz.cofe.mitrenier.midi.MidiPortIn;

public interface MidiPortInputSupplier {
    MidiPortIn<?> getMidiPortInput();
}
