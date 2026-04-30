package xyz.cofe.mitrenier.player.andr;

import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.Listeners;
import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.MidiPortIn;
import xyz.cofe.mitrenier.midi.RawMidiEvent;

import java.io.Closeable;
import java.io.IOException;

public class AndrMidiPortIn implements MidiPortIn<AndrMidiPortIn>,
                                       Closeable {

    private static final Logger log = LoggerFactory.getLogger(AndrMidiPortIn.class);
    private MidiOutputPort port;
    private MidiReceiver receiver;

    public AndrMidiPortIn(MidiOutputPort port){
        if( port==null ) throw new IllegalArgumentException("port==null");
        this.port = port;

        receiver = new MidiReceiver() {
            @Override
            public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
                var raw = RawMidiEvent.createFromNativeEvent(timestamp, msg, offset, count);
                rawMidiPort.fire(raw);

                var parsed = MidiEvent.parse(raw);
                parsed.forEach(midiPort::fire);
            }
        };
        port.connect(receiver);
    }

    @Override
    public void close() throws IOException {
        synchronized(this){
            if( port!=null && receiver!=null ){
                port.disconnect(receiver);
            }
            port = null;
            receiver = null;
        }
    }

    private Listeners<MidiEvent<?>, AndrMidiPortIn> midiPort = new Listeners<>(()->this);

    @Override
    public Listeners<MidiEvent<?>, AndrMidiPortIn> midiInput() {
        return midiPort;
    }

    private Listeners<RawMidiEvent, AndrMidiPortIn> rawMidiPort = new Listeners<>(()->this);

    @Override
    public Listeners<RawMidiEvent, AndrMidiPortIn> rawMidiInput() {
        return rawMidiPort;
    }
}
