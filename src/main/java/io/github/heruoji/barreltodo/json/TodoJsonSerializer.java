package io.github.heruoji.barreltodo.json;

import io.github.heruoji.barreltodo.exception.BarrelTodoException;
import io.github.heruoji.barreltodo.model.Priority;
import io.github.heruoji.barreltodo.model.Todo;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todo を BarrelKv の単一 String value に載せるための、最小限の JSON エンコード/デコード。
 * Todo の固定シェイプ(フラットなオブジェクト、ネストや配列なし)のみを扱う専用実装であり、
 * 汎用 JSON ライブラリの代替ではない。
 */
public final class TodoJsonSerializer {

    private TodoJsonSerializer() {
    }

    public static String encode(Todo todo) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendNumber(sb, "id", todo.id()).append(',');
        appendString(sb, "title", todo.title()).append(',');
        appendNullableString(sb, "description", todo.description()).append(',');
        appendBoolean(sb, "done", todo.done()).append(',');
        appendNullableString(sb, "dueDate", todo.dueDate() == null ? null : todo.dueDate().toString()).append(',');
        appendString(sb, "priority", todo.priority().name()).append(',');
        appendNumber(sb, "createdAt", todo.createdAt()).append(',');
        appendNumber(sb, "updatedAt", todo.updatedAt());
        sb.append('}');
        return sb.toString();
    }

    public static Todo decode(String json) {
        try {
            Map<String, Object> fields = new Cursor(json).parseObject();
            long id = requireLong(fields, "id");
            String title = requireString(fields, "title");
            String description = optionalString(fields, "description");
            boolean done = requireBoolean(fields, "done");
            String dueDateText = optionalString(fields, "dueDate");
            LocalDate dueDate = dueDateText == null ? null : LocalDate.parse(dueDateText);
            Priority priority = Priority.valueOf(requireString(fields, "priority"));
            long createdAt = requireLong(fields, "createdAt");
            long updatedAt = requireLong(fields, "updatedAt");
            return new Todo(id, title, description, done, dueDate, priority, createdAt, updatedAt);
        } catch (RuntimeException e) {
            throw new BarrelTodoException("Failed to decode Todo JSON: " + json, e);
        }
    }

    private static StringBuilder appendString(StringBuilder sb, String key, String value) {
        return sb.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    }

    private static StringBuilder appendNullableString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":");
        return value == null ? sb.append("null") : sb.append('"').append(escape(value)).append('"');
    }

    private static StringBuilder appendBoolean(StringBuilder sb, String key, boolean value) {
        return sb.append('"').append(key).append("\":").append(value);
    }

    private static StringBuilder appendNumber(StringBuilder sb, String key, long value) {
        return sb.append('"').append(key).append("\":").append(value);
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static long requireLong(Map<String, Object> fields, String key) {
        if (fields.get(key) instanceof Long value) {
            return value;
        }
        throw new IllegalArgumentException("Missing or non-numeric field: " + key);
    }

    private static boolean requireBoolean(Map<String, Object> fields, String key) {
        if (fields.get(key) instanceof Boolean value) {
            return value;
        }
        throw new IllegalArgumentException("Missing or non-boolean field: " + key);
    }

    private static String requireString(Map<String, Object> fields, String key) {
        if (fields.get(key) instanceof String value) {
            return value;
        }
        throw new IllegalArgumentException("Missing or non-string field: " + key);
    }

    private static String optionalString(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("Non-string field: " + key);
    }

    /** JSON構文の1回走査カーソル。Todoの固定シェイプ(フラットなオブジェクト)のみ対応する軽量パーサー。 */
    private static final class Cursor {
        private final String s;
        private int i;

        Cursor(String s) {
            this.s = s;
        }

        Map<String, Object> parseObject() {
            skipWhitespace();
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                result.put(key, parseValue());
                skipWhitespace();
                char c = next();
                if (c == ',') {
                    continue;
                }
                if (c == '}') {
                    break;
                }
                throw new IllegalArgumentException("Expected ',' or '}' at position " + (i - 1));
            }
            return result;
        }

        private Object parseValue() {
            char c = peek();
            if (c == '"') {
                return parseString();
            }
            if (c == 't') {
                expectLiteral("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expectLiteral("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expectLiteral("null");
                return null;
            }
            int start = i;
            if (peek() == '-') {
                i++;
            }
            while (hasNext() && Character.isDigit(peek())) {
                i++;
            }
            if (i == start || (i == start + 1 && s.charAt(start) == '-')) {
                throw new IllegalArgumentException("Expected a number at position " + start);
            }
            return Long.parseLong(s.substring(start, i));
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char escaped = next();
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        default -> throw new IllegalArgumentException("Unsupported escape: \\" + escaped);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private boolean hasNext() {
            return i < s.length();
        }

        private char peek() {
            if (!hasNext()) {
                throw new IllegalArgumentException("Unexpected end of JSON at position " + i);
            }
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + (i - 1));
            }
        }

        private void expectLiteral(String literal) {
            if (i + literal.length() > s.length() || !s.regionMatches(i, literal, 0, literal.length())) {
                throw new IllegalArgumentException("Expected literal '" + literal + "' at position " + i);
            }
            i += literal.length();
        }

        private void skipWhitespace() {
            while (hasNext() && Character.isWhitespace(peek())) {
                i++;
            }
        }
    }
}
