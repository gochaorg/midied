package xyz.cofe.mitrenier.andr.midi.http;

import org.eclipse.jetty.server.Handler;
import xyz.cofe.mitrenier.andr.midi.MidiPortInputSupplier;
import xyz.cofe.mitrenier.api.server.rest.MidiClientApi;
import xyz.cofe.mitrenier.api.server.rest.MidiInputClients;
import xyz.cofe.mitrenier.midi.MidiPortIn;
import xyz.cofe.nipal.RequestRouter;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MidiRestApi extends ArrayList<Handler> {
    private final MidiPortIn<?> midiPortInput;

    private static final ScheduledExecutorService MIDI_HTTP_SCHEDULER = Executors.newScheduledThreadPool(1, r -> {
        var th = new Thread(r);
        th.setDaemon(true);
        return th;
    });

    public MidiRestApi(MidiPortInputSupplier midiPortInputSupplier) {
        if( midiPortInputSupplier == null ) throw new IllegalArgumentException("midiPortInputSupplier==null");
        midiPortInput = midiPortInputSupplier.getMidiPortInput();

        add(RequestRouter.prefixes("/client", () -> new MidiClientApi(new MidiInputClients(midiPortInput, MIDI_HTTP_SCHEDULER))));
    }
}
