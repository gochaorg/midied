package xyz.cofe.mitrenier.math;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class MapSynchronizer {
    public static <K, V, A, B> FlatMapListener<K, V, A, B> synchronize(ObservableNavigableMap<K, V> source, Map<A, B> target, BiFunction<K, V, Map<A, B>> mapper) {
        target.clear();
        FlatMapListener<K, V, A, B> listener = new FlatMapListener<>(target, mapper);
        for (Map.Entry<K, V> entry : source.entrySet()) {
            listener.onInsert(entry.getKey(), entry.getValue());
        }
        source.addListener(listener);
        return listener;
    }

    public static <K,V,A> FlatMap_MapToSet_Listener<K,V,A> synchronize(ObservableNavigableMap<K,V> source, Set<A> target, BiFunction<K,V, Set<A>> mapper ){
        target.clear();
        var listener = new FlatMap_MapToSet_Listener<>(target, mapper);
        for (Map.Entry<K, V> entry : source.entrySet()) {
            listener.onInsert(entry.getKey(), entry.getValue());
        }
        source.addListener(listener);
        return listener;
    }
}
