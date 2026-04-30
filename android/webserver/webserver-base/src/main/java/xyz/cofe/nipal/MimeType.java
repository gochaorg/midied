package xyz.cofe.nipal;

/**
 * Класс, содержащий константы для часто используемых MIME-типов.
 * Предоставляет удобный доступ к общепринятым значениям MIME-типов
 * для текстовых файлов, изображений, аудио и других типов данных.
 */
public class MimeType {
    // Текстовые типы
    /** MIME-тип для HTML документов */
    public static final String TEXT_HTML = "text/html";
    /** MIME-тип для простых текстовых файлов */
    public static final String TEXT_PLAIN = "text/plain";
    /** MIME-тип для CSS стилей */
    public static final String TEXT_CSS = "text/css";

    // Изображения
    /** MIME-тип для JPEG изображений */
    public static final String IMAGE_JPEG = "image/jpeg";
    /** MIME-тип для GIF изображений */
    public static final String IMAGE_GIF = "image/gif";
    /** MIME-тип для PNG изображений */
    public static final String IMAGE_PNG = "image/png";
    /** MIME-тип для SVG векторных изображений */
    public static final String IMAGE_SVG = "image/svg+xml";

    // Приложения/скрипты
    /** MIME-тип для WebAssembly файлов */
    public static final String APP_WASM = "application/wasm";
    /** MIME-тип для JavaScript файлов */
    public static final String APP_JS = "application/javascript";
    /** MIME-тип для JSON данных */
    public static final String APP_JSON = "application/json";

    public static boolean isJson(String type){
        if( type==null )return false;
        if( APP_JSON.equalsIgnoreCase(type) )return true;
        return false;
    }

    // Аудио
    /** MIME-тип для MP3 аудио */
    public static final String AUDIO_MPEG = "audio/mpeg";
    /** MIME-тип для MP3 аудио (альтернативное обозначение) */
    public static final String AUDIO_MP3 = "audio/mpeg";
    /** MIME-тип для WAV аудио */
    public static final String AUDIO_WAV = "audio/wav";
    /** MIME-тип для MIDI аудио */
    public static final String AUDIO_MIDI = "audio/midi";
    /** MIME-тип для OGG аудио */
    public static final String AUDIO_OGG = "audio/ogg";
}