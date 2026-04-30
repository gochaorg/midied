package xyz.cofe.nipal.files;

import xyz.cofe.coll.im.Result;
import xyz.cofe.mitrenier.json.JSON;
import xyz.cofe.nipal.util.HtmlUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static xyz.cofe.coll.im.Result.error;
import static xyz.cofe.coll.im.Result.ok;

public class DirJsonView implements DirContentWriter {
    private volatile Appendable out;
    private volatile boolean headerWasWrite = false;
    private final List<Object> content = new ArrayList<>();

    public DirJsonView(Appendable out){
        if( out==null ) throw new IllegalArgumentException("out==null");
        this.out = out;
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
        var map = new LinkedHashMap<String,Object>();
        map.put("type", "file");
        assign(map, entry);
        map.put("size", entry.size());
        entry.hash().ifPresent( hash -> {
            if( hash instanceof FileHash.MD5 h ){
                map.put("hash", Map.of("md5", h.value()));
            }else if( hash instanceof FileHash.SHA1 h){
                map.put("hash", Map.of("sha1", h.value()));
            }else if( hash instanceof FileHash.SHA256 h){
                map.put("hash", Map.of("sha256", h.value()));
            }
        });
        content.add(map);
        return ok();
    }

    private Result<Result.NoValue, Throwable> write(Appendable out, DirEntry.Dir entry){
        var map = new LinkedHashMap<String,Object>();
        map.put("type", "dir");
        assign(map, entry);
        content.add(map);
        return ok();
    }

    private void assign(Map<String,Object> view, DirEntry entry){
        view.put("name", entry.name());
        entry.createTime().ifPresent(t -> view.put("createTime", t));
        entry.modifyTime().ifPresent(t -> view.put("modifyTime", t));
    }

    private Result<Result.NoValue, Throwable> writeHeadIfNot(Appendable out){
        if( headerWasWrite )return ok();
        headerWasWrite = true;

        return ok();
    }

    private Result<Result.NoValue, Throwable> writeTail(Appendable out){
        try {
            out.append(JSON.toJson(content));
            content.clear();
            return ok();
        } catch ( IOException e ) {
            return error(e);
        }
    }
}
