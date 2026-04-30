package xyz.cofe.mitrenier.music;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import xyz.cofe.mitrenier.midi.MidiNote;

import java.lang.reflect.Type;

/**
 * Тон/ высота ноты
 */
public sealed interface Pitch {
    public static class JSONView implements JsonSerializer<Pitch>,
                                        JsonDeserializer<Pitch> {
        @Override
        public Pitch deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
            JsonParseException {
            var obj = json.getAsJsonObject();
            switch( obj.get("type").getAsString().toLowerCase() ){
                case "abs" -> {
                    return new AbsolutePitch(obj.get("note").getAsInt());
                }
                case "rel" -> {
                    return new RelativePitch(obj.get("note").getAsInt());
                }
                case "any" -> {
                    return new AnyPitch();
                }
                case "none" -> {
                    return new NonePitch();
                }
                default ->
                    throw new JsonParseException("undfeined type");
            }
        }

        @Override
        public JsonElement serialize(Pitch src, Type typeOfSrc, JsonSerializationContext context) {
            var obj = new JsonObject();

            if( src instanceof AbsolutePitch a ){
                obj.addProperty("type", "abs");
                obj.addProperty("note", a.note());
            }else if( src instanceof RelativePitch r ){
                obj.addProperty("type", "rel");
                obj.addProperty("note", r.note());
            }else if( src instanceof AnyPitch a ){
                obj.addProperty("type", "any");
            }else if( src instanceof NonePitch n ){
                obj.addProperty("type", "none");
            }

            return obj;
        }
    }

    /**
     * Отсуствие звучания
     */
    record NonePitch() implements Pitch {}

    static NonePitch none(){ return new NonePitch(); }

    /**
     * Любая нота
     */
    record AnyPitch() implements Pitch {}

    static AnyPitch any(){ return new AnyPitch(); }

    /**
     * Нота относительно октавы
     *
     * @param note номер ноты 0..11, где 0 - До (C), 1 - До-диез (C#)
     */
    record RelativePitch(int note) implements Pitch, MidiNote {
        public RelativePitch {
            if( note < 0 ) throw new IllegalArgumentException("note<0");
            if( note > 11 ) throw new IllegalArgumentException("note>11");
        }

        @Override
        public int octaveIndex() {
            return 5;
        }
    }

    static RelativePitch rel(int note){ return new RelativePitch(note); }

    /**
     * Нота в нотации midi {@link MidiNote#note()}
     *
     * @param note номер ноты, 0..127
     */
    record AbsolutePitch(int note) implements Pitch, MidiNote {
        public AbsolutePitch {
            if( note < 0 || note > 127 ) throw new IllegalArgumentException("note<0 || note>127");
        }
    }

    static AbsolutePitch abs(int note){ return new AbsolutePitch(note); }
}
