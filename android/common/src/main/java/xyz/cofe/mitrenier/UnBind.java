package xyz.cofe.mitrenier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Отписка от уведомлений
 *
 * @param runnables
 */
public record UnBind(
    List<Runnable> runnables
) implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(UnBind.class);

    public UnBind(){
        this(new ArrayList<>());
    }

    public UnBind add(Runnable r){
        if( r==null ) throw new IllegalArgumentException("r==null");
        runnables.add(r);
        return this;
    }

    public UnBind addRun(Runnable r){
        if( r==null ) throw new IllegalArgumentException("r==null");
        runnables.add(r);
        return this;
    }

    public UnBind add(Closeable r){
        if( r==null ) throw new IllegalArgumentException("r==null");
        runnables.add(()->{
            try{
                r.close();
            } catch ( IOException e ) {
                log.error("can't close {}", r, e);
            }
        });
        return this;
    }

    public void close() {
        runnables.forEach(Runnable::run);
    }
}
