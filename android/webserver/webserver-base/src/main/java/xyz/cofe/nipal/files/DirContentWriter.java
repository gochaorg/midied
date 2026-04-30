package xyz.cofe.nipal.files;

import xyz.cofe.coll.im.Result;

public interface DirContentWriter {
    Result<Result.NoValue, Throwable> write(DirEntry entry);
    Result<Result.NoValue, Throwable> end();
}
