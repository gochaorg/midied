package xyz.cofe.mitrenier.math;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

// Класс для преобразования SortedMap в NavigableMap
public class SortedMapToNavigableMap {

    // Функция преобразования SortedMap в NavigableMap
    public static <K, V> NavigableMap<K, V> toNavigableMap(SortedMap<K, V> sortedMap) {
        if (sortedMap == null) {
            throw new NullPointerException("Input SortedMap cannot be null");
        }
        // Если SortedMap уже является NavigableMap, возвращаем его
        if (sortedMap instanceof NavigableMap) {
            return (NavigableMap<K, V>) sortedMap;
        }
        // Иначе создаем адаптер
        return new NavigableMapAdapter<>(sortedMap);
    }

    // Внутренний класс-адаптер, реализующий NavigableMap
    private static class NavigableMapAdapter<K, V> implements NavigableMap<K, V> {
        private final SortedMap<K, V> delegate;

        NavigableMapAdapter(SortedMap<K, V> sortedMap) {
            this.delegate = sortedMap;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return delegate.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return delegate.containsValue(value);
        }

        @Override
        public V get(Object key) {
            return delegate.get(key);
        }

        @Override
        public V put(K key, V value) {
            return delegate.put(key, value);
        }

        @Override
        public V remove(Object key) {
            return delegate.remove(key);
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            delegate.putAll(m);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Set<K> keySet() {
            return delegate.keySet();
        }

        @Override
        public Collection<V> values() {
            return delegate.values();
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return delegate.entrySet();
        }

        @Override
        public Comparator<? super K> comparator() {
            return delegate.comparator();
        }

        @Override
        public K firstKey() {
            return delegate.firstKey();
        }

        @Override
        public K lastKey() {
            return delegate.lastKey();
        }

        @Override
        public SortedMap<K, V> subMap(K fromKey, K toKey) {
            return delegate.subMap(fromKey, toKey);
        }

        @Override
        public SortedMap<K, V> headMap(K toKey) {
            return delegate.headMap(toKey);
        }

        @Override
        public SortedMap<K, V> tailMap(K fromKey) {
            return delegate.tailMap(fromKey);
        }

        // Реализация навигационных методов
        @Override
        public Entry<K, V> lowerEntry(K key) {
            Iterator<Entry<K, V>> iterator = delegate.headMap(key).entrySet().iterator();
            Entry<K, V> last = null;
            while (iterator.hasNext()) {
                last = iterator.next();
            }
            return last;
        }

        @Override
        public K lowerKey(K key) {
            Entry<K, V> entry = lowerEntry(key);
            return entry != null ? entry.getKey() : null;
        }

        @Override
        public Entry<K, V> floorEntry(K key) {
            Iterator<Entry<K, V>> iterator = delegate.tailMap(key).entrySet().iterator();
            if (iterator.hasNext()) {
                Entry<K, V> entry = iterator.next();
                if (delegate.comparator() != null) {
                    if (delegate.comparator().compare(entry.getKey(), key) <= 0) {
                        return entry;
                    }
                } else if (((Comparable<? super K>) entry.getKey()).compareTo(key) <= 0) {
                    return entry;
                }
            }
            return lowerEntry(key);
        }

        @Override
        public K floorKey(K key) {
            Entry<K, V> entry = floorEntry(key);
            return entry != null ? entry.getKey() : null;
        }

        @Override
        public Entry<K, V> ceilingEntry(K key) {
            Iterator<Entry<K, V>> iterator = delegate.tailMap(key).entrySet().iterator();
            if (iterator.hasNext()) {
                return iterator.next();
            }
            return null;
        }

        @Override
        public K ceilingKey(K key) {
            Entry<K, V> entry = ceilingEntry(key);
            return entry != null ? entry.getKey() : null;
        }

        @Override
        public Entry<K, V> higherEntry(K key) {
            Iterator<Entry<K, V>> iterator = delegate.tailMap(key).entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next(); // Пропускаем ключ <= key
                if (iterator.hasNext()) {
                    return iterator.next();
                }
            }
            return null;
        }

        @Override
        public K higherKey(K key) {
            Entry<K, V> entry = higherEntry(key);
            return entry != null ? entry.getKey() : null;
        }

        @Override
        public Entry<K, V> firstEntry() {
            K firstKey = delegate.firstKey();
            return firstKey != null ? new AbstractMap.SimpleEntry<>(firstKey, delegate.get(firstKey)) : null;
        }

        @Override
        public Entry<K, V> lastEntry() {
            K lastKey = delegate.lastKey();
            return lastKey != null ? new AbstractMap.SimpleEntry<>(lastKey, delegate.get(lastKey)) : null;
        }

        @Override
        public Entry<K, V> pollFirstEntry() {
            Entry<K, V> entry = firstEntry();
            if (entry != null) {
                delegate.remove(entry.getKey());
            }
            return entry;
        }

        @Override
        public Entry<K, V> pollLastEntry() {
            Entry<K, V> entry = lastEntry();
            if (entry != null) {
                delegate.remove(entry.getKey());
            }
            return entry;
        }

        @Override
        public NavigableMap<K, V> descendingMap() {
            // Создаем новый SortedMap с обратным компаратором
            Comparator<? super K> cmp = delegate.comparator();
            Comparator<? super K> reverseCmp = cmp == null ? Collections.reverseOrder() : cmp.reversed();
            TreeMap<K, V> reversed = new TreeMap<>(reverseCmp);
            reversed.putAll(delegate);
            return new NavigableMapAdapter<>(reversed);
        }

        @Override
        public NavigableSet<K> navigableKeySet() {
            return new TreeSet<>(delegate.keySet());
        }

        @Override
        public NavigableSet<K> descendingKeySet() {
            return descendingMap().navigableKeySet();
        }

        @Override
        public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
            // Создаем подкарту и оборачиваем её
            TreeMap<K, V> subMap = new TreeMap<>(delegate.comparator());
            for (Entry<K, V> entry : delegate.entrySet()) {
                K key = entry.getKey();
                boolean include = (fromInclusive ? compare(key, fromKey) >= 0 : compare(key, fromKey) > 0) &&
                    (toInclusive ? compare(key, toKey) <= 0 : compare(key, toKey) < 0);
                if (include) {
                    subMap.put(key, entry.getValue());
                }
            }
            return new NavigableMapAdapter<>(subMap);
        }

        @Override
        public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
            return subMap(firstKey(), true, toKey, inclusive);
        }

        @Override
        public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
            return subMap(fromKey, inclusive, lastKey(), true);
        }

        // Вспомогательный метод для сравнения ключей
        private int compare(K k1, K k2) {
            Comparator<? super K> cmp = delegate.comparator();
            if (cmp != null) {
                return cmp.compare(k1, k2);
            }
            return ((Comparable<? super K>) k1).compareTo(k2);
        }

        // Реализация методов Map из Java 8+
        @Override
        public V getOrDefault(Object key, V defaultValue) {
            return delegate.getOrDefault(key, defaultValue);
        }

//        @Override
//        public void forEach(B dictionConsumer<? super K, ? super V> action) {
//            delegate.forEach(action);
//        }

        @Override
        public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
            delegate.replaceAll(function);
        }

        @Override
        public V putIfAbsent(K key, V value) {
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public boolean remove(Object key, Object value) {
            return delegate.remove(key, value);
        }

        @Override
        public boolean replace(K key, V oldValue, V newValue) {
            return delegate.replace(key, oldValue, newValue);
        }

        @Override
        public V replace(K key, V value) {
            return delegate.replace(key, value);
        }

        @Override
        public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
            return delegate.computeIfAbsent(key, mappingFunction);
        }

        @Override
        public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            return delegate.computeIfPresent(key, remappingFunction);
        }

        @Override
        public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            return delegate.compute(key, remappingFunction);
        }

        @Override
        public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
            return delegate.merge(key, value, remappingFunction);
        }
    }

}