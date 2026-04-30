package xyz.cofe.mitrenier.midi;

public class MidiPortOutNull implements MidiPortOut {
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
    }

    @Override
    public void sendAt(MidiEvent<?> event, long nano) {
    }

    @Override
    public void stop() {
        started = false;
    }
}
