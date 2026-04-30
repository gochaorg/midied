package xyz.cofe.nipal;

import xyz.cofe.mitrenier.IO;
import xyz.cofe.nipal.header.Range;

import java.io.ByteArrayInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Представляет байтовые данные с дополнительной метаинформацией.
 * <p>
 * Класс предоставляет функциональность для работы с различными источниками данных:
 * строками (как текст, так и HTML), файлами и частями файлов.
 * </p>
 *
 * @param data Потребитель, который принимает другой потребитель InputStream.
 *      Используется для безопасного получения потока данных без необходимости
 *      управления ресурсами вне контекста класса.
 *
 * @param contentLength Необязательная длина содержимого в байтах.
 * @param contentType Необязательный тип MIME содержимого.
 * @param fileName Необязательное имя файла.
 * @param statusCode Необязательный код состояния HTTP.
 * @param contentStart Начало данный в файле
 */
public record Bytes(
    Consumer<Consumer<InputStream>> data,
    Optional<Long> contentLength,
    Optional<String> contentType,
    Optional<String> fileName,
    Optional<StatusCode> statusCode,
    Optional<Long> contentStart,
    Optional<Long> totalSize,
    Optional<Map<String,String>> headers
)
{
    /**
     * Создает экземпляр Bytes с простым текстом.
     *
     * @param plainText Текстовое содержимое в формате UTF-8
     * @return Новый экземпляр Bytes с типом "text/plain"
     * @throws IllegalArgumentException если plainText равен null
     */
    public static Bytes plainText(String plainText){
        if( plainText==null ) throw new IllegalArgumentException("plainText==null");
        return of( plainText.getBytes(StandardCharsets.UTF_8) ).withContentType("text/plain");
    }

    /**
     * Создает экземпляр Bytes с HTML-содержимым.
     *
     * @param plainText HTML-содержимое в формате UTF-8
     * @return Новый экземпляр Bytes с типом "text/html"
     * @throws IllegalArgumentException если plainText равен null
     */
    public static Bytes html(String plainText){
        if( plainText==null ) throw new IllegalArgumentException("plainText==null");
        return of( plainText.getBytes(StandardCharsets.UTF_8) ).withContentType("text/html");
    }

    /**
     * Создает экземпляр Bytes из массива байт.
     *
     * @param bytes Массив байт с данными
     * @return Новый экземпляр Bytes
     * @throws IllegalArgumentException если bytes равен null
     */
    public static Bytes of(byte[] bytes) {
        if( bytes == null ) throw new IllegalArgumentException("bytes==null");

        Consumer<Consumer<InputStream>> data = reader -> {
            try( var dataStream = new ByteArrayInputStream(bytes) ) {
                reader.accept(dataStream);
            } catch ( IOException err ) {
                throw new IOError(err);
            }
        };

        return new Bytes(
            data,
            Optional.of((long)bytes.length),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(0L),
            Optional.of((long)bytes.length),
            Optional.empty()
        );
    }

    /**
     * Создает экземпляр Bytes из файла.
     *
     * @param path Путь к файлу
     * @return Новый экземпляр Bytes с информацией о размере файла
     * @throws IllegalArgumentException если path равен null или не указывает на обычный файл
     * @throws IOError если возникает ошибка при работе с файлом
     */
    public static Bytes of(Path path) {
        if( path == null ) throw new IllegalArgumentException("path==null");
        if( !Files.isRegularFile(path) )
            throw new IllegalArgumentException("path is not file, or file not found, path=" + path);

        long size;
        try {
            size = Files.size(path);
        } catch ( IOException e ) {
            throw new IOError(e);
        }

        Consumer<Consumer<InputStream>> data = reader -> {
            try( var dataStream = Files.newInputStream(path) ) {
                reader.accept(dataStream);
            } catch ( IOException err ) {
                throw new IOError(err);
            }
        };

        return new Bytes(
            data,
            Optional.of(size),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(0L),
            Optional.of(size),
            Optional.empty()
        );
    }

    public static Bytes of(Path path, Range.FromTo fromTo){
        if( path==null ) throw new IllegalArgumentException("path==null");
        if( fromTo==null ) throw new IllegalArgumentException("fromTo==null");
        return ofRange(path, fromTo.from(), fromTo.size()).withStatusCode(StatusCode.Partial_Content);
    }

    /**
     * Создает экземпляр Bytes из диапазона байт в файле.
     *
     * @param path Путь к файлу
     * @param from Начальная позиция в файле (в байтах)
     * @param size Количество байт для чтения
     * @return Новый экземпляр Bytes с частью содержимого файла
     * @throws IllegalArgumentException если path равен null, from или size отрицательны,
     *         или если path не указывает на обычный файл
     * @throws IOError если возникает ошибка при работе с файлом
     */
    public static Bytes ofRange(Path path, long from, long size) {
        if( path == null ) throw new IllegalArgumentException("path==null");
        if( from<0 ) throw new IllegalArgumentException("from<0");
        if( size<0 ) throw new IllegalArgumentException("size<0");

        if( !Files.isRegularFile(path) )
            throw new IllegalArgumentException("path is not file, or file not found, path=" + path);

        if( size==0 ){
            Consumer<Consumer<InputStream>> data = reader -> {
                reader.accept(new ByteArrayInputStream(new byte[0]));
            };

            return new Bytes(
                data,
                Optional.of(0L),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            );
        }

        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch ( IOException e ) {
            throw new IOError(e);
        }

        long targetSize = from + size;
        long readSize;

        if( targetSize>fileSize ){
            if( fileSize<=from ){
                Consumer<Consumer<InputStream>> data = reader -> {
                    reader.accept(new ByteArrayInputStream(new byte[0]));
                };

                return new Bytes(
                    data,
                    Optional.of(0L),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(0L),
                    Optional.of(fileSize),
                    Optional.empty()
                );
            }

            readSize = fileSize - from;
        } else {
            readSize = size;
        }

        Consumer<Consumer<InputStream>> data = reader -> {
            try( var dataStream = Files.newInputStream(path) ) {
                IO.skipNBytesStrict(dataStream, from);
                reader.accept(IO.limitInputStream(dataStream, readSize));
            } catch ( IOException err ) {
                throw new IOError(err);
            }
        };

        return new Bytes(
            data,
            Optional.of(readSize),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(from),
            Optional.of(fileSize),
            Optional.empty()
        );
    }

    /**
     * Возвращает новый экземпляр Bytes с указанной длиной содержимого.
     *
     * @param len Длина содержимого в байтах
     * @return Новый экземпляр Bytes
     */
    public Bytes withContentLength(long len) {
        if( len<0 ) throw new IllegalArgumentException("len<0");
        return new Bytes(data, Optional.of(len), contentType, fileName, statusCode, contentStart, totalSize, headers);
    }

    /**
     * Возвращает новый экземпляр Bytes с указанным типом содержимого.
     *
     * @param contentType MIME-тип содержимого
     * @return Новый экземпляр Bytes
     */
    public Bytes withContentType(String contentType) {
        return new Bytes(data, contentLength, Optional.ofNullable(contentType), fileName, statusCode, contentStart, totalSize, headers);
    }

    /**
     * Возвращает новый экземпляр Bytes с указанным именем файла.
     *
     * @param fileName Имя файла
     * @return Новый экземпляр Bytes
     */
    public Bytes withFileName(String fileName) {
        return new Bytes(data, contentLength, contentType, Optional.ofNullable(fileName), statusCode, contentStart, totalSize, headers);
    }

    /**
     * Возвращает новый экземпляр Bytes с указанным кодом состояния.
     *
     * @param statusCode Код состояния HTTP
     * @return Новый экземпляр Bytes
     */
    public Bytes withStatusCode(StatusCode statusCode) {
        return new Bytes(data, contentLength, contentType, fileName, Optional.ofNullable(statusCode), contentStart, totalSize, headers);
    }

    public Bytes withContentStart(long startAt){
        if( startAt<0 ) throw new IllegalArgumentException("startAt<0");
        return new Bytes(data, contentLength, contentType, fileName, statusCode, Optional.of(startAt) , totalSize, headers);
    }

    public Bytes withTotalSize(long totalSize){
        if( totalSize<0 ) throw new IllegalArgumentException("totalSize<0");
        return new Bytes(data,contentLength,contentType,fileName,statusCode,contentStart,Optional.of(totalSize), headers);
    }

    public Bytes withHeaders(Map<String,String> headers){
        return new Bytes(data,contentLength,contentType,fileName,statusCode,contentStart,totalSize, Optional.ofNullable(headers));
    }
}