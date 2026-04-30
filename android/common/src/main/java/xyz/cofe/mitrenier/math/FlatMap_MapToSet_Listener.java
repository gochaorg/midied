package xyz.cofe.mitrenier.math;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * FlatMap отображение для Map в Set
 * @param <K> Ключ Map
 * @param <V> Значение Map
 * @param <A> Значение Set
 */
public class FlatMap_MapToSet_Listener<K, V, A> implements MapEventListener<K, V> {
    private final Set<A> target;
    private final BiFunction<K,V, Set<A>> mapper;

    public FlatMap_MapToSet_Listener(Set<A> target, BiFunction<K, V, Set<A>> mapper) {
        if( target==null ) throw new IllegalArgumentException("target==null");
        if( mapper==null ) throw new IllegalArgumentException("mapper==null");

        this.target = target;
        this.mapper = mapper;
    }

    @Override
    public void onInsert(K key, V value) {
        target.addAll(mapper.apply(key, value));
    }

    @Override
    public void onDelete(K key, V value) {
        for( var a : mapper.apply(key, value)){
            target.remove(a);
        }
    }
}
