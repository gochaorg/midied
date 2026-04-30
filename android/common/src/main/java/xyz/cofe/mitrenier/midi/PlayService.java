package xyz.cofe.mitrenier.midi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.coll.im.Result;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PlayService implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(PlayService.class);
    private final Set<Player> players = new CopyOnWriteArraySet<>();
    private final MidiPortOut midiPortOut;
    private final ScheduledExecutorService scheduler;

    public PlayService(MidiPortOut midiPortOut, ScheduledExecutorService scheduler) {
        if( midiPortOut==null ) throw new IllegalArgumentException("midiPortOut==null");
        if( scheduler==null ) throw new IllegalArgumentException("scheduler==null");
        this.midiPortOut = midiPortOut;
        this.scheduler = scheduler;

        var dur = cleanupPeriod;
        if( dur!=null && !dur.isNegative() ){
            cleanupJob = scheduler.schedule(this::cleanup, dur.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private volatile boolean closed = false;

    @Override
    public void close() throws IOException {
        if( closed )return;
        closed = true;

        if( cleanupJob!=null && !cleanupJob.isDone() ){
            cleanupJob.cancel(false);
        }
        cleanupJob = null;

        closeAllPlayers();
        players.clear();
    }

    //region cleanup
    private volatile ScheduledFuture<?> cleanupJob;

    //region keepTimeout : Duration
    private volatile Duration keepTimeout = Duration.ofSeconds(15);

    public Duration getKeepTimeout() {
        return keepTimeout;
    }

    public void setKeepTimeout(Duration keepTimeout) {
        if( keepTimeout==null ) throw new IllegalArgumentException("keepTimeout==null");
        if( keepTimeout.isNegative() ) throw new IllegalArgumentException("keepTimeout.isNegative()");

        this.keepTimeout = keepTimeout;
    }
    //endregion

    //region cleanupPeriod : Duration
    private Duration cleanupPeriod = Duration.ofSeconds(5);

    public Duration getCleanupPeriod() {
        return cleanupPeriod;
    }

    public void setCleanupPeriod(Duration cleanupPeriod) {
        if( closed )throw new IllegalStateException();
        synchronized(scheduler) {
            this.cleanupPeriod = cleanupPeriod;
            if( cleanupJob!=null && !cleanupJob.isDone() ){
                cleanupJob.cancel(false);
            }
            cleanupJob = null;

            if( cleanupPeriod!=null && !cleanupPeriod.isNegative() ){
                cleanupJob = scheduler.schedule(this::cleanup, cleanupPeriod.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }
    //endregion

    public void cleanup(){
        log.info("cleanup");
        var removeSet = new HashSet<Player>();
        for( var player : players ){
            log.debug("found player {} playing={} stopped={} created={}", player.getId(), player.isPlaying(), player.getPlayStopped(), player.getCreatedAt() );
            if( !player.isPlaying() ){
                Instant t = player.getPlayStopped().orElse( player.getCreatedAt() );
                var dur = Duration.between(t, Instant.now());

                log.debug("player {} playing={} dur={} keep timeout={}", player.getId(), player.isPlaying(), dur, keepTimeout);
                if( dur.compareTo(keepTimeout)>0 ){
                    removeSet.add(player);
                }
            }
        }

        for( var player : removeSet ){
            release(player);
        }
    }
    //endregion

    public Set<Player> getPlayers(){
        return Collections.unmodifiableSet(players);
    }

    //region createPlayer(), release()
    private final Lock startPlayLock = new ReentrantLock();

    public Result<Player,String> createPlayer(Consumer<Player> consumer){
        if( closed )throw new IllegalStateException();
        if( consumer==null ) throw new IllegalArgumentException("consumer==null");

        try {
            startPlayLock.lock();
            var players = getActivePlayers();
            if( !players.isEmpty() ){
                return Result.error(
                    "can't allocate player, has follow active players: "+players.stream().map(Player::getId).collect(Collectors.toSet())
                );
            }

            var player = createPlayer();
            players.add(player);
            consumer.accept(player);

            return Result.ok(player);
        } finally {
            startPlayLock.unlock();
        }
    }

    private SvcPlayer createPlayer() {
        SvcPlayer player = new SvcPlayer( (tryStartPlayer,exec) -> {
            startPlayLock.lock();
            try{
                var activePlayers = getActivePlayers();
                if( !activePlayers.isEmpty() ){
                    var msg = "can't start play, has follow active players: " + activePlayers.stream().map(Player::getId).collect(Collectors.toSet());
                    log.warn(msg);
                    throw new IllegalStateException(msg);
                }

                exec.run();
            } finally {
                startPlayLock.unlock();
            }
        });

        player.setMidiOut(midiPortOut);
        player.setScheduler(scheduler);
        log.info("createPlayer player {}",player.getId());
        return player;
    }

    public void release( Player player ){
        if( player==null ) throw new IllegalArgumentException("player==null");
        log.info("release player {}",player.getId());
        player.stop();
        players.remove(player);
        if( player instanceof Closeable c ){
            try{
                c.close();
            } catch ( IOException e ) {
                log.error("can't close player "+player, e);
            }
        }
    }
    //endregion

    public Set<Player> getActivePlayers(){
        return players.stream().filter(Player::isPlaying).collect(Collectors.toSet());
    }

    //region stopAllPlayers()
    public void stopAllPlayers(){
        log.info("stopAllPlayers");
        players.forEach(Player::stop);
    }
    //endregion

    //region closeAllPlayers()
    private void closeAllPlayers(){
        log.info("closeAllPlayers");
        for( var player : players ){
            player.stop();
            if( player instanceof Closeable c ){
                try{
                    c.close();
                } catch ( IOException e ) {
                    log.error("can't close player "+player, e);
                }
            }
        }
    }
    //endregion
}
