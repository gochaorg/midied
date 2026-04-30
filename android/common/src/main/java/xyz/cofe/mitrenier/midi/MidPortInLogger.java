package xyz.cofe.mitrenier.midi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class MidPortInLogger implements Consumer<MidiEvent<?>> {
    private static final Logger log = LoggerFactory.getLogger(MidPortInLogger.class);

    public void accept(MidiEvent<?> event){
        if( event==null ){
            log.info("event null");
        }else{
            log.info("accept {}", event);
        }
    }
}
