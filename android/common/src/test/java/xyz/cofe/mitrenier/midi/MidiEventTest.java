package xyz.cofe.mitrenier.midi;

import org.junit.jupiter.api.Test;
import xyz.cofe.mitrenier.json.JSON;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MidiEventTest {
    @Test
    public void json(){
        MidiEvent<?> src = new MidiEvent.NoteOn(Instant.now(), 0, 1, 2, 3);

        var json = JSON.toJson(src);
        // {
        //  "type": "noteOn",
        //  "note": 2,
        //  "channel": 1,
        //  "velocity": 3,
        //  "timestamp": 0,
        //  "time": "2026-02-23T19:55:11.720979042Z"
        // }
        System.out.println(json);

        var dst = JSON.fromJson(json, MidiEvent.class);
        assertEquals(src,dst);
    }
}
