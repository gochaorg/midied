package xyz.cofe.mitrenier.math;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;

/**
 * Класс для представления дроби (числитель/знаменатель).
 */
@SuppressWarnings("NullableProblems")
public class Fraction implements IFraction<Fraction> {
    public static class JSONView implements JsonSerializer<Fraction>,
                                            JsonDeserializer<Fraction> {
        @Override
        public Fraction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
            JsonParseException {
            var str = json.getAsString();
            var numStrs = str.split("/",2);
            if( numStrs.length!=2 ){
                throw new JsonParseException("expect string with 'num/num'");
            }
            var n1 = Integer.parseInt(numStrs[0]);
            var n2 = Integer.parseInt(numStrs[1]);
            return of(n1,n2);
        }

        @Override
        public JsonElement serialize(Fraction src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }

    public static final Fraction ZERO = Fraction.of(0,1);
    public static final Fraction ONE = Fraction.of(1,1);

    private final int dividend; // Числитель
    private final int divisor;  // Знаменатель

    @Override
    public int compareTo(@NotNull Fraction o) {
        return compare(o);
    }

    @Override
    public Fraction create(int dividend, int divisor) {
        return of(dividend,divisor);
    }

    /**
     * Конструктор для создания дроби.
     *
     * @param dividend числитель
     * @param divisor  знаменатель
     * @throws IllegalArgumentException если знаменатель равен нулю
     */
    public Fraction(int dividend, int divisor) {
        if (divisor == 0) {
            throw new IllegalArgumentException("Знаменатель не может быть равен нулю");
        }
        this.dividend = dividend;
        this.divisor = divisor;
    }

    public static Fraction of(int dividend, int divisor){
        return new Fraction(dividend, divisor);
    }

    /**
     * Возвращает строковое представление дроби в виде "числитель/знаменатель".
     */
    @Override
    public String toString() {
        Fraction reduced = this.reduce();
        return reduced.dividend + "/" + reduced.divisor;
    }

    /**
     * Преобразует дробь в длительность в секундах/долях секунды.
     *
     * @param bpm частота ударов в минуту (для четвертной ноты)
     * @return длительность
     */
    public Duration toDuration(int bpm) {
        double quarterNoteSeconds = 60.0 / bpm;
        double singleNoteDuration = quarterNoteSeconds * (4.0 / divisor);
        double totalSeconds = singleNoteDuration * dividend;
        return Duration.ofMillis((long) (totalSeconds * 1000));
    }

    /**
     * Преобразует длительность и bpm в дробь.
     *
     * @param duration длительность
     * @param bpm удары в минуту (для четвертной ноты)
     * @return дробь, представляющая музыкальную длительность
     * @throws IllegalArgumentException если bpm <= 0 или duration <= 0
     */
    public static Fraction fromDuration(Duration duration, int bpm) {
        if (bpm <= 0) {
            throw new IllegalArgumentException("BPM должен быть положительным");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Длительность должна быть положительной");
        }

        double totalSeconds = duration.toMillis() / 1000.0;
        double quarterNoteSeconds = 60.0 / bpm;

        // Сколько четвертных нот помещается в длительности
        double quarterNotes = totalSeconds / quarterNoteSeconds;

        // Преобразуем это число в дробь
        return approximateFraction(quarterNotes, 1024).reduce().multiply( of(1,4)); // с максимально допустимым знаменателем
    }

    /**
     * Аппроксимирует десятичное число дробью.
     *
     * @param value значение с плавающей точкой
     * @param maxDenominator максимальный знаменатель
     * @return приближённая дробь
     */
    private static Fraction approximateFraction(double value, int maxDenominator) {
        int sign = value < 0 ? -1 : 1;
        value = Math.abs(value);

        int bestNumerator = 1;
        int bestDenominator = 1;
        double bestError = Math.abs(value - 1.0);

        for (int denominator = 1; denominator <= maxDenominator; denominator++) {
            int numerator = (int) Math.round(value * denominator);
            double error = Math.abs(value - (double) numerator / denominator);

            if (error < bestError) {
                bestNumerator = numerator;
                bestDenominator = denominator;
                bestError = error;

                if (error < 1e-6) {
                    break; // достаточно точно
                }
            }
        }

        return new Fraction(sign * bestNumerator, bestDenominator);
    }

    /**
     * Проверяет равенство с другой дробью.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fraction)) return false;
        Fraction other = (Fraction) o;
        return this.compare(other) == 0;
    }

    /**
     * Вычисляет хэш-код дроби.
     */
    @Override
    public int hashCode() {
        Fraction reduced = this.reduce();
        return Objects.hash(reduced.dividend, reduced.divisor);
    }

    /**
     * Возвращает числитель.
     */
    public int getDividend() {
        return dividend;
    }

    /**
     * Возвращает знаменатель.
     */
    public int getDivisor() {
        return divisor;
    }
}
