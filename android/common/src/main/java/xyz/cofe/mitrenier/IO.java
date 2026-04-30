package xyz.cofe.mitrenier;

import xyz.cofe.coll.im.Result;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Доп методы, которые отсутствуют на android
 */
public class IO {
    public static byte[] readAllBytes(InputStream stream) throws IOException {
        if( stream==null ) throw new IllegalArgumentException("stream==null");
        var ba = new ByteArrayOutputStream();
        var buff = new byte[1024];
        while( true ){
            var reads = stream.read(buff);
            if( reads>0 ){
                ba.write(buff,0,reads);
            }else{
                break;
            }
        }
        return ba.toByteArray();
    }

    public static byte[] readAllBytes(URL url) throws IOException {
        if( url==null ) throw new IllegalArgumentException("url==null");
        try( var stream = url.openStream() ){
            return readAllBytes(stream);
        }
    }

    public static String readString(InputStream stream, Charset charset ) throws IOException {
        if( stream==null ) throw new IllegalArgumentException("stream==null");
        if( charset==null ) throw new IllegalArgumentException("charset==null");
        return new String(readAllBytes(stream), charset);
    }

    public static String readString(URL url, Charset charset ) throws IOException {
        if( url==null ) throw new IllegalArgumentException("url==null");
        if( charset==null ) throw new IllegalArgumentException("charset==null");
        return new String(readAllBytes(url), charset);
    }

    /**
     * Создает InputStream, который читает не более size байт из исходного input.
     * Исходный поток input НЕ закрывается ни по достижении лимита, ни при вызове close().
     *
     * @param input исходный InputStream (не может быть null)
     * @param size  максимальное количество байт для чтения
     * @return новый InputStream с ограничением
     */
    public static InputStream limitInputStream(InputStream input, long size) {
        Objects.requireNonNull(input, "Input stream cannot be null");

        return new InputStream() {
            private long remaining = Math.max(0, size);
            private boolean closed = false;

            @Override
            public int read() throws IOException {
                if( closed || remaining <= 0 ) {
                    return -1;
                }
                int b = input.read();
                if( b == -1 ) {
                    return -1;
                }
                remaining--;
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if( closed || remaining <= 0 ) {
                    return -1;
                }
                if( b == null ) {
                    throw new NullPointerException();
                }
                if( len == 0 ) {
                    return 0;
                }

                // Читаем не больше, чем осталось по лимиту
                int toRead = (int) Math.min(len, remaining);
                int n = input.read(b, off, toRead);

                if( n == -1 ) {
                    return -1;
                }

                remaining -= n;
                return n;
            }

            @Override
            public long skip(long n) throws IOException {
                if( closed || remaining <= 0 ) {
                    return 0;
                }
                long toSkip = Math.min(n, remaining);
                long skipped = input.skip(toSkip);
                remaining -= skipped;
                return skipped;
            }

            @Override
            public int available() throws IOException {
                if( closed || remaining <= 0 ) {
                    return 0;
                }
                // available() возвращает int, поэтому приводим к int безопасно
                return (int) Math.min(input.available(), remaining);
            }

            @Override
            public void close() throws IOException {
                closed = true;
                // ВАЖНО: input.close() НЕ вызывается намеренно,
                // чтобы не закрывать исходный поток
            }
        };
    }

    /**
     * Пропускает ровно n байт из InputStream.
     * Использует skip() в цикле, чтобы гарантировать пропуск нужного количества байт.
     *
     * @param input InputStream (не может быть null)
     * @param n     количество байт для пропуска
     * @throws IOException              если происходит ошибка ввода-вывода
     * @throws IllegalArgumentException если n < 0
     */
    public static void skipNBytes(InputStream input, long n) throws IOException {
        Objects.requireNonNull(input, "Input stream cannot be null");
        if( n < 0 ) {
            throw new IllegalArgumentException("Cannot skip negative bytes: " + n);
        }

        long skipped = 0;
        while( skipped < n ) {
            long result = input.skip(n - skipped);
            if( result == 0 ) {
                // skip() вернул 0 — пробуем прочитать и отбросить 1 байт
                if( input.read() == -1 ) {
                    // Достигнут конец потока
                    break;
                }
                skipped++;
            } else if( result < 0 ) {
                // Неожиданное отрицательное значение
                throw new IOException("Skip returned negative value: " + result);
            } else {
                skipped += result;
            }
        }
    }

    /**
     * Пропускает ровно n байт из InputStream.
     * Бросает EOFException, если не удалось пропустить все байты.
     *
     * @param input InputStream (не может быть null)
     * @param n     количество байт для пропуска
     * @throws IOException              если происходит ошибка ввода-вывода
     * @throws IllegalArgumentException если n < 0
     * @throws java.io.EOFException     если достигнут конец потока до пропуска всех байт
     */
    public static void skipNBytesStrict(InputStream input, long n) throws IOException {
        Objects.requireNonNull(input, "Input stream cannot be null");
        if( n < 0 ) {
            throw new IllegalArgumentException("Cannot skip negative bytes: " + n);
        }

        long skipped = 0;
        while( skipped < n ) {
            long result = input.skip(n - skipped);
            if( result == 0 ) {
                if( input.read() == -1 ) {
                    throw new java.io.EOFException(
                        "Unable to skip " + n + " bytes, only skipped " + skipped
                    );
                }
                skipped++;
            } else if( result < 0 ) {
                throw new IOException("Skip returned negative value: " + result);
            } else {
                skipped += result;
            }
        }
    }

    /**
     * Пытается прочитать все байты из InputStream, оборачивая результат в Result.
     *
     * @param inputStream Поток для чтения
     * @return Результат операции: успех с массивом байт или ошибка с исключением
     * @throws IllegalArgumentException если inputStream равен null
     */
    public static Result<byte[], IOException> tryReadAllBytes(InputStream inputStream) {
        if( inputStream == null ) throw new IllegalArgumentException("inputStream==null");
        try {
            return Result.ok(readAllBytes(inputStream));
        } catch ( IOException e ) {
            return Result.error(e);
        }
    }

    /**
     * Пытается прочитать все байты из URL, оборачивая результат в Result.
     *
     * @param url URL для чтения
     * @return Результат операции: успех с массивом байт или ошибка с исключением
     * @throws IllegalArgumentException если url равен null
     */
    public static Result<byte[], IOException> tryReadAllBytes(URL url) {
        if( url == null ) throw new IllegalArgumentException("url==null");

        try( var stream = url.openStream() ) {
            return Result.ok(readAllBytes(stream));
        } catch ( IOException e ) {
            return Result.error(e);
        }
    }

    public static void copy(InputStream input, OutputStream output, Consumer<Long> progress) throws IOException {
        var buff = new byte[1024 * 8];
        var total = 0L;
        while( true ) {
            var reads = input.read(buff);
            if( reads < 0 ) break;
            if( reads > 0 ) {
                output.write(buff, 0, reads);
                total += reads;
                if( progress!=null ){
                    progress.accept(total);
                }
            }
        }
    }
}
