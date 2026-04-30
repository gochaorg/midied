package xyz.cofe.nipal.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class ContentDispositionUtil {

    private static final Pattern SAFE_FILENAME_PATTERN =
        Pattern.compile("^[a-zA-Z0-9._-]+$");

    /**
     * Формирует заголовок Content-Disposition с корректным экранированием
     */
    public static String formatContentDisposition(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "attachment";
        }

        StringBuilder header = new StringBuilder("attachment");

        // Безопасное ASCII-имя для параметра filename
        String asciiFilename = sanitizeForAscii(filename);
        if (!asciiFilename.isEmpty()) {
            header.append("; filename=\"").append(escapeQuotes(asciiFilename)).append("\"");
        }

        // UTF-8 имя для параметра filename* (RFC 5987)
        String encodedFilename = encodeRFC5987(filename);
        if (!encodedFilename.isEmpty()) {
            header.append("; filename*=\"UTF-8''").append(encodedFilename).append("\"");
        }

        return header.toString();
    }

    /**
     * Экранирование двойных кавычек и обратных слэшей
     */
    private static String escapeQuotes(String filename) {
        return filename.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Кодирование по RFC 5987 (UTF-8 + URL-encoding)
     */
    private static String encodeRFC5987(String filename) {
        try {
            return URLEncoder.encode(filename, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("'", "%27")
                .replace("(", "%28")
                .replace(")", "%29");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Оставляет только безопасные ASCII-символы для параметра filename
     */
    private static String sanitizeForAscii(String filename) {
        StringBuilder result = new StringBuilder();
        for (char c : filename.toCharArray()) {
            if (c < 128 && SAFE_FILENAME_PATTERN.matcher(String.valueOf(c)).matches()) {
                result.append(c);
            } else if (c == ' ') {
                result.append('_');
            }
        }
        String sanitized = result.toString();
        return sanitized.isEmpty() ? "download" : sanitized;
    }
}