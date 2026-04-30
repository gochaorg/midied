package xyz.cofe.mitrenier.player.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.MidiEvent.NoteOff;
import xyz.cofe.mitrenier.midi.MidiEvent.NoteOn;
import xyz.cofe.mitrenier.midi.MidiPortOut;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class JavaMidiPortOut implements MidiPortOut {
    private static final Logger log = LoggerFactory.getLogger(JavaMidiPortOut.class);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<Runnable> closing = new ArrayList<>();
        private List<Consumer<Synthesizer>> synthConfigure = new ArrayList<>();
        private boolean resetSynthesizer = false;

        public boolean resetSynthesizer() {return resetSynthesizer;}

        public Builder resetSynthesizer(boolean v) {
            resetSynthesizer = v;
            return this;
        }

        public Builder soundBank(File sf2File) {
            if( sf2File == null ) throw new IllegalArgumentException("sf2File==null");
            try{
                return soundBank(MidiSystem.getSoundbank(sf2File));
            } catch ( InvalidMidiDataException | IOException e ) {
                throw new RuntimeException(e);
            }
        }

        public Builder soundBank(Soundbank sb) {
            if( sb == null ) throw new IllegalArgumentException("sb==null");
            synthConfigure.add(synth -> {
                if( synth.isSoundbankSupported(sb) ){
                    Soundbank defsbk = synth.getDefaultSoundbank();
                    synth.unloadAllInstruments(defsbk);

                    synth.loadAllInstruments(sb);
                }
            });
            return this;
        }

        @SuppressWarnings("Convert2MethodRef")
        public JavaMidiPortOut build() {
            var jMidi = new JavaMidiPortOut();
            jMidi.closing = closing;

            try{
                var synthesizer = MidiSystem.getSynthesizer();

                Runnable openning = () -> {
                    try{
                        if( !synthesizer.isOpen() ){
                            log.info("synthesizer.open()");
                            synthesizer.open();
                        }

                        jMidi.receiver = synthesizer.getReceiver();
                    } catch ( MidiUnavailableException e ) {
                        throw new RuntimeException(e);
                    }

                    log.info("configure");
                    for( var conf : synthConfigure ){
                        conf.accept(synthesizer);
                    }

                    closing.add(() -> {
                        if( synthesizer.isOpen() ){
                            log.info("synthesizer.close()");
                            synthesizer.close();
                        }
                    });

                    //jMidi.openning

                    jMidi.tryGetChannel = (chNo) -> {
                        var channels = synthesizer.getChannels();
                        if( chNo >= 0 && chNo < channels.length ){
                            return Optional.of(channels[chNo]);
                        }
                        return Optional.empty();
                    };

                    closing.add(() -> {
                        jMidi.tryGetChannel = null;
                        jMidi.channels.clear();
                    });

                    if( resetSynthesizer ){
                        log.info("resetSynthesizer");
                        var recv = jMidi.receiver;
                        if( recv != null ){
                            for( var ci = 0; ci < 16; ci++ ){
                                try{
                                    // All Sound Off
                                    recv.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, ci, 0x78, 0), -1);
                                    // All Notes Off
                                    recv.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, ci, 0x7B, 0), -1);
                                    // Reset All Controllers
                                    recv.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, ci, 0x79, 0), -1);
                                } catch ( InvalidMidiDataException e ) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                };

                //openning.run();
                jMidi.openning = openning;
            } catch ( MidiUnavailableException e ) {
                throw new RuntimeException(e);
            }
            return jMidi;
        }
    }

    private Runnable openning;
    private List<Runnable> closing;
    private Function<Integer, Optional<MidiChannel>> tryGetChannel;
    private Map<Integer, MidiChannel> channels = new HashMap<>();
    private Receiver receiver;

    private volatile boolean started = false;

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public void start() {
        log.info("start");

        var open = openning;
        if( open != null ){
            open.run();
            started = true;
        }
    }

    private Optional<MidiChannel> findChannel(int channelNum) {
        var ch = channels.get(channelNum);
        if( ch != null ){
            return Optional.of(ch);
        }

        var tryGetCh = tryGetChannel;
        if( tryGetCh == null ) return Optional.empty();

        var chOpt = tryGetCh.apply(channelNum);
        chOpt.ifPresent(midiChannel -> {
            channels.put(channelNum, midiChannel);
        });

        return chOpt;
    }

    @Override
    public void send(MidiEvent<?> event) {
        if( event == null ) throw new IllegalArgumentException("event==null");

        if( event instanceof MidiEvent.Channel ch ){
            findChannel(ch.channel()).ifPresent(chMidi -> {
                if( event instanceof NoteOn noteOn ){
                    chMidi.noteOn(noteOn.note(), noteOn.velocity());
                } else if( event instanceof NoteOff noteOff ){
                    chMidi.noteOff(noteOff.note(), noteOff.velocity());
                } else if( event instanceof MidiEvent.ProgramChange prog ){
                    chMidi.programChange(prog.programm());
                } else if( event instanceof MidiEvent.PolyphonicKeyPressure pk ){
                    chMidi.setPolyPressure(pk.note(), pk.pressureValue());
                } else if( event instanceof MidiEvent.ControlChange cc ){
                    chMidi.controlChange(cc.controller(), cc.value());
                } else if( event instanceof MidiEvent.ChannelPressure cp ){
                    chMidi.setChannelPressure(cp.value());
                } else if( event instanceof MidiEvent.PitchWheelChange pc ){
                    chMidi.setPitchBend(pc.value());
                } else if( event instanceof MidiEvent.ChannelModeMessages cmm ){
                    chMidi.controlChange(cmm.controller(), cmm.value());
                }
            });
        }
    }

    @Override
    public void sendAt(MidiEvent<?> event, long nano) {
        var recv = receiver;

        if( recv == null ){
            send(event);
            return;
        }

        if( event instanceof MidiEvent.Channel ch ){
            var microTime = nano / 1000;
            findChannel(ch.channel()).ifPresent(chMidi -> {
                var raw = event.toRawMidiEvent();
                try{
                    var sm =
                        raw.data().length == 3
                            ? new ShortMessage(raw.data()[0] & 0xFF, raw.data()[1] & 0xFF, raw.data()[2] & 0xFF)
                            : raw.data().length == 4
                            ? new ShortMessage(raw.data()[0] & 0xFF, raw.data()[1] & 0xFF, raw.data()[2] & 0xFF, raw.data()[3] & 0xFF)
                            : new ShortMessage();
                    recv.send(sm, microTime);
                } catch ( InvalidMidiDataException e ) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Override
    public void stop() {
        log.info("stop");
        var closing = this.closing;
        if( closing != null ){
            for( var c : closing ){
                if( c != null ){
                    c.run();
                }
            }
            closing.clear();
        }

        started = false;
    }
}
