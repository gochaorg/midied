package xyz.cofe.mitrenier.web.main;

import org.junit.jupiter.api.Test;

public class StaticMainWebTest {
    @Test
    public void test1(){
        var main = new StaticMainWeb();
        main.cmdLine.parse(
            "threads", "minmax", "2", "10"
        );
    }
}
