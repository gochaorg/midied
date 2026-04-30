package xyz.cofe.mitrenier.api.server.rest;

import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.RawMidiEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class MidiInputClient {
    /**
     * Идентификатор клиента
     */
    public final String id;

    public MidiInputClient(String id) {
        this.id = id;
    }

    //region lastActivity : Instant
    private volatile Instant lastActivity = Instant.now();

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Instant lastActivity) {
        if( lastActivity==null ) throw new IllegalArgumentException("lastActivity==null");
        this.lastActivity = lastActivity;
    }
    //endregion

    //region midiEventsQueue : Set<BlockingQueue<MidiEvent<?>>>
    private final Set<BlockingQueue<MidiEvent<?>>> midiEventsQueue = new CopyOnWriteArraySet<>();
    public Set<BlockingQueue<MidiEvent<?>>> midiEventsQueue(){ return midiEventsQueue; }
    //endregion

    //region midiEvents : List<MidiEvent<?>>
    private List<MidiEvent<?>> midiEvents = new CopyOnWriteArrayList<>();

    public List<MidiEvent<?>> getMidiEvents() {
        return midiEvents;
    }

    public void setMidiEvents(List<MidiEvent<?>> midiEvents) {
        if( midiEvents==null ) throw new IllegalArgumentException("midiEvents==null");
        this.midiEvents = midiEvents;
    }
    //endregion

    //region midiRawEventsQueue : Set<BlockingQueue<RawMidiEvent>>
    private final Set<BlockingQueue<RawMidiEvent>> midiRawEventsQueue = new CopyOnWriteArraySet<>();
    public Set<BlockingQueue<RawMidiEvent>> midiRawEventsQueue(){ return midiRawEventsQueue; }
    //endregion

    //region rawMidiEvents : List<RawMidiEvent>
    private List<RawMidiEvent> rawMidiEvents = new CopyOnWriteArrayList<>();

    public List<RawMidiEvent> getRawMidiEvents() {
        return rawMidiEvents;
    }

    public void setRawMidiEvents(List<RawMidiEvent> rawMidiEvents) {
        if( rawMidiEvents==null ) throw new IllegalArgumentException("rawMidiEvents==null");
        this.rawMidiEvents = rawMidiEvents;
    }
    //endregion

    //region nanoTimeShift : Optional<Long>
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private volatile Optional<Long> nanoTimeShift = Optional.empty();

    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
    public MidiInputClient setNanoTimeShift(Optional<Long> nanoTimeShift){
        if( nanoTimeShift==null ) throw new IllegalArgumentException("nanoTimeShift==null");
        this.nanoTimeShift = nanoTimeShift;
        return this;
    }

    public MidiInputClient setNanoTimeShift(Long nanoTimeShift){
        this.nanoTimeShift = Optional.ofNullable(nanoTimeShift);
        return this;
    }

    public Optional<Long> getNanoTimeShift(){
        return nanoTimeShift;
    }
    //endregion

    //region timeShift : Optional<Duration>
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private volatile Optional<Duration> timeShift = Optional.empty();

    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
    public MidiInputClient setTimeShift(Optional<Duration> timeShift) {
        if( timeShift==null ) throw new IllegalArgumentException("timeShift==null");
        this.timeShift = timeShift;
        return this;
    }

    public MidiInputClient setTimeShift(Duration shift){
        this.timeShift = Optional.ofNullable(shift);
        return this;
    }

    public Optional<Duration> getTimeShift(){
        return this.timeShift;
    }
    //endregion

    private <A extends MidiEvent<A>> MidiEvent<A> timeShift(MidiEvent<A> event){
        var time = event.time();
        var time1 = timeShift.map( d -> time.plusNanos(d.toNanos()) ).orElse( time );

        var nano = event.timestampNano();
        var nano1 = nanoTimeShift.map( ts -> nano + ts ).orElse( nano );

        return event.withTime(time1, nano1);
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private RawMidiEvent timeShift(RawMidiEvent event){
        var time = event.time();
        var nano = event.timestamp();

        RawMidiEvent event1 = timeShift.map( d -> event.time( time.plusNanos(d.toNanos()) ) ).orElse( event );
        RawMidiEvent event2 = nanoTimeShift.map( d -> event1.timestamp(nano + d) ).orElse( event1 );
        return event2;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void accept(MidiEvent<?> midiEvent) {
        if( midiEvent!=null ){
            midiEvent = timeShift(midiEvent);
            getMidiEvents().add(midiEvent);
            for( var q : midiEventsQueue ){
                q.offer(midiEvent);
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void accept(RawMidiEvent event) {
        if( event!=null ){
            event = timeShift(event);
            getRawMidiEvents().add(event);
            for( var q : midiRawEventsQueue ){
                q.offer(event);
            }
        }
    }
}
