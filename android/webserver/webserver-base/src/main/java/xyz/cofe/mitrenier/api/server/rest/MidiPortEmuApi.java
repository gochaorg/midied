package xyz.cofe.mitrenier.api.server.rest;

import org.eclipse.jetty.server.Handler;
import xyz.cofe.mitrenier.json.JSON;
import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.MidiPortIn;
import xyz.cofe.mitrenier.midi.RawMidiEvent;
import xyz.cofe.nipal.JsonItfImpl;
import xyz.cofe.nipal.RequestRouter;

import java.time.Instant;
import java.util.ArrayList;

public class MidiPortEmuApi extends ArrayList<Handler> {
    private final MidiPortIn.Dummy midiPortIn;

    public MidiPortEmuApi(MidiPortIn.Dummy midiPortIn) {
        this.midiPortIn = midiPortIn;
    }

    private MidiPortIn.Dummy midiPortIn(){ return midiPortIn; }

    {
        add(
            RequestRouter.Builder
                .POST()
                .with(JsonItfImpl.gson(JSON.gson))
                .path("/event/raw")
                .jsonResponse()
                .jsonBody(RawMidiEvent.class)
                .queryParam("now")
                .call( now -> raw -> () -> {
                    var rawMut = raw;
                    if( now.equals("1") ){
                        rawMut = rawMut.time(Instant.now()).timestamp(System.nanoTime());
                    }
                    midiPortIn().midiRaw.fire(rawMut);
                    return "ok";
                })
        );

        /*
        🚀 curl -X POST http://localhost:8899/emu/event?now=1 -d '{ "type":"noteOn", "note":2, "channel":1, "velocity": 13, "timestamp": 0, "time": "2026-02-23T19:55:11.720979042Z" }'
         */
        add(
            RequestRouter.Builder
                .POST()
                .with(JsonItfImpl.gson(JSON.gson))
                .path("/event")
                .jsonResponse()
                .jsonBody(MidiEvent.class)
                .queryParam("now")
                .call( now -> raw -> () -> {
                    MidiEvent ev = raw;
                    if( now.equals("1") ){
                        ev = (MidiEvent) ev.withTime(Instant.now(),System.nanoTime());
                    }
                    midiPortIn().midiDecoded.fire(ev);
                    return "ok";
                })
        );
    }
}
