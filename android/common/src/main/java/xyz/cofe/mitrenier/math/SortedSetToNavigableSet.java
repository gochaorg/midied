package xyz.cofe.mitrenier.math;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SortedSetToNavigableSet {
    // Функция преобразования SortedSet в NavigableSet
    public static <E> NavigableSet<E> toNavigableSet(SortedSet<E> sortedSet) {
        if (sortedSet == null) {
            throw new NullPointerException("Input SortedSet cannot be null");
        }
        // Если SortedSet уже является NavigableSet, возвращаем его
        if (sortedSet instanceof NavigableSet) {
            return (NavigableSet<E>) sortedSet;
        }
        // Иначе создаем адаптер
        return new NavigableSetAdapter<>(sortedSet);
    }

    // Внутренний класс-адаптер, реализующий NavigableSet
    private static class NavigableSetAdapter<E> implements NavigableSet<E> {
        private final SortedSet<E> delegate;

        NavigableSetAdapter(SortedSet<E> sortedSet) {
            this.delegate = sortedSet;
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
        public boolean add(E e) {
            return delegate.add(e);
        }

        @Override
        public boolean remove(Object o) {
            return delegate.remove(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            return delegate.addAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return delegate.retainAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return delegate.removeAll(c);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Iterator<E> iterator() {
            return delegate.iterator();
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
        public SortedSet<E> subSet(E fromElement, E toElement) {
            return delegate.subSet(fromElement, toElement);
        }

        @Override
        public SortedSet<E> headSet(E toElement) {
            return delegate.headSet(toElement);
        }

        @Override
        public SortedSet<E> tailSet(E fromElement) {
            return delegate.tailSet(fromElement);
        }

        // Реализация навигационных методов
        @Override
        public E lower(E e) {
            Iterator<E> iterator = delegate.headSet(e).iterator();
            E last = null;
            while (iterator.hasNext()) {
                last = iterator.next();
            }
            return last;
        }

        @Override
        public E floor(E e) {
            Iterator<E> iterator = delegate.tailSet(e).iterator();
            if (iterator.hasNext()) {
                E element = iterator.next();
                if (delegate.comparator() != null) {
                    if (delegate.comparator().compare(element, e) <= 0) {
                        return element;
                    }
                } else if (((Comparable<? super E>) element).compareTo(e) <= 0) {
                    return element;
                }
            }
            return lower(e);
        }

        @Override
        public E ceiling(E e) {
            Iterator<E> iterator = delegate.tailSet(e).iterator();
            if (iterator.hasNext()) {
                return iterator.next();
            }
            return null;
        }

        @Override
        public E higher(E e) {
            Iterator<E> iterator = delegate.tailSet(e).iterator();
            if (iterator.hasNext()) {
                iterator.next(); // Пропускаем элемент <= e
                if (iterator.hasNext()) {
                    return iterator.next();
                }
            }
            return null;
        }

        @Override
        public E pollFirst() {
            E first = first();
            if (first != null) {
                delegate.remove(first);
            }
            return first;
        }

        @Override
        public E pollLast() {
            E last = last();
            if (last != null) {
                delegate.remove(last);
            }
            return last;
        }

        @Override
        public NavigableSet<E> descendingSet() {
            // Создаем новый SortedSet с обратным компаратором
            Comparator<? super E> cmp = delegate.comparator();
            Comparator<? super E> reverseCmp = cmp == null ? Collections.reverseOrder() : cmp.reversed();
            TreeSet<E> reversed = new TreeSet<>(reverseCmp);
            reversed.addAll(delegate);
            return new NavigableSetAdapter<>(reversed);
        }

        @Override
        public Iterator<E> descendingIterator() {
            return descendingSet().iterator();
        }

        @Override
        public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
            // Создаем подмножество и оборачиваем его
            TreeSet<E> subSet = new TreeSet<>(delegate.comparator());
            for (E element : delegate) {
                boolean include = (fromInclusive ? compare(element, fromElement) >= 0 : compare(element, fromElement) > 0) &&
                    (toInclusive ? compare(element, toElement) <= 0 : compare(element, toElement) < 0);
                if (include) {
                    subSet.add(element);
                }
            }
            return new NavigableSetAdapter<>(subSet);
        }

        @Override
        public NavigableSet<E> headSet(E toElement, boolean inclusive) {
            return subSet(first(), true, toElement, inclusive);
        }

        @Override
        public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
            return subSet(fromElement, inclusive, last(), true);
        }

        // Вспомогательный метод для сравнения элементов
        private int compare(E e1, E e2) {
            Comparator<? super E> cmp = delegate.comparator();
            if (cmp != null) {
                return cmp.compare(e1, e2);
            }
            return ((Comparable<? super E>) e1).compareTo(e2);
        }

        // Реализация методов Set из Java 8+
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
            return delegate.removeIf(filter);
        }
    }

}
