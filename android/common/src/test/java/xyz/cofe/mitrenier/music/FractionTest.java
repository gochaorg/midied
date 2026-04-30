package xyz.cofe.mitrenier.music;

import org.junit.jupiter.api.Test;
import xyz.cofe.mitrenier.math.Fraction;

import java.time.Duration;
import java.util.List;

public class FractionTest {
    @Test
    public void test1(){
        List.of(30,60,120).forEach(bpm -> {
            List.of(20,40,100).forEach(ms -> {
                System.out.print("bpm "+bpm+" ms "+ms+" ");
                var f = Fraction.fromDuration(Duration.ofMillis(ms), bpm);
                System.out.print(" f "+f);
                System.out.print(" <-> ms "+f.toDuration(bpm).toMillis());
                System.out.println();
            });
        });
    }
}
