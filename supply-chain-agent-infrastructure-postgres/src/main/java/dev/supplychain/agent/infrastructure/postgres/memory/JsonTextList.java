package dev.supplychain.agent.infrastructure.postgres.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Minimal JSON string-array codec for the TEXT_LIST column; accepts no non-string JSON values. */
final class JsonTextList {
    private JsonTextList() { }
    static String encode(List<String> values) {
        Objects.requireNonNull(values, "values");
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append('"');
            String value = Objects.requireNonNull(values.get(i), "text-list item");
            for (int j = 0; j < value.length(); j++) {
                char ch = value.charAt(j);
                switch (ch) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> { if (ch < 0x20) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) ch)); else out.append(ch); }
                }
            }
            out.append('"');
        }
        return out.append(']').toString();
    }
    static List<String> decode(String json) {
        Objects.requireNonNull(json, "json");
        int[] index = {0};
        skip(json, index);
        expect(json, index, '[');
        List<String> values = new ArrayList<>();
        skip(json, index);
        if (peek(json, index) == ']') { index[0]++; ensureEnd(json, index); return List.of(); }
        while (true) {
            skip(json, index);
            expect(json, index, '"');
            StringBuilder value = new StringBuilder();
            while (index[0] < json.length()) {
                char ch = json.charAt(index[0]++);
                if (ch == '"') break;
                if (ch == '\\') {
                    if (index[0] >= json.length()) throw new IllegalArgumentException("invalid JSON escape");
                    char escaped = json.charAt(index[0]++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b'); case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n'); case 'r' -> value.append('\r'); case 't' -> value.append('\t');
                        case 'u' -> {
                            if (index[0] + 4 > json.length()) throw new IllegalArgumentException("invalid JSON unicode escape");
                            value.append((char) Integer.parseInt(json.substring(index[0], index[0] + 4), 16)); index[0] += 4;
                        }
                        default -> throw new IllegalArgumentException("invalid JSON escape: " + escaped);
                    }
                } else value.append(ch);
            }
            values.add(value.toString());
            skip(json, index);
            char delimiter = peek(json, index);
            if (delimiter == ']') { index[0]++; break; }
            expect(json, index, ',');
        }
        ensureEnd(json, index);
        return List.copyOf(values);
    }
    private static void skip(String json, int[] i) { while (i[0] < json.length() && Character.isWhitespace(json.charAt(i[0]))) i[0]++; }
    private static char peek(String json, int[] i) { if (i[0] >= json.length()) throw new IllegalArgumentException("unexpected end of JSON"); return json.charAt(i[0]); }
    private static void expect(String json, int[] i, char ch) { if (peek(json, i) != ch) throw new IllegalArgumentException("invalid JSON array"); i[0]++; }
    private static void ensureEnd(String json, int[] i) { skip(json, i); if (i[0] != json.length()) throw new IllegalArgumentException("trailing JSON content"); }
}
