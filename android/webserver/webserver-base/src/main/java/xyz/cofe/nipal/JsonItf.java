package xyz.cofe.nipal;

import xyz.cofe.json.stream.rec.RecMapError;

import java.io.IOError;

/**
 * Интерфейс для работы с JSON-данными.
 * Определяет методы для парсинга JSON-строк в объекты заданного типа
 * и сериализации объектов в JSON-строки.
 */
public interface JsonItf {
    /**
     * Парсит JSON-строку в объект заданного типа.
     *
     * @param <T>        Тип целевого объекта
     * @param json       JSON-строка для парсинга
     * @param targetType Класс целевого типа
     * @return Объект заданного типа, созданный из JSON-данных
     * @throws IOError     Если возникает ошибка ввода-вывода при обработке JSON
     * @throws RecMapError Если возникает ошибка при маппинге JSON-данных в объект
     */
    <T> T parseJson(String json, Class<T> targetType) throws IOError, RecMapError;

    /**
     * Преобразует объект в JSON-строку.
     *
     * @param obj Объект для сериализации
     * @return JSON-строка, представляющая переданный объект
     */
    String toJson(Object obj);
}