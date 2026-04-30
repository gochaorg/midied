package xyz.cofe.mitrenier.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import xyz.cofe.mitrenier.midi.MidiEvent;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * midi song
 */
@SuppressWarnings("MethodDoesntCallSuperMethod")
public class MidiSong {
    //region json
    public static class JSONView implements JsonSerializer<MidiSong>,
                                            JsonDeserializer<MidiSong>
    {
        @Override
        public MidiSong deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
            JsonParseException {

            var jsObj = json.getAsJsonObject();
            var eventsJs = jsObj.get("events").getAsJsonArray();
            var events = new ArrayList<MidiEvent<?>>();

            for( var evJs : eventsJs ){
                MidiEvent<?> ev = context.deserialize(evJs, MidiEvent.class);
                events.add(ev);
            }

            var song = new MidiSong();
            song.events.addAll(events);

            return song;
        }

        @Override
        public JsonElement serialize(MidiSong src, Type typeOfSrc, JsonSerializationContext context) {
            var obj = new JsonObject();
            var eventsArr = new JsonArray();

            for( var ev : src.getEvents() ){
                if( ev==null )continue;
                eventsArr.add(context.serialize(ev));
            }

            obj.add("events", eventsArr);
            return obj;
        }
    }
    //endregion

    /** Расширение файла */
    public static final String FILE_EXTENSION="midisongmt1b";

    /** Mime тип */
    public static final String MIME_TYPE="text/json+midisongmt1b";

    public MidiSong(){
    }

    public MidiSong(Iterable<MidiEvent<?>> events){
        if( events==null ) throw new IllegalArgumentException("events==null");
        events.forEach(this.events::add);
    }

    public MidiSong(MidiSong song){
        if( song==null ) throw new IllegalArgumentException("song==null");
        events.addAll( song.events );
    }

    public MidiSong clone(){
        return new MidiSong(this);
    }

    private final List<MidiEvent<?>> events = new ArrayList<>();
    public List<MidiEvent<?>> getEvents(){ return events; }

    public Optional<Instant> getStartTime(){
        return events.stream().map(MidiEvent::time).min(Comparator.naturalOrder());
    }
}
