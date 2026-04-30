package xyz.cofe.mitrenier.midi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MidiPortOutCapture implements MidiPortOut {
    public final MidiPortOut target;

    public MidiPortOutCapture(MidiPortOut target) {
        if( target==null ) throw new IllegalArgumentException("target==null");
        this.target = target;
    }

    public final List<MidiPortOutEvent> events = new ArrayList<>();

    @Override
    public boolean isRunning() {return target.isRunning();}

    @Override
    public void start() {
        events.add(new MidiPortOutEvent.Start(Instant.now()));
        target.start();
    }

    @Override
    public void send(MidiEvent<?> event) {
        events.add(new MidiPortOutEvent.Send(Instant.now(), event));
        target.send(event);
    }

    @Override
    public void sendAt(MidiEvent<?> event, long nano) {
        events.add(new MidiPortOutEvent.SendAt(Instant.now(), event,nano));
        target.sendAt(event, nano);
    }

    @Override
    public void stop() {
        events.add(new MidiPortOutEvent.Stop(Instant.now()));
        target.stop();
    }
}
