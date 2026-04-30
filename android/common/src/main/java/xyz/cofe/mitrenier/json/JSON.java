package xyz.cofe.mitrenier.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.MidiEventCollector;
import xyz.cofe.mitrenier.midi.MidiEventJsonView;
import xyz.cofe.mitrenier.midi.RawMidiEvent;
import xyz.cofe.mitrenier.math.Fraction;
import xyz.cofe.mitrenier.music.MPattern;
import xyz.cofe.mitrenier.music.MidiSong;
import xyz.cofe.mitrenier.music.Pitch;
import xyz.cofe.mitrenier.music.Power;
import xyz.cofe.mitrenier.music.TimeBeats;
//import xyz.cofe.mitrenier.practice.Round;
//import xyz.cofe.mitrenier.practice.RoundJsonView;

import java.time.Instant;
import java.util.ServiceLoader;

/**
 * Сериализация JSON
 */
public class JSON {
    public static final MidiEventJsonView midiNoteSerializer = new MidiEventJsonView();

    /**
     * Конфигурация gson builder, добавляет поддержку для типов определенных в модуле common
     * @param builder gson builder
     * @return gson builder
     */
    public static GsonBuilder configure( GsonBuilder builder ){
        if( builder==null ) throw new IllegalArgumentException("builder==null");

        //gsonBuilder.registerTypeAdapter(Long.class, LongAsStringOrNumberTypeAdapter.adapter);

        builder
            .registerTypeAdapter(Long.class, LongAsStringOrNumberTypeAdapter.adapter)
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .registerTypeAdapter(RawMidiEvent.class, RawMidiEvent.JSONView.serializing)
            .registerTypeAdapter(MidiEventCollector.class, MidiEventCollector.JSONView.serializing)
            .registerTypeAdapter(Pitch.class, new Pitch.JSONView())
            .registerTypeAdapter(Power.class, new Power.JSONView())
            .registerTypeAdapter(Fraction.class, new Fraction.JSONView())
            .registerTypeAdapter(TimeBeats.class, new TimeBeats.JSONView())
            .registerTypeAdapter(MPattern.class, new MPattern.JSONView())
            .registerTypeAdapter(MidiEvent.class, midiNoteSerializer)
            .registerTypeAdapter(MidiEvent.NoteOn.class, midiNoteSerializer)
            .registerTypeAdapter(MidiEvent.NoteOff.class, midiNoteSerializer)
            .registerTypeAdapter(MidiEvent.ControlChange.class, midiNoteSerializer)
            .registerTypeAdapter(MidiEvent.ProgramChange.class, midiNoteSerializer)
            .registerTypeAdapter(MidiEvent.PolyphonicKeyPressure.class, midiNoteSerializer)
            .registerTypeAdapter(MidiEvent.ChannelPressure.class, midiNoteSerializer)
            .registerTypeAdapter(MidiEvent.PitchWheelChange.class, midiNoteSerializer)
            .registerTypeAdapter(MidiEvent.ChannelModeMessages.class, midiNoteSerializer)
            //.registerTypeAdapter(Round.class, new RoundJsonView())
            .registerTypeAdapter(MidiSong.class, new MidiSong.JSONView())
            .setPrettyPrinting();

        for( var gsonConf : ServiceLoader.load(GsonConfigure.class) ){
            gsonConf.configure(builder);
        }

        return builder;
    }

    public static final Gson gson = configure(new GsonBuilder())
        .create();

    /**
     * Преобразование в json
     * @param obj объект
     * @return json
     */
    public static String toJson(Object obj){
        return gson.toJson(obj);
    }

    /**
     * Де-сериализация из json
     * @param json json
     * @param cls целевой тип
     * @return значение
     * @param <A> целевой тип
     */
    public static <A> A fromJson(String json, Class<A> cls){
        if( json==null )throw new IllegalArgumentException("json==null");
        if( cls==null )throw new IllegalArgumentException("cls==null");
        return gson.fromJson(json, cls);
    }
}
