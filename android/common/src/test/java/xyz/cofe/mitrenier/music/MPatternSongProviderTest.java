package xyz.cofe.mitrenier.music;

import org.junit.jupiter.api.Test;
import xyz.cofe.mitrenier.math.Fraction;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MPatternSongProviderTest {
    @Test
    public void test(){
        var len = Fraction.of(1,4);
        var a0 = Fraction.of(0,1);
        var a1 = a0.add(len);
        var a2 = a1.add(len);
        var a2b = a1.add(len).add(Fraction.of(1,8));
        var a3 = a2.add(len);
        var ptrn = MPattern.of(
            TimeBeats.of(a0, Beat.of(Pitch.any(), len, Power.strong())),
            TimeBeats.of(a1, Beat.of(Pitch.none(), len, Power.weak())),
            TimeBeats.of(a2, Beat.of(Pitch.any(), len.divide(Fraction.of(2,1)), Power.weak())),
            TimeBeats.of(a2b, Beat.of(Pitch.any(), len.divide(Fraction.of(2,1)), Power.strong())),
            TimeBeats.of(a3, Beat.of(Pitch.any(), len, Power.strong()))
        );

        var songProvider = new MPatternSongProvider().pattern(ptrn).startTime(Instant.now().truncatedTo(ChronoUnit.MINUTES));
        var iter = songProvider.iterator();
        for( int i=0;i<3;i++ ){
            var song = iter.next();
            assertTrue(song!=null);

            System.out.println("iter "+i);
            for( var ev : song.getEvents() ){
                System.out.println("  ev "+ev);
            }
        }
    }
}
