package xyz.cofe.mitrenier.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

/**
 * Instant сериализатор для JSON/Gson
 */
public class InstantTypeAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            // Сериализация Instant в строку ISO-8601
            out.value(value.toString());
        }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        String value = in.nextString();
        if (value == null) {
            return null;
        }
        // Десериализация строки ISO-8601 в Instant
        return Instant.parse(value);
    }
}
