package xyz.cofe.nipal.util;

import org.eclipse.jetty.server.Request;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Утилитарный класс для парсинга строки запроса (query string) из HTTP-запросов.
 * Предоставляет методы для разбора параметров из строки запроса с декодированием URL-символов.
 */
public class QueryString {
    /**
     * Парсит строку запроса из HTTP-запроса Jetty.
     *
     * @param request HTTP-запрос Jetty
     * @return Карта параметров строки запроса (ключ -> значение), где порядок сохраняется
     * @throws IllegalArgumentException если request равен null
     */
    public static Map<String,String> parseQueryString(Request request){
        if( request==null ) throw new IllegalArgumentException("request==null");

        var qs = request.getHttpURI().getQuery();
        if( qs==null )return new LinkedHashMap<>();

        return parseQueryStringDecoded(qs);
    }

    /**
     * Парсит закодированную строку запроса и возвращает расшифрованные параметры.
     * Выполняет URL-декодирование ключей и значений параметров.
     *
     * @param queryString Строка запроса (например, "param1=value1&param2=value2")
     * @return Карта параметров строки запроса (ключ -> значение), где порядок сохраняется
     * @throws IllegalArgumentException если queryString равен null
     */
    public static Map<String, String> parseQueryStringDecoded(String queryString) {
        if( queryString==null ) throw new IllegalArgumentException("queryString==null");

        Map<String, String> params = new LinkedHashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }

        try {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                String value = keyValue.length > 1
                    ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name())
                    : "";
                params.put(key, value);
            }
        } catch ( UnsupportedEncodingException e) {
            return params;
        }

        return params;
    }
}