package xyz.cofe.mitrenier.math;

import java.time.Duration;
import java.util.Optional;

/**
 * Расстояние по времени {@link Duration}
 * @param from Начало
 * @param to Конец
 */
public record DurationRange( Duration from, Duration to ) {
    public DurationRange {
        if( from==null ) throw new IllegalArgumentException("from==null");
        if( to==null ) throw new IllegalArgumentException("to==null");
        if( from.compareTo(to)>0 ) throw new IllegalArgumentException("from.compareTo(to)>0");
    }

    public static DurationRange of( Duration from, Duration to ){
        if( from==null ) throw new IllegalArgumentException("from==null");
        if( to==null ) throw new IllegalArgumentException("to==null");

        if( from.compareTo(to) > 0 ){
            return new DurationRange( to, from );
        }

        return new DurationRange( from, to );
    }

    public static boolean contains(Duration from, Duration to, Duration point) {
        if( from==null ) throw new IllegalArgumentException("from==null");
        if( to==null ) throw new IllegalArgumentException("to==null");
        if( point==null ) throw new IllegalArgumentException("point==null");

        var swap = from.compareTo(to) > 0;
        var from1 = swap ? to : from;
        var to1 = swap ? from : to;

        return from1.compareTo(point) <= 0 && point.compareTo(to1) <= 0;
    }

    public Duration length(){
        var f = from.toNanos();
        var t = to.toNanos();
        return Duration.ofNanos( t - f );
    }

    public Optional<DurationRange> intersect( DurationRange range ){
        if( range==null ) throw new IllegalArgumentException("range==null");

        long t0 = from().toNanos();
        long t1 = to().toNanos();

        long t2 = range.from().toNanos();
        long t3 = range.to().toNanos();

        long start = Math.max(t0, t2);
        long end = Math.min(t1, t3);

        if( start < end ){
            return Optional.of( DurationRange.of( Duration.ofNanos(start), Duration.ofNanos(end) ) );
        }else{
            return Optional.empty();
        }
    }
}
