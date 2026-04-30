package xyz.cofe.nipal.header;

import org.junit.jupiter.api.Test;
import xyz.cofe.coll.im.ImList;
import xyz.cofe.coll.im.Tuple2;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContentRangeTest {
    @Test
    public void parsing1(){
        var cr = ContentRange.parse("bytes 0-1023/146515").get();
        assertEquals(new ContentRange.RangeWithTotal(0, 1023, 146515), cr);
    }

    @Test
    public void parsing2(){
        for( var sample : ImList.of(
            Tuple2.of("bytes 0-1023/*", new ContentRange.RangeOnly(0, 1023)),
            Tuple2.of("Bytes 0 - 1023/*", new ContentRange.RangeOnly(0, 1023)),
            Tuple2.of("bYtes 0- 1023/*", new ContentRange.RangeOnly(0, 1023)),
            Tuple2.of("BYTES  0 -1023/*", new ContentRange.RangeOnly(0, 1023))
        ) ){
            assertEquals(sample._2(), ContentRange.parse(sample._1()).get());
        }
    }
}
