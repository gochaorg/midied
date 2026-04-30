package xyz.cofe.nipal.header;

import xyz.cofe.coll.im.ImList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AcceptParser {
    /**
     * Парсит заголовок Accept в заданную структуру.
     * Полностью соответствует RFC 9110: игнорирует лишние пробелы,
     * поддерживает q=0..1, дефолтный вес 1.0, регистронезависимый q=.
     */
    public static Optional<Accept> parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.of(new Accept(ImList.of()));
        }

        List<MediaType> buffer = new ArrayList<>();
        String[] segments = headerValue.split(",");

        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;

            // Разделяем MIME-тип и параметры
            int semicolonIdx = trimmed.indexOf(';');
            String mime = (semicolonIdx >= 0)
                ? trimmed.substring(0, semicolonIdx).trim()
                : trimmed;

            if (mime.isEmpty()) continue;

            double weight = 1.0; // Дефолт по RFC

            if (semicolonIdx >= 0) {
                String params = trimmed.substring(semicolonIdx + 1);
                // Ищем параметр q= среди параметров (может быть не первым)
                for (String param : params.split(";")) {
                    String p = param.trim();
                    // regionMatches(true) -> регистронезависимое сравнение "q="
                    if (p.regionMatches(true, 0, "q=", 0, 2)) {
                        try {
                            double val = Double.parseDouble(p.substring(2).trim());
                            // RFC 9110 §12.4.2: q-value должен быть [0, 1]
                            weight = Math.max(0.0, Math.min(1.0, val));
                        } catch (NumberFormatException ignored) {
                            // Неверный формат q -> игнорируем, оставляем 1.0
                        }
                        break; // q найден, дальше искать не нужно
                    }
                }
            }

            buffer.add(new MediaType(mime, weight));
        }

        return Optional.of(new Accept(ImList.from(buffer)));
    }
}
