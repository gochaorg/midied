package xyz.cofe.mitrenier.midi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public class SortedMidiEvents {
    public SortedMidiEvents(Iterable<RawMidiEvent> events){
        if( events==null ) throw new IllegalArgumentException("events==null");

        var lst = new ArrayList<MidiEvent>();
        events.forEach( raw -> {
            lst.addAll(MidiEvent.parse(raw));
        });

        lst.sort(Comparator.comparing(MidiEvent::time));
        this.events = lst;

        eventsByTime = new TreeMap<>();
        lst.forEach( e -> eventsByTime.computeIfAbsent(e.time(), x -> new ArrayList<>()).add(e) );
    }

    private final TreeMap<Instant,List<MidiEvent>> eventsByTime;
    public TreeMap<Instant,List<MidiEvent>> getEventsByTime(){ return eventsByTime; }

    private final List<MidiEvent> events;
    public List<MidiEvent> getEvents(){ return events; }
}
