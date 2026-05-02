package xyz.cofe.nipal.files;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.coll.im.Tuple2;
import xyz.cofe.mitrenier.IO;
import xyz.cofe.mitrenier.file.UnixPath;
import xyz.cofe.mitrenier.json.JSON;
import xyz.cofe.nipal.Bytes;
import xyz.cofe.nipal.MimeType;
import xyz.cofe.nipal.RequestRouter;
import xyz.cofe.nipal.StatusCode;
import xyz.cofe.nipal.header.Accept;
import xyz.cofe.nipal.header.ContentRange;
import xyz.cofe.nipal.header.HeaderValue;
import xyz.cofe.nipal.header.Range;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Обработчик статических файлов для Jetty.
 * <p>
 * Предоставляет функциональность для обслуживания статических файлов из файловой системы,
 * поддерживает настройку префиксов URI, сопоставление расширений файлов с MIME-типами,
 * а также добавление виртуальных файлов из памяти.
 * </p>
 * <p>
 * Класс реализует паттерн "immutable builder" через клонирование: все методы настройки
 * возвращают новый экземпляр с изменёнными параметрами, не модифицируя исходный объект.
 * </p>
 *
 * @see Handler
 * @see Request
 */
public class StaticFiles {
    private static final Logger log = LoggerFactory.getLogger(StaticFiles.class);

    /**
     * Создаёт новый экземпляр обработчика статических файлов.
     */
    public StaticFiles() {
        contentRoot = () -> null;
    }

    /**
     * Создаёт новый экземпляр обработчика статических файлов.
     *
     * @param dir поставщик корневого пути для статических файлов; не может быть {@code null}
     * @throws IllegalArgumentException если {@code dir == null}
     */
    public StaticFiles(Supplier<Path> dir) {
        if( dir == null ) throw new IllegalArgumentException("dir==null");
        contentRoot = dir;
    }

    /**
     * Создаёт копию обработчика на основе образца.
     * <p>
     * Копирует все настройки из предоставленного экземпляра: корневой каталог,
     * префикс URI, карту MIME-типов и флаг проверки существования путей.
     * </p>
     *
     * @param sample образец для копирования; не может быть {@code null}
     * @throws IllegalArgumentException если {@code sample == null}
     */
    public StaticFiles(StaticFiles sample) {
        if( sample == null ) throw new IllegalArgumentException("sample==null");
        contentRoot = sample.contentRoot;
        uriPathPrefix = sample.uriPathPrefix;
        _fileExtension2MimeTypes =
            sample._fileExtension2MimeTypes != null ? new HashMap<>(sample._fileExtension2MimeTypes) : null;
        checkPathExists = sample.checkPathExists;
        customFiles = new LinkedHashMap<>(sample.customFiles);
        allowAbilities = sample.allowAbilities;
        allowResources = sample.allowResources;
        allowRead = sample.allowRead;
        allowUpload = sample.allowUpload;
        allowDelete = sample.allowDelete;
        allowMkDir = sample.allowMkDir;
        allowRename = sample.allowRename;
        visiblePath = sample.visiblePath;
        readablePath = sample.readablePath;
        writeablePath = sample.writeablePath;
        deletablePath = sample.deletablePath;
    }

    /**
     * Создаёт клон текущего экземпляра.
     * <p>
     * Внутренний метод для реализации паттерна неизменяемого билдера.
     * </p>
     *
     * @return новый экземпляр с копией настроек текущего объекта
     */
    @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException", "MethodDoesntCallSuperMethod"})
    protected StaticFiles clone() {
        return new StaticFiles(this);
    }

    //region contentRoot : Supplier<Path>
    private final Supplier<Path> contentRoot;

    /**
     * Статический фабричный метод для создания обработчика с фиксированным корневым каталогом.
     *
     * @param dir корневой каталог для статических файлов; не может быть {@code null}
     * @return новый экземпляр {@link StaticFiles}
     * @throws IllegalArgumentException если {@code dir == null}
     */
    public static StaticFiles dir(Path dir) {
        if( dir == null ) throw new IllegalArgumentException("dir==null");
        return new StaticFiles(() -> dir);
    }

    /**
     * Статический фабричный метод для создания обработчика с динамическим корневым каталогом.
     * <p>
     * Позволяет поставлять корневой путь через {@link Supplier}, что полезно для
     * конфигураций, зависящих от контекста или изменяющихся во времени.
     * </p>
     *
     * @param dir поставщик корневого каталога; не может быть {@code null}
     * @return новый экземпляр {@link StaticFiles}
     * @throws IllegalArgumentException если {@code dir == null}
     */
    public static StaticFiles dir(Supplier<Path> dir) {
        if( dir == null ) throw new IllegalArgumentException("dir==null");
        return new StaticFiles(dir);
    }
    //endregion

    //region uriPathPrefix : Supplier<String>
    private Supplier<String> uriPathPrefix = () -> "";

    /**
     * Устанавливает базовый префикс для обрабатываемых URL-путей.
     * <p>
     * Все запросы, начинающиеся с указанного префикса, будут обрабатываться данным обработчиком.
     * Например, при префиксе {@code "/static"} запрос {@code "/static/style.css"} будет
     * сопоставлен с файлом {@code "style.css"} в корневом каталоге.
     * </p>
     *
     * @param base базовый адрес (префикс пути URI); не может быть {@code null}
     * @return новый экземпляр {@link StaticFiles} с установленным префиксом
     * @throws IllegalArgumentException если {@code base == null}
     */
    public StaticFiles uriBase(String base) {
        if( base == null ) throw new IllegalArgumentException("base==null");
        var clone = clone();
        clone.uriPathPrefix = () -> base;
        return clone;
    }

    /**
     * Устанавливает базовый префикс для обрабатываемых URL-путей через поставщик.
     * <p>
     * Позволяет динамически определять префикс во время обработки запроса.
     * </p>
     *
     * @param base поставщик базового адреса; не может быть {@code null}
     * @return новый экземпляр {@link StaticFiles} с установленным поставщиком префикса
     * @throws IllegalArgumentException если {@code base == null}
     */
    public StaticFiles uriBase(Supplier<String> base) {
        if( base == null ) throw new IllegalArgumentException("base==null");
        var clone = clone();
        clone.uriPathPrefix = base;
        return clone;
    }
    //endregion

    //region mime types
    private Map<String, String> _fileExtension2MimeTypes;

    /**
     * Возвращает карту сопоставления расширений файлов и MIME-типов.
     * <p>
     * При первом вызове инициализирует карту значениями по умолчанию для распространённых
     * типов файлов: HTML, изображения, стили, скрипты, аудио и другие.
     * </p>
     *
     * @return карта {@code <расширение, MIME-тип>}
     */
    private Map<String, String> fileExtension2MimeTypes() {
        if( _fileExtension2MimeTypes != null ) return _fileExtension2MimeTypes;
        var m = new HashMap<String, String>();

        m.put("html", MimeType.TEXT_HTML);
        m.put("htm", MimeType.TEXT_HTML);
        m.put("css", MimeType.TEXT_CSS);

        m.put("java", MimeType.TEXT_PLAIN);
        m.put("gradle", MimeType.TEXT_PLAIN);
        m.put("bat", MimeType.TEXT_PLAIN);
        m.put(".gitignore", MimeType.TEXT_PLAIN);
        m.put("gitignore", MimeType.TEXT_PLAIN);

        m.put("wasm", MimeType.APP_WASM);
        m.put("js", MimeType.APP_JS);
        m.put("json", MimeType.APP_JSON);

        m.put("jpg", MimeType.IMAGE_JPEG);
        m.put("jpeg", MimeType.IMAGE_JPEG);
        m.put("gif", MimeType.IMAGE_GIF);
        m.put("png", MimeType.IMAGE_PNG);
        m.put("svg", MimeType.IMAGE_SVG);

        m.put("mp3", MimeType.AUDIO_MP3);
        m.put("ogg", MimeType.AUDIO_OGG);
        m.put("wav", MimeType.AUDIO_WAV);
        m.put("midi", MimeType.AUDIO_MIDI);

        _fileExtension2MimeTypes = m;
        return _fileExtension2MimeTypes;
    }

    /**
     * Заменяет всю карту сопоставления расширений файлов на пользовательскую.
     * <p>
     * Полностью заменяет внутренние настройки MIME-типов. Для добавления отдельных
     * типов рекомендуется использовать {@link #addFileExt2MimeType(Map)}.
     * </p>
     *
     * @param extMap карта {@code <расширение без точки, MIME-тип>}
     * @return новый экземпляр {@link StaticFiles} с заменённой картой MIME-типов
     */
    public StaticFiles fileExtension2MimeType(Map<String, String> extMap) {
        var clone = clone();
        clone._fileExtension2MimeTypes = extMap;
        return clone;
    }

    /**
     * Добавляет или обновляет MIME-типы для указанных расширений файлов.
     * <p>
     * Сохраняет существующие настройки и добавляет к ним новые значения из переданной карты.
     * </p>
     *
     * @param extMap карта {@code <расширение без точки, MIME-тип>}; не может быть {@code null}
     * @return новый экземпляр {@link StaticFiles} с дополненной картой MIME-типов
     * @throws IllegalArgumentException если {@code extMap == null}
     */
    public StaticFiles addFileExt2MimeType(Map<String, String> extMap) {
        if( extMap == null ) throw new IllegalArgumentException("extMap==null");

        var clone = clone();
        if( clone._fileExtension2MimeTypes == null ) {
            clone._fileExtension2MimeTypes = extMap;
        } else {
            clone._fileExtension2MimeTypes = new HashMap<>(_fileExtension2MimeTypes);
            clone._fileExtension2MimeTypes.putAll(extMap);
        }
        return clone;
    }
    //endregion

    //region checkPathExists
    private boolean checkPathExists = true;

    /**
     * Настраивает проверку существования файла перед попыткой чтения.
     * <p>
     * По умолчанию включена. Отключение может быть полезно для виртуальных файлов,
     * добавленных через {@link #customFiles(Consumer)}, или для оптимизации,
     * когда проверка выполняется на другом уровне.
     * </p>
     *
     * @param doCheck {@code true} — выполнять проверку существования пути, {@code false} — пропускать
     * @return новый экземпляр {@link StaticFiles} с обновлённым флагом проверки
     */
    public StaticFiles checkPathExists(boolean doCheck) {
        var c = clone();
        c.checkPathExists = doCheck;
        return c;
    }
    //endregion

    //region custom files
    private Map<String, Supplier<Bytes>> customFiles = new LinkedHashMap<>();

    /**
     * Внутренний класс для конфигурации виртуальных файлов.
     * <p>
     * Предоставляет fluent API для добавления файлов, содержимое которых генерируется
     * программно или загружается из ресурсов, без привязки к файловой системе.
     * </p>
     */
    public class CustomFiles {
        private Class<?> baseClass = CustomFiles.class;

        public CustomFiles() {
        }

        public CustomFiles(Class<?> baseClass) {
            if( baseClass == null ) throw new IllegalArgumentException("baseClass==null");
            this.baseClass = baseClass;
        }

        /**
         * Очищает все ранее добавленные виртуальные файлы.
         *
         * @return ссылка на этот экземпляр для цепочки вызовов
         */
        public CustomFiles clear() {
            customFiles.clear();
            return this;
        }

        /**
         * Добавляет виртуальный файл с содержимым, поставляемым динамически.
         *
         * @param filePath    путь к файлу относительно префикса URI; не может быть {@code null}
         * @param fileContent поставщик содержимого файла; не может быть {@code null}
         * @return ссылка на этот экземпляр для цепочки вызовов
         * @throws IllegalArgumentException если любой из параметров {@code null}
         */
        public CustomFiles add(String filePath, Supplier<Bytes> fileContent) {
            if( filePath == null ) throw new IllegalArgumentException("filePath==null");
            if( fileContent == null ) throw new IllegalArgumentException("fileContent==null");
            customFiles.put(filePath, fileContent);
            return this;
        }

        /**
         * Добавляет виртуальный файл с готовым содержимым.
         *
         * @param filePath    путь к файлу относительно префикса URI; не может быть {@code null}
         * @param fileContent содержимое файла; не может быть {@code null}
         * @return ссылка на этот экземпляр для цепочки вызовов
         * @throws IllegalArgumentException если любой из параметров {@code null}
         */
        public CustomFiles add(String filePath, Bytes fileContent) {
            if( filePath == null ) throw new IllegalArgumentException("filePath==null");
            if( fileContent == null ) throw new IllegalArgumentException("fileContent==null");
            customFiles.put(filePath, () -> fileContent);
            return this;
        }

        /**
         * Добавляет файл из ресурса класса (classpath).
         * <p>
         * Загружает содержимое ресурса один раз при конфигурации и сохраняет в памяти.
         * </p>
         *
         * @param filePath     путь к файлу относительно префикса URI; не может быть {@code null}
         * @param baseClass    класс, относительно которого загружается ресурс; не может быть {@code null}
         * @param resourceName имя ресурса в формате {@link Class#getResource(String)}; не может быть {@code null}
         * @return объект для дополнительной настройки ответа (Content-Type, статус и т.д.)
         * @throws IllegalArgumentException если любой из параметров {@code null}
         * @throws IllegalStateException    если ресурс не найден или не может быть прочитан
         */
        public ResourceConfigure addResource(String filePath, Class<?> baseClass, String resourceName) {
            if( filePath == null ) throw new IllegalArgumentException("filePath==null");
            if( baseClass == null ) throw new IllegalArgumentException("baseClass==null");
            if( resourceName == null ) throw new IllegalArgumentException("resourceName==null");

            var url = baseClass.getResource(resourceName);
            if( url == null ) throw new IllegalStateException("resource " + resourceName + " not found");

            var bytesRes = IO.tryReadAllBytes(url);
            if( bytesRes.isError() ) {
                throw new IllegalStateException("resource " + resourceName + " can't read", bytesRes.swap().unwrap());
            }

            var bytes = Bytes.of(bytesRes.unwrap());
            var bytes0 = new Bytes[]{bytes};

            Supplier<Bytes> bytesSupplier = () -> bytes0[0];
            customFiles.put(filePath, bytesSupplier);

            return new ResourceConfigure(bytes0, bytesSupplier);
        }

        /**
         * Добавляет файл из ресурса класса (classpath).
         * <p>
         * Загружает содержимое ресурса один раз при конфигурации и сохраняет в памяти.
         * </p>
         *
         * @param filePath     путь к файлу относительно префикса URI; не может быть {@code null}
         * @param resourceName имя ресурса в формате {@link Class#getResource(String)}; не может быть {@code null}
         * @return объект для дополнительной настройки ответа (Content-Type, статус и т.д.)
         * @throws IllegalArgumentException если любой из параметров {@code null}
         * @throws IllegalStateException    если ресурс не найден или не может быть прочитан
         */
        public ResourceConfigure addResource(String filePath, String resourceName) {
            if( baseClass == null ) throw new IllegalArgumentException("baseClass==null");
            if( filePath == null ) throw new IllegalArgumentException("filePath==null");
            if( resourceName == null ) throw new IllegalArgumentException("resourceName==null");

            return addResource(filePath, baseClass, resourceName);
        }
    }

    /**
     * Класс для дополнительной настройки ответа виртуального файла.
     * <p>
     * Позволяет переопределить Content-Type, имя файла для скачивания и HTTP-статус
     * для конкретного ресурса, добавленного через {@link CustomFiles#addResource}.
     * </p>
     */
    public class ResourceConfigure {
        private final Bytes[] bytes;
        private final Supplier<Bytes> bytesSupplier;

        /**
         * Создаёт конфигуратор для указанного экземпляра Bytes.
         *
         * @param bytes массив из одного элемента {@link Bytes} для настройки
         */
        public ResourceConfigure(Bytes[] bytes, Supplier<Bytes> bytesSupplier) {
            this.bytes = bytes;
            this.bytesSupplier = bytesSupplier;
        }

        @SuppressWarnings("UnusedReturnValue")
        public ResourceConfigure addPath(String filePath) {
            if( filePath == null ) throw new IllegalArgumentException("filePath==null");
            customFiles.put(filePath, bytesSupplier);
            return this;
        }

        /**
         * Устанавливает Content-Type для ответа.
         *
         * @param contentType значение заголовка Content-Type; не может быть {@code null}
         * @return этот экземпляр для цепочки вызовов
         * @throws IllegalArgumentException если {@code contentType == null}
         */
        public ResourceConfigure contentType(String contentType) {
            if( contentType == null ) throw new IllegalArgumentException("contentType==null");
            bytes[0] = bytes[0].withContentType(contentType);
            return this;
        }

        /**
         * Устанавливает имя файла для заголовка Content-Disposition.
         * <p>
         * Полезно для скачивания файлов с пользовательским именем.
         * </p>
         *
         * @param fileName имя файла; не может быть {@code null}
         * @return этот экземпляр для цепочки вызовов
         * @throws IllegalArgumentException если {@code fileName == null}
         */
        public ResourceConfigure fileName(String fileName) {
            if( fileName == null ) throw new IllegalArgumentException("contentType==null");
            bytes[0] = bytes[0].withFileName(fileName);
            return this;
        }

        /**
         * Устанавливает HTTP-статус код для ответа.
         *
         * @param statusCode код статуса; не может быть {@code null}
         * @return этот экземпляр для цепочки вызовов
         * @throws IllegalArgumentException если {@code statusCode == null}
         */
        public ResourceConfigure statusCode(StatusCode statusCode) {
            if( statusCode == null ) throw new IllegalArgumentException("statusCode==null");
            bytes[0] = bytes[0].withStatusCode(statusCode);
            return this;
        }
    }

    /**
     * Конфигурирует коллекцию виртуальных файлов через потребитель.
     * <p>
     * Пример использования:
     * <pre>
     * staticFiles.customFiles(cf -> cf
     *     .add("/config.json", () -> Bytes.json("{\"key\":\"value\"}"))
     *     .addResource("/lib.js", MyClass.class, "/assets/lib.js")
     *         .contentType("application/javascript")
     * );
     * </pre>
     * </p>
     *
     * @param customFilesConsumer потребитель для настройки виртуальных файлов; не может быть {@code null}
     * @return новый экземпляр {@link StaticFiles} с добавленными виртуальными файлами
     * @throws IllegalArgumentException если {@code customFilesConsumer == null}
     */
    public StaticFiles customFiles(Consumer<CustomFiles> customFilesConsumer) {
        if( customFilesConsumer == null ) throw new IllegalArgumentException("customFilesConsumer==null");
        var clone = clone();
        customFilesConsumer.accept(clone.new CustomFiles());
        return clone;
    }

    /**
     * Конфигурирует коллекцию виртуальных файлов через потребитель.
     * <p>
     * Пример использования:
     * <pre>
     * staticFiles.customFiles(cf -> cf
     *     .add("/config.json", () -> Bytes.json("{\"key\":\"value\"}"))
     *     .addResource("/lib.js", MyClass.class, "/assets/lib.js")
     *         .contentType("application/javascript")
     * );
     * </pre>
     * </p>
     *
     * @param baseClass           класс, относительно которого загружается ресурс
     * @param customFilesConsumer потребитель для настройки виртуальных файлов; не может быть {@code null}
     * @return новый экземпляр {@link StaticFiles} с добавленными виртуальными файлами
     * @throws IllegalArgumentException если {@code customFilesConsumer == null}
     */
    public StaticFiles customFiles(Class<?> baseClass, Consumer<CustomFiles> customFilesConsumer) {
        if( baseClass == null ) throw new IllegalArgumentException("baseClass==null");
        if( customFilesConsumer == null ) throw new IllegalArgumentException("customFilesConsumer==null");
        var clone = clone();
        customFilesConsumer.accept(clone.new CustomFiles(baseClass));
        return clone;
    }
    //endregion

    private Predicate<Path> visiblePath = path -> true;
    private Predicate<Path> readablePath = path -> true;
    private Predicate<Path> writeablePath = path -> true;
    private Predicate<Path> deletablePath = path -> true;

    private boolean allowResources = true;
    private boolean allowRead = true;
    private boolean allowAbilities = true;
    private boolean allowUpload = false;
    private boolean allowDelete = false;
    private boolean allowMkDir = false;
    private boolean allowRename = false;

//    public sealed interface CORS {
//        record None() implements CORS {}
//        record Any() implements CORS {}
//    }
//
//    private CORS cors = new CORS.Any();

    @SuppressWarnings("UnusedReturnValue")
    public class Allow {
        //region resources : boolean
        public boolean resources(){ return allowResources; }

        public Allow resources(boolean switchOn){
            allowResources = switchOn;
            return this;
        }
        //endregion
        //region read : boolean
        public boolean read(){
            return allowRead;
        }

        public Allow read(boolean switchOn){
            allowRead = switchOn;
            return this;
        }
        //endregion
        //region abilities : boolean
        public boolean abilities(){
            return allowAbilities;
        }

        public Allow abilities(boolean switchOn){
            allowAbilities = switchOn;
            return this;
        }
        //endregion
        //region upload : boolean
        public boolean upload(){
            return allowUpload;
        }

        public Allow upload(boolean switchOn){
            allowUpload = switchOn;
            return this;
        }
        //endregion
        //region delete : boolean
        public boolean delete(){
            return allowDelete;
        }

        public Allow delete(boolean switchOn){
            allowDelete = switchOn;
            return this;
        }
        //endregion
        //region mkdir : boolean
        public boolean mkdir(){
            return allowMkDir;
        }

        public Allow mkdir(boolean switchOn){
            allowMkDir = switchOn;
            return this;
        }
        //endregion
        //region allowRename : boolean
        public boolean rename(){
            return allowRename;
        }

        public Allow rename(boolean switchOn){
            allowRename = switchOn;
            return this;
        }
        //endregion
        public Allow invisiblePath( Predicate<Path> pathPredicate ){
            if( pathPredicate==null ) throw new IllegalArgumentException("pathPredicate==null");
            visiblePath = p -> !pathPredicate.test(p);
            return this;
        }
        public Allow readablePath( Predicate<Path> pathPredicate ){
            if( pathPredicate==null ) throw new IllegalArgumentException("pathPredicate==null");
            readablePath = pathPredicate;
            return this;
        }
        public Allow writeablePath( Predicate<Path> pathPredicate ){
            if( pathPredicate==null ) throw new IllegalArgumentException("pathPredicate==null");
            writeablePath = pathPredicate;
            return this;
        }
        public Allow deletablePath( Predicate<Path> pathPredicate ){
            if( pathPredicate==null ) throw new IllegalArgumentException("pathPredicate==null");
            deletablePath = pathPredicate;
            return this;
        }
    }

    public StaticFiles allow(Consumer<Allow> allows){
        var clone = clone();
        allows.accept(clone.new Allow());
        return clone;
    }

    /**
     * Создаёт и настраивает обработчик Jetty для обслуживания статических файлов.
     * <p>
     * Возвращает {@link Handler}, который:
     * <ul>
     *   <li>Обрабатывает только GET-запросы</li>
     *   <li>Фильтрует запросы по префиксу {@code uriPathPrefix}</li>
     *   <li>Проверяет существование файлов (если включено)</li>
     *   <li>Сначала проверяет виртуальные файлы, затем файловую систему</li>
     *   <li>Автоматически определяет MIME-тип по расширению</li>
     *   <li>Поддерживает листинг каталогов в формате HTML</li>
     * </ul>
     * </p>
     *
     * @return настроенный экземпляр {@link Handler} для интеграции с Jetty
     */
    public Handler buildRouter() {
        var handlers = new ArrayList<Handler>();
        if( allowResources )handlers.add(downloadResourceHandler());
        if( allowRead )handlers.add(downloadFileLsDirHandler());
        if( allowUpload )handlers.add(uploadFileHandler());
        if( allowDelete )handlers.add(deleteFileOrDirHandler());
        if( allowMkDir )handlers.add(mkdirHandler());
        if( allowAbilities )handlers.add(abilitiesHandler());
        if( allowRename )handlers.add(renameHandler());

        return new Handler.Sequence(
            handlers
        );
    }

    //region target path / target file
    private class TargetPath {
        public final String uriSubPath;

        public TargetPath(String uriSubPath) {
            if( uriSubPath == null ) throw new IllegalArgumentException("uriSubPath==null");
            this.uriSubPath = uriSubPath;
        }

        public TargetPath(TargetPath sample) {
            if( sample == null ) throw new IllegalArgumentException("sample==null");
            this.uriSubPath = sample.uriSubPath;
        }

        public Optional<TargetFile> resolvePhysical() {
            var dir = contentRoot.get();
            if( dir == null ) return Optional.empty();

            var file = dir.resolve(uriSubPath);
            return Optional.of(new TargetFile(this, file));
        }
    }

    private class TargetFile extends TargetPath {
        public final Path physicalPath;

        public TargetFile(String uriSubPath, Path physicalPath) {
            super(uriSubPath);
            this.physicalPath = physicalPath;
        }

        public TargetFile(TargetPath targetPath, Path physicalPath) {
            super(targetPath);
            this.physicalPath = physicalPath;
        }

        public boolean isExists() {
            return Files.exists(physicalPath);
        }

        public Optional<TargetFile> exists() {
            return isExists()
                ? Optional.of(this)
                : Optional.empty();
        }
    }

    private static Optional<String> extractSubPath(String uriPath, String uriPrefix) {
        if( uriPath == null ) throw new IllegalArgumentException("uriPath==null");
        if( uriPrefix == null ) throw new IllegalArgumentException("uriPrefix==null");

        if( uriPath.equals(uriPrefix) ) return Optional.of("");

        var pref = uriPrefix.endsWith("/") ? uriPrefix : uriPrefix + "/";
        if( pref.equals(uriPath) ) return Optional.of("");

        if( uriPath.length() < pref.length() ) return Optional.empty();
        if( !uriPath.startsWith(pref) ) return Optional.empty();

        return Optional.of(uriPath.substring(pref.length()));
    }

    private Optional<TargetPath> targetPathFromUriPath(String uriCanonPath) {
        if( uriCanonPath == null ) return Optional.empty();

        var uriPrefix = uriPathPrefix.get();
        if( uriPrefix == null ) return Optional.empty();

        return extractSubPath(uriCanonPath, uriPrefix).map(TargetPath::new);
    }

    private Optional<TargetPath> targetPathFrom(Request request) {
        if( request == null ) throw new IllegalArgumentException("request==null");
        return targetPathFromUriPath(request.getHttpURI().getCanonicalPath());
    }
    //endregion

    //region Обработчики ошибок

    /**
     * Формирует ответ об ошибке при отсутствии канонического пути в запросе.
     *
     * @param uri исходный URI для логирования
     * @return объект {@link Bytes} с текстовым сообщением и статусом 500
     */
    private Bytes canonicalPathIsNull(HttpURI uri) {
        return Bytes.plainText("canonicalPathIsNull").withStatusCode(StatusCode.Internal_Server_Error);
    }

    /**
     * Формирует ответ об ошибке при отсутствии префикса пути.
     *
     * @param uri исходный URI для логирования
     * @return объект {@link Bytes} с текстовым сообщением и статусом 500
     */
    private Bytes uriPathPrefixIsNull(HttpURI uri) {
        return Bytes.plainText("uriPathPrefixIsNull").withStatusCode(StatusCode.Internal_Server_Error);
    }

    /**
     * Формирует ответ об ошибке при несовпадении префикса пути.
     *
     * @param uriPath       фактический путь из запроса
     * @param uriPathPrefix ожидаемый префикс
     * @return объект {@link Bytes} с сообщением и статусом 406
     */
    private Bytes prefixNotMatch(String uriPath, String uriPathPrefix) {
        return Bytes.plainText(
            "uriPathPrefixIsNull ${uriPath} ${uriPathPrefix}"
                .replace("${uriPathPrefix}", uriPathPrefix)
                .replace("${uriPath}", uriPath)
        ).withStatusCode(StatusCode.Not_Acceptable);
    }

    /**
     * Формирует ответ об ошибке при отсутствии корневого каталога.
     *
     * @return объект {@link Bytes} с текстовым сообщением и статусом 500
     */
    private Bytes contentRootIsNull() {
        return Bytes.plainText("contentRootIsNull").withStatusCode(StatusCode.Internal_Server_Error);
    }

    /**
     * Формирует ответ об ошибке при отсутствии корневого пути в файловой системе.
     *
     * @param path путь, который не существует
     * @return объект {@link Bytes} с сообщением и статусом 500
     */
    private Bytes pathNotExists(String path) {
        return Bytes.plainText("pathNotExists " + path).withStatusCode(StatusCode.Internal_Server_Error);
    }
    //endregion

    private Handler downloadResourceHandler(){
        return RequestRouter.Builder
            .GET()
            .path(uriCanonPath -> {
                    var targetPath = targetPathFromUriPath(uriCanonPath);
                    return targetPath.filter(path -> customFiles.containsKey(path.uriSubPath)).isPresent();
                }
            )
            .bytesResponse()
            .jettyRequest()
            .call(req -> () -> {
                var tPathOpt = targetPathFrom(req);
                if( tPathOpt.isEmpty() ) return canonicalPathIsNull(req.getHttpURI());

                var tPath = tPathOpt.get();

                Optional<Supplier<Bytes>> customBytesSupplier =
                    Optional.ofNullable(customFiles.get(tPath.uriSubPath));

                if( customBytesSupplier.isPresent() ) {
                    return customBytesSupplier.get().get();
                }

                return Bytes.plainText("not found").withStatusCode(StatusCode.Not_Found);
            });
    }

    //region read file
    private Handler downloadFileLsDirHandler() {
        return RequestRouter.Builder
            .GET()
            .path(uriCanonPath -> {
                    var targetPath = targetPathFromUriPath(uriCanonPath);
                    if( targetPath.isEmpty() ) return false;
                    if( !checkPathExists ) return true;

                    var targetFileOpt = targetPath.get().resolvePhysical();
                    if( targetFileOpt.isEmpty() ) return false;

                    var targetFile = targetFileOpt.get();
                    return targetFile.isExists();
                }
            )
            .bytesResponse()
            .jettyRequest()
            .call(req -> () -> {
                var tPathOpt = targetPathFrom(req);
                if( tPathOpt.isEmpty() ) return canonicalPathIsNull(req.getHttpURI());

                var tPath = tPathOpt.get();

                var tFileOpt =
                    tPath.resolvePhysical()
                        .flatMap(TargetPath::resolvePhysical)
                        .flatMap(TargetFile::exists);

                if( tFileOpt.isEmpty() ) return pathNotExists(tPath.uriSubPath);

                return read(tFileOpt.get().physicalPath, req);
            });
    }

    /**
     * Основной метод чтения ресурса: делегирует обработку файлу или каталогу.
     *
     * @param path    путь к ресурсу (файл или каталог)
     * @param request исходный запрос для контекста
     * @return объект {@link Bytes} с содержимым и метаданными ответа
     */
    private Bytes read(Path path, Request request) {
        if( !Files.exists(path) ) {
            return Bytes.plainText("not found").withStatusCode(StatusCode.Not_Found);
        }

        if( Files.isDirectory(path) ) {
            var uriPrefix = uriPathPrefix.get();
            var pref = uriPrefix.endsWith("/") ? uriPrefix : uriPrefix + "/";

            Tuple2<String, Function<StringBuilder, DirContentWriter>> defaultOutput =
                Tuple2.of(MimeType.TEXT_HTML, out ->
                    new DirHtmlView(out)
                        .urlDirEntryBuilder(de -> Optional.of(pref + de.name())));

            var write = Accept.parse(request.getHeaders().get("Accept")).flatMap(accept -> {
                return accept.preferredTypes().head();
            }).flatMap(mimeType -> {
                if( MimeType.isJson(mimeType) ) {
                    Function<StringBuilder, DirContentWriter> writer = DirJsonView::new;
                    return Optional.of(Tuple2.of(
                        MimeType.APP_JS,
                        writer
                    ));
                }

                return Optional.empty();
            }).orElse(defaultOutput);

            return readDir(path, write._1(), write._2());
        }

        if( Files.isRegularFile(path) ) {
            return readFile(
                path,
                Optional.ofNullable(request.getHeaders().get("Range")).flatMap(Range::parse)
            );
        }

        return Bytes.plainText("not found").withStatusCode(StatusCode.Internal_Server_Error);
    }

    /**
     * Генерирует HTML-листинг содержимого каталога.
     * <p>
     * Создаёт простую страницу со ссылками на файлы и подкаталоги.
     * Все имена и URL экранируются для предотвращения XSS.
     * </p>
     *
     * @param path путь к каталогу для листинга
     * @return объект {@link Bytes} с HTML-содержимым и статусом 200, или ошибкой при сбое чтения
     */
    private Bytes readDir(Path path, String contentType, Function<StringBuilder, DirContentWriter> contentWriter) {
        if( !readablePath.test(path) ){
            return Bytes.plainText("not found").withStatusCode(StatusCode.Not_Found);
        }

        StringBuilder html = new StringBuilder();

        var result = DirEntry.writeDirContent(
            contentWriter.apply(html),
            path,
            visiblePath
        );

        if( result.isError() ) {
            log.error("can't read dir {}", path, result.swap().unwrap());
            return Bytes.plainText("read dir error").withStatusCode(StatusCode.Service_Unavailable);
        }

        return Bytes.html(html.toString())
            .withContentType(contentType)
            .withStatusCode(StatusCode.Ok);
    }

    /**
     * Читает содержимое файла и определяет MIME-тип по расширению.
     *
     * @param path путь к файлу
     * @param rangeOpt сегмент файла
     * @return объект {@link Bytes} с содержимым файла, Content-Type и статусом 200
     */
    private Bytes readFile(Path path, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Range> rangeOpt) {
        if( !readablePath.test(path) ){
            return Bytes.plainText("not found").withStatusCode(StatusCode.Not_Found);
        }

        var mime = "application/octet-stream";

        String fileName = path.getFileName().toString();
        if( fileName.contains(".") ) {
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
            mime = fileExtension2MimeTypes().getOrDefault(ext.toLowerCase(), mime);
        }

        if( rangeOpt.isPresent() ){
            var range = rangeOpt.get();
            var segmentCount = range.segments().size();
            if( segmentCount==0 ){
                return Bytes.of(path).withContentType(mime).withStatusCode(StatusCode.Ok);
            }else if( segmentCount>1 ){
                return Bytes.plainText("multiple ranges not supported").withStatusCode(StatusCode.Range_Not_Satisfiable);
            }

            //noinspection OptionalGetWithoutIsPresent
            var segment = range.segments().head().get();

            try {
                var fileSize = Files.size(path);
                var rangeOfFileOpt = segment.intersectWithFileSize(fileSize);

                //noinspection OptionalIsPresent
                if( rangeOfFileOpt.isEmpty() ){
                    return Bytes.plainText("out of file range").withStatusCode(StatusCode.Range_Not_Satisfiable);
                }

                return Bytes.of(path, rangeOfFileOpt.get());
            } catch ( IOException e ) {
                return Bytes.plainText(e.getMessage()).withStatusCode(StatusCode.Service_Unavailable);
            }
        }

        return Bytes.of(path).withContentType(mime).withStatusCode(StatusCode.Ok);
    }
    //endregion

    //region upload file
    @SuppressWarnings("UnusedReturnValue")
    private static class UploadOption {
        //region overwrite
        private boolean overwrite = true;

        public UploadOption overwrite(boolean v) {
            overwrite = v;
            return this;
        }

        public boolean overwrite() {return overwrite;}
        //endregion
        //region mkdir
        private boolean mkdir = true;

        public UploadOption mkdir(boolean v) {
            mkdir = v;
            return this;
        }

        public boolean mkdir() {return mkdir;}
        //endregion

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private Optional<ContentRange.Segment> dataSegment = Optional.empty();

        public Optional<ContentRange.Segment> dataSegment(){
            return dataSegment;
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public UploadOption dataSegment(Optional<ContentRange.Segment> dataSegment){
            //noinspection OptionalAssignedToNull
            if( dataSegment==null ) throw new IllegalArgumentException("dataSegment==null");
            this.dataSegment = dataSegment;
            return this;
        }
    }

    private static Optional<Path> existsDirOf(Path path){
        var dir = path.getParent();
        if( dir==null )return Optional.empty();
        while( dir != null ) {
            if( Files.exists(dir) ) {
                return Optional.of(dir);
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    @SuppressWarnings({"OptionalIsPresent", "RedundantIfStatement"})
    private Handler uploadFileHandler(){
        return RequestRouter.Builder
            .POST()
            .path(uriPath -> {
                var targetPathOpt = targetPathFromUriPath(uriPath);
                if( targetPathOpt.isEmpty() ) return false;

                var targetFileOpt = targetPathOpt.get().resolvePhysical();
                if( targetFileOpt.isEmpty() ) return false;

                return true;
            })
            .bytesResponse()
            .jettyRequest()
            .inputBodyStream()
            .header("fs-op-overwrite", HeaderValue.boolValue.defaultValue(true))
            .header("content-range", ContentRange.headerSegmentOptional() )
            .call(
                dataSegmentOpt ->
                    overwrite ->
                        dataStream -> req -> () -> {
                var targetPathOpt = targetPathFrom(req);
                if( targetPathOpt.isEmpty() ) return canonicalPathIsNull(req.getHttpURI());

                var targetFileOpt = targetPathOpt.get().resolvePhysical();
                if( targetFileOpt.isEmpty() ) return pathNotExists(targetPathOpt.get().uriSubPath);

                return uploadFile(
                    targetFileOpt.get().physicalPath,
                    dataStream,
                    conf -> conf
                        .overwrite(overwrite)
                        .mkdir(allowMkDir)
                        .dataSegment(dataSegmentOpt)
                );
            });
    }

    private Bytes uploadFile(Path path, InputStream stream, Consumer<UploadOption> postOptConf) {
        try {
            if( !writeablePath.test(path) ){
                return Bytes.plainText("write file " + path + " forbidden").withStatusCode(StatusCode.Forbidden);
            }

            UploadOption upldOpt = new UploadOption();
            postOptConf.accept(upldOpt);

            var dir = path.getParent();
            if( dir != null && !Files.exists(dir) ) {
                var pdir = existsDirOf(dir);

                if( upldOpt.mkdir() && allowMkDir && pdir.map( d -> writeablePath.test(d) ).orElse(true) ) {
                    Files.createDirectories(dir);
                    log.info("mkdir {} success", dir);
                } else {
                    return Bytes.plainText("create dir " + dir + " forbidden").withStatusCode(StatusCode.Forbidden);
                }
            }

            if( Files.exists(path) && !upldOpt.overwrite() ) {
                return Bytes.plainText("overwrite file " + path + " forbidden").withStatusCode(StatusCode.Forbidden);
            }

            if( upldOpt.dataSegment().isEmpty() ) {
                try( var outStream = Files.newOutputStream(path) ) {
                    IO.copy(stream, outStream, x -> {});
                    outStream.flush();
                }
            } else {
                var segment = upldOpt.dataSegment().get();
                if( segment.from()>0 ){
                    if( Files.exists(path) ){
                        return Bytes.plainText("file not found").withStatusCode(StatusCode.Not_Found);
                    }
                    if( !Files.isRegularFile(path) ){
                        return Bytes.plainText("not file").withStatusCode(StatusCode.Not_Found);
                    }

                    long fileSize = Files.size(path);
                    if( segment.to() > fileSize ){
                        return Bytes.plainText("update segment out of range").withStatusCode(StatusCode.Bad_Request);
                    }
                }

                var sizeWasWrote = 0L;
                try( var channel =
                         Files.newByteChannel(path,
                             StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE
                         ) ){

                    channel.position(segment.from());

                    byte[] buff = new byte[1024*8];
                    while( sizeWasWrote < segment.size() ){
                        var reads = stream.read(buff);
                        if( reads<=0 )break;

                        var wrote = channel.write(ByteBuffer.wrap(buff,0,reads));
                        if( wrote!=reads ){
                            throw new IOException("write size not equals was reads");
                        }

                        sizeWasWrote += reads;
                    }
                }
            }

            log.info("data was write into {}", path);

            return Bytes.plainText("ok").withStatusCode(StatusCode.Ok);
        } catch ( IOException e ) {
            return Bytes.plainText(e.toString()).withStatusCode(StatusCode.Service_Unavailable);
        }
    }
    //endregion

    //region delete file
    private Bytes deleteFile(Path path, Consumer<DeleteOption> deleteConf) {
        DeleteOption opt = new DeleteOption();
        if( deleteConf != null ) {
            deleteConf.accept(opt);
        }

        try {
            deleteFile(path, opt);
            return Bytes.plainText("").withStatusCode(StatusCode.Ok);
        } catch ( IOException | ForbiddenDelete e ) {
            return Bytes.plainText("can't delete: " + e.getMessage())
                .withStatusCode(StatusCode.Internal_Server_Error);
        } catch ( NonEmptyDirecotry e ) {
            return Bytes.plainText("can't delete non empty directory: " + e.getMessage())
                .withStatusCode(StatusCode.Internal_Server_Error);
        }
    }

    @SuppressWarnings({"OptionalIsPresent", "RedundantIfStatement"})
    private Handler deleteFileOrDirHandler(){
        return RequestRouter.Builder
            .DELETE()
            .path(uriPath -> {
                var targetPathOpt = targetPathFromUriPath(uriPath);
                if( targetPathOpt.isEmpty() ) return false;

                var targetFileOpt = targetPathOpt.get().resolvePhysical();
                if( targetFileOpt.isEmpty() ) return false;

                return true;
            })
            .bytesResponse()
            .jettyRequest()
            //.header("fs-op-recursive", HeaderValue.boolValue.defaultValue(false) )
            //.header("fs-op-follow-links", HeaderValue.boolValue.defaultValue(false) )
            .call(
                //followLinks -> recursive ->
                req -> () -> {
                var targetPathOpt = targetPathFrom(req);
                if( targetPathOpt.isEmpty() ) return canonicalPathIsNull(req.getHttpURI());

                var targetFileOpt = targetPathOpt.get().resolvePhysical();
                if( targetFileOpt.isEmpty() ) return pathNotExists(targetPathOpt.get().uriSubPath);

                return deleteFile(
                    targetFileOpt.get().physicalPath,
                    conf -> conf.recursive(
                        HeaderValue.boolValue.parse("fs-op-recursive").orElse(false)
                    ).followLink(
                        HeaderValue.boolValue.parse("fs-op-follow-links").orElse(false)
                    )
                );
            });
    }

    @SuppressWarnings("UnusedReturnValue")
    private static class DeleteOption {
        //region recursive
        private boolean recursive = true;

        public boolean recursive() {
            return recursive;
        }

        public DeleteOption recursive(boolean v) {
            recursive = v;
            return this;
        }
        //endregion
        //region followLink
        private boolean followLink = false;

        public boolean followLink() {
            return followLink;
        }

        public DeleteOption followLink(boolean v) {
            followLink = v;
            return this;
        }
        //endregion
    }

    private static class NonEmptyDirecotry extends Error {
        public final Path path;

        public NonEmptyDirecotry(Path path, String message) {
            super(message);
            this.path = path;
        }
    }

    private static class ForbiddenDelete extends Error {
        public final Path path;

        public ForbiddenDelete(Path path, String message) {
            super(message);
            this.path = path;
        }
    }

    private void deleteFile(Path path, DeleteOption opt) throws IOException, NonEmptyDirecotry, ForbiddenDelete {
        if( !Files.exists(path) ) {
            return;
        }

        if( Files.isDirectory(path) ) {
            if( !deletablePath.test(path) ){
                throw new ForbiddenDelete(path, "foribidden delete");
            }

            List<Path> subFiles = new ArrayList<>();
            Set<Path> links = new HashSet<>();

            try( var ds = Files.list(path) ) {
                ds.forEach(p -> {
                    subFiles.add(p);
                    if( Files.isSymbolicLink(p) ) {
                        links.add(p);
                    }
                });
            }

            if( !opt.recursive() && !subFiles.isEmpty() ) {
                throw new NonEmptyDirecotry(path, "directory not empty");
            }

            for( var p : subFiles ) {
                if( links.contains(p) ) {
                    if( !opt.followLink() ) {
                        Files.deleteIfExists(p);
                    } else {
                        deleteFile(p, opt);
                    }
                } else {
                    deleteFile(p, opt);
                }
            }
            Files.deleteIfExists(path);
            return;
        }

        if( Files.isRegularFile(path) ) {
            if( !deletablePath.test(path) ){
                throw new ForbiddenDelete(path, "foribidden delete");
            }
            Files.deleteIfExists(path);
        }
    }
    //endregion

    //region mkdir
    @SuppressWarnings({"OptionalIsPresent", "RedundantIfStatement"})
    private Handler mkdirHandler(){
        return RequestRouter.Builder
            .method(name -> name.equalsIgnoreCase("mkdir"))
            .path(uriPath -> {
                var targetPathOpt = targetPathFromUriPath(uriPath);
                if( targetPathOpt.isEmpty() ) return false;

                var targetFileOpt = targetPathOpt.get().resolvePhysical();
                if( targetFileOpt.isEmpty() ) return false;

                return true;
            })
            .bytesResponse()
            .jettyRequest()
            .call( req -> () -> {
                var targetPathOpt = targetPathFrom(req);
                if( targetPathOpt.isEmpty() ) return canonicalPathIsNull(req.getHttpURI());

                var targetFileOpt = targetPathOpt.get().resolvePhysical();
                if( targetFileOpt.isEmpty() ) return pathNotExists(targetPathOpt.get().uriSubPath);

                return mkdir(targetFileOpt.get().physicalPath);
            });
    }

    private Bytes mkdir(Path path) {
        if( Files.exists(path) ){
            return Bytes.plainText(
                JSON.toJson(Map.of("message", "already exists"))
            ).withContentType(MimeType.APP_JSON).withStatusCode(StatusCode.Ok);
        }

        try {
            var pdir = existsDirOf(path);
            if( !pdir.map(d -> writeablePath.test(d)).orElse(true) ){
                return Bytes.plainText("mkdir " + path + " forbidden").withStatusCode(StatusCode.Forbidden);
            }

            Files.createDirectories(path);
            return Bytes.plainText(
                JSON.toJson(Map.of("message", "created"))
            ).withContentType(MimeType.APP_JSON).withStatusCode(StatusCode.Ok);
        } catch ( IOException e ) {
            return Bytes.plainText(
                JSON.toJson(Map.of("message", e.getMessage()))
            ).withContentType(MimeType.APP_JSON).withStatusCode(StatusCode.Internal_Server_Error);
        }
    }
    //endregion

    //region rename
    public static class RenameBody {
        public String source;
        public String target;
    }

    private Handler renameHandler() {
        return RequestRouter.Builder
            .method( name -> name.equalsIgnoreCase("rename") )
            .bytesResponse()
            .jettyRequest()
            .jsonBody(RenameBody.class)
            .call( renameReqBody -> jettryReq -> () -> {
                if( renameReqBody==null )return Bytes.plainText("body is null").withStatusCode(StatusCode.Bad_Request);
                if( renameReqBody.source==null )return Bytes.plainText("body.source is null").withStatusCode(StatusCode.Bad_Request);
                if( renameReqBody.target==null )return Bytes.plainText("body.target is null").withStatusCode(StatusCode.Bad_Request);

                var sourceRes = UnixPath.parse(renameReqBody.source);
                if( sourceRes.isError() )return Bytes.plainText("body.source "+sourceRes.swap().unwrap()).withStatusCode(StatusCode.Bad_Request);
                var sourcePath = sourceRes.unwrap();
                if( !sourcePath.isAbsolute() )return Bytes.plainText("body.source "+sourcePath+" must be absolute").withStatusCode(StatusCode.Bad_Request);
                var normalized = sourcePath.normalize();
                if( normalized.isError() )return Bytes.plainText("body.source "+sourcePath+" can't normalize: "+normalized.swap().unwrap()).withStatusCode(StatusCode.Internal_Server_Error);
                sourcePath = normalized.unwrap();

                var targetRes = UnixPath.parse(renameReqBody.target);
                if( targetRes.isError() )return Bytes.plainText("body.target "+targetRes.swap().unwrap()).withStatusCode(StatusCode.Bad_Request);
                var targetPath = targetRes.unwrap();
                if( !targetPath.isAbsolute() )return Bytes.plainText("body.target "+targetPath+" must be absolute").withStatusCode(StatusCode.Bad_Request);
                normalized = targetPath.normalize();
                if( normalized.isError() )return Bytes.plainText("body.target "+targetPath+" can't normalize: "+normalized.swap().unwrap()).withStatusCode(StatusCode.Internal_Server_Error);
                targetPath = normalized.unwrap();

                return rename(sourcePath, targetPath);
            });
    }

    private Bytes rename(UnixPath source, UnixPath target){
        var root = contentRoot.get();
        if( root==null ) return Bytes.plainText("content root not defined").withStatusCode(StatusCode.Internal_Server_Error);

        if( !Files.exists(root) )
            return Bytes.plainText("content root not exists").withStatusCode(StatusCode.Internal_Server_Error);

        var srcPath = root.resolve(source.asRelative().toString());
        var tgtPath = root.resolve(target.asRelative().toString());

        if( !writeablePath.test(srcPath) ){
            return Bytes.plainText("source is not writeable");
        }

        if( !deletablePath.test(srcPath) ){
            return Bytes.plainText("source is not deleteable").withStatusCode(StatusCode.Internal_Server_Error);
        }

        if( !writeablePath.test(tgtPath) ){
            return Bytes.plainText("target is not writeable").withStatusCode(StatusCode.Internal_Server_Error);
        }

        if( Files.exists(tgtPath) ){
            return Bytes.plainText("target already exists").withStatusCode(StatusCode.Internal_Server_Error);
        }

        if( !Files.exists(srcPath) ){
            return Bytes.plainText("source not exists").withStatusCode(StatusCode.Internal_Server_Error);
        }

        try {
            Files.move(srcPath, tgtPath);
        } catch ( IOException e ) {
            log.error("can't rename {} to {}", srcPath, tgtPath, e);
            return Bytes.plainText("can't rename").withStatusCode(StatusCode.Internal_Server_Error);
        }

        return Bytes.plainText(
            JSON.toJson(Map.of("message", "renamed"))
        ).withContentType(MimeType.APP_JSON).withStatusCode(StatusCode.Ok);
    }
    //endregion

    //region abilities
    private Handler abilitiesHandler(){
        return RequestRouter.Builder.method(name -> name.equalsIgnoreCase("abilities"))
            .path(uriPath -> {
                var targetPathOpt = targetPathFromUriPath(uriPath);
                return targetPathOpt.map(targetPath -> targetPath.uriSubPath.equalsIgnoreCase("/") || targetPath.uriSubPath.isEmpty()).orElse(false);
            })
            .bytesResponse()
            .call( () -> {
                var abilities = new ArrayList<String>();
                if( allowResources )abilities.add("resources");
                if( allowRead )abilities.add("read");
                if( allowUpload )abilities.add("upload");
                if( allowDelete )abilities.add("delete");
                if( allowMkDir )abilities.add("mkdir");
                if( allowAbilities )abilities.add("abilities");
                if( allowRename )abilities.add("rename");
                return Bytes.plainText(JSON.toJson(abilities)).withContentType(MimeType.APP_JSON).withStatusCode(StatusCode.Ok);
            });
    }
    //endregion
}