package xyz.cofe.mitrenier.math;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * FlatMap отображение для Set в Set
 * @param <E> Исходное значение
 * @param <R> Целевое значение
 */
public class FlatMapSetListener<E, R> implements SetEventListener<E> {
    private final Set<R> target;
    private final Function<E, Collection<R>> mapper;
    private final Map<R, Integer> countMap = new HashMap<>();

    public FlatMapSetListener(Set<R> target, Function<E, Collection<R>> mapper) {
        this.target = target;
        this.mapper = mapper;
    }

    @Override
    public void onInsert(E element) {
        Collection<R> rs = mapper.apply(element);
        for (R r : rs) {
            int count = countMap.getOrDefault(r, 0);
            countMap.put(r, count + 1);
            if (count == 0) {
                target.add(r);
            }
        }
    }

    @Override
    public void onDelete(E element) {
        Collection<R> rs = mapper.apply(element);
        for (R r : rs) {
            int count = countMap.getOrDefault(r, 0);
            if (count == 1) {
                countMap.remove(r);
                target.remove(r);
            } else if (count > 1) {
                countMap.put(r, count - 1);
            }
        }
    }
}
