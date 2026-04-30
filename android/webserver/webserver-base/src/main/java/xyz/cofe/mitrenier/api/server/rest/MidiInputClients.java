package xyz.cofe.mitrenier.api.server.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.coll.im.Result;
import xyz.cofe.mitrenier.UnBind;
import xyz.cofe.mitrenier.midi.MidiPortIn;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MidiInputClients implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(MidiInputClients.class);
    private final MidiPortIn<?> midiPortIn;

    public MidiInputClients(MidiPortIn<?> midiPortIn, ScheduledExecutorService scheduler) {
        if( midiPortIn==null ) throw new IllegalArgumentException("midiPortIn==null");
        if( scheduler==null ) throw new IllegalArgumentException("scheduler==null");
        this.midiPortIn = midiPortIn;

        cleanupJob = scheduler.scheduleWithFixedDelay(this::gc, 5, 30, TimeUnit.SECONDS);
    }

    private final ScheduledFuture<?> cleanupJob;

    @Override
    public void close() {
        cleanupJob.cancel(false);
        var clients = this.clients.values().toArray(new MidiInputClient[0]);
        for( var c : clients ){
            stop(c);
        }
    }

    //region keepTimeout : Duration
    private volatile Duration keepTimeout = Duration.ofSeconds(60*5);

    public Duration getKeepTimeout() {
        return keepTimeout;
    }

    public void setKeepTimeout(Duration keepTimeout) {
        if( keepTimeout==null ) throw new IllegalArgumentException("keepTimeout==null");
        this.keepTimeout = keepTimeout;
    }
    //endregion

    //region clients : Map<String, MidiInputClient>
    private final Map<String, MidiInputClient> clients = new ConcurrentSkipListMap<>();

    public Map<String, MidiInputClient> getClients() {
        return Collections.unmodifiableMap(clients);
    }

    private final Map<String, UnBind> clientUnBind = new ConcurrentSkipListMap<>();
    //endregion

    public Result<MidiInputClient,String> startWithUniqID(Function<String,MidiInputClient> clientIdConsumer){
        if( clientIdConsumer==null ) throw new IllegalArgumentException("clientIdConsumer==null");
        synchronized(clients){
            var id = newClientID();
            log.info("generated id {}", id);
            return start( clientIdConsumer.apply(id) );
        }
    }

    //region newClientID()
    private String newClientID(){
        Set<String> ids = clients.keySet();

        var id1 = newClientID_RND();
        if( !ids.contains(id1) )return id1;

        var id2 = newClientID_UUID();
        if( !ids.contains(id2) )return id2;

        return newClientID_NUM();
    }
    private String newClientID_NUM(){
        Set<String> ids = clients.keySet();
        while( true ){
            String id = "client-"+System.nanoTime();
            if( !ids.contains(id)) return id;
        }
    }
    private String newClientID_UUID(){
        return UUID.randomUUID().toString();
    }

    private static final String rndLetters = "qwertyuiopasdfghjklzxcvbnm1234567890";
    private String newClientID_RND(){
        var id = new StringBuilder();
        var rnd = ThreadLocalRandom.current();
        for( var i=0; i<5; i++ ){
            var idx = Math.abs(rnd.nextInt(rndLetters.length()));
            id.append(rndLetters.charAt(idx));
        }
        return id.toString();
    }
    //endregion

    public Result<MidiInputClient,String> start(MidiInputClient client){
        if( client==null ) throw new IllegalArgumentException("client==null");
        synchronized(clients){
            if( clients.containsKey(client.id) )return Result.error("client with id, already registered");
            clients.put(client.id, client);

            UnBind unBind = new UnBind();
            midiPortIn.rawMidiInput().listener(client::accept).unBind(unBind).bind();
            midiPortIn.midiInput().listener(client::accept).unBind(unBind).bind();
            clientUnBind.put(client.id, unBind);

            log.info("start client {}", client.id);
            return Result.ok(client);
        }
    }

    public void stop(MidiInputClient client){
        if( client==null ) throw new IllegalArgumentException("client==null");
        synchronized(clients){
            var optCl = Optional.ofNullable(clientUnBind.get(client.id));
            if( optCl.isPresent() ){
                log.info("stop client {}", client.id);
            }

            optCl.ifPresent(UnBind::close);
            clients.remove(client.id);
            //noinspection resource
            clientUnBind.remove(client.id);
        }
    }

    public List<MidiInputClient> gc(){
        var lst = new ArrayList<MidiInputClient>();
        var now = Instant.now();
        for( var cl : clients.values() ){
            var d = Duration.between(cl.getLastActivity(), now);
            if( d.compareTo(keepTimeout)>0 ){
                lst.add(cl);
            }
        }
        lst.forEach(this::stop);
        log.info("gc cleanup {} clients: {}", lst.size(), lst.stream().map(c -> c.id).collect(Collectors.toSet()));
        return lst;
    }
}
