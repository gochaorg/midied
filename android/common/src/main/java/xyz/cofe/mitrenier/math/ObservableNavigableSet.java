package xyz.cofe.mitrenier.math;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Кастомная реализация NavigableSet, которая генерирует события
 * @param <E> Тип значения
 */
@SuppressWarnings("NullableProblems")
public class ObservableNavigableSet<E> implements NavigableSet<E> {
    private final NavigableSet<E> delegate; // Внутренняя реализация TreeSet
    private final List<SetEventListener<E>> listeners; // Список слушателей
    private final WeakHashMap<SetEventListener<E>, Object> weakListeners = new WeakHashMap<>();

    public ObservableNavigableSet() {
        //noinspection SortedCollectionWithNonComparableKeys
        this.delegate = new TreeSet<>();
        this.listeners = new ArrayList<>();
    }

    // Конструктор с компаратором для пользовательской сортировки
    public ObservableNavigableSet(Comparator<? super E> comparator) {
        this.delegate = new TreeSet<>(comparator);
        this.listeners = new ArrayList<>();
    }

    public <R> FlatMapSetListener<E, R> synchronize(Set<R> target, Function<E, Collection<R>> mapper) {
        return SetSynchronizer.synchronize(this, target, mapper);
    }

    // Метод для добавления слушателя
    public void addListener(SetEventListener<E> listener) {
        listeners.add(listener);
    }

    public void addWeakListener(SetEventListener<E> listener) {
        if( listener == null ) throw new IllegalArgumentException("listener==null");
        weakListeners.put(listener, 1);
    }

    // Метод для удаления слушателя
    public void removeListener(SetEventListener<E> listener) {
        weakListeners.remove(listener);
        listeners.remove(listener);
    }

    // Уведомление всех слушателей о вставке
    private void notifyInsert(E element) {
        for( SetEventListener<E> listener : listeners ){
            listener.onInsert(element);
        }
        for( SetEventListener<E> listener : weakListeners.keySet() ){
            listener.onInsert(element);
        }
    }

    // Уведомление всех слушателей об удалении
    private void notifyDelete(E element) {
        for( SetEventListener<E> listener : listeners ){
            listener.onDelete(element);
        }
        for( SetEventListener<E> listener : weakListeners.keySet() ){
            listener.onDelete(element);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public class SimpleListenerBuilder {
        private final List<Consumer<E>> insertListeners = new ArrayList<>();

        public SimpleListenerBuilder onInsert(Consumer<E> listener) {
            if( listener == null ) throw new IllegalArgumentException("listener==null");
            insertListeners.add(listener);
            return this;
        }

        private final List<Consumer<E>> deleteListeners = new ArrayList<>();

        public SimpleListenerBuilder onDelete(Consumer<E> listener) {
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

        public SimpleListenerBuilder listenerConsumer( Consumer<SimpleListener> consumer ){
            if( consumer==null ) throw new IllegalArgumentException("consumer==null");
            consumers.add(consumer);
            return this;
        }

        public SimpleListener addHardListener() {
            var ls = createListener();
            ObservableNavigableSet.this.addListener(ls);
            return ls;
        }

        public SimpleListener addWeakListener() {
            var ls = createListener();
            ObservableNavigableSet.this.addWeakListener(ls);
            return ls;
        }

        public SimpleListener add() {
            return addHardListener();
        }

        public ObservableNavigableSet<E> bind(){
            addHardListener();
            return ObservableNavigableSet.this;
        }

        public ObservableNavigableSet<E> bindWeak(){
            addWeakListener();
            return ObservableNavigableSet.this;
        }
    }

    public class SimpleListener implements SetEventListener<E> {
        private final List<Consumer<E>> insertListeners;
        private final List<Consumer<E>> deleteListeners;

        public SimpleListener(List<Consumer<E>> insertListeners, List<Consumer<E>> deleteListeners) {
            this.insertListeners = insertListeners;
            this.deleteListeners = deleteListeners;
        }

        @Override
        public void onInsert(E element) {
            for( var ls : insertListeners ) ls.accept(element);
        }

        @Override
        public void onDelete(E element) {
            for( var ls : deleteListeners ) ls.accept(element);
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
    public boolean add(E e) {
        boolean added = delegate.add(e);
        if( added ){
            notifyInsert(e);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = delegate.remove(o);
        if( removed ){
            notifyDelete((E) o);
        }
        return removed;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean modified = false;
        for( E e : c ){
            if( delegate.add(e) ){
                notifyInsert(e);
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        List<E> toRemove = new ArrayList<>();
        for( Object o : c ){
            if( delegate.contains(o) ){
                toRemove.add((E) o);
            }
        }
        boolean modified = delegate.removeAll(c);
        if( modified ){
            for( E e : toRemove ){
                notifyDelete(e);
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        List<E> toRemove = new ArrayList<>();
        for( E e : delegate ){
            if( !c.contains(e) ){
                toRemove.add(e);
            }
        }
        boolean modified = delegate.retainAll(c);
        if( modified ){
            for( E e : toRemove ){
                notifyDelete(e);
            }
        }
        return modified;
    }

    @Override
    public void clear() {
        List<E> toRemove = new ArrayList<>(delegate);
        delegate.clear();
        for( E e : toRemove ){
            notifyDelete(e);
        }
    }

    /**
     * Возвращает наибольший элемент в наборе, который строго меньше заданного элемента
     * @param e the value to match
     * @return Значение
     */
    @Override
    public E lower(E e) {
        return delegate.lower(e);
    }

    public Optional<E> previousOf(E e){
        return Optional.ofNullable(lower(e));
    }

    /**
     * Возвращает наибольший элемент в наборе, который меньше или равен заданному элементу
     * @param e the value to match
     * @return Значение
     */
    @Override
    public E floor(E e) {
        return delegate.floor(e);
    }

    public Optional<E> previousOrItOf(E e){
        return Optional.ofNullable(floor(e));
    }

    /**
     * Возвращает наименьший элемент в наборе, который больше или равен заданному элементу
     * @param e the value to match
     * @return Значение
     */
    @Override
    public E ceiling(E e) {
        return delegate.ceiling(e);
    }

    public Optional<E> nextOrItOf(E e){
        return Optional.ofNullable(ceiling(e));
    }

    /**
     * Возвращает наименьший элемент в наборе, который строго больше заданного элемента
     * @param e the value to match
     * @return Значение
     */
    @Override
    public E higher(E e) {
        return delegate.higher(e);
    }

    public Optional<E> nextOf(E e){
        return Optional.ofNullable(higher(e));
    }

    @Override
    public E pollFirst() {
        E element = delegate.pollFirst();
        if( element != null ){
            notifyDelete(element);
        }
        return element;
    }

    @Override
    public E pollLast() {
        E element = delegate.pollLast();
        if( element != null ){
            notifyDelete(element);
        }
        return element;
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new ObservableNavigableSet<>(delegate.descendingSet());
    }

    @Override
    public Iterator<E> descendingIterator() {
        return descendingSet().iterator();
    }

    @Override
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        return new ObservableNavigableSet<>(delegate.subSet(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        return new ObservableNavigableSet<>(delegate.headSet(toElement, inclusive));
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        return new ObservableNavigableSet<>(delegate.tailSet(fromElement, inclusive));
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return new ObservableNavigableSet<>(delegate.subSet(fromElement, toElement));
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return new ObservableNavigableSet<>(delegate.headSet(toElement));
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return new ObservableNavigableSet<>(delegate.tailSet(fromElement));
    }

    @Override
    public Comparator<? super E> comparator() {
        return delegate.comparator();
    }

    @Override
    public E first() {
        return delegate.first();
    }

    @Override
    public E last() {
        return delegate.last();
    }

    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public Spliterator<E> spliterator() {
        return delegate.spliterator();
    }

    @Override
    public Stream<E> stream() {
        return delegate.stream();
    }

    @Override
    public Stream<E> parallelStream() {
        return delegate.parallelStream();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        delegate.forEach(action);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        List<E> toRemove = new ArrayList<>();
        delegate.forEach(e -> {
            if( filter.test(e) ){
                toRemove.add(e);
            }
        });
        boolean modified = delegate.removeIf(filter);
        if( modified ){
            for( E e : toRemove ){
                notifyDelete(e);
            }
        }
        return modified;
    }

    // Приватный конструктор для создания подмножеств (subSet, headSet, tailSet, descendingSet)
    private ObservableNavigableSet(SortedSet<E> delegate) {
        this.delegate = SortedSetToNavigableSet.toNavigableSet(delegate);
        this.listeners = new ArrayList<>();
    }
}