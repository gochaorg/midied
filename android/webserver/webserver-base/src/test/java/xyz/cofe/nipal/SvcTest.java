package xyz.cofe.nipal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import xyz.cofe.nipal.files.StaticFiles;

import java.nio.file.Path;
import java.util.Optional;

//@Disabled
@Tag("manual")
public class SvcTest {
    public static final int PORT = 8899;

    @Test
    public void httpSample() {
        var srvc = Service
            .builder()
            .port(PORT)
            .addRouter(router -> router.GET().path("/hello").jsonResponse().call(() -> "aa"))
            .addRouter(router -> router.GET().path(p -> p.startsWith("/dir")).htmlResponse().jettyRequest().call(req -> () ->
                """
                    <html>
                        <body>
                            <h1>header</h1>
                            uri: <code>${uri}</code> <br/>
                            path: <code>${path}</code> <br/>
                            query: <code>${query}</code> <br/>
                        </body>
                    </html>
                    """
                    .replace("${uri}", req.getHttpURI().asString())
                    .replace("${path}", Optional.ofNullable(req.getHttpURI().getPath()).orElse("none"))
                    .replace("${query}", Optional.ofNullable(req.getHttpURI().getQuery()).orElse("none"))
            ))
            .addHandler(StaticFiles
                .dir(Path.of("/home/user/code/midi/android/webserver/webserver-base/build/tmp"))
                .uriBase("/static")
                .allow( allow -> allow
                    .resources(true)
                    .read(true)
                    .abilities(true)
                    .upload(true)
                    .delete(true)
                    .mkdir(true)
                )
                .buildRouter())
            .start();

        try {
            Thread.sleep(1000 * 60 * 15);
        } catch ( InterruptedException e ) {
            throw new RuntimeException(e);
        }

        srvc.stop();
    }
}
