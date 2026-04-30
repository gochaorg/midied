package xyz.cofe.mitrenier.json;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class LongAsStringOrNumberTypeAdapter extends TypeAdapter<Long> {

    public static final TypeAdapter<Long> adapter = new LongAsStringOrNumberTypeAdapter();

    @Override
    public void write(JsonWriter out, Long value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value);
        }
    }

    @Override
    public Long read(JsonReader in) throws IOException {
        JsonToken peek = in.peek();
        switch (peek) {
            case NUMBER:
                // Проверяем, является ли число целым (Long)
                // Gson автоматически использует double для чисел с плавающей точкой,
                // но для целых чисел может вернуть long.
                // Однако, peek() NUMBER не гарантирует, что это целое.
                // Лучше прочитать как строку и потом конвертировать,
                // чтобы избежать проблем с точностью для очень больших чисел.
                String numberStr = in.nextString();
                try {
                    return Long.parseLong(numberStr);
                } catch (NumberFormatException e) {
                    // Если число слишком велико для Long, бросьте исключение или верните null
                    // в зависимости от ваших требований.
                    throw new JsonSyntaxException("Expected LONG but found: " + numberStr, e);
                }
            case STRING:
                String stringVal = in.nextString();
                try {
                    return Long.parseLong(stringVal);
                } catch (NumberFormatException e) {
                    // Если строка не является допустимым Long, бросьте исключение или верните null
                    throw new JsonSyntaxException("Expected LONG but found: " + stringVal, e);
                }
            case NULL:
                in.nextNull();
                return null;
            default:
                // Если ни число, ни строка, выбрасываем исключение
                throw new JsonSyntaxException("Expected a long (as number or string) but was: " + peek);
        }
    }
}