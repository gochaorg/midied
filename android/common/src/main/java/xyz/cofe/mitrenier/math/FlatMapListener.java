package xyz.cofe.mitrenier.math;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * FlatMap отображение для Map в Map
 * @param <K> Исходный ключ
 * @param <V> Исходное значение
 * @param <A> Целевой ключ
 * @param <B> Целевое значение
 */
public class FlatMapListener<K, V, A, B> implements MapEventListener<K, V> {
    private final Map<A, B> target;
    private final BiFunction<K, V, Map<A, B>> mapper;

    public FlatMapListener(Map<A, B> target, BiFunction<K, V, Map<A, B>> mapper) {
        this.target = target;
        this.mapper = mapper;
    }

    @Override
    public void onInsert(K key, V value) {
        Map<A, B> mappedValues = mapper.apply(key, value);
        for( Map.Entry<A, B> entry : mappedValues.entrySet() ){
            A a = entry.getKey();
            B b = entry.getValue();
            target.put(a, b);
        }
    }

    @Override
    public void onDelete(K key, V value) {
        Map<A, B> mappedValues = mapper.apply(key, value);
        for( A a : mappedValues.keySet() ){
            target.remove(a);
        }
    }
}
