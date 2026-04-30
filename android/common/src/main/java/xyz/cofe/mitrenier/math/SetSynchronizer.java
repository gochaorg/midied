package xyz.cofe.mitrenier.math;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public class SetSynchronizer {
    public static <E, R> FlatMapSetListener<E, R> synchronize(ObservableNavigableSet<E> source, Set<R> target, Function<E, Collection<R>> mapper) {
        target.clear();
        FlatMapSetListener<E, R> listener = new FlatMapSetListener<>(target, mapper);
        for (E element : source) {
            listener.onInsert(element);
        }
        source.addListener(listener);
        return listener;
    }
}
