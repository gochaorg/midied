package xyz.cofe.mitrenier.web.main;

import org.jetbrains.annotations.NotNull;
import xyz.cofe.mitrenier.str.Wildcard;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public record MidiSelection(MidiDevice.Info info, int index) {
    public static List<MidiSelection> list() {
        var lst = new ArrayList<MidiSelection>();

        var devices = MidiSystem.getMidiDeviceInfo();
        System.out.println("midi devices(" + devices.length + ")");
        var idx = -1;
        for( var di : devices ) {
            idx++;
            lst.add(new MidiSelection(di, idx));
        }

        return lst;
    }

    public static MidiDevice open(Predicate<MidiSelection> selection) {
        var select = list().stream().filter(selection).findFirst();
        if( select.isEmpty() ) throw new RuntimeException("not found midi device: " + selection);
        var select1 = select.get();
        try {
            return MidiSystem.getMidiDevice(select1.info);
        } catch ( MidiUnavailableException e ) {
            throw new RuntimeException("can't open midi device " + select1, e);
        }
    }

    @NotNull
    @Override
    public String toString() {
        return "idx=" + index + " name=" + info.getName() + " vendor=" + info.getVendor() + " version=" + info.getVersion() + " description=" + info.getDescription();
    }

    public MidiDevice open() {
        try {
            return MidiSystem.getMidiDevice(info);
        } catch ( MidiUnavailableException e ) {
            throw new RuntimeException(e);
        }
    }

    public static class Predicates {
        public record SelectAny() implements Predicate<MidiSelection> {
            @Override
            public boolean test(MidiSelection midiSelection) {
                return true;
            }
        }

        public static SelectAny selectMidiAny() {
            return new SelectAny();
        }

        public record SelectByIdx(int idx) implements Predicate<MidiSelection> {
            @Override
            public boolean test(MidiSelection sel) {
                return sel.index == idx;
            }
        }

        public static SelectByIdx selectMidiByIdx(int idx) {
            return new SelectByIdx(idx);
        }

        public record SelectByName(String name, boolean wildcard) implements Predicate<MidiSelection> {
            private static final HashMap<String, Pattern> patterns = new HashMap<>();

            @Override
            public boolean test(MidiSelection sel) {

                return wildcard
                    ? patterns.computeIfAbsent(name, x -> Wildcard.wildcardToPattern(name)).matcher(name).matches()
                    : name.equals(sel.info.getName());
            }
        }

        public static SelectByName selectMidiByName(String name) {
            return new SelectByName(name, false);
        }

        public static SelectByName selectMidiByNameWildcard(String name) {
            return new SelectByName(name, true);
        }
    }
}
