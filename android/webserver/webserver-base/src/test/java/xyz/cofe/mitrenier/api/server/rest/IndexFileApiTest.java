package xyz.cofe.mitrenier.api.server.rest;

import org.junit.jupiter.api.Test;

public class IndexFileApiTest {
    @Test
    void test(){
        String txt = new IndexFileApi().source("<html><body></body></html>").compile();
        System.out.println(txt);
    }
}
