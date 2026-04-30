package xyz.cofe.nipal.header;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "SimplifiableAssertion"})
public class AcceptParserTest {
    @Test
    public void test0(){
        var headerOpt = AcceptParser.parse("");
        var header = headerOpt.get();
        assertTrue(header.media().isEmpty());
    }

    @Test
    public void test1(){
        var headerOpt = AcceptParser.parse("application/json");
        var header = headerOpt.get();
        assertTrue(header.media().size()==1);
        assertTrue(header.media().get(0).get().mime().equals("application/json"));
    }

    @Test
    public void test2(){
        var headerOpt = AcceptParser.parse("text/html;q=0.8");
        var header = headerOpt.get();
        assertTrue(header.media().size()==1);
        assertTrue(header.media().get(0).get().mime().equals("text/html"));
        assertTrue(header.media().get(0).get().weight()==0.8);
    }

    @Test
    public void test3(){
        var headerOpt = AcceptParser.parse("text/html;q=1.0, image/*;q=0.8, application/json;q=0.5, */*;q=0.1");
        var header = headerOpt.get();
        System.out.println(header);

        assertTrue(header.media().size()==4);

        assertTrue(header.media().get(0).get().mime().equals("text/html"));
        assertTrue(header.media().get(0).get().weight()==1.0);

        assertTrue(header.media().get(1).get().mime().equals("image/*"));
        assertTrue(header.media().get(1).get().weight()==0.8);

        assertTrue(header.media().get(2).get().mime().equals("application/json"));
        assertTrue(header.media().get(2).get().weight()==0.5);

        assertTrue(header.media().get(2).get().mime().equals("*/*"));
        assertTrue(header.media().get(2).get().weight()==0.1);
    }
}
