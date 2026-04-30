package xyz.cofe.mitrenier.player.andr;

import android.media.midi.MidiInputPort;
import android.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.MidiPortOut;

import java.io.Closeable;
import java.io.IOException;

/**
 * Вывод событий Midi на устройство
 */
public class AndrMidiPortOut implements MidiPortOut,
                                        Closeable {
    private static final Logger log = LoggerFactory.getLogger(AndrMidiPortOut.class);

    private volatile MidiInputPort midiInputPort;

    public AndrMidiPortOut(MidiInputPort midiInputPort) {
        this.midiInputPort = midiInputPort;
    }

    private volatile boolean started = false;

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void send(MidiEvent<?> event) {
        if( event == null ) throw new IllegalArgumentException("event==null");

        var port = midiInputPort;
        if( port==null ){
            log.error("can't send, midi port is null");
            throw new IllegalStateException();
        }

        var rawMidiEvent = event.toRawMidiEvent();
        try{
            port.send(rawMidiEvent.data(), 0, rawMidiEvent.data().length);
        } catch ( IOException e ) {
            log.error("can't send to midi port", e);
        }
    }

    @Override
    public void sendAt(MidiEvent<?> event, long nano) {
        if( event == null ) throw new IllegalArgumentException("event==null");

        var port = midiInputPort;
        if( port==null ){
            log.error("can't send, midi port is null");
            throw new IllegalStateException();
        }

        var rawMidiEvent = event.toRawMidiEvent();
        try{
            port.send(rawMidiEvent.data(), 0, rawMidiEvent.data().length, nano);
        } catch ( IOException e ) {
            log.error("can't send to midi port", e);
        }
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public void close() {
        started = false;
        try{
            if( midiInputPort!=null ){
                midiInputPort.close();
            }
            midiInputPort = null;
        } catch ( IOException e ) {
            log.error("can't close midi port");
        }
    }
}
