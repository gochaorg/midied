package xyz.cofe.mitrenier.math;

/**
 * Интерфейс для слушателя событий Map
 * @param <K> Ключ карты
 * @param <V> Значение карты
 */
public interface MapEventListener<K, V> {
    void onInsert(K key, V value);
    void onDelete(K key, V value);
}

