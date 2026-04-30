package xyz.cofe.mitrenier;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Test;
import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.MidiEventCollector;
import xyz.cofe.mitrenier.midi.RawMidiEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class MidiEventCollectTest {
    @Test
    public void testJson() {
        var collector = new MidiEventCollector();
        collector.add(new RawMidiEvent(Instant.now(), 0, new byte[]{0}));
        collector.add(new RawMidiEvent(Instant.now(), 1, new byte[]{0, 1}));
        collector.add(new RawMidiEvent(Instant.now(), 2, new byte[]{0, 1, (byte) 200}));
        System.out.println(collector.toJson());
    }

    //    @Test
    public void sendTest() {
        var collector = new MidiEventCollector();
        collector.add(new RawMidiEvent(Instant.now(), 0, new byte[]{0}));
        collector.add(new RawMidiEvent(Instant.now(), 1, new byte[]{0, 1}));
        collector.add(new RawMidiEvent(Instant.now(), 2, new byte[]{0, 1, (byte) 200}));
        var json = collector.toJson();

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
            .url("http://localhost:3000")
            .post(body)
            .build();

        OkHttpClient client = new OkHttpClient();
        try{
            var resp = client.newCall(request).execute();
            System.out.println("resp " + resp.body().string());
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        }
    }
    
    private String align(String str, int len) {
        if( str.length() >= len ) return str;
        return str + " ".repeat(len - str.length());
    }

    @Test
    public void testRead() {
        try( var stream = MidiEventCollectTest.class.getResource("/capture/midi-events.json").openStream() ){
            var bytes = stream.readAllBytes();
            var rawEvents = MidiEventCollector.fromJson(new String(bytes, StandardCharsets.UTF_8));
            System.out.println("raw events " + rawEvents.events.size());

            var events = rawEvents.toSorted();

            Instant prevTime = null;
            Instant prevNoteOnTime = null;
            Instant prevNoteOffTime = null;
            for( var midiEv : events.getEvents() ){
                Duration dur = prevTime != null ? Duration.between(prevTime, midiEv.time()) : null;
                Duration durNoteOn = prevNoteOnTime != null ? Duration.between(prevNoteOnTime, midiEv.time()) : null;
                Duration durNoteOff = prevNoteOffTime != null ? Duration.between(prevNoteOffTime, midiEv.time()) : null;

                // @formatter:off
                System.out.print("event "+(LocalDateTime.ofInstant(midiEv.time(), ZoneId.systemDefault()))+" ");

//                System.out.print(
//                    switch( midiEv ){
//                        case MidiEvent.NoteOn n  -> {
//                            prevNoteOnTime = n.time();
//                            yield ASCII.GREEN+ASCII.BOLD+
//                                  "note on  " + align(n.noteName(),8) + "dur "+durNoteOn+
//                                  ASCII.RESET
//                                  ;
//                        }
//                        case MidiEvent.NoteOff n -> {
//                            prevNoteOffTime = n.time();
//                            yield ASCII.BRIGHT_BLACK+
//                                  "note off " + align(n.noteName(),8) + "dur "+durNoteOff+
//                                  ASCII.RESET
//                                  ;
//                        }
//                        default -> midiEv.toString();
//                    }
//                );
                System.out.println();
                // @formatter:on

                prevTime = midiEv.time();
            }
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        }
    }
}
