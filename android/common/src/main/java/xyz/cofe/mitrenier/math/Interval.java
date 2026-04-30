package xyz.cofe.mitrenier.math;

/**
 * Класс для представления интервала с обобщенным типом
 * @param <T> Тип начала/конца интервала
 * @param <V> Тип значения
 */
public class Interval<T, V> {
    public final T begin;
    public final T end;
    public final V value;

    public Interval(T begin, T end, V value) {
        this.begin = begin;
        this.end = end;
        this.value = value;
    }

    @Override
    public String toString() {
        return "[" + begin + ", " + end + ", " + value + "]";
    }
}
