package xyz.cofe.mitrenier.math;

// Интерфейс для слушателя событий NavigableSet
public interface SetEventListener<E> {
    void onInsert(E element);
    void onDelete(E element);
}


