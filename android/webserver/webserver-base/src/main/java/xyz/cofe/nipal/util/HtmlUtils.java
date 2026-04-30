package xyz.cofe.nipal.util;

public class HtmlUtils {

    /**
     * Кодирует строку для безопасного вывода в HTML.
     * Экранирует специальные символы: &, <, >, ", '
     *
     * @param input исходная строка (может быть null)
     * @return закодированная строка, или null если input был null
     */
    public static String encodeHtml(String input) {
        if (input == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(input.length() + (input.length() / 10));

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#x27;");
                    break;
                case '/':
                    // Опционально: раскомментируйте для дополнительной защиты от XSS
                    // sb.append("&#x2F;");
                    // break;
                default:
                    sb.append(c);
                    break;
            }
        }

        return sb.toString();
    }

    /**
     * Кодирует строку для безопасного вывода в HTML-атрибуты.
     * Дополнительно экранирует символ /
     *
     * @param input исходная строка (может быть null)
     * @return закодированная строка для атрибута
     */
    public static String encodeHtmlAttribute(String input) {
        if (input == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(input.length() + (input.length() / 10));

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#x27;");
                    break;
                case '/':
                    sb.append("&#x2F;");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }

        return sb.toString();
    }
}
