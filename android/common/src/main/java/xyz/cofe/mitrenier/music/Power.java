package xyz.cofe.mitrenier.music;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Сила нажатия
 */
public sealed interface Power {
    /**
     * Сильная доля (strong beat)
     */
    record Strong() implements Power {}

    static Strong strong(){ return new Strong(); }

    /* Полусильная доля (medium-strong beat, иногда называют secondary accent) */
    record Medium() implements Power {}

    static Medium medium(){ return new Medium(); }

    /* Слабая доля (weak beat) */
    record Weak() implements Power {}

    static Weak weak(){ return new Weak(); }

    /* Внеакцентная доля */
    @Deprecated
    record Offbeat() implements Power {}

    public static class JSONView implements JsonSerializer<Power>,
                                            JsonDeserializer<Power> {
        @Override
        public Power deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
            JsonParseException {
            var s = json.getAsString().toLowerCase();
            if( s.equals("strong") )return new Strong();
            if( s.equals("medium") )return new Medium();
            if( s.equals("weak") )return new Weak();
            if( s.equals("offbeat") )return new Offbeat();
            return null;
        }

        @Override
        public JsonElement serialize(Power src, Type typeOfSrc, JsonSerializationContext context) {
            if( src instanceof Strong )return new JsonPrimitive("strong");
            else if( src instanceof Medium )return new JsonPrimitive("medium");
            else if( src instanceof Weak )return new JsonPrimitive("weak");
            else if( src instanceof Offbeat )return new JsonPrimitive("offbeat");
            return new JsonPrimitive("?");
        }
    }
}
