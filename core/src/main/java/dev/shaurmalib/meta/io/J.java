package dev.shaurmalib.meta.io;

import dev.shaurmalib.anim.io.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON для мета-двигуна без залежностей: читання — спільний парсер ядра ({@link Json}),
 * запис — тут. Дерево: {@code Map<String,Object>}, {@code List<Object>}, {@code Number}, {@code String},
 * {@code Boolean}, {@code null}. Хелпери {@code num/str/obj/arr} терплять відсутні ключі й чужі типи —
 * старий або пошкоджений файл профілю не валить сервер, а дає значення за замовчуванням.
 */
public final class J {

    private J() {}

    public static Map<String, Object> parse(String text) {
        if (text == null || text.isBlank()) return new LinkedHashMap<>();
        return Json.parseObject(text);
    }

    // ── Читання ─────────────────────────────────────────────────────────

    public static long num(Map<String, Object> o, String k, long def) {
        Object v = o == null ? null : o.get(k);
        return v instanceof Number n ? (long) Math.floor(n.doubleValue() + 1e-9) : def;
    }

    public static int i(Map<String, Object> o, String k, int def) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, num(o, k, def)));
    }

    public static float f(Map<String, Object> o, String k, float def) {
        Object v = o == null ? null : o.get(k);
        return v instanceof Number n && Double.isFinite(n.doubleValue()) ? n.floatValue() : def;
    }

    public static boolean bool(Map<String, Object> o, String k, boolean def) {
        Object v = o == null ? null : o.get(k);
        return v instanceof Boolean b ? b : def;
    }

    public static String str(Map<String, Object> o, String k, String def) {
        Object v = o == null ? null : o.get(k);
        return v instanceof String s ? s : def;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> o, String k) {
        Object v = o == null ? null : o.get(k);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Map<String, Object> o, String k) {
        Object v = o == null ? null : o.get(k);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObj(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    // ── Запис ───────────────────────────────────────────────────────────

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder(256);
        write(sb, v, 0, false);
        return sb.toString();
    }

    public static String pretty(Object v) {
        StringBuilder sb = new StringBuilder(512);
        write(sb, v, 0, true);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int depth, boolean pretty) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String s) { quote(sb, s); return; }
        if (v instanceof Boolean b) { sb.append(b); return; }
        if (v instanceof Float || v instanceof Double) {
            double d = ((Number) v).doubleValue();
            if (!Double.isFinite(d)) { sb.append('0'); return; }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append((float) d == d ? Float.toString((float) d) : Double.toString(d));
            return;
        }
        if (v instanceof Number n) { sb.append(n.longValue()); return; }
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                nl(sb, depth + 1, pretty);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                write(sb, e.getValue(), depth + 1, pretty);
                if (it.hasNext()) sb.append(',');
            }
            nl(sb, depth, pretty);
            sb.append('}');
            return;
        }
        if (v instanceof Collection<?> c) {
            if (c.isEmpty()) { sb.append("[]"); return; }
            sb.append('[');
            Iterator<?> it = c.iterator();
            boolean flat = c.stream().allMatch(x -> x instanceof Number || x instanceof String);
            while (it.hasNext()) {
                if (!flat) nl(sb, depth + 1, pretty);
                write(sb, it.next(), depth + 1, pretty);
                if (it.hasNext()) sb.append(flat && pretty ? ", " : ",");
            }
            if (!flat) nl(sb, depth, pretty);
            sb.append(']');
            return;
        }
        if (v instanceof int[] a) {
            List<Object> l = new ArrayList<>();
            for (int x : a) l.add(x);
            write(sb, l, depth, pretty);
            return;
        }
        quote(sb, v.toString());
    }

    private static void nl(StringBuilder sb, int depth, boolean pretty) {
        if (!pretty) return;
        sb.append('\n');
        for (int i = 0; i < depth; i++) sb.append("  ");
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** Зручний конструктор впорядкованого об'єкта: {@code J.o("a", 1, "b", "x")}. */
    public static Map<String, Object> o(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
