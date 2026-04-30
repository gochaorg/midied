package xyz.cofe.nipal.files;

import xyz.cofe.coll.im.Result;
import xyz.cofe.nipal.util.HtmlUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import static xyz.cofe.coll.im.Result.error;
import static xyz.cofe.coll.im.Result.ok;

public class DirHtmlView implements DirContentWriter {
    private volatile Appendable out;
    private volatile boolean headerWasWrite = false;
    private Function<DirEntry, Optional<String>> urlFetch = e -> Optional.empty();

    public DirHtmlView(Appendable out){
        if( out==null ) throw new IllegalArgumentException("out==null");
        this.out = out;
    }

    public DirHtmlView urlDirEntryBuilder( Function<DirEntry, Optional<String>> builder ){
        if( builder==null ) throw new IllegalArgumentException("builder==null");
        this.urlFetch = builder;
        return this;
    }

    @Override
    public Result<Result.NoValue, Throwable> write(DirEntry entry) {
        var out = this.out;
        if( out==null )return error(new IllegalStateException("closed"));
        synchronized(out){
            return writeHeadIfNot(out).fmap( r -> write(out, entry));
        }
    }

    @Override
    public Result<Result.NoValue, Throwable> end() {
        var out = this.out;
        if( out==null )return error(new IllegalStateException("closed"));
        synchronized(out){
            return writeTail(out).map( r -> {
                this.out = null;
                return r;
            });
        }
    }

    private Result<Result.NoValue, Throwable> write(Appendable out, DirEntry entry){
        if( entry instanceof DirEntry.File f ){
            return write(out,f);
        } else if( entry instanceof DirEntry.Dir d ){
            return write(out,d);
        } else {
            return ok();
        }
    }

    private Result<Result.NoValue, Throwable> write(Appendable out, DirEntry.File entry){
        try {
            var urlOpt = urlFetch.apply(entry);
            if( urlOpt.isEmpty() ){
                out.append(HtmlUtils.encodeHtml(entry.name()));
            }else{
                out.append("<a href=\"").append(urlOpt.get()).append("\">");
                out.append(HtmlUtils.encodeHtml(entry.name()));
                out.append("</a>");
            }
            out.append("<br/>\n");
            return ok();
        } catch ( IOException e ) {
            return error(e);
        }
    }

    private Result<Result.NoValue, Throwable> write(Appendable out, DirEntry.Dir entry){
        try {
            var urlOpt = urlFetch.apply(entry);
            if( urlOpt.isEmpty() ){
                out.append(HtmlUtils.encodeHtml(entry.name()));
            }else{
                out.append("<a href=\"").append(urlOpt.get()).append("\">");
                out.append(HtmlUtils.encodeHtml(entry.name()));
                out.append("</a>");
            }
            out.append("<br/>\n");
            return ok();
        } catch ( IOException e ) {
            return error(e);
        }
    }

    private Result<Result.NoValue, Throwable> writeHeadIfNot(Appendable out){
        if( headerWasWrite )return ok();
        headerWasWrite = true;

        try {
            out.append("<html><body>\n");
            return ok();
        } catch ( IOException e ) {
            return error(e);
        }
    }

    private Result<Result.NoValue, Throwable> writeTail(Appendable out){
        try {
            out.append("</body></html>\n");
            return ok();
        } catch ( IOException e ) {
            return error(e);
        }
    }
}
