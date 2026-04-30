package xyz.cofe.mitrenier.str;

import java.util.regex.Pattern;

public class Wildcard {
    /**
     * Преобразует строку с подстановочными знаками в объект Pattern.
     * '?' соответствует любому одному символу.
     * '*' соответствует любой последовательности символов (включая пустую).
     * Использует Pattern.quote() для безопасного включения литералов в регулярное выражение.
     *
     * @param wildcard Строка с подстановочными знаками.
     * @return Объект Pattern, представляющий эквивалентное регулярное выражение.
     */
    public static Pattern wildcardToPattern(String wildcard) {
        if (wildcard == null) {
            throw new IllegalArgumentException("Wildcard cannot be null");
        }

        // StringBuilder для построения финального regex
        StringBuilder quotedLiteralPart = new StringBuilder();
        StringBuilder finalRegex = new StringBuilder();

        for (char c : wildcard.toCharArray()) {
            switch (c) {
                case '?':
                    // Если есть накопленная строка литералов, заключаем её в кавычки
                    if (quotedLiteralPart.length() > 0) {
                        finalRegex.append(Pattern.quote(quotedLiteralPart.toString()));
                        quotedLiteralPart.setLength(0); // Очищаем
                    }
                    finalRegex.append('.'); // '?' -> любой один символ
                    break;
                case '*':
                    // Если есть накопленная строка литералов, заключаем её в кавычки
                    if (quotedLiteralPart.length() > 0) {
                        finalRegex.append(Pattern.quote(quotedLiteralPart.toString()));
                        quotedLiteralPart.setLength(0); // Очищаем
                    }
                    finalRegex.append(".*"); // '*' -> любая строка
                    break;
                default:
                    // Накапливаем обычные символы
                    quotedLiteralPart.append(c);
                    break;
            }
        }

        // Не забываем обработать остаток строки после последнего подстановочного знака
        if (quotedLiteralPart.length() > 0) {
            finalRegex.append(Pattern.quote(quotedLiteralPart.toString()));
        }

        // Pattern.CASE_INSENSITIVE - если нужен регистронезависимый поиск
        return Pattern.compile(finalRegex.toString(), Pattern.CASE_INSENSITIVE);
    }}
