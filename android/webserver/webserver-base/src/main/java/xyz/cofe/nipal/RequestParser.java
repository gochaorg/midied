package xyz.cofe.nipal;

import org.eclipse.jetty.server.Request;
import xyz.cofe.coll.im.Result;

/**
 * Интерфейс для парсера HTTP-запросов.
 * Определяет метод для преобразования HTTP-запроса Jetty в объект заданного типа.
 * Результат парсинга представлен в виде {@link Result}, что позволяет обрабатывать
 * как успешные результаты, так и ошибки парсинга.
 *
 * @param <T> Тип результата парсинга
 */
public interface RequestParser<T> {
    /**
     * Парсит HTTP-запрос Jetty и возвращает результат.
     *
     * @param jettyRequest HTTP-запрос Jetty для парсинга
     * @return Результат парсинга: либо успешно преобразованный объект типа T,
     *         либо строка с сообщением об ошибке
     */
    Result<T,String> parse(Request jettyRequest);
}