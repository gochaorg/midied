package xyz.cofe.mitrenier.music;

import xyz.cofe.mitrenier.midi.MidiEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class MPatternSongProvider implements Iterable<MidiSong> {
    //region bpm : int = 120
    private int bpm = 120;

    public int getBpm() {
        return bpm;
    }

    public void setBpm(int bpm) {
        if( bpm<1 ) throw new IllegalArgumentException("bpm<1");
        if( bpm>1000 ) throw new IllegalArgumentException("bpm>1000");
        this.bpm = bpm;
    }

    public MPatternSongProvider bpm(int bpm){
        setBpm(bpm);
        return this;
    }
    //endregion
    //region pattern : MPattern
    private MPattern pattern;

    public MPattern getPattern() {
        return pattern;
    }

    public void setPattern(MPattern pattern) {
        this.pattern = pattern;
    }

    public MPatternSongProvider pattern(MPattern pattern){
        setPattern(pattern);
        return this;
    }
    //endregion
    //region defaultOctave : int = 4
    private int defaultOctave = 4;
    public int getDefaultOctave() {
        return defaultOctave;
    }

    public void setDefaultOctave(int defaultOctave) {
        this.defaultOctave = defaultOctave;
    }
    //endregion
    //region channel : int = 0
    private int channel = 0;

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }
    //endregion
    //region defaultProgram : int = 0
    private int program = 0;

    public int getProgram() {
        return program;
    }

    public void setProgram(int program) {
        this.program = program;
    }
    //endregion
    //region initProgram : boolean = true
    private boolean initProgram = true;

    public boolean isInitProgram() {
        return initProgram;
    }

    public void setInitProgram(boolean initProgram) {
        this.initProgram = initProgram;
    }
    //endregion
    //region initLag : Duration
    private Duration initLag = Duration.ZERO;

    public Duration getInitLag() {
        return initLag;
    }

    public void setInitLag(Duration initLag) {
        this.initLag = initLag;
    }
    //endregion
    //region startTime : Instant
    private Instant startTime = Instant.now();

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public MPatternSongProvider startTime(Instant startTime) {
        this.startTime = startTime;
        return this;
    }
    //endregion

    //region cycleLimit : int = -1
    private int cycleLimit = -1;

    public int getCycleLimit() {
        return cycleLimit;
    }

    public void setCycleLimit(int cycleLimit) {
        this.cycleLimit = cycleLimit;
    }

    public MPatternSongProvider cycleLimit(int cycleLimit) {
        this.cycleLimit = cycleLimit;
        return this;
    }

    public MPatternSongProvider noCycleLimit() {
        this.cycleLimit = -1;
        return this;
    }
    //endregion

    //region build()
    public MidiSong build(boolean initProgram, Duration initLag){
        if( initLag==null ) throw new IllegalArgumentException("initLag==null");

        var pattern = this.pattern;
        if( pattern==null )throw new IllegalStateException("pattern==null");

        List<MidiEvent<?>> song = new ArrayList<>();

        Instant start = startTime != null ? startTime : Instant.now();
        if( initProgram ){
            song.add(new MidiEvent.ProgramChange(start, start.toEpochMilli(), channel, program));
        }

        var beatsIdx = -1;
        for( var beats : pattern.getBeats() ){
            beatsIdx++;
            var at = beats.at();
            for( var beat : beats.beats() ){
                var fBeatsIdx = beatsIdx;
                midiNote(beat.pitch()).ifPresent( midiNote -> {
                    var velocity = velocityOf(beat.power());

                    var dStart = at.toDuration(bpm);
                    var dEnd = at.add(beat.length()).toDuration(bpm);

                    if( fBeatsIdx==0 && initProgram && !initLag.isNegative() && dStart.isZero() ){
                        dStart = dStart.plusMillis(initLag.toMillis());
                    }

                    Instant noteStatTime = start.plusNanos(dStart.toNanos());
                    Instant noteEndTime = start.plusNanos(dEnd.toNanos());

                    song.add(new MidiEvent.NoteOn(noteStatTime, noteStatTime.toEpochMilli(), channel, midiNote, velocity));
                    song.add(new MidiEvent.NoteOff(noteEndTime, noteEndTime.toEpochMilli(), channel, midiNote, 0));
                });
            }
        }

        return new MidiSong(song);
    }

    private Optional<Integer> midiNote(Pitch pitch){
        if( pitch instanceof Pitch.NonePitch p ) return Optional.empty();
        else if( pitch instanceof Pitch.RelativePitch p )Optional.of(defaultOctave * 12 + p.note());
        else if( pitch instanceof Pitch.AbsolutePitch p )Optional.of(p.note());
        else if( pitch instanceof Pitch.AnyPitch a )Optional.of(defaultOctave * 12 + 0 );
        return Optional.empty();
    }

    @SuppressWarnings("deprecation")
    private int velocityOf(Power power){
        if( power instanceof Power.Weak w ) return 40;
        if( power instanceof Power.Medium w ) return 80;
        if( power instanceof Power.Strong w ) return 120;
        if( power instanceof Power.Offbeat w ) return 80;
        return 80;
    }

    public static class CycledIterator implements Iterator<MidiSong> {
        private final AtomicInteger index = new AtomicInteger(0);
        private final MidiSong firstSong;
        private final MidiSong nextSong;
        private final int limit;

        public CycledIterator(MidiSong firstSong, MidiSong nextSong, int limit) {
            if( firstSong==null ) throw new IllegalArgumentException("firstSong==null");
            if( nextSong==null ) throw new IllegalArgumentException("nextSong==null");

            this.firstSong = firstSong;
            this.nextSong = nextSong;
            this.limit = limit;
        }

        @Override
        public boolean hasNext() {
            if( limit<0 )return true;
            var i = index.get();
            return i<=limit;
        }

        @Override
        public MidiSong next() {
            if( limit>=0 ){
                if( limit==0 )return null;

                var i = index.get();
                if( i>=limit )return null;
            }

            var idx = index.getAndIncrement();
            if( idx==0 )return firstSong;
            return nextSong;
        }
    }
    //endregion

    @SuppressWarnings("NullableProblems")
    @Override
    public CycledIterator iterator() {
        MidiSong first = build(initProgram, initLag);
        MidiSong next = build(false, initLag);
        return new CycledIterator(first,next,getCycleLimit());
    }
}
