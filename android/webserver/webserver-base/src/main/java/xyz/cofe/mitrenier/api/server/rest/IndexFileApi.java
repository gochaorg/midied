package xyz.cofe.mitrenier.api.server.rest;

import org.eclipse.jetty.server.Handler;
import xyz.cofe.nipal.util.HtmlUtils;
import xyz.cofe.nipal.RequestRouter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class IndexFileApi {
    private String path = "/index.html";

    public IndexFileApi urlPath(String path) {
        if( path == null ) throw new IllegalArgumentException("path==null");
        this.path = path;
        return this;
    }
    public String urlPath(){
        return path;
    }

    private Supplier<String> source = () -> "<html><body></body></html>";

    public class Source {
        public Source of(Supplier<String> src) {
            if( src == null ) throw new IllegalArgumentException("src==null");
            source = src;
            return this;
        }

        public Source of(String src) {
            if( src == null ) throw new IllegalArgumentException("src==null");
            source = () -> src;
            return this;
        }

        public Source resource(String resourceName) {
            if( resourceName == null ) throw new IllegalArgumentException("resourceName==null");
            return resource(IndexFileApi.class, resourceName);
        }

        public Source resource(Class<?> cls, String resourceName) {
            if( cls==null ) throw new IllegalArgumentException("cls==null");
            if( resourceName == null ) throw new IllegalArgumentException("resourceName==null");

            var url = cls.getResource(resourceName);
            if( url == null ) throw new IllegalArgumentException("resource "+resourceName+" not found");

            var bytes = new ByteArrayOutputStream();
            var buff = new byte[1024 * 8];
            try( var stream = url.openStream() ) {
                while( true ) {
                    var reads = stream.read(buff);
                    if( reads < 0 ) break;
                    if( reads > 0 ) {
                        bytes.write(buff, 0, reads);
                    }
                }
            } catch ( IOException e ) {
                throw new RuntimeException(e);
            }

            //noinspection StringOperationCanBeSimplified
            return of(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }

        public Source file(String fileName){
            if( fileName==null ) throw new IllegalArgumentException("fileName==null");

            var bytes = new ByteArrayOutputStream();
            var buff = new byte[1024 * 8];
            try( var stream = Files.newInputStream(Paths.get(fileName)) ) {
                while( true ) {
                    var reads = stream.read(buff);
                    if( reads < 0 ) break;
                    if( reads > 0 ) {
                        bytes.write(buff, 0, reads);
                    }
                }
            } catch ( IOException e ) {
                throw new RuntimeException(e);
            }

            //noinspection StringOperationCanBeSimplified
            return of(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    public Source source() {
        return new Source();
    }

    public IndexFileApi source(Consumer<Source> srcConf){
        if( srcConf==null ) throw new IllegalArgumentException("srcConf==null");
        srcConf.accept(source());
        return this;
    }

    public IndexFileApi source(Supplier<String> src) {
        if( src == null ) throw new IllegalArgumentException("src==null");
        source().of(src);
        return this;
    }

    public IndexFileApi source(String src) {
        if( src == null ) throw new IllegalArgumentException("src==null");
        source().of(src);
        return this;
    }

    private final Map<String, Supplier<String>> params = new TreeMap<>();

    public class Params {
        public Params clear() {
            params.clear();
            return this;
        }

        public Params set(String key, String value) {
            if( key == null ) throw new IllegalArgumentException("key==null");
            if( value == null ) throw new IllegalArgumentException("value==null");
            params.put(key, () -> value);
            return this;
        }

        public Params set(String key, Supplier<String> value) {
            if( key == null ) throw new IllegalArgumentException("key==null");
            if( value == null ) throw new IllegalArgumentException("value==null");
            params.put(key, value);
            return this;
        }
    }

    public Params params() {
        return new Params();
    }

    public IndexFileApi params(Consumer<Params> params){
        if( params==null ) throw new IllegalArgumentException("params==null");
        params.accept(new Params());
        return this;
    }

    public Handler build() {
        return RequestRouter.Builder.GET().path(path).htmlResponse().jettyRequest().call(req -> this::compile);
    }

    private final Pattern bodyStart = Pattern.compile("(?i)<body\\w*>");

    public String compile() {
        String srcHtml = source.get();
        var m = bodyStart.matcher(srcHtml);
        if( m.find() ) {
            int s = m.start();
            int e = m.end();
            String before = srcHtml.substring(0, e);
            String after = srcHtml.substring(e);
            String result = before + buildParams() + after;
            return result;
        }
        return srcHtml;
    }

    private String buildParams() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");

        params.forEach((key, value) -> {
            if( key == null || value == null ) return;
            var strValue = value.get();
            if( strValue == null ) return;

            sb.append("<config key=\"")
                .append(HtmlUtils.encodeHtmlAttribute(key))
                .append("\" value=\"")
                .append(HtmlUtils.encodeHtmlAttribute(strValue))
                .append("\"></config>\n");
        });

        return sb.toString();
    }
}
