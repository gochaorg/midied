package xyz.cofe.mitrenier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.coll.im.ImList;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Вычисляемое/обновляемое свойство
 *
 * @param <V> тип
 */
public class LazySingle<V> implements Supplier<V>
                                      {
    private static final Logger logger = LoggerFactory.getLogger(LazySingle.class);
    private final WeakHashMap<LazySingle<?>, Object> updateDependenciesListeners = new WeakHashMap<>();
    private String logName;

    private volatile V value;
    private final Function<?, V> build;
    private final Supplier<?> arg;

    public <A> LazySingle(Supplier<A> arg, Function<A, V> builder) {
        if( builder == null ) throw new IllegalArgumentException("builder==null");
        if( arg == null ) throw new IllegalArgumentException("arg==null");
        this.build = builder;
        this.arg = arg;
    }

    public LazySingle(Supplier<V> initial) {
        if( initial == null ) throw new IllegalArgumentException("initial==null");

        value = null;
        //noinspection FunctionalExpressionCanBeFolded
        arg = initial::get;

        //noinspection unchecked,rawtypes
        build = (Function) ((v) -> v);
    }

    public static <V> LazySingle<V> of(V initial){
        return new LazySingle<V>(()->initial);
    }

    public LazySingle<V> unBind(UnBind unBind) {
        if( unBind == null ) throw new IllegalArgumentException("unBind==null");
        unBind.runnables().add(this::close);
        return this;
    }

    public void close() {
        clear();
        strongUpdateListeners.clear();
        weakUpdateListeners.clear();
    }

    private void log(String message) {
        var ln = logName;
        if( ln != null && message != null ){
            //LoggerFactory.getLogger(LazySingle.class.getName()+"."+ln).info(message);
            logger.info("[" + ln + "] " + message);
        }
    }

    public LazySingle<V> logSuffix(String name) {
        logName = name;
        return this;
    }

    private volatile Instant cachedAt;
    private volatile Duration cacheTimeToLive;
    private volatile boolean useCache = false;

    public LazySingle<V> cached(Duration duration){
        if( duration==null ) throw new IllegalArgumentException("duration==null");
        if( duration.isNegative() ) throw new IllegalArgumentException("duration.isNegative()");

        var result = new LazySingle(arg, build);
        result.useCache = true;
        result.cacheTimeToLive = duration;
        return result;
    }

    private volatile LazySingle<V> binded;

    public class Binding {
        private LazySingle<V> source;

        public Binding(LazySingle<V> source) {
            this.source = source;
        }

        private UnBind unBind;
        public Binding unBind(UnBind unBind){
            if( unBind==null ) throw new IllegalArgumentException("unBind==null");
            this.unBind = unBind;
            return this;
        }

        public LazySingle<V> bind(){
            binded = source;
            binded.updateDependenciesListeners.put(LazySingle.this, true);
            if( unBind!=null ){
                unBind.addRun( ()->{
                    if( binded!=null ){
                        binded.updateDependenciesListeners.remove(LazySingle.this);
                    }
                    binded = null;
                });
            }
            return LazySingle.this;
        }
    }

    public Binding bind(LazySingle<V> source){
        if( source==null ) throw new IllegalArgumentException("source==null");
        if( binded!=null ) throw new IllegalArgumentException("already binded");
        return new Binding(source);
    }

    public boolean isBinded(){ return binded!=null; }

    public LazySingle<V> unBind(){
        if( binded!=null ){
            binded.updateDependenciesListeners.remove(LazySingle.this);
        }
        binded = null;
        return this;
    }

    public V get() {
        boolean fireChanges = false;

        if( value != null ){
            if( !useCache )return value;

            if( cachedAt==null ){
                cachedAt = Instant.now();
            }
            if( cacheTimeToLive!=null ){
                var age = Duration.between(Instant.now(), cachedAt);
                if( age.compareTo(cacheTimeToLive)<=0 ){
                    return value;
                }
            }else{
                fireChanges = true;
            }
        }

        V prev = null;
        V cur = null;

        synchronized(this) {
            if( value != null ) return value;

            log("compute value");

            if( binded!=null ){
                value = binded.get();
                cur = value;
            }else{
                Object a = arg.get();

                //noinspection unchecked,rawtypes
                value = (V) (((Function) build).apply(a));
                cur = value;
            }
        }

        if( !Objects.equals(prev, cur) || fireChanges ){
            for( var ls : weakUpdateListeners.keySet() ) ls.accept(prev, cur);
            for( var ls : strongUpdateListeners ) ls.accept(prev, cur);
        }

        return cur;
    }

    public <R> R apply(Function<V, R> compute) {
        if( compute == null ) throw new IllegalArgumentException("compute==null");
        return compute.apply(get());
    }

    public void accept(Consumer<V> compute) {
        if( compute == null ) throw new IllegalArgumentException("compute==null");
        compute.accept(get());
    }

    private final WeakHashMap<BiConsumer<V, V>, Object> weakUpdateListeners = new WeakHashMap<>();
    private final List<BiConsumer<V, V>> strongUpdateListeners = new CopyOnWriteArrayList<>();

    public class OnUpdate {
        private final BiConsumer<V, V> listener;

        public OnUpdate(BiConsumer<V, V> listener) {
            if( listener == null ) throw new IllegalArgumentException("listener==null");
            this.listener = listener;
        }

        private boolean weak = false;

        public OnUpdate weak(boolean v) {
            weak = v;
            return this;
        }

        public OnUpdate weak() {return weak(true);}

        private UnBind unBind;

        public OnUpdate unBind(UnBind unBind) {
            this.unBind = unBind;
            return this;
        }

        public LazySingle<V> bind() {
            if( weak ){
                weakUpdateListeners.put(listener, true);
                if( unBind != null ) unBind.runnables().add(() -> {
                    weakUpdateListeners.remove(listener);
                });
                return LazySingle.this;
            } else{
                strongUpdateListeners.add(listener);
                if( unBind != null ) unBind.runnables().add(() -> {
                    strongUpdateListeners.remove(listener);
                });
                return LazySingle.this;
            }
        }
    }

    public OnUpdate onUpdate(BiConsumer<V, V> listener) {
        if( listener == null ) throw new IllegalArgumentException("listener==null");
        return new OnUpdate(listener);
    }

    public LazySingle<V> update(V newValue) {
        if( binded!=null )throw new IllegalStateException("already binded");

        if( newValue == null ) throw new IllegalArgumentException("newValue==null");
        log("update");

        V prev = null;
        V cur = null;

        synchronized(this) {
            prev = value;

            if( value instanceof Closeable cl ){
                log("clear on update");
                try{
                    cl.close();
                } catch ( IOException e ) {
                    logger.error("can't close", e);
                }
            }

            value = newValue;
            cur = newValue;
        }

        for( var listener : updateDependenciesListeners.keySet() ){
            listener.clear();
        }

        if( !Objects.equals(prev, cur) ){
            for( var ls : weakUpdateListeners.keySet() ) ls.accept(prev, cur);
            for( var ls : strongUpdateListeners ) ls.accept(prev, cur);
        }

        return this;
    }

    public LazySingle<V> clear() {
        log("clear");
        synchronized(this) {
            if( value == null ) return this;

            var v = value;
            value = null;

            if( v instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof Closeable cl ){
                try{
                    cl.close();
                } catch ( IOException e ) {
                    logger.error("can't close", e);
                }
            } else if( v instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof LazySingle<?> s ){
                try {
                    s.close();
                } catch ( Throwable e ) {
                    logger.error("can't close", e);
                }
            } else if( v instanceof ImList<?> coll ){
                for( var v1 : coll ){
                    if( v1 instanceof Closeable cl ){
                        try{
                            cl.close();
                        } catch ( IOException e ) {
                            logger.error("can't close", e);
                        }
                    }
                }
            } else if( v instanceof Iterable<?> coll ){
                for( var v1 : coll ){
                    if( v1 instanceof Closeable cl ){
                        try{
                            cl.close();
                        } catch ( IOException e ) {
                            logger.error("can't close", e);
                        }
                    }else if( v1 instanceof LazySingle<?> s ){
                        try {
                            s.close();
                        } catch ( Throwable e ) {
                            logger.error("can't close", e);
                        }
                    }
                }
            } else if( v instanceof Closeable cl ){
                try{
                    cl.close();
                } catch ( IOException e ) {
                    logger.error("can't close", e);
                }
            } else if( v instanceof LazySingle<?> s ){
                try {
                    s.close();
                } catch ( Throwable e ) {
                    logger.error("can't close", e);
                }
            }
        }

        for( var listener : updateDependenciesListeners.keySet() ){
            listener.clear();
        }

        return this;
    }

    public <R> LazySingle<R> map(Function<V, R> compute) {
        if( compute == null ) throw new IllegalArgumentException("compute==null");
        var ls = new LazySingle<>(()->{
            var v = get();
            return v;
        }, compute);
        this.updateDependenciesListeners.put(ls, 1);
        return ls;
    }

    public <R, V2> LazySingle<R> compose(LazySingle<V2> other, BiFunction<V, V2, R> compose) {
        if( other == null ) throw new IllegalArgumentException("other==null");
        if( compose == null ) throw new IllegalArgumentException("compose==null");
        var ls = new LazySingle<>(this, (v1) -> compose.apply(v1, other.get()));
        this.updateDependenciesListeners.put(ls, 1);
        other.updateDependenciesListeners.put(ls, 2);
        return ls;
    }
}
