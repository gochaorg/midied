package xyz.cofe.mitrenier.midi;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import xyz.cofe.mitrenier.json.JSON;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * События midi
 */
public class MidiEventCollector {
    public final Queue<RawMidiEvent> events = new ConcurrentLinkedQueue<RawMidiEvent>();

    /**
     * Добавление. Потоко безопасно
     *
     * @param rawMidiEvent событие
     */
    public void add(RawMidiEvent rawMidiEvent) {
        if( rawMidiEvent == null ) throw new IllegalArgumentException("midiEvent==null");
        synchronized(this) {
            events.add(rawMidiEvent);
        }
    }

    /**
     * Удаление. Потоко безопасно
     */
    public void clear() {
        synchronized(this) {
            events.clear();
        }
    }

    /**
     * Удаление. Потоко безопасно
     *
     * @param consumer Принимает удаленные события
     */
    public void flushTo(Consumer<RawMidiEvent> consumer) {
        if( consumer == null ) throw new IllegalArgumentException("consumer==null");
        synchronized(this) {
            events.forEach(consumer);
            events.clear();
        }
    }

    /**
     * Удаление. Потоко безопасно
     *
     * @param cloneConsumer Принимает удаленные события
     */
    public void flushToClone(Consumer<MidiEventCollector> cloneConsumer) {
        if( cloneConsumer == null ) throw new IllegalArgumentException("cloneConsumer==null");
        synchronized(this) {
            MidiEventCollector collector = new MidiEventCollector();
            flushTo(collector.events::add);
            cloneConsumer.accept(collector);
        }
    }

    /**
     * Клонирование и удаление ранее добавленных. Потоко безопасно.
     * <ul>
     * <li>Текущий объект - пустой будет</li>
     * <li>Клон - содержит ранее добавленных</li>
     * </ul>
     *
     * @return клон с событиями
     */
    public MidiEventCollector cloneAndFlush() {
        synchronized(this) {
            MidiEventCollector collector = new MidiEventCollector();
            flushTo(collector.events::add);
            return collector;
        }
    }

    /**
     * Сортированный список событий
     * @return Сортированные события
     */
    public SortedMidiEvents toSorted() {
        synchronized(this) {
            return new SortedMidiEvents(events);
        }
    }

    public record JSONView(List<RawMidiEvent> events) implements JsonSerializer<MidiEventCollector>,
                                                                 JsonDeserializer<MidiEventCollector> {
        public static final JSONView serializing = new JSONView(List.of());

        public static JSONView from(MidiEventCollector collector) {
            if( collector == null ) throw new IllegalArgumentException("collector==null");
            return new JSONView(new ArrayList<>(collector.events));
        }

        public MidiEventCollector toMidiEventCollector() {
            var coll = new MidiEventCollector();
            coll.events.addAll(events);
            return coll;
        }

        @Override
        public JsonElement serialize(MidiEventCollector src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(JSONView.from(src));
        }

        @Override
        public MidiEventCollector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
            JsonParseException {
            JSONView view = context.deserialize(json, JSONView.class);
            return view.toMidiEventCollector();
        }
    }

    /**
     * Потоко не безопасно
     *
     * @return json представление
     */
    public String toJson() {
        return JSON.toJson(this);
    }

    public static MidiEventCollector fromJson(String json) {
        if( json == null ) throw new IllegalArgumentException("json==null");
        return JSON.fromJson(json, MidiEventCollector.class);
    }
}
