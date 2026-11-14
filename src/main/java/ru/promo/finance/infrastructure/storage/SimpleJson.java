package ru.promo.finance.infrastructure.storage;

import java.util.*;

/**
 * Легковесный, автономный JSON парсер и сериализатор.
 * Не требует внешних зависимостей (Jackson/Gson).
 */
public final class SimpleJson {

    private SimpleJson() {}

    // ================= SERIALIZATION =================

    public static String stringify(Object obj) {
        return stringify(obj, 0);
    }

    private static String stringify(Object obj, int indentLevel) {
        if (obj == null) return "null";
        if (obj instanceof String s) {
            return quote(s);
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            int i = 0;
            String indent = "  ".repeat(indentLevel + 1);
            String closeIndent = "  ".repeat(indentLevel);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(indent)
                        .append(quote(String.valueOf(entry.getKey())))
                        .append(": ")
                        .append(stringify(entry.getValue(), indentLevel + 1));
                if (++i < map.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(closeIndent).append("}");
            return sb.toString();
        }
        if (obj instanceof Iterable<?> iter) {
            List<Object> list = new ArrayList<>();
            iter.forEach(list::add);
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            String indent = "  ".repeat(indentLevel + 1);
            String closeIndent = "  ".repeat(indentLevel);
            for (int i = 0; i < list.size(); i++) {
                sb.append(indent).append(stringify(list.get(i), indentLevel + 1));
                if (i < list.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(closeIndent).append("]");
            return sb.toString();
        }
        return quote(obj.toString());
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // ================= PARSING =================

    public static Object parse(String json) {
        if (json == null) return null;
        return new Parser(json.trim()).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object res = parse(json);
        if (res instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalArgumentException("Expected JSON object, got: " + (res == null ? "null" : res.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        Object res = parse(json);
        if (res instanceof List<?> l) {
            return (List<Object>) l;
        }
        throw new IllegalArgumentException("Expected JSON array, got: " + (res == null ? "null" : res.getClass().getSimpleName()));
    }

    private static final class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            skipWhitespace();
            if (pos >= src.length()) return '\0';
            return src.charAt(pos);
        }

        private char next() {
            skipWhitespace();
            if (pos >= src.length()) throw new IllegalArgumentException("Unexpected EOF at pos " + pos);
            return src.charAt(pos++);
        }

        Object parseValue() {
            char c = peek();
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            throw new IllegalArgumentException("Unexpected character '" + c + "' at pos " + pos);
        }

        Map<String, Object> parseObject() {
            match('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                next();
                return map;
            }
            while (true) {
                String key = parseString();
                match(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw new IllegalArgumentException("Expected ',' or '}' at pos " + (pos - 1));
            }
            return map;
        }

        List<Object> parseArray() {
            match('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                next();
                return list;
            }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw new IllegalArgumentException("Expected ',' or ']' at pos " + (pos - 1));
            }
            return list;
        }

        String parseString() {
            match('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalArgumentException("Unterminated escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) throw new IllegalArgumentException("Invalid unicode escape");
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string starting at pos " + pos);
        }

        Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean literal at pos " + pos);
        }

        Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null literal at pos " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E' || src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            String numStr = src.substring(start, pos);
            if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                return Double.parseDouble(numStr);
            }
            return Long.parseLong(numStr);
        }

        void match(char expected) {
            char c = next();
            if (c != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' but got '" + c + "' at pos " + (pos - 1));
            }
        }
    }
}
