package dev.shaurmalib.anim.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Мінімальний JSON-парсер для анімацій: {@code Map<String,Object>}, {@code List<Object>},
 * {@code Double}, {@code String}, {@code Boolean}, {@code null}.
 *
 * Свій, а не Gson/SnakeYAML: рушій анімацій — чиста Java без залежностей, щоб один і той
 * самий код працював на всіх лоадерах і версіях. Допускає коментарі {@code //} та
 * {@code /* *}{@code /} і кінцеві коми (Blockbench їх не пише, але люди руками — так).
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < p.s.length()) throw p.err("зайві символи після JSON");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("очікувався JSON-об'єкт");
        return (Map<String, Object>) v;
    }

    private Object value() {
        if (i >= s.length()) throw err("неочікуваний кінець");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default:
                if (c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9')) return number();
                throw err("неочікуваний символ '" + c + "'");
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (peek('}')) { i++; return m; }
        while (true) {
            ws();
            if (peek('}')) { i++; return m; } // кінцева кома
            String k = string();
            ws();
            if (!peek(':')) throw err("очікувалось ':'");
            i++;
            ws();
            m.put(k, value());
            ws();
            if (peek(',')) { i++; continue; }
            if (peek('}')) { i++; return m; }
            throw err("очікувалось ',' або '}'");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek(']')) { i++; return l; }
        while (true) {
            ws();
            if (peek(']')) { i++; return l; }
            l.add(value());
            ws();
            if (peek(',')) { i++; continue; }
            if (peek(']')) { i++; return l; }
            throw err("очікувалось ',' або ']'");
        }
    }

    private String string() {
        if (!peek('"')) throw err("очікувався рядок");
        i++;
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw err("обрізаний \\u");
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
        throw err("незакритий рядок");
    }

    private Double number() {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
            else break;
        }
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw err("погане число '" + s.substring(start, i) + "'");
        }
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw err("очікувалось " + word);
        i += word.length();
    }

    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') { i++; continue; }
            if (c == '/' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '/') { while (i < s.length() && s.charAt(i) != '\n') i++; continue; }
                if (n == '*') {
                    int end = s.indexOf("*/", i + 2);
                    i = end < 0 ? s.length() : end + 2;
                    continue;
                }
            }
            break;
        }
    }

    private IllegalArgumentException err(String msg) {
        int line = 1;
        for (int k = 0; k < Math.min(i, s.length()); k++) if (s.charAt(k) == '\n') line++;
        return new IllegalArgumentException("JSON: " + msg + " (рядок " + line + ")");
    }

    // ── Зручні гетери для парсерів ────────────────────────────────────────

    public static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String str) {
            try { return Double.parseDouble(str.trim()); } catch (NumberFormatException e) { return def; }
        }
        if (o instanceof Boolean b) return b ? 1 : 0;
        return def;
    }

    public static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    public static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.doubleValue() != 0;
        if (o instanceof String str) return str.equalsIgnoreCase("true");
        return def;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }
}
