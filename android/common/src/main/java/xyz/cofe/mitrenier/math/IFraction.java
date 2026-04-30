package xyz.cofe.mitrenier.math;

import java.util.function.BiFunction;

/**
 * Класс для представления дроби (числитель/знаменатель).
 */
public interface IFraction<SELF extends IFraction<SELF>> extends Comparable<SELF> {
    public SELF create(int dividend, int divisor);

    /**
     * Возвращает числитель.
     */
    public int getDividend();

    /**
     * Возвращает знаменатель.
     */
    public int getDivisor();

    /**
     * Вычисляет наибольший общий делитель (НОД) двух чисел.
     *
     * @param a первое число
     * @param b второе число
     * @return НОД
     */
    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /**
     * Сокращает дробь до минимальной формы.
     *
     * @return новая дробь в сокращённом виде
     */
    public default SELF reduce() {
        int gcdVal = gcd(Math.abs(getDividend()), Math.abs(getDivisor()));
        int sign = getDivisor() < 0 ? -1 : 1;
        return create(sign * getDividend() / gcdVal, Math.abs(getDivisor()) / gcdVal);
    }

    /**
     * Складывает текущую дробь с другой.
     *
     * @param other другая дробь
     * @return сумма в сокращённом виде
     */
    public default SELF add(IFraction other) {
        int num = this.getDividend() * other.getDivisor() + other.getDividend() * this.getDivisor();
        int den = this.getDivisor() * other.getDivisor();
        return create(num, den).reduce();
    }

    /**
     * Вычитает другую дробь из текущей.
     *
     * @param other другая дробь
     * @return разность в сокращённом виде
     */
    public default SELF subtract(IFraction other) {
        int num = this.getDividend() * other.getDivisor() - other.getDividend() * this.getDivisor();
        int den = this.getDivisor() * other.getDivisor();
        return create(num, den).reduce();
    }

    /**
     * Умножает текущую дробь на другую.
     *
     * @param other другая дробь
     * @return произведение в сокращённом виде
     */
    public default SELF multiply(IFraction other) {
        return create(this.getDividend() * other.getDividend(), this.getDivisor() * other.getDivisor()).reduce();
    }

    /**
     * Делит текущую дробь на другую.
     *
     * @param other другая дробь
     * @return частное в сокращённом виде
     * @throws IllegalArgumentException если деление на ноль
     */
    public default SELF divide(IFraction other) {
        if( other.getDividend() == 0 ){
            throw new IllegalArgumentException("Нельзя делить на дробь с нулевым числителем");
        }
        return create(this.getDividend() * other.getDivisor(), this.getDivisor() * other.getDividend()).reduce();
    }

    /**
     * Сравнивает текущую дробь с другой.
     *
     * @param other другая дробь
     * @return отрицательное число, если this < other; ноль, если равны; положительное, если this > other
     */
    public default int compare(IFraction other) {
        long left = (long) this.getDividend() * other.getDivisor();
        long right = (long) other.getDividend() * this.getDivisor();
        return Long.compare(left, right);
    }

    @Override
    public default int compareTo(IFraction fraction) {
        if( fraction == null ) return 0;
        return compare(fraction);
    }

    /**
     * Проверяет, меньше ли текущая дробь другой.
     */
    public default boolean lessThan(SELF other) {
        return compare(other) < 0;
    }

    /**
     * Проверяет, меньше или равна ли текущая дробь другой.
     */
    public default boolean lessThanOrEqual(SELF other) {
        return compare(other) <= 0;
    }

    /**
     * Проверяет, больше ли текущая дробь другой.
     */
    public default boolean greaterThan(SELF other) {
        return compare(other) > 0;
    }

    /**
     * Проверяет, больше или равна ли текущая дробь другой.
     */
    public default boolean greaterThanOrEqual(SELF other) {
        return compare(other) >= 0;
    }

    /**
     * Целая и дробная часть
     *
     * @param whole    Целая
     * @param fraction дробная часть
     */
    public record SplitFraction(
        int whole,
        Fraction fraction
    ) {}

    /**
     * Разделяет дробь на целую и дробную части.
     *
     * @return массив, содержащий целую часть (Integer) и дробную часть (Fraction)
     */
    public default SplitFraction split() {
        int whole = getDividend() / getDivisor();
        int remainder = getDividend() % getDivisor();
        Fraction fracPart = new Fraction(remainder, getDivisor()).reduce();
        return new SplitFraction(whole, fracPart);
    }

    /**
     * Вычисляет остаток от деления текущей дроби на другую.
     *
     * @param other другая дробь (делитель)
     * @return остаток от деления (this % other)
     * @throws IllegalArgumentException если деление на ноль
     */
    public default SELF mod(IFraction other) {
        if( other.getDividend() == 0 ){
            throw new IllegalArgumentException("Нельзя делить на ноль");
        }

        // (this / other)
        var division = divide(other);

        // Целая часть от деления (floor)
        int quotient = division.getDividend() / division.getDivisor();

        // (other * quotient)
        IFraction oo = create(quotient, 1);
        IFraction product = other.multiply(oo);

        // Остаток: this - (other * quotient)
        return this.subtract(product);
    }

    /**
     * Преобразует дробь в длительность в секундах/долях секунды.
     *
     * @param bpm частота ударов в минуту (для четвертной ноты)
     * @return длительность
     */
    public default long toDurationMS(int bpm) {
        double quarterNoteSeconds = 60.0 / bpm;
        double singleNoteDuration = quarterNoteSeconds * (4.0 / getDivisor());
        double totalSeconds = singleNoteDuration * getDividend();
        return ((long) (totalSeconds * 1000));
    }

    /**
     * Преобразует длительность и bpm в дробь.
     *
     * @param durationMS длительность
     * @param bpm        удары в минуту (для четвертной ноты)
     * @return дробь, представляющая музыкальную длительность
     * @throws IllegalArgumentException если bpm <= 0 или duration <= 0
     */
    public static <FRac extends IFraction<FRac>> FRac fromDuration(long durationMS, int bpm, BiFunction<Integer, Integer, FRac> create) {
        if( bpm <= 0 ){
            throw new IllegalArgumentException("BPM должен быть положительным");
        }
        if( durationMS == 0 ) return create.apply(0, 1);
        if( durationMS < 0 ){
            throw new IllegalArgumentException("Длительность должна быть положительной");
        }

        double totalSeconds = durationMS / 1000.0;
        double quarterNoteSeconds = 60.0 / bpm;

        // Сколько четвертных нот помещается в длительности
        double quarterNotes = totalSeconds / quarterNoteSeconds;

        // Преобразуем это число в дробь
        return approximateFraction
            (quarterNotes, 1024, create)
            .reduce().multiply(create.apply(1, 4)); // с максимально допустимым знаменателем
    }

    /**
     * Аппроксимирует десятичное число дробью.
     *
     * @param value          значение с плавающей точкой
     * @param maxDenominator максимальный знаменатель
     * @return приближённая дробь
     */
    public static <FRac extends IFraction<FRac>> FRac approximateFraction(double value, int maxDenominator, BiFunction<Integer, Integer, FRac> create) {
        int sign = value < 0 ? -1 : 1;
        value = Math.abs(value);

        int bestNumerator = 1;
        int bestDenominator = 1;
        double bestError = Math.abs(value - 1.0);

        for( int denominator = 1; denominator <= maxDenominator; denominator++ ){
            int numerator = (int) Math.round(value * denominator);
            double error = Math.abs(value - (double) numerator / denominator);

            if( error < bestError ){
                bestNumerator = numerator;
                bestDenominator = denominator;
                bestError = error;

                if( error < 1e-6 ){
                    break; // достаточно точно
                }
            }
        }

        return create.apply(sign * bestNumerator, bestDenominator);
    }
}
