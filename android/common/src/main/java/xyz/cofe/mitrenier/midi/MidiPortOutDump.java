package xyz.cofe.mitrenier.midi;

import xyz.cofe.mitrenier.str.StrAlign;
import xyz.cofe.mitrenier.str.Template;

import java.io.IOException;
import java.time.Instant;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class MidiPortOutDump implements MidiPortOut {
    private final MidiPortOut target;
    private final Appendable output;

    public MidiPortOutDump(MidiPortOut target, Appendable output) {
        if( target==null ) throw new IllegalArgumentException("target==null");
        if( output==null ) throw new IllegalArgumentException("output==null");
        this.target = target;
        this.output = output;
    }

    @Override
    public boolean isRunning() {return target.isRunning();}

    private static final Pattern AlignPattern = Pattern.compile("^(?<text>.*)((?<dir>[=<>])(?<len>\\d+))$");

    public record MidiEventPrinter<E>(Template template, BiFunction<E,String,String> eval) {
        public String toString(E event){
            return template.toString( code -> {
                var m = AlignPattern.matcher(code);
                if( m.matches() ){
                    var dir = m.group("dir");
                    var len = Integer.parseInt(m.group("len"));
                    var text = m.group("text");
                    return switch( dir ){
                        case "<" -> StrAlign.strAlign(eval.apply(event,text)).left(len).toString();
                        case ">" -> StrAlign.strAlign(eval.apply(event,text)).right(len).toString();
                        case "=" -> StrAlign.strAlign(eval.apply(event,text)).left().right().len(len).toString();
                        default -> StrAlign.strAlign(eval.apply(event,text)).left().len(len).toString();
                    };
                }
                return eval.apply(event,code);
            });
        }
    }

    private MidiEventPrinter<MidiPortOutEvent.Start> startTemplate = new MidiEventPrinter<>(
        Template.parse("${at<30} start\n"),
        (ev,code) -> switch( code ){
            case "at" -> ev.at().toString();
            default -> "";
        }
    );

    private MidiEventPrinter<MidiPortOutEvent.Stop> stopTemplate = new MidiEventPrinter<>(
        Template.parse("${at<30} stop\n"),
        (ev,code) -> switch( code ){
            case "at" -> ev.at().toString();
            default -> "";
        }
    );

    private MidiEventPrinter<MidiEvent.NoteOn> noteOnTemplate = new MidiEventPrinter<>(
        Template.parse("NoteOn  ch=${channel<2} note=${note.name<5} v=${velocity<3} ${time<30} ts=${timestamp}"),
        (midiEvent, code) -> switch( code ){
            case "time" -> midiEvent.time().toString();
            case "timestamp" -> ""+midiEvent.timestampNano();
            case "channel" -> ""+midiEvent.channel();
            case "note" -> ""+midiEvent.note();
            case "note.name" -> ""+midiEvent.noteName();
            case "velocity" -> ""+midiEvent.velocity();
            default -> midiEvent.toString();
        }
    );

    private MidiEventPrinter<MidiEvent.NoteOff> noteOffTemplate = new MidiEventPrinter<>(
        Template.parse("NoteOff ch=${channel<2} note=${note.name<5} v=${velocity<3} ${time<30} ts=${timestamp}"),
        (midiEvent, code) -> switch( code ){
            case "time" -> midiEvent.time().toString();
            case "timestamp" -> ""+midiEvent.timestampNano();
            case "channel" -> ""+midiEvent.channel();
            case "note" -> ""+midiEvent.note();
            case "note.name" -> ""+midiEvent.noteName();
            case "velocity" -> ""+midiEvent.velocity();
            default -> midiEvent.toString();
        }
    );

    private MidiEventPrinter<MidiEvent.ProgramChange> progTemplate = new MidiEventPrinter<>(
        Template.parse("${default}"),
        (midiEvent, code) -> switch( code ){
            case "time" -> midiEvent.time().toString();
            case "timestamp" -> ""+midiEvent.timestampNano();
            case "channel" -> midiEvent.time().toString();
            case "prog" -> ""+midiEvent.programm();
            default -> midiEvent.toString();
        }
    );

    private MidiEventPrinter<MidiEvent<?>> midiEventTemplate = new MidiEventPrinter<>(
        Template.parse("${default}"),
        (midiEvent, code) -> {
            if( midiEvent instanceof MidiEvent.NoteOn n )return noteOnTemplate.toString(n);
            if( midiEvent instanceof MidiEvent.NoteOff n )return noteOffTemplate.toString(n);
            if( midiEvent instanceof MidiEvent.ProgramChange n )return progTemplate.toString(n);
            if( midiEvent instanceof MidiEvent.NoteOn n )return noteOnTemplate.toString(n);
            return midiEvent.toString();
        }
    );

    private MidiEventPrinter<MidiPortOutEvent.Send> sendTemplate = new MidiEventPrinter<>(
        Template.parse("${at<30} send ${event}\n"),
        (ev,code) -> switch( code ){
            case "event" -> midiEventTemplate.toString( ev.event() );
            case "at" -> ev.at().toString();
            default -> "";
        }
    );

    private MidiEventPrinter<MidiPortOutEvent.SendAt> sendAtTemplate = new MidiEventPrinter<>(
        Template.parse("${at<30} sendAt ${nano} ${event}\n"),
        (ev,code) -> switch( code ){
            case "event" -> midiEventTemplate.toString( ev.event() );
            case "at" -> ev.at().toString();
            default -> "";
        }
    );


    @Override
    public void start() {
        target.start();
        try{
            output.append(startTemplate.toString(new MidiPortOutEvent.Start(Instant.now())));
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void send(MidiEvent<?> event) {
        target.send(event);
        try{
            output.append(sendTemplate.toString(new MidiPortOutEvent.Send(Instant.now(), event)));
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendAt(MidiEvent<?> event, long nano) {
        target.sendAt(event, nano);
        try{
            output.append(sendAtTemplate.toString(new MidiPortOutEvent.SendAt(Instant.now(),event,nano)));
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        target.stop();
        try{
            output.append(stopTemplate.toString(new MidiPortOutEvent.Stop(Instant.now())));
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        }
    }
}
