package lab;

import java.util.*;

/** Выделяет строки, слова, числа и знаки, не разделяя содержимое кавычек. */
public final class Tokenizer {
    public record Token(String text, boolean quoted) { }

    /** Два апострофа внутри строки обозначают один апостроф. */
    public static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '\'') {
                StringBuilder value = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < sql.length()) {
                    c = sql.charAt(i++);
                    if (c == '\'') {
                        if (i < sql.length() && sql.charAt(i) == '\'') {
                            value.append('\'');
                            i++;
                        } else {
                            closed = true;
                            break;
                        }
                    } else {
                        value.append(c);
                    }
                }
                if (!closed) {
                    throw new DbException("Не закрыта строка");
                }
                tokens.add(new Token(value.toString(), true));
            } else if ("(),;*=<>!".indexOf(c) >= 0) {
                String value = String.valueOf(c);
                i++;
                if (i < sql.length() && "<>=!".indexOf(c) >= 0 && "=>".indexOf(sql.charAt(i)) >= 0) {
                    value += sql.charAt(i++);
                }
                tokens.add(new Token(value, false));
            } else {
                int start = i;
                while (i < sql.length() && !Character.isWhitespace(sql.charAt(i)) && "'(),;*=<>!".indexOf(sql.charAt(i)) < 0) {
                    i++;
                }
                tokens.add(new Token(sql.substring(start, i), false));
            }
        }
        return tokens;
    }
}
