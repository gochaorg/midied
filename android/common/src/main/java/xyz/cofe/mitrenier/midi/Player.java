package xyz.cofe.mitrenier.midi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.Listeners;
import xyz.cofe.mitrenier.music.MidiSong;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * "Бесконечный" плеер, использует итератор по {@link xyz.cofe.mitrenier.music.MidiSong}
 */
public class Player {
    private static final Logger log = LoggerFactory.getLogger(Player.class);
    private final Lock playLock = new ReentrantLock();

    //region onStarted listeners
    public final Listeners<Player, Player> onStarted = new Listeners<>(()->this);
    //endregion

    //region onStopped listeners
    public final Listeners<Player, Player> onStopped = new Listeners<>(()->this);
    //endregion

    //region song : Iterable<MidiSong>
    private volatile Iterable<MidiSong> song;

    public Iterable<MidiSong> getSong() {
        return song;
    }

    public void setSong(Iterable<MidiSong> song) {
        log.debug("setSong {}", song);
        this.song = song;
    }

    public void setSong(MidiSong song){
        this.song = song==null ? null : List.of(song);
    }
    //endregion

    //region scheduler : ScheduledExecutorService
    private ScheduledExecutorService scheduler;

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        log.debug("setScheduler {}",scheduler);
        this.scheduler = scheduler;
    }
    //endregion

    //region midiOut : MidiOut
    private MidiPortOut midiPortOut;

    public MidiPortOut getMidiOut() {
        return midiPortOut;
    }

    public void setMidiOut(MidiPortOut midiPortOut) {
        log.debug("setMidiOut {}",midiPortOut);
        this.midiPortOut = midiPortOut;
    }
    //endregion

    //region playLag : Duration = 50 ms
    private Duration playLag = Duration.ofMillis(50);

    public Duration getPlayLag() {
        return playLag;
    }

    public void setPlayLag(Duration playLag) {
        if( playLag == null ) throw new IllegalArgumentException("playLag==null");
        if( playLag.isNegative() ) throw new IllegalArgumentException("playLag.isNegative()");
        try{
            playLock.lock();
            log.debug("setPlayLag {}", playLag);
            this.playLag = playLag;
        } finally {
            playLock.unlock();
        }
    }
    //endregion

    //region createdAt : Instant
    private final Instant createdAt = Instant.now();

    public Instant getCreatedAt() {
        return createdAt;
    }
    //endregion

    //region id : String
    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
    //endregion

    //region playStarted : Instant
    private volatile Instant playStarted;

    public Optional<Instant> getPlayStarted() {
        return Optional.ofNullable(playStarted);
    }

    { onStarted.listener( (p) -> {
        playStarted = Instant.now();
        playStopped = null;
    }).bind(); }
    //endregion

    //region playStopped : Instant
    private volatile Instant playStopped;

    public Optional<Instant> getPlayStopped() {
        return Optional.ofNullable(playStopped);
    }

    { onStopped.listener( (p) -> { playStopped = Instant.now(); }).bind(); }
    //endregion

    private static final long NOT_PLAYING = -1;
    private final NavigableMap<Long, List<MidiEvent<?>>> playPositions = new TreeMap<>();
    private final List<ScheduledFuture<?>> playMidiEvents = new CopyOnWriteArrayList<>();

    private Set<Integer> usedChannel(){
        return playPositions.values().stream()
            .flatMap(Collection::stream)
            .flatMap( ev -> ev instanceof MidiEvent.Channel ch ? Stream.of(ch.channel()) : Stream.empty() )
            .collect(Collectors.toSet());
    }

    //region playPosition : long
    private final AtomicLong playPosition = new AtomicLong(NOT_PLAYING);

    public Optional<Long> getPlayPositionMS() {
        var v = playPosition.get();
        return v < 0 ? Optional.empty() : Optional.of(v);
    }

    public NavigableMap<Long, List<MidiEvent<?>>> getPlayMidiEvents() {
        return Collections.unmodifiableNavigableMap(playPositions);
    }
    //endregion

    //region play(), stop(), isPlaying()
    public boolean isPlaying() {
        try{
            playLock.lock();
            return playMidiEvents.stream().anyMatch(f -> !f.isDone());
        } finally {
            playLock.unlock();
        }
    }

    public void play(){
        try {
            log.info("play");
            playLock.lock();

            if( isPlaying() ){
                log.error("is playing");
                throw new IllegalStateException("is playing");
            }

            var song = getSong();
            if( song==null ){
                log.warn("song is null");
                return;
            }

            var iter0 = song.iterator();

            Supplier<Optional<MidiSong>> songProd = new Supplier<>() {
                final Iterator<MidiSong> iter = iter0;

                @Override
                public Optional<MidiSong> get() {
                    if( iter.hasNext() ) return Optional.ofNullable(iter.next());
                    return Optional.empty();
                }
            };

            play(Optional.empty(), songProd, getPlayLag(), 0, false, System.nanoTime() );
        } finally {
            playLock.unlock();
        }
    }

    public void stop() {
        try{
            log.info("stop");
            playLock.lock();

            if( !isPlaying() ){
                log.debug("is stopped");
                return;
            }

            for( var f : playMidiEvents ){
                if( !f.isDone() ){
                    log.debug("stop future");
                    f.cancel(true);
                }
            }

            stopMidi();
        } finally {
            playLock.unlock();
        }

        onStopped.fire(this);
    }

    private void startMidi() {
        var midi = midiPortOut;
        if( midi!=null && !midi.isRunning() ){
            log.info("midiOut.start()");
            midi.start();
        }
    }

    private void stopMidi() {
        var midi = midiPortOut;
        if( midi!=null && midi.isRunning() ){
//            log.info("send stopAllNotes");
//            usedChannel().forEach(ch -> midi.send(MidiEvent.stopAllNotes(ch)) );

            log.info("midi.stop");
            midi.stop();
        }
    }

    /**
     * Запустить воспроизведение
     * @param currentSongOpt Музыка которую надо воспроизвести
     * @param songProducer Поставшик остальной части мелодии, если currentSongOpt = null, то он будет вызван для первой мелодии
     * @param playLag Задержка перед воспроизведением для планирования
     * @param playIndex Индекс воспроизведения
     * @param useSendAt Использовать {@link MidiPortOut#sendAt(MidiEvent, long)} для указания точного времени воспроизведения
     * @param firstPlayNano Время первого воспроизведения
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected void play(Optional<MidiSong> currentSongOpt, Supplier<Optional<MidiSong>> songProducer, Duration playLag, int playIndex, boolean useSendAt, long firstPlayNano) {
        try{
            log.debug("play(Optional<MidiSong>, Supplier<Optional<MidiSong>>, Duration, int, boolean, long)");
            playLock.lock();

            final var playLagFinal = playLag;

            var songOpt = currentSongOpt.isPresent() ? currentSongOpt : songProducer.get();
            if( songOpt.isEmpty() ){
                if( playIndex > 0 ){
                    log.info("stop playing, no song");
                    stopMidi();
                    onStopped.fire(this);
                }
                return;
            }

            var song = songOpt.get();

            @SuppressWarnings("SimplifyStreamApiCallChains") var events =
                song.getEvents().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(MidiEvent::time))
                    .collect(Collectors.toList());

            if( events.isEmpty() ){
                log.warn("no MidiEvent for playing");
                if( playIndex > 0 ){
                    log.info("stop playing, no MidiEvent for playing");
                    stopMidi();
                    onStopped.fire(this);

                }
                return;
            }

            var midiOut = getMidiOut();
            if( midiOut == null ){
                log.warn("no midi out");
                if( playIndex > 0 ){
                    log.info("stop playing, no midi out");
                    stopMidi();
                    onStopped.fire(this);

                }
                return;
            }

            var sched = getScheduler();
            if( sched == null ){
                log.warn("no scheduler");
                if( playIndex > 0 ){
                    log.info("stop playing, scheduler");
                    stopMidi();
                    onStopped.fire(this);

                }
                return;
            }

            /// ////////////////////////

            if( playIndex==0 ){
//                sched.schedule( ()->{
//                    startMidi();
//                }, 0, TimeUnit.MILLISECONDS);
                startMidi();
            }

            playLag = playLag != null ? playLag : Duration.ofSeconds(0);
            var playLagMS = playLag.toMillis();

            //noinspection SequencedCollectionMethodCanBeUsed
            var firstMidiEvent = events.get(0);
            var firstMidiTime = firstMidiEvent.time();
            long lastSendAtSched = -1;

            for( MidiEvent<?> mevent : events ){
                var mEventTime = mevent.time();
                var mEventDur = Duration.between(firstMidiTime, mEventTime);
                var mEventDurMS = mEventDur.toMillis();

                var sendAtSched = mEventDurMS + playLagMS;
                var sendAtNano = firstPlayNano + playLagMS * 1_000_000;

                lastSendAtSched = sendAtSched;

                playPositions.computeIfAbsent(sendAtSched, x -> new ArrayList<>()).add(mevent);

                playMidiEvents.add(
                    sched.schedule(() -> {
                        playPosition.set(sendAtSched);

                        if( useSendAt ){
                            midiOut.sendAt(mevent, sendAtNano);
                        } else{
                            midiOut.send(mevent);
                        }

                    }, sendAtSched, TimeUnit.MILLISECONDS)
                );
            }

            if( playIndex == 0 ){
                onStarted.fire(this);
            }

            var nextSongOpt = songProducer.get();
            if( nextSongOpt.isEmpty() ){
                playMidiEvents.add(
                    sched.schedule( () ->
                        {
                            log.info("stop playing, no song for continue");
                            stopMidi();
                            onStopped.fire(this);
                        },
                        lastSendAtSched + playLagMS, TimeUnit.MILLISECONDS)
                );
            } else{
                playMidiEvents.add(sched.schedule(() -> {
                        play(currentSongOpt, songProducer, playLagFinal, playIndex + 1, useSendAt, firstPlayNano);
                    },
                    lastSendAtSched - playLagMS, TimeUnit.MILLISECONDS));
            }
        } finally {
            playLock.unlock();
        }
    }
    //endregion
}
