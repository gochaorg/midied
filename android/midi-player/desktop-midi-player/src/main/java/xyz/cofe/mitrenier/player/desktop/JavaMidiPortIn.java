package xyz.cofe.mitrenier.player.desktop;

import xyz.cofe.mitrenier.Listeners;
import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.MidiPortIn;
import xyz.cofe.mitrenier.midi.RawMidiEvent;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;

public class JavaMidiPortIn implements MidiPortIn<JavaMidiPortIn> {
    public JavaMidiPortIn(MidiDevice device){
        if( device==null ) throw new IllegalArgumentException("device==null");

        device.getDeviceInfo();

        try {
            var newTransmitter = device.getTransmitter();
            newTransmitter.setReceiver(receiver);
        } catch ( MidiUnavailableException e ) {
            throw new RuntimeException(e);
        }
    }

    private Receiver receiver = new Receiver() {
        @Override
        public void send(MidiMessage message, long timeStampMicroSec) {
            var raw = RawMidiEvent.createFromNativeEvent(timeStampMicroSec * 1000L, message.getMessage(), 0, message.getLength());
            rawMidiInput.fire(raw);

            MidiEvent.parse(raw).forEach(midiInput::fire);
        }

        @Override
        public void close() {

        }
    };

    private final Listeners<MidiEvent<?>, JavaMidiPortIn> midiInput = new Listeners<>(()->this);

    @Override
    public Listeners<MidiEvent<?>, JavaMidiPortIn> midiInput() {
        return midiInput;
    }

    private final Listeners<RawMidiEvent, JavaMidiPortIn> rawMidiInput = new Listeners<>(()->this);

    @Override
    public Listeners<RawMidiEvent, JavaMidiPortIn> rawMidiInput() {
        return rawMidiInput;
    }
}
