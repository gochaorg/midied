package xyz.cofe.mitrenier.midi;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.time.Instant;

public class MidiPortOutDumpTest {
    @Test
    public void test1(){
        var strOut = new StringWriter();
        var dump = new MidiPortOutDump(new MidiPortOutNull(), strOut);
        var t = Instant.now();
        dump.start();
        dump.send(new MidiEvent.NoteOn(t,0, 0, MidiNote.octave(3).C(), 45));
        dump.send(new MidiEvent.NoteOff(t.plusMillis(300),1, 0, MidiNote.octave(3).C(), 0));
        dump.send(new MidiEvent.NoteOn(t.plusMillis(500),10, 0, MidiNote.octave(3).D(), 45));
        dump.send(new MidiEvent.NoteOff(t.plusMillis(700),11, 0, MidiNote.octave(3).D(), 0));
        dump.stop();
        System.out.println(strOut);
    }
}
