package xyz.cofe.nipal.header;

import org.junit.jupiter.api.Test;
import xyz.cofe.coll.im.ImList;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SimplifiableAssertion")
public class RangeTest {
    @Test
    public void testFromTo1(){
        var range = Range.parse("bytes=0-99").get();
        assertTrue(range.equals(new Range(ImList.of(new Range.FromTo(0,100)))));
    }

    @Test
    public void testFromTo2(){
        var range = Range.parse("bytes = 0 - 99").get();
        assertTrue(range.equals(new Range(ImList.of(new Range.FromTo(0,100)))));
    }

    @Test
    public void testFrom1(){
        var range = Range.parse("Bytes = 10 -").get();
        assertTrue(range.equals(new Range(ImList.of(new Range.From(10)))));
    }

    @Test
    public void testTo1(){
        var range = Range.parse("Bytes = -12").get();
        assertTrue(range.equals(new Range(ImList.of(new Range.To(13)))));
    }
}
