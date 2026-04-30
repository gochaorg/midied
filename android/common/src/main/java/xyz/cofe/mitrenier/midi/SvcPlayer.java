package xyz.cofe.mitrenier.midi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

public class SvcPlayer extends Player {
    private static final Logger log = LoggerFactory.getLogger(SvcPlayer.class);
    private final BiConsumer<SvcPlayer, Runnable> tryPlay;

    public SvcPlayer(BiConsumer<SvcPlayer, Runnable> tryPlay) {
        if( tryPlay==null ) throw new IllegalArgumentException("tryPlay==null");
        this.tryPlay = tryPlay;
    }

    @Override
    public void play() {
        log.info("try accept lock");
        tryPlay.accept(this, ()->{
            log.info("accepted lock");
            super.play();
        });
    }
}
