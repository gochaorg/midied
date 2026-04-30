package xyz.cofe.mitrenier.math;

import xyz.cofe.coll.im.Countable;
import xyz.cofe.coll.im.ImList;
import xyz.cofe.coll.im.PositionalRead;
import xyz.cofe.mitrenier.LazySingle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Расчет основных статистических показателей
 *
 * @param <A> Тип данных, некое число
 */
public class Stat<A> {
    private final Function<A, Double> toNum;
    private final Iterable<A> source;

    public Stat(Iterable<A> source, Function<A, Double> toNum) {
        if( source == null ) throw new IllegalArgumentException("source==null");
        if( toNum == null ) throw new IllegalArgumentException("toNum==null");
        this.source = source;
        this.toNum = toNum;
    }

    private double numOf(A a) {
        return toNum.apply(a);
    }

    public Iterable<A> getSource() {return source;}

    public final LazySingle<ImList<Double>> sortedValues = new LazySingle<>(
        () -> {
            var lst = ImList.<Double>of();
            for( var a : getSource() ){
                var n = numOf(a);
                lst = lst.prepend(n);
            }
            return lst.sort(n -> n);
        }
    );

    /**
     * Кол-во элементов
     */
    public final LazySingle<Integer> count = sortedValues.map(Countable::size);

    /**
     * Сумма чисел
     */
    public final LazySingle<Double> sum = sortedValues.map(xes -> xes.foldLeft(0.0, Double::sum));

    /**
     * Среднее
     */
    public final LazySingle<Optional<Double>> avg = count.compose(sum, (c, s) -> c == 0 ? Optional.empty() : Optional.of(s / c));

    /**
     * Минимальное
     */
    public final LazySingle<Optional<Double>> min = sortedValues.map(PositionalRead::head);

    /**
     * Максимальное
     */
    public final LazySingle<Optional<Double>> max = sortedValues.map(PositionalRead::last);

    /**
     * Сумма квадратов отклонений от среднего sum( math.pow( sortedValues[i] - avg, 2 ) )
     */
    public final LazySingle<Optional<Double>> quadOfAvgDiff = sortedValues.compose(avg, (values, avgOpt) ->
        avgOpt.flatMap(avg ->
            Optional.of(values.foldLeft(
                0.0,
                (sum, it) -> sum + Math.pow(it - avg, 2)))));

    /**
     * Выборочная дисперсия
     */
    public final LazySingle<Optional<Double>> stdev = quadOfAvgDiff.compose(count, (q, c) ->
        c > 1
            ? q.flatMap(q0 -> Optional.of(q0 * (1.0 / (c.doubleValue() - 1.0)))).map(Math::sqrt)
            : Optional.empty());

    /**
     * Генеральная дисперсия
     */
    public final LazySingle<Optional<Double>> stdevp = quadOfAvgDiff.compose(count, (q, c) ->
        c > 0
            ? q.flatMap(q0 -> Optional.of(q0 * (1.0 / c.doubleValue()))).map(Math::sqrt)
            : Optional.empty());

    private LazySingle<Optional<Double>> createPercentile(int p) {
        if( p < 0 || p > 100 ) throw new IllegalArgumentException("p<0 || p>100");

        return sortedValues.map(xs -> {
            if( xs.isEmpty() ) return Optional.empty();
            if( xs.size() == 1 )
                //noinspection OptionalGetWithoutIsPresent
                return Optional.of(xs.head().get());

            int i = (int) (xs.size() * ((double) p / 100));

            if( i >= xs.size() )
                //noinspection OptionalGetWithoutIsPresent
                return Optional.of(xs.last().get());

            if( i <= 0 )
                //noinspection OptionalGetWithoutIsPresent
                return Optional.of(xs.head().get());

            if( (xs.size() % 2) == 0 ){
                //noinspection OptionalGetWithoutIsPresent
                var a = xs.get(i).get();
                return Optional.of(xs.get(i + 1).map(b -> (a + b) / 2).orElse(a));
            } else{
                return xs.get(i);
            }
        });
    }

    private final Map<Integer, LazySingle<Optional<Double>>> percentiles = new TreeMap<>();

    public LazySingle<Optional<Double>> percentile(int p) {
        if( p < 0 || p > 100 ) throw new IllegalArgumentException("p<0 || p>100");

        var pr = percentiles.get(p);
        if( pr != null ) return pr;

        pr = createPercentile(p);
        percentiles.put(p, pr);

        return pr;
    }

    @SuppressWarnings("resource")
    public Optional<Double> p25(){ return percentile(25).get(); }

    @SuppressWarnings("resource")
    public Optional<Double> p50(){ return percentile(50).get(); }

    @SuppressWarnings("resource")
    public Optional<Double> p75(){ return percentile(75).get(); }

    @SuppressWarnings("resource")
    public Optional<Double> p90(){ return percentile(90).get(); }

    @SuppressWarnings("resource")
    public Optional<Double> p95(){ return percentile(95).get(); }

    @SuppressWarnings("resource")
    public Optional<Double> p97(){ return percentile(97).get(); }

    @SuppressWarnings("resource")
    public Optional<Double> p99(){ return percentile(99).get(); }

    public Optional<Stat<A>> cutPercentile(int p, boolean include){
        if( p < 0 || p > 100 ) throw new IllegalArgumentException("p<0 || p>100");
        return percentile(p).get().flatMap( pValue -> {
            var values = new ArrayList<A>();
            for( var srcValueA : source ){
                var srcValueN = toNum.apply(srcValueA);
                if( include ){
                    if( !pValue.isNaN() && !srcValueN.isNaN() && srcValueN <= pValue ){
                        values.add(srcValueA);
                    }
                }else{
                    if( !pValue.isNaN() && !srcValueN.isNaN() && srcValueN < pValue ){
                        values.add(srcValueA);
                    }
                }
            }
            return Optional.of(new Stat<>(values, toNum));
        });
    }
}
