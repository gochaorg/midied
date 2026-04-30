package xyz.cofe.mitrenier.midi;

import org.junit.jupiter.api.Test;
import xyz.cofe.mitrenier.json.JSON;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RawMidiEventTest {
    @Test
    public void test1(){
        var raw = new RawMidiEvent(Instant.now(),0,RawMidiEvent.hex("901556"));
        var ev = MidiEvent.parse(raw);
        ev.forEach(e -> {
            System.out.println(""+e);
        });
    }

    @Test
    public void json(){
        var src = new RawMidiEvent(Instant.now(), 0, RawMidiEvent.hex("901556"));

        var json = JSON.toJson(src);
        // {
        //  "time": "2026-02-23T19:52:04.447573404Z",
        //  "timestamp": 0,
        //  "data": "901556"
        // }
        System.out.println(json);

        var dst = JSON.fromJson(json, RawMidiEvent.class);
        assertEquals(src,dst);
    }
}
