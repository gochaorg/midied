package xyz.cofe.nipal.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.coll.im.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public sealed interface DirEntry {
    static final Logger log = LoggerFactory.getLogger(DirEntry.class);

    String name();
    Optional<Instant> createTime();
    Optional<Instant> modifyTime();

    record File(String name, long size, Optional<Instant> createTime, Optional<Instant> modifyTime, Optional<FileHash> hash) implements DirEntry {}
    record Dir(String name, Optional<Instant> createTime, Optional<Instant> modifyTime) implements DirEntry {}

    static Result<Result.NoValue,Throwable> writeDirContent(DirContentWriter dirContentWriter, Path path, Predicate<Path> filter){
        if( dirContentWriter==null ) throw new IllegalArgumentException("dirContentWriter==null");
        if( path==null ) throw new IllegalArgumentException("path==null");
        if( filter==null ) throw new IllegalArgumentException("filter==null");

        try( var ds = Files.newDirectoryStream(path) ){
            for( var file : ds ) {
                if( !filter.test(file) )continue;

                Optional<Instant> createTime = Optional.empty();
                Optional<Instant> modifyTime = Optional.empty();

                var basicAttrNull = Files.getFileAttributeView(file, BasicFileAttributeView.class);
                if( basicAttrNull!=null ) {
                    try {
                        var attrs = basicAttrNull.readAttributes();
                        createTime = Optional.of(attrs.creationTime().toInstant());
                        modifyTime = Optional.of(attrs.lastModifiedTime().toInstant());
                    } catch ( SecurityException | IOException ex ){
                        log.error("read attributes",ex);
                    }
                }

                if( Files.isDirectory(file) ){
                    var res = dirContentWriter.write(new Dir(file.getFileName().toString(), createTime, modifyTime));
                    if( res.isError() )return res;
                } else if( Files.isRegularFile(file) ){
                    var fileSize = Files.size(file);
                    var res = dirContentWriter.write(new File(file.getFileName().toString(), fileSize, createTime, modifyTime, Optional.empty()));
                    if( res.isError() )return res;
                }
            }

            return dirContentWriter.end();
        }catch ( IOException e ){
            return Result.error(e);
        }
    }
}
