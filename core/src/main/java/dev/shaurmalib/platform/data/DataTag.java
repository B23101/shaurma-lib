package dev.shaurmalib.platform.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Версійно-незалежне дерево даних (замінює {@code CompoundTag}). Значення:
 * String, Integer, Long, Float, Double, Boolean, byte[], DataTag, List&lt;?&gt; цих типів.
 * Адаптер версії конвертує в NBT (1.20.1/1.21.1/26.x мають різні Tag-API та Codec-и).
 */
public final class DataTag {
    private final Map<String, Object> map = new LinkedHashMap<>();

    public DataTag putString(String k, String v) { return put(k, v); }
    public DataTag putInt(String k, int v) { return put(k, v); }
    public DataTag putLong(String k, long v) { return put(k, v); }
    public DataTag putFloat(String k, float v) { return put(k, v); }
    public DataTag putDouble(String k, double v) { return put(k, v); }
    public DataTag putBool(String k, boolean v) { return put(k, v); }
    public DataTag putBytes(String k, byte[] v) { return put(k, v.clone()); }
    public DataTag putTag(String k, DataTag v) { return put(k, v.copy()); }
    public DataTag putList(String k, List<?> v) { return put(k, copyList(v)); }

    private DataTag put(String k, Object v) {
        map.put(Objects.requireNonNull(k), Objects.requireNonNull(v));
        return this;
    }

    public Optional<String> getString(String k) { return get(k, String.class); }
    public Optional<Integer> getInt(String k) { return get(k, Integer.class); }
    public Optional<Long> getLong(String k) { return get(k, Long.class); }
    public Optional<Float> getFloat(String k) { return get(k, Float.class); }
    public Optional<Double> getDouble(String k) { return get(k, Double.class); }
    public Optional<Boolean> getBool(String k) { return get(k, Boolean.class); }
    public Optional<DataTag> getTag(String k) { return get(k, DataTag.class).map(DataTag::copy); }

    private <T> Optional<T> get(String k, Class<T> c) {
        Object v = map.get(k);
        return c.isInstance(v) ? Optional.of(c.cast(v)) : Optional.empty();
    }

    public boolean contains(String k) { return map.containsKey(k); }
    public boolean isEmpty() { return map.isEmpty(); }
    /** Незмінний вигляд для адаптерів (конвертація в NBT). */
    public Map<String, Object> entries() { return java.util.Collections.unmodifiableMap(map); }

    public DataTag copy() {
        DataTag c = new DataTag();
        for (Map.Entry<String, Object> e : map.entrySet()) c.map.put(e.getKey(), deepCopy(e.getValue()));
        return c;
    }

    private static Object deepCopy(Object v) {
        if (v instanceof DataTag t) return t.copy();
        if (v instanceof byte[] b) return b.clone();
        if (v instanceof List<?> l) return copyList(l);
        return v;
    }
    private static List<Object> copyList(List<?> l) {
        List<Object> out = new ArrayList<>(l.size());
        for (Object o : l) out.add(deepCopy(o));
        return out;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof DataTag t) || map.size() != t.map.size()) return false;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!valEq(e.getValue(), t.map.get(e.getKey()))) return false;
        }
        return true;
    }
    private static boolean valEq(Object a, Object b) {
        if (a instanceof byte[] x && b instanceof byte[] y) return java.util.Arrays.equals(x, y);
        return Objects.equals(a, b);
    }
    @Override public int hashCode() { return map.hashCode(); }
    @Override public String toString() { return "DataTag" + map; }
}
