package xyz.cofe.nipal;

import com.google.gson.Gson;
import xyz.cofe.json.stream.rec.RecMapError;
import xyz.cofe.json.stream.rec.StdMapper;

import java.io.IOError;

/**
 * Реализация интерфейса {@link JsonItf} с использованием различных библиотек JSON.
 * Предоставляет фабричные методы для создания реализаций, использующих
 * {@link StdMapper} (из библиотеки json-stream) и {@link Gson} (Google Gson).
 */
public class JsonItfImpl {
    /**
     * Создает реализацию {@link JsonItf}, использующую {@link StdMapper} для работы с JSON.
     *
     * @param stdMapper Экземпляр StdMapper для выполнения операций парсинга и сериализации
     * @return Новая реализация JsonItf, использующая StdMapper
     * @throws IllegalArgumentException если stdMapper равен null
     */
    public static JsonItf jsonStream(StdMapper stdMapper){
        if( stdMapper==null ) throw new IllegalArgumentException("stdMapper==null");
        return new JsonItf() {
            @Override
            public <T> T parseJson(String json, Class<T> targetType) throws IOError, RecMapError {
                return stdMapper.parse(json, targetType);
            }

            @Override
            public String toJson(Object obj) {
                return stdMapper.toJson(obj);
            }
        };
    }

    /**
     * Создает реализацию {@link JsonItf}, использующую Google Gson для работы с JSON.
     *
     * @param gson Экземпляр Gson для выполнения операций парсинга и сериализации
     * @return Новая реализация JsonItf, использующая Gson
     * @throws IllegalArgumentException если gson равен null
     */
    public static JsonItf gson(Gson gson){
        if( gson==null ) throw new IllegalArgumentException("gson==null");
        return new JsonItf() {
            @Override
            public <T> T parseJson(String json, Class<T> targetType) throws IOError, RecMapError {
                return gson.fromJson(json,targetType);
            }

            @Override
            public String toJson(Object obj) {
                return gson.toJson(obj);
            }
        };
    }
}