package xyz.cofe.mitrenier.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import xyz.cofe.coll.im.ImList;
import xyz.cofe.mitrenier.math.Fraction;

import java.lang.reflect.Type;
import java.util.ArrayList;

/**
 * Начало удара
 * @param at Время начала
 * @param beats Какие ноты
 */
public record TimeBeats(Fraction at, ImList<Beat> beats) {
    public static class JSONView
        implements JsonSerializer<TimeBeats>,
                   JsonDeserializer<TimeBeats>
    {
        @Override
        public TimeBeats deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
            JsonParseException
        {
            var obj = json.getAsJsonObject();
            var arr = obj.get("beats").getAsJsonArray();
            Fraction at = context.deserialize(obj.get("at"), Fraction.class);

            var lst = new ArrayList<Beat>();
            for( var arrEl : arr ){
                Beat b = context.deserialize(arrEl, Beat.class);
                lst.add(b);
            }
            return new TimeBeats(at, ImList.from(lst));
        }

        @Override
        public JsonElement serialize(TimeBeats src, Type typeOfSrc, JsonSerializationContext context) {
            var obj = new JsonObject();
            obj.add("at", context.serialize(src.at()));

            var arr = new JsonArray();
            src.beats.map(context::serialize).forEach(arr::add);
            obj.add("beats", arr);

            return obj;
        }
    }

    public TimeBeats {
        if( at==null ) throw new IllegalArgumentException("at==null");
        if( beats==null ) throw new IllegalArgumentException("beats==null");
        if( beats.isEmpty() ) throw new IllegalArgumentException("beats.isEmpty()");
    }

    public static TimeBeats of( Fraction at, Beat ... beats ){
        if( at==null ) throw new IllegalArgumentException("at==null");
        if( beats==null ) throw new IllegalArgumentException("beats==null");
        if( beats.length==0 ) throw new IllegalArgumentException("beats.length==0");
        return new TimeBeats(at, ImList.of(beats));
    }
}