package xyz.cofe.mitrenier.midi;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Событие midi
 * @param time Время события
 * @param timestamp Время события nano
 * @param data Данные события
 */
public record RawMidiEvent(Instant time, long timestamp, byte[] data) {
    public RawMidiEvent {
        Objects.requireNonNull(time);
        Objects.requireNonNull(data);
    }

    public RawMidiEvent time(Instant time){
        return new RawMidiEvent(time, timestamp, data);
    }

    public RawMidiEvent timestamp(long timestamp){
        return new RawMidiEvent(time, timestamp, data);
    }

    @Override
    public boolean equals(Object object) {
        if( object == null || getClass() != object.getClass() ) return false;
        RawMidiEvent that = (RawMidiEvent) object;
        return timestamp == that.timestamp && Objects.deepEquals(data, that.data) && Objects.equals(time, that.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, timestamp, Arrays.hashCode(data));
    }

    public static RawMidiEvent createFromNativeEvent(Instant time, long timestamp, byte[] data, int offset, int len) {
        if( data==null )throw new IllegalArgumentException("data==null");
        if( time==null )throw new IllegalArgumentException("time==null");
        return new RawMidiEvent(time, timestamp, Arrays.copyOfRange(data, offset, offset+len));
    }

    public static RawMidiEvent createFromNativeEvent(long timestamp, byte[] data, int offset, int len) {
        if( data==null )throw new IllegalArgumentException("data==null");
        return new RawMidiEvent(Instant.now(), timestamp, Arrays.copyOfRange(data, offset, offset+len));
    }

    public static String hex(byte[] data){
        var sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02X", data[i]));
        }
        return sb.toString().trim();
    }

    public static byte[] hex(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    public static record JSONView(Instant time, long timestamp, String data) implements JsonSerializer<RawMidiEvent>, JsonDeserializer<RawMidiEvent> {
        public static JSONView serializing = new JSONView(Instant.now(),0,"");

        public JSONView {
            if( time == null ) throw new IllegalArgumentException("time==null");
            if( data == null ) throw new IllegalArgumentException("data==null");
        }

        public static JSONView from( RawMidiEvent event ){
            if( event == null ) throw new IllegalArgumentException("event==null");
            return new JSONView(event.time, event.timestamp, hex(event.data()));
        }

        public RawMidiEvent toMidiEvent(){
            return new RawMidiEvent(time, timestamp, hex(data));
        }

        @Override
        public RawMidiEvent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JSONView v = context.deserialize(json, JSONView.class);
            return v.toMidiEvent();
        }

        @Override
        public JsonElement serialize(RawMidiEvent src, Type typeOfSrc, JsonSerializationContext context) {
            var jsnView = JSONView.from(src);
            return context.serialize(jsnView);
        }
    }
}
