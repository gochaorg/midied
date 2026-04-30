package xyz.cofe.nipal;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.coll.im.ImList;
import xyz.cofe.coll.im.Result;
import xyz.cofe.json.stream.rec.StdMapper;
import xyz.cofe.mitrenier.str.Template;
import xyz.cofe.nipal.header.HeaderValue;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static xyz.cofe.nipal.util.ContentDispositionUtil.formatContentDisposition;
import static xyz.cofe.nipal.util.QueryString.parseQueryStringDecoded;

/**
 * Класс для маршрутизации HTTP-запросов на основе набора фильтров.
 * Предоставляет DSL для построения цепочек фильтров и обработчиков запросов.
 * Поддерживает различные типы фильтрации: по пути, методу, заголовкам и т.д.
 * Также предоставляет функциональность для парсинга тела запроса в JSON
 * и генерации ответов в различных форматах.
 */
public class RequestRouter {
    private static final Logger log = LoggerFactory.getLogger(RequestRouter.class);
    /**
     * Фильтры для проверки соответствия запроса маршруту.
     */
    protected ImList<Predicate<Request>> filters;
    /**
     * Маппер для работы с JSON-данными.
     */
    protected JsonItf jsonMapper;

    /**
     * Глобальный маппер по умолчанию для работы с JSON.
     */
    protected static JsonItf defaultJsonMapper;

    /**
     * Получает глобальный маппер по умолчанию для работы с JSON.
     * Если маппер не установлен, создается новый экземпляр StdMapper.
     *
     * @return Маппер для работы с JSON
     */
    public synchronized static JsonItf getDefaultJsonMapper() {
        return defaultJsonMapper != null ? defaultJsonMapper : JsonItfImpl.jsonStream(new StdMapper());
    }

    /**
     * Устанавливает глобальный маппер по умолчанию для работы с JSON.
     *
     * @param defaultJsonMapper Новый маппер по умолчанию
     */
    public synchronized static void setDefaultJsonMapper(JsonItf defaultJsonMapper) {
        RequestRouter.defaultJsonMapper = defaultJsonMapper;
    }

    /**
     * Создает новый экземпляр маршрутизатора с маппером по умолчанию.
     *
     * @param filters Список фильтров для проверки запросов
     */
    public RequestRouter(ImList<Predicate<Request>> filters) {
        this(getDefaultJsonMapper(), filters);
    }

    /**
     * Создает новый экземпляр маршрутизатора с указанным маппером.
     *
     * @param jsonMapper Маппер для работы с JSON
     * @param filters    Список фильтров для проверки запросов
     */
    public RequestRouter(JsonItf jsonMapper, ImList<Predicate<Request>> filters) {
        if( filters == null ) throw new IllegalArgumentException("filters==null");
        this.filters = filters;

        if( jsonMapper == null ) throw new IllegalArgumentException("mapper==null");
        this.jsonMapper = jsonMapper;
    }

    /**
     * Статический экземпляр маршрутизатора с пустым списком фильтров,
     * используемый как точка входа для построения цепочки фильтров.
     */
    public static final RequestRouter Builder = new RequestRouter(ImList.of());

    /**
     * Локальное хранилище префиксов пути для текущего потока.
     */
    private static final ThreadLocal<List<String>> pathPrefixes = ThreadLocal.withInitial(ArrayList::new);

    /**
     * Добавляет префикс пути к обработчику.
     *
     * @param prefixPath Префикс пути
     * @param handler    Поставщик обработчика
     * @return Обработчик с примененным префиксом
     */
    public static Handler prefix(String prefixPath, Supplier<Handler> handler) {
        if( prefixPath == null ) throw new IllegalArgumentException("prefixPath==null");
        if( handler == null ) throw new IllegalArgumentException("handler==null");
        var lst = pathPrefixes.get();
        try {
            lst.add(prefixPath);
            return handler.get();
        } finally {
            lst.remove(lst.size() - 1);
        }
    }

    /**
     * Добавляет префикс пути к последовательности обработчиков.
     *
     * @param prefixPath Префикс пути
     * @param handler    Поставщик итерируемой коллекции обработчиков
     * @return Обработчик последовательности с примененным префиксом
     */
    public static Handler prefixes(String prefixPath, Supplier<Iterable<Handler>> handler) {
        if( prefixPath == null ) throw new IllegalArgumentException("prefixPath==null");
        if( handler == null ) throw new IllegalArgumentException("handler==null");
        var lst = pathPrefixes.get();
        try {
            lst.add(prefixPath);
            List<Handler> hs = new ArrayList<>();
            handler.get().forEach(hs::add);
            return new Handler.Sequence(hs);
        } finally {
            lst.remove(lst.size() - 1);
        }
    }

    /**
     * Возвращает текущий префикс пути для текущего потока.
     *
     * @return Строковый префикс пути
     */
    private static String pathPrefix() {
        var lst = pathPrefixes.get();
        return String.join("", lst);
    }

    /**
     * Создает новый маршрутизатор с указанными фильтрами.
     *
     * @param filters Новый список фильтров
     * @return Новый экземпляр маршрутизатора
     */
    public RequestRouter withFilters(ImList<Predicate<Request>> filters) {
        if( filters == null ) throw new IllegalArgumentException("filters==null");
        return new RequestRouter(jsonMapper, filters);
    }

    /**
     * Создает новый маршрутизатор с указанным JSON-маппером.
     *
     * @param itf Новый маппер для работы с JSON
     * @return Новый экземпляр маршрутизатора
     */
    public RequestRouter with(JsonItf itf) {
        return new RequestRouter(itf != null ? itf : getDefaultJsonMapper(), filters);
    }

    /**
     * Добавляет фильтр к маршрутизатору.
     *
     * @param predicate Фильтр для проверки запроса
     * @return Новый экземпляр маршрутизатора с добавленным фильтром
     */
    public RequestRouter filter(Predicate<Request> predicate) {
        if( predicate == null ) throw new IllegalArgumentException("predicate==null");
        return withFilters(filters.prepend(predicate));
    }

    /**
     * Добавляет фильтр по HTTP URI.
     *
     * @param uri Фильтр для проверки URI
     * @return Новый экземпляр маршрутизатора с добавленным фильтром
     */
    public RequestRouter httpUri(Predicate<HttpURI> uri) {
        if( uri == null ) throw new IllegalArgumentException("uri==null");
        return filter(r -> uri.test(r.getHttpURI()));
    }

    /**
     * Добавляет фильтр по пути запроса.
     *
     * @param path Фильтр для проверки пути
     * @return Новый экземпляр маршрутизатора с добавленным фильтром
     */
    public RequestRouter path(Predicate<String> path) {
        if( path == null ) throw new IllegalArgumentException("path==null");

        var prefix = pathPrefix();
        return httpUri(uri -> {
                var canonPath = uri.getCanonicalPath();
                if( !prefix.isEmpty() && !canonPath.startsWith(prefix) ) return false;
                return path.test(canonPath.substring(prefix.length()));
            }
        );
    }

    /**
     * Добавляет фильтр по точному совпадению пути.
     *
     * @param path Точный путь для сравнения
     * @return Новый экземпляр маршрутизатора с добавленным фильтром
     */
    public RequestRouter path(String path) {
        if( path == null ) throw new IllegalArgumentException("path==null");
        return path(path::equals);
    }

    /**
     * Локальное хранилище для совпадений шаблона пути.
     */
    private static final ThreadLocal<Optional<Matcher>> pathTemplateMatcher = ThreadLocal.withInitial(Optional::empty);

    /**
     * Добавляет фильтр по шаблону пути с поддержкой параметров.
     * Шаблон может содержать именованные параметры в фигурных скобках.
     *
     * @param path Шаблон пути (например, "/users/{id:\\d+}")
     * @return Новый экземпляр маршрутизатора с добавленным фильтром
     */
    public RequestRouter pathTemplate(String path) {
        if( path == null ) throw new IllegalArgumentException("path==null");

        var regex = Template
            .parse(path)
            .eval("", (a, b) -> a + b)
            .plain(Pattern::quote)
            .expression(exp -> {
                if( exp.contains(":") ) {
                    var lr = exp.split(":", 2);
                    if( lr.length == 2 ) {
                        var regexExp = lr[1];
                        var groupName = lr[0];
                        return "(?<" + groupName + ">" + regexExp + ")";
                    } else {
                        return "(?<" + exp + ">[^/]*)";
                    }
                } else {
                    return "(?<" + exp + ">[^/]*)";
                }
            })
            .go();

        Pattern ptrn = Pattern.compile(regex);
        return path(path0 -> {
            var matcher = ptrn.matcher(path0);
            if( matcher.matches() ) {
                pathTemplateMatcher.set(Optional.of(matcher));
                return true;
            }
            return false;
        });
    }

    /**
     * Добавляет фильтр по HTTP методу.
     *
     * @param method Фильтр для проверки метода
     * @return Новый экземпляр маршрутизатора с добавленным фильтром
     */
    public RequestRouter method(Predicate<String> method) {
        if( method == null ) throw new IllegalArgumentException("method==null");
        return filter(r -> method.test(r.getMethod()));
    }

    /**
     * Добавляет фильтр для HTTP GET метода.
     *
     * @return Новый экземпляр маршрутизатора с фильтром GET
     */
    public RequestRouter GET() {
        return method("get"::equalsIgnoreCase);
    }

    /**
     * Добавляет фильтр для HTTP POST метода.
     *
     * @return Новый экземпляр маршрутизатора с фильтром POST
     */
    public RequestRouter POST() {
        return method("post"::equalsIgnoreCase);
    }

    /**
     * Добавляет фильтр для HTTP PUT метода.
     *
     * @return Новый экземпляр маршрутизатора с фильтром PUT
     */
    public RequestRouter PUT() {
        return method("put"::equalsIgnoreCase);
    }

    /**
     * Добавляет фильтр для HTTP DELETE метода.
     *
     * @return Новый экземпляр маршрутизатора с фильтром DELETE
     */
    public RequestRouter DELETE() {
        return method("delete"::equalsIgnoreCase);
    }

    /**
     * Добавляет фильтр для HTTP HEAD метода.
     *
     * @return Новый экземпляр маршрутизатора с фильтром HEAD
     */
    public RequestRouter HEAD() {
        return method("head"::equalsIgnoreCase);
    }

    /**
     * Добавляет фильтр для HTTP PATCH метода.
     *
     * @return Новый экземпляр маршрутизатора с фильтром PATCH
     */
    public RequestRouter PATCH() {
        return method("patch"::equalsIgnoreCase);
    }

    /**
     * Создает обработчик, который применяет все фильтры и вызывает целевой обработчик,
     * если все фильтры прошли успешно.
     *
     * @param handler Целевой обработчик
     * @return Обработчик, инкапсулирующий логику фильтрации и вызова
     */
    public Handler handle(Handler handler) {
        if( handler == null ) throw new IllegalArgumentException("handler==null");
        if( filters.isEmpty() ) return handler;

        var r_filters = filters.reverse();
        return new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                if( request == null ) return false;

                pathTemplateMatcher.set(Optional.empty());

                for( var pred : r_filters ) {
                    if( !pred.test(request) ) return false;
                }

                return handler.handle(request, response, callback);
            }
        };
    }

    /**
     * Извлекает тело запроса в виде JSON и десериализует его в указанный класс.
     *
     * @param request Запрос с JSON-телом
     * @param cls     Класс для десериализации
     * @param <I>     Тип результата
     * @return Десериализованный объект
     */
    private <I> I extractJsonBody(Request request, Class<I> cls) {
        try {
            var str = Content.Source.asString(request);
            return jsonMapper.parseJson(str, cls);
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Функциональный интерфейс для безаргументной функции.
     *
     * @param <R> Тип возвращаемого значения
     */
    public interface Fn0<R> extends Function<Object, R>,
                                    Supplier<R>
    {
        @Override
        default R apply(Object o) {
            return get();
        }
    }

    /**
     * Создает билдер для ответа с байтовыми данными.
     *
     * @param <O> Тип результата
     * @return Билдер для построения ответа с байтовыми данными
     */
    public <O> ResponseBuilder<Fn0<Bytes>> bytesResponse() {
        return new ResponseBuilder<>((request, f) -> {
            var mimeDef = "application/octet-stream";
            var value = f.get();

            value.headers().ifPresent( headers -> {
                if( !headers.isEmpty() ){
                    headers.forEach( (k,v) -> request.response().getHeaders().add(k,v));
                }
            });

            request.response().getHeaders().add(HttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            if( value == null ) {
                request.response().getHeaders().add(HttpHeader.CONTENT_TYPE, mimeDef);
                Content.Sink.write(
                    request.response(),
                    true,
                    "",
                    request.callback()
                );

                return true;
            }

            value.contentLength().ifPresent(v -> request.response.getHeaders().add(HttpHeader.CONTENT_LENGTH, v));

            value.contentLength().ifPresent(len ->
                value.contentStart().ifPresent(start ->
                    value.totalSize().ifPresent(total -> {
                        if( total != null && total >= 0 && start != null && start >= 0 && len != null && len > 0 ) {
                            var from = start.longValue();
                            var toInc = from + len - 1;
                            request.response.getHeaders().add(HttpHeader.CONTENT_RANGE, "bytes " + from + "-" + toInc + "/" + total);
                        }
                    })));

            value.contentType().ifPresentOrElse(
                t -> request.response.getHeaders().add(HttpHeader.CONTENT_TYPE, t),
                () -> request.response.getHeaders().add(HttpHeader.CONTENT_TYPE, mimeDef)
            );
            value.fileName().ifPresent(fn -> request.response.getHeaders().add(HttpHeader.CONTENT_DISPOSITION,
                formatContentDisposition(fn))
            );

            value.statusCode().ifPresentOrElse(c -> request.response().setStatus(c.value), () -> request.response().setStatus(StatusCode.Ok.value));

            try(
                OutputStream output = Content.Sink.asOutputStream(request.response);
            ) {

                value.data().accept(input -> {
                    try {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while( (bytesRead = input.read(buffer)) != -1 ) {
                            output.write(buffer, 0, bytesRead);
                        }
                    } catch ( IOException e ) {
                        throw new IOError(e);
                    }
                });
                request.callback().succeeded();
            } catch ( IOException | IOError e ) {
                request.callback().failed(e);
            }

            return true;
        });
    }

    /**
     * Создает билдер для HTML-ответа.
     *
     * @param <O> Тип результата
     * @return Билдер для построения HTML-ответа
     */
    public <O> ResponseBuilder<Fn0<O>> htmlResponse() {
        return new ResponseBuilder<>((request, f) -> {
            var value = f.get();

            var jsonString = value != null ? value.toString() : "";
            if( value != null ) {
                var status = value.getClass().getAnnotation(Status.class);
                if( status != null ) {
                    request.response().setStatus(status.value().value);
                }
            }

            request.response().getHeaders().add(HttpHeader.CONTENT_TYPE, "text/html");
            request.response().getHeaders().add(HttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            Content.Sink.write(
                request.response(),
                true,
                jsonString,
                request.callback()
            );

            return true;
        });
    }

    /**
     * Создает билдер для JSON-ответа.
     *
     * @param <O> Тип результата
     * @return Билдер для построения JSON-ответа
     */
    public <O> ResponseBuilder<Fn0<O>> jsonResponse() {
        return new ResponseBuilder<>((request, f) -> {
            var value = f.get();

            var jsonString = jsonMapper.toJson(value);
            if( value != null ) {
                var status = value.getClass().getAnnotation(Status.class);
                if( status != null ) {
                    request.response().setStatus(status.value().value);
                }
            }

            request.response().getHeaders().add(HttpHeader.CONTENT_TYPE, "application/json");
            request.response().getHeaders().add(HttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            Content.Sink.write(
                request.response(),
                true,
                jsonString,
                request.callback()
            );

            return true;
        });
    }

    /**
     * Запись, содержащая компоненты HTTP-запроса.
     */
    public record RequestHandler(Request request, Response response, Callback callback) {}

    /**
     * Билдер для построения ответов на основе функций.
     *
     * @param <F> Тип функции обработки
     */
    public class ResponseBuilder<F extends Function<?, ?>> {
        /**
         * Реализация вызова функции обработки.
         */
        public final BiFunction<RequestHandler, F, Boolean> callImpl;

        public ResponseBuilder(BiFunction<RequestHandler, F, Boolean> callImpl) {
            this.callImpl = callImpl;
        }

        /**
         * Создает обработчик, который вызывает целевую функцию.
         *
         * @param callTarget Целевая функция для вызова
         * @return Обработчик, инкапсулирующий вызов функции
         */
        public Handler call(F callTarget) {
            if( callTarget == null ) throw new IllegalArgumentException("callTarget==null");

            var h = new Handler.Abstract() {
                @Override
                public boolean handle(Request request, Response response, Callback callback) throws Exception {
                    try {
                        return callImpl.apply(
                            new RequestHandler(
                                request,
                                response,
                                callback
                            ),
                            callTarget
                        );
                    } catch ( Throwable err ) {
                        response.setStatus(500);
                        Content.Sink.write(response, true, err.toString(), callback);
                        return true;
                    }
                }
            };

            return handle(h);
        }

        /**
         * Добавляет возможность получения оригинального запроса Jetty в функции.
         *
         * @return Новый билдер с возможностью получения запроса Jetty
         */
        public ResponseBuilder<Function<Request, F>> jettyRequest() {
            var me = this;
            return new ResponseBuilder<>(
                (request, f) -> {
                    var res = f.apply(request.request());
                    return me.callImpl.apply(request, res);
                }
            );
        }

        /**
         * Добавляет возможность извлечения JSON-тела запроса в указанном типе.
         *
         * @param <T> Тип для десериализации JSON-тела
         * @param cls Класс для десериализации
         * @return Новый билдер с возможностью извлечения JSON-тела
         */
        public <T> ResponseBuilder<Function<T, F>> jsonBody(Class<T> cls) {
            if( cls == null ) throw new IllegalArgumentException("cls==null");
            var me = this;

            return new ResponseBuilder<>(
                (request, f) -> {
                    var body = extractJsonBody(request.request(), cls);
                    var res = f.apply(body);

                    return me.callImpl.apply(request, res);
                }
            );
        }

        /**
         * Добавляет возможность работы с сессией пользователя.
         *
         * @param <T>        Тип данных сессии
         * @param newSession Поставщик новых данных сессии
         * @return Новый билдер с поддержкой сессии
         */
        @SuppressWarnings({"ReassignedVariable", "unchecked"})
        public <T> ResponseBuilder<Function<T, F>> session(Supplier<T> newSession) {
            if( newSession == null ) throw new IllegalArgumentException("newSession==null");
            var me = this;

            return new ResponseBuilder<>(
                (req, f) -> {
                    var ses = req.request().getSession(true);
                    var sesDataObj = ses.getAttribute("sesData");
                    if( sesDataObj == null ) {
                        sesDataObj = newSession.get();
                        ses.setAttribute("sesData", sesDataObj);
                    }

                    var sesData = (T) sesDataObj;
                    return me.callImpl.apply(req, f.apply(sesData));
                }
            );
        }

        /**
         * Добавляет возможность извлечения произвольных данных из запроса.
         *
         * @param <T>       Тип извлекаемых данных
         * @param extractor Функция извлечения данных из запроса
         * @return Новый билдер с пользовательской логикой извлечения
         */
        public <T> ResponseBuilder<Function<T, F>> extract(Function<RequestHandler, Result<T, String>> extractor) {
            if( extractor == null ) throw new IllegalArgumentException("extractor==null");
            return extract(extractor, errMsg -> {
                log.debug("can't extract: {}", errMsg);
                return false;
            });
        }

        /**
         * Добавляет возможность извлечения произвольных данных из запроса.
         *
         * @param <T>          Тип извлекаемых данных
         * @param extractor    Функция извлечения данных из запроса
         * @param failStrategy Способ обработки ошибки извлечения, если null - то кидает runTimeException
         * @return Новый билдер с пользовательской логикой извлечения
         */
        public <T> ResponseBuilder<Function<T, F>> extract(Function<RequestHandler, Result<T, String>> extractor, Function<String, Boolean> failStrategy) {
            if( extractor == null ) throw new IllegalArgumentException("extractor==null");
            var me = this;

            return new ResponseBuilder<>(
                (req, f) -> {
                    var res = extractor.apply(req);
                    if( res.isOk() ) {
                        return me.callImpl.apply(req, f.apply(res.unwrap()));
                    }

                    if( failStrategy != null ) {
                        return failStrategy.apply(res.swap().unwrap());
                    }

                    throw new RuntimeException("extract fail: " + res.getError().get());
                }
            );
        }

        public ResponseBuilder<Function<InputStream, F>> inputBodyStream() {
            return extract(req -> {
                return Result.ok(Content.Source.asInputStream(req.request()));
            });
        }

        /**
         * Добавляет возможность извлечения параметра из шаблона пути.
         *
         * @param name Имя параметра в шаблоне пути
         * @return Новый билдер с извлечением параметра из шаблона пути
         */
        public ResponseBuilder<Function<String, F>> pathValue(String name) {
            if( name == null ) throw new IllegalArgumentException("name==null");
            return extract(req -> {
                var matcherOpt = pathTemplateMatcher.get();
                if( matcherOpt.isEmpty() ) return Result.error("not matched");

                var value = matcherOpt.get().group(name);
                return Result.ok(value != null ? value : "");
            });
        }

        /**
         * Добавляет возможность извлечения параметра из строки запроса.
         *
         * @param name Имя параметра в строке запроса
         * @return Новый билдер с извлечением параметра из строки запроса
         */
        public ResponseBuilder<Function<String, F>> queryParam(String name) {
            if( name == null ) throw new IllegalArgumentException("name==null");
            return extract(req -> {
                var qs = req.request().getHttpURI().getQuery();
                if( qs == null ) return Result.ok("");

                var qsMap = parseQueryStringDecoded(qs);

                return Result.ok(qsMap.getOrDefault(name, ""));
            });
        }

        public <T> ResponseBuilder<Function<T, F>> header(String header, Function<String, Optional<T>> headerValueParser) {
            if( header == null ) throw new IllegalArgumentException("header==null");
            if( headerValueParser == null ) throw new IllegalArgumentException("headerValueParser==null");
            return extract(req -> {
                var str = req.request().getHeaders().get(header);
                if( str == null ) return Result.error("header " + header + " not exists");
                var value = headerValueParser.apply(str);
                if( value == null || value.isEmpty() ) return Result.error("header " + header + " not parsed");
                return Result.ok(value.get());
            });
        }

        @SuppressWarnings("OptionalIsPresent")
        public <T> ResponseBuilder<Function<T, F>> header(String header, HeaderValue<T> headerValueParser) {
            if( header == null ) throw new IllegalArgumentException("header==null");
            if( headerValueParser == null ) throw new IllegalArgumentException("headerValueParser==null");
            return extract(req -> {
                var str = req.request().getHeaders().get(header);
                if( str == null ) {
                    var defVal = headerValueParser.defaultValue();
                    if( defVal.isPresent() ){
                        return Result.ok(defVal.get());
                    }
                    return Result.error("header " + header + " not parsed");
                }else{
                    var value = headerValueParser.parse(str);
                    if( value.isPresent() ){
                        return Result.ok(value.get());
                    }
                    var defVal = headerValueParser.defaultValue();
                    if( defVal.isPresent() ){
                        return Result.ok(defVal.get());
                    }
                    return Result.error("header " + header + " not parsed");
                }
            });
        }
    }
}