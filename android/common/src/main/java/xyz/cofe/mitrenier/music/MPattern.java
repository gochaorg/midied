package xyz.cofe.mitrenier.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import xyz.cofe.coll.im.ImList;
import xyz.cofe.coll.im.iter.ExtIterable;
import xyz.cofe.mitrenier.math.Fraction;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Музыкальный шаблон
 */
@SuppressWarnings("SimplifyStreamApiCallChains")
public class MPattern {
    public static class JSONView
        implements JsonSerializer<MPattern>,
                   JsonDeserializer<MPattern>
    {
        @Override
        public MPattern deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
            JsonParseException {

            var lst = new ArrayList<TimeBeats>();

            var arr = json.getAsJsonArray();
            arr.asList().stream().map(e -> (TimeBeats)context.deserialize(e, TimeBeats.class)).forEach(lst::add);

            return new MPattern(ImList.from(lst));
        }

        @Override
        public JsonElement serialize(MPattern src, Type typeOfSrc, JsonSerializationContext context) {
            var arr = new JsonArray();
            src.beats.map(context::serialize).forEach(arr::add);
            return arr;
        }
    }

    private final ImList<TimeBeats> beats;

    public MPattern(ImList<TimeBeats> beats) {
        if( beats == null ) throw new IllegalArgumentException("beats==null");
        if( beats.isEmpty() ) throw new IllegalArgumentException("beats.isEmpty()");
        this.beats = beats;
    }

    public static MPattern of( TimeBeats ... beats ){
        if( beats==null ) throw new IllegalArgumentException("beats==null");
        if( beats.length==0 ) throw new IllegalArgumentException("beats.length==0");
        return new MPattern(ImList.of(beats));
    }

    /**
     * Удары/ноты
     * @return Удары/ноты
     */
    public ImList<TimeBeats> getBeats() {
        return beats;
    }

    /**
     * Находит время начала первой ноты
     *
     * @return Fraction времени начала первой ноты
     */
    public Fraction getFirstNoteStart() {
        return beats.stream()
            .map(TimeBeats::at)
            .min(Fraction::compareTo).orElseThrow();
    }

    /**
     * Находит время конца последней ноты с учетом ее продолжительности
     *
     * @return Fraction времени конца последней ноты
     */
    public Fraction getLastNoteEnd() {
        return beats.stream()
            .flatMap(tb -> tb.beats().stream()
                .map(beat -> tb.at().add(beat.length())))
            .max(Fraction::compareTo).orElseThrow();
    }

    /**
     * Находит общую продолжительность от начала первой ноты до конца последней
     *
     * @return Fraction общей продолжительности
     */
    public Fraction getTotalNoteDuration() {
        var start = getFirstNoteStart();
        var end = getLastNoteEnd();
        return end.subtract(start);
    }

    /**
     * Находит минимальный интервал между началами любых двух последовательных нот
     *
     * @return Fraction минимального интервала или empty, если меньше двух нот
     */
    public Optional<Fraction> getMinIntervalBetweenNotes() {
        // Собираем все времена начала нот
        List<Fraction> startTimes = beats.stream()
            .map(TimeBeats::at)
            .distinct()
            .sorted(Fraction::compareTo)
            .collect(Collectors.toList());

        if( startTimes.size() < 2 ){
            return Optional.empty();
        }

        // Находим минимальную разницу между соседними временами
        Fraction minInterval = null;
        for( int i = 1; i < startTimes.size(); i++ ){
            Fraction interval = startTimes.get(i).subtract(startTimes.get(i - 1)).reduce();
            if( minInterval == null || interval.compareTo(minInterval) < 0 ){
                minInterval = interval;
            }
        }
        return Optional.of(minInterval);
    }

    /**
     * Возвращает список нот, которые должны начать играть в заданный момент времени
     *
     * @param time Момент времени
     * @return Список нот (Beat), начинающихся в указанный момент, или пустой список
     */
    public ImList<Beat> getBeatsAt(Fraction time) {
        if( time == null ) throw new IllegalArgumentException("time==null");
        return beats
            .filter(tb -> tb.at().compareTo(time) == 0)
            .fmap(TimeBeats::beats);
    }

    /**
     * Находит ближайший следующий момент времени, когда начинается нота или группа нот
     *
     * @param time Текущий момент времени
     * @return Fraction ближайшего следующего момента времени или empty, если такого нет
     */
    public Optional<Fraction> getNextNoteStart(Fraction time) {
        if( time == null ) throw new IllegalArgumentException("time==null");
        return beats.stream()
            .map(TimeBeats::at)
            .filter(t -> t.compareTo(time) > 0)
            .min(Fraction::compareTo);
    }

    /**
     * Находит ближайший следующий момент времени, когда начинается нота или группа нот
     *
     * @param time Текущий момент времени
     * @return Fraction ближайшего следующего момента времени или empty, если такого нет
     */
    public Optional<Fraction> getPrevNoteStart(Fraction time) {
        if( time == null ) throw new IllegalArgumentException("time==null");
        return beats.stream()
            .map(TimeBeats::at)
            .filter(t -> t.compareTo(time) < 0)
            .max(Fraction::compareTo);
    }

    /**
     * Бесконечный итератор по нотам в паттерне
     * @return итератор
     */
    public ExtIterable<TimeBeats> infinitySequence(){
        return ()->new MPatternInfinityIterator(this);
    }

    public record LeftNearest(Fraction left, Fraction mod) {}

    /**
     * Ближайший "левый" момент времени относительно указанного
     * @param at момент времени
     * @return ближайший момент времени
     */
    public LeftNearest leftNearestAt( Fraction at ){
        if( at==null ) throw new IllegalArgumentException("at==null");

        var totalLen = getLastNoteEnd();
        var mod = at.mod( totalLen );

        Fraction left = null;
        for( var tBeats : beats ){
            var t = tBeats.at();
            if( left==null ){
                if( t.compare(mod)<=0 ){
                    left = t;
                }
            }else {
                if( mod.compare(t)<=0 ){
                    break;
                }else{
                    left = t;
                }
            }
        }

        return new LeftNearest(left, mod);
    }
}
