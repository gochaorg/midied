package xyz.cofe.mitrenier.music;

import org.junit.jupiter.api.Test;
import xyz.cofe.coll.im.ImList;
import xyz.cofe.mitrenier.json.JSON;
import xyz.cofe.mitrenier.math.Fraction;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SimplifiableAssertion")
public class MPatternTest {
    @Test
    public void pattern_4_4(){
        var d1_4 = new Fraction(1,4);

        var t0 = new Fraction(0,4);
        //noinspection UnnecessaryLocalVariable
        var t1 = d1_4;
        var t2 = d1_4.add(d1_4);
        var t3 = d1_4.add(d1_4).add(d1_4);

        var pattern = MPattern.of(
            TimeBeats.of(t0, Beat.strong().any().length(d1_4)),
            TimeBeats.of(t1, Beat.weak().any().length(d1_4)),
            TimeBeats.of(t2, Beat.medium().any().length(d1_4)),
            TimeBeats.of(t3, Beat.weak().any().length(d1_4))
            );

        var tTot = pattern.getTotalNoteDuration();
        System.out.println(tTot);
        assertTrue(tTot.toString().equals("1/1"));

        var tStart = pattern.getFirstNoteStart();
        System.out.println(tStart);
        assertTrue(tStart.toString().equals("0/1"));

        var tEnd = pattern.getLastNoteEnd();
        System.out.println(tEnd);
        assertTrue(tEnd.toString().equals("1/1"));

        var minInterval = pattern.getMinIntervalBetweenNotes();
        System.out.println(minInterval);
        assertTrue(minInterval.isPresent());
        assertTrue(minInterval.get().toString().equals("1/4"));

        Map<Fraction, ImList<Beat>> beats = new TreeMap<>();
        var t = tStart;
        while( true ){
            var beats0 = pattern.getBeatsAt(t);
            beats.put(t,beats0);
            System.out.println("t "+t+" beats "+beats0);

            var nextT = pattern.getNextNoteStart(t);
            if( nextT.isEmpty() )break;

            t = nextT.get();
        }

        assertTrue(beats.get(t0).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Strong[]]]"));
        assertTrue(beats.get(t1).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Weak[]]]"));
        assertTrue(beats.get(t2).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Medium[]]]"));
        assertTrue(beats.get(t3).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Weak[]]]"));

        System.out.println("----");
        beats.clear();
        var iter = pattern.infinitySequence().iterator();
        for( var i=0;i<8;i++ ){
            var tbeats = iter.next();
            System.out.println("t "+tbeats.at()+" beats "+tbeats.beats());
            beats.put(tbeats.at(), tbeats.beats());
        }

        var t4 = d1_4.add(d1_4).add(d1_4).add(d1_4);
        var t5 = d1_4.add(d1_4).add(d1_4).add(d1_4).add(d1_4);
        var t6 = d1_4.add(d1_4).add(d1_4).add(d1_4).add(d1_4).add(d1_4);
        var t7 = d1_4.add(d1_4).add(d1_4).add(d1_4).add(d1_4).add(d1_4).add(d1_4);

        assertTrue(beats.get(t0).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Strong[]]]"));
        assertTrue(beats.get(t1).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Weak[]]]"));
        assertTrue(beats.get(t2).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Medium[]]]"));
        assertTrue(beats.get(t3).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Weak[]]]"));
        assertTrue(beats.get(t4).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Strong[]]]"));
        assertTrue(beats.get(t5).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Weak[]]]"));
        assertTrue(beats.get(t6).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Medium[]]]"));
        assertTrue(beats.get(t7).toString().equals("[Beat[pitch=AnyPitch[], length=1/4, power=Weak[]]]"));
    }

    @Test
    public void json(){
        var d1_4 = new Fraction(1,4);

        var t0 = new Fraction(0,4);
        //noinspection UnnecessaryLocalVariable
        var t1 = d1_4;
        var t2 = d1_4.add(d1_4);
        var t3 = d1_4.add(d1_4).add(d1_4);

        var pattern = MPattern.of(
            TimeBeats.of(t0, Beat.strong().any().length(d1_4)),
            TimeBeats.of(t1, Beat.weak().any().length(d1_4)),
            TimeBeats.of(t2, Beat.medium().any().length(d1_4)),
            TimeBeats.of(t3, Beat.weak().any().length(d1_4))
        );

        var json = JSON.toJson(pattern);
        System.out.println(json);

        var restored = JSON.fromJson(json,MPattern.class);

        assertTrue(restored.getBeats().size()==4);
        var b0 = restored.getBeats().get(0).get();
        var b1 = restored.getBeats().get(1).get();
        var b2 = restored.getBeats().get(2).get();
        var b3 = restored.getBeats().get(3).get();

        assertTrue(b0.at().equals(t0));
        assertTrue(b0.beats().size()==1);
        assertTrue(b0.beats().get(0).get().pitch().equals(new Pitch.AnyPitch()));
        assertTrue(b0.beats().get(0).get().length().equals(d1_4));
        assertTrue(b0.beats().get(0).get().power().equals(new Power.Strong()));

        assertTrue(b1.at().equals(t1));
        assertTrue(b2.at().equals(t2));
        assertTrue(b3.at().equals(t3));
    }
}
