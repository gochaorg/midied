package xyz.cofe.mitrenier.math;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Кастомная реализация NavigableMap, которая генерирует события
 * @param <K> Ключ карты
 * @param <V> Значение карты
 */
@SuppressWarnings("ALL")
public class ObservableNavigableMap<K, V> implements NavigableMap<K, V> {
    private final NavigableMap<K, V> delegate; // Внутренняя реализация TreeMap
    private final List<MapEventListener<K, V>> listeners; // Список слушателей
    private final WeakHashMap<MapEventListener<K, V>, Object> weakListeners = new WeakHashMap<>();

    public ObservableNavigableMap() {
        this.delegate = new TreeMap<>();
        this.listeners = new ArrayList<>();
    }

    // Конструктор с компаратором для пользовательской сортировки
    public ObservableNavigableMap(Comparator<? super K> comparator) {
        this.delegate = new TreeMap<>(comparator);
        this.listeners = new ArrayList<>();
    }

    public <A, B> FlatMapListener<K, V, A, B> synchronize(Map<A, B> target, BiFunction<K, V, Map<A, B>> mapper) {
        return MapSynchronizer.synchronize(this, target, mapper);
    }

    public <A> FlatMap_MapToSet_Listener<K, V, A> synchronize(Set<A> target, BiFunction<K, V, Set<A>> mapper) {
        return MapSynchronizer.synchronize(this, target, mapper);
    }

    // Метод для добавления слушателя
    public void addListener(MapEventListener<K, V> listener) {
        listeners.add(listener);
    }

    public void addWeakListener(MapEventListener<K, V> listener) {
        weakListeners.put(listener, 1);
    }

    // Метод для удаления слушателя
    public void removeListener(MapEventListener<K, V> listener) {
        listeners.remove(listener);
    }

    // Уведомление всех слушателей о вставке
    private void notifyInsert(K key, V value) {
        for( MapEventListener<K, V> listener : listeners ){
            listener.onInsert(key, value);
        }
        for( MapEventListener<K, V> listener : weakListeners.keySet() ){
            listener.onInsert(key, value);
        }
    }

    // Уведомление всех слушателей об удалении
    private void notifyDelete(K key, V value) {
        for( MapEventListener<K, V> listener : listeners ){
            listener.onDelete(key, value);
        }
        for( MapEventListener<K, V> listener : weakListeners.keySet() ){
            listener.onDelete(key, value);
        }
    }

    public class SimpleListenerBuilder {
        private final List<BiConsumer<K, V>> insertListeners = new ArrayList<>();
        private final List<BiConsumer<K, V>> deleteListeners = new ArrayList<>();

        public SimpleListenerBuilder onInsert(BiConsumer<K, V> listener) {
            if( listener == null ) throw new IllegalArgumentException("listener==null");
            insertListeners.add(listener);
            return this;
        }

        public SimpleListenerBuilder onDelete(BiConsumer<K, V> listener) {
            if( listener == null ) throw new IllegalArgumentException("listener==null");
            deleteListeners.add(listener);
            return this;
        }

        private SimpleListener createListener() {
            var ls = new SimpleListener(insertListeners, deleteListeners);
            for( var c : consumers ){
                c.accept(ls);
            }
            return ls;
        }

        private final List<Consumer<SimpleListener>> consumers = new ArrayList<>();

        public SimpleListenerBuilder listenerConsumer(Consumer<SimpleListener> consumer ){
            if( consumer==null ) throw new IllegalArgumentException("consumer==null");
            consumers.add(consumer);
            return this;
        }

        public SimpleListener addHardListener() {
            var ls = createListener();
            ObservableNavigableMap.this.addListener(ls);
            return ls;
        }

        public SimpleListener addWeakListener() {
            var ls = createListener();
            ObservableNavigableMap.this.addWeakListener(ls);
            return ls;
        }

        public SimpleListener add() {
            return addHardListener();
        }

        public ObservableNavigableMap<K,V> bind(){
            addHardListener();
            return ObservableNavigableMap.this;
        }

        public ObservableNavigableMap<K,V> bindWeak(){
            addWeakListener();
            return ObservableNavigableMap.this;
        }
    }

    public class SimpleListener implements MapEventListener<K, V> {
        private final List<BiConsumer<K, V>> insertListeners;
        private final List<BiConsumer<K, V>> deleteListeners;

        public SimpleListener(List<BiConsumer<K, V>> insertListeners, List<BiConsumer<K, V>> deleteListeners) {
            this.insertListeners = insertListeners;
            this.deleteListeners = deleteListeners;
        }

        @Override
        public void onInsert(K key, V value) {
            for( var ls : insertListeners ) ls.accept(key, value);
        }

        @Override
        public void onDelete(K key, V value) {
            for( var ls : deleteListeners ) ls.accept(key, value);
        }
    }

    public SimpleListenerBuilder listener() {return new SimpleListenerBuilder();}

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
        V oldValue = delegate.put(key, value);
        if( oldValue == null ){
            notifyInsert(key, value); // Новая вставка
        } else if( !oldValue.equals(value) ){
            notifyDelete(key, oldValue); // Удаление старого значения
            notifyInsert(key, value); // Вставка нового
        }
        return oldValue;
    }

    @Override
    public V remove(Object key) {
        V value = delegate.remove(key);
        if( value != null ){
            notifyDelete((K) key, value);
        }
        return value;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for( Entry<? extends K, ? extends V> entry : m.entrySet() ){
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        List<Entry<K, V>> entries = new ArrayList<>(delegate.entrySet());
        delegate.clear();
        for( Entry<K, V> entry : entries ){
            notifyDelete(entry.getKey(), entry.getValue());
        }
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
    public V getOrDefault(Object key, V defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        delegate.forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        delegate.replaceAll((k, v) -> {
            V newValue = function.apply(k, v);
            if( !v.equals(newValue) ){
                notifyDelete(k, v);
                notifyInsert(k, newValue);
            }
            return newValue;
        });
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V oldValue = delegate.putIfAbsent(key, value);
        if( oldValue == null && delegate.containsKey(key) ){
            notifyInsert(key, value);
        }
        return oldValue;
    }

    @Override
    public boolean remove(Object key, Object value) {
        boolean removed = delegate.remove(key, value);
        if( removed ){
            notifyDelete((K) key, (V) value);
        }
        return removed;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        boolean replaced = delegate.replace(key, oldValue, newValue);
        if( replaced ){
            notifyDelete(key, oldValue);
            notifyInsert(key, newValue);
        }
        return replaced;
    }

    @Override
    public V replace(K key, V value) {
        V oldValue = delegate.replace(key, value);
        if( oldValue != null ){
            notifyDelete(key, oldValue);
            notifyInsert(key, value);
        }
        return oldValue;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V value = delegate.computeIfAbsent(key, k -> {
            V newValue = mappingFunction.apply(k);
            if( newValue != null ){
                notifyInsert(k, newValue);
            }
            return newValue;
        });
        return value;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V value = delegate.computeIfPresent(key, (k, v) -> {
            V newValue = remappingFunction.apply(k, v);
            if( newValue == null ){
                notifyDelete(k, v);
            } else if( !v.equals(newValue) ){
                notifyDelete(k, v);
                notifyInsert(k, newValue);
            }
            return newValue;
        });
        return value;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V value = delegate.compute(key, (k, v) -> {
            V newValue = remappingFunction.apply(k, v);
            if( v != null && newValue == null ){
                notifyDelete(k, v);
            } else if( v == null && newValue != null ){
                notifyInsert(k, newValue);
            } else if( v != null && !v.equals(newValue) ){
                notifyDelete(k, v);
                notifyInsert(k, newValue);
            }
            return newValue;
        });
        return value;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        V oldValue = delegate.merge(key, value, (oldV, newV) -> {
            V result = remappingFunction.apply(oldV, newV);
            if( result == null ){
                notifyDelete(key, oldV);
            } else if( !oldV.equals(result) ){
                notifyDelete(key, oldV);
                notifyInsert(key, result);
            }
            return result;
        });
        if( oldValue == null && delegate.containsKey(key) ){
            notifyInsert(key, value);
        }
        return oldValue;
    }

    // Реализация методов NavigableMap
    @Override
    public Entry<K, V> lowerEntry(K key) {
        return delegate.lowerEntry(key);
    }

    @Override
    public K lowerKey(K key) {
        return delegate.lowerKey(key);
    }

    public Optional<K> previousKeyOf(K key){
        return Optional.ofNullable(lowerKey(key));
    }

    @Override
    public Entry<K, V> floorEntry(K key) {
        return delegate.floorEntry(key);
    }

    @Override
    public K floorKey(K key) {
        return delegate.floorKey(key);
    }

    public Optional<K> previousKeyOrItOf(K key){ return Optional.ofNullable(floorKey(key)); }

    @Override
    public Entry<K, V> ceilingEntry(K key) {
        return delegate.ceilingEntry(key);
    }

    @Override
    public K ceilingKey(K key) {
        return delegate.ceilingKey(key);
    }

    public Optional<K> nextKeyOrIt(K key){ return Optional.ofNullable(ceilingKey(key)); }

    @Override
    public Entry<K, V> higherEntry(K key) {
        return delegate.higherEntry(key);
    }

    @Override
    public K higherKey(K key) {
        return delegate.higherKey(key);
    }

    public Optional<K> nextKey(K key){ return Optional.ofNullable(higherKey(key)); }

    @Override
    public Entry<K, V> firstEntry() {
        return delegate.firstEntry();
    }

    @Override
    public Entry<K, V> lastEntry() {
        return delegate.lastEntry();
    }

    @Override
    public Entry<K, V> pollFirstEntry() {
        Entry<K, V> entry = delegate.pollFirstEntry();
        if( entry != null ){
            notifyDelete(entry.getKey(), entry.getValue());
        }
        return entry;
    }

    @Override
    public Entry<K, V> pollLastEntry() {
        Entry<K, V> entry = delegate.pollLastEntry();
        if( entry != null ){
            notifyDelete(entry.getKey(), entry.getValue());
        }
        return entry;
    }

    @Override
    public NavigableMap<K, V> descendingMap() {
        return new ObservableNavigableMap<>(delegate.descendingMap(), listeners);
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        return delegate.navigableKeySet();
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        return delegate.descendingKeySet();
    }

    @Override
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return new ObservableNavigableMap<>(delegate.subMap(fromKey, fromInclusive, toKey, toInclusive), listeners);
    }

    @Override
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return new ObservableNavigableMap<>(delegate.headMap(toKey, inclusive), listeners);
    }

    @Override
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return new ObservableNavigableMap<>(delegate.tailMap(fromKey, inclusive), listeners);
    }

    @Override
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return new ObservableNavigableMap<>(delegate.subMap(fromKey, toKey), listeners);
    }

    @Override
    public SortedMap<K, V> headMap(K toKey) {
        return new ObservableNavigableMap<>(delegate.headMap(toKey), listeners);
    }

    @Override
    public SortedMap<K, V> tailMap(K fromKey) {
        return new ObservableNavigableMap<>(delegate.tailMap(fromKey), listeners);
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

    // Приватный конструктор для создания подкарт (subMap, headMap, tailMap, descendingMap)
    private ObservableNavigableMap(SortedMap<K, V> delegate, Collection<MapEventListener<K, V>> listeners) {
        this.delegate = SortedMapToNavigableMap.toNavigableMap(delegate);
        this.listeners = new ArrayList<>(listeners);
    }
}