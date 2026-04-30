package xyz.cofe.mitrenier.math;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SimplifiableAssertion")
public class IntervalManageTest {
    public record Interval<A>(A left, A right) {}


    @Test
    public void interval3() {
        IntervalManager<Integer> intervals = new IntervalManager<Integer>(Comparator.comparing(x -> x));

        var t0 = 0;
        var t1 = 10;
        var t2 = 20;
        var t3 = 30;

        intervals.begins.listener()
            .onInsert(b -> System.out.println("begin += " + b))
            .onDelete(b -> System.out.println("begin -= " + b))
            .add();

        intervals.ends.listener()
            .onInsert(e -> System.out.println("end += " + e))
            .onDelete(e -> System.out.println("end -= " + e))
            .add();

        intervals.intervalsBeginEnd.listener()
            .onInsert((b, e) -> System.out.println("interval begin->end += " + b + " .. " + e))
            .onDelete((b, e) -> System.out.println("interval begin->end -= " + b + " .. " + e))
            .add();

        Set<String> intervals2 = new TreeSet<>();
        intervals.sync(intervals2)
            .fullInterval((b, e) -> "full[" + b + "," + e + ")")
            .halfOpenInterval( b -> "half["+b )
            .bind();

        intervals.onInsertBegin(t0);
        intervals.onInsertEnd(t3);

        System.out.println("------------");
        intervals.intervalsBeginEnd.forEach((b, e) -> System.out.println("[" + b + "," + e + ")"));
        assertTrue(intervals.intervalsBeginEnd.size() == 1);
        assertTrue(intervals.intervalsBeginEnd.get(0) == 30);
        assertTrue(intervals.getHalfOpen().isEmpty());
        System.out.println(intervals2);
        System.out.println();

        intervals.onInsertEnd(t1);
        intervals.onInsertBegin(t2);

        System.out.println("------------");
        intervals.intervalsBeginEnd.forEach((b, e) -> System.out.println("[" + b + "," + e + ")"));
        assertTrue(intervals.intervalsBeginEnd.size() == 2);
        assertTrue(intervals.intervalsBeginEnd.get(0) == 10);
        assertTrue(intervals.intervalsBeginEnd.get(20) == 30);
        assertTrue(intervals.getHalfOpen().isEmpty());
        System.out.println(intervals2);
        System.out.println();

        intervals.onDeleteEnd(t1);

        System.out.println("------------");
        intervals.intervalsBeginEnd.forEach((b, e) -> System.out.println("[" + b + "," + e + ")"));
        assertTrue(intervals.intervalsBeginEnd.size() == 1);
        assertTrue(intervals.intervalsBeginEnd.get(20) == 30);
        assertTrue(intervals.getHalfOpen().isEmpty());
        System.out.println(intervals2);
        System.out.println();

        intervals.onInsertEnd(t1);

        System.out.println("------------");
        intervals.intervalsBeginEnd.forEach((b, e) -> System.out.println("[" + b + "," + e + ")"));
        assertTrue(intervals.intervalsBeginEnd.size() == 2);
        assertTrue(intervals.intervalsBeginEnd.get(0) == 10);
        assertTrue(intervals.intervalsBeginEnd.get(20) == 30);
        assertTrue(intervals.getHalfOpen().isEmpty());
        System.out.println(intervals2);
        System.out.println();

        intervals.onDeleteEnd(t3);

        System.out.println("------------");
        intervals.intervalsBeginEnd.forEach((b, e) -> System.out.println("[" + b + "," + e + ")"));
        System.out.println(intervals2);
        assertTrue(intervals.getHalfOpen().isPresent());
        assertTrue(intervals.getHalfOpen().map(v -> v==20).orElse(false));
        assertTrue(intervals.intervalsBeginEnd.get(0) == 10);
        assertTrue(intervals.intervalsBeginEnd.size() == 1);
        System.out.println();

        intervals.onInsertEnd(t3);

        System.out.println("------------");
        intervals.intervalsBeginEnd.forEach((b, e) -> System.out.println("[" + b + "," + e + ")"));
        assertTrue(intervals.getHalfOpen().isEmpty());
        assertTrue(intervals.intervalsBeginEnd.size() == 2);
        assertTrue(intervals.intervalsBeginEnd.get(0) == 10);
        assertTrue(intervals.intervalsBeginEnd.get(20) == 30);
        System.out.println(intervals2);
        System.out.println();
    }
}
