package com.resumematch.util;

/**
 * Escapes text before it is written into an HTML page, so user input such as a
 * name or an error message can never inject markup. Exposed to the JSP pages
 * as the EL function {@code rm:h(...)} through WEB-INF/rm.tld.
 *
 * Demonstrates: static utility class, StringBuilder, switch on char.
 */
public final class HtmlUtil {

    private HtmlUtil() {
    }

    /** Returns the HTML-safe form of the value; null becomes an empty string. */
    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&#39;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
