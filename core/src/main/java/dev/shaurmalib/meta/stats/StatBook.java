package dev.shaurmalib.meta.stats;

import java.util.Map;
import java.util.TreeMap;

/**
 * Книга статистики гравця: плоскі лічильники {@code ключ → число}. Ключі ієрархічні через крапку
 * ({@code maniac.deathwood.downs}, {@code maniac.deathwood.trap.bear.placed}), тож будь-який режим
 * пише що завгодно, не змінюючи бібліотеку, а профіль/лідерборди читають через {@link StatDef}.
 * Відсутній ключ = 0.
 */
public final class StatBook {

    private final TreeMap<String, Long> values = new TreeMap<>();

    public long get(String key) {
        Long v = values.get(key);
        return v == null ? 0L : v;
    }

    public StatBook add(String key, long delta) {
        if (delta == 0 || key == null || key.isEmpty()) return this;
        values.merge(key, delta, Long::sum);
        return this;
    }

    /** Найкращий результат: зберігається більше з двох. */
    public StatBook max(String key, long candidate) {
        if (candidate > get(key)) values.put(key, candidate);
        return this;
    }

    public StatBook set(String key, long value) {
        if (value == 0) values.remove(key); else values.put(key, value);
        return this;
    }

    /** Сума за всіма ключами, що починаються з {@code prefix} і закінчуються на {@code suffix}. */
    public long sum(String prefix, String suffix) {
        long n = 0;
        for (Map.Entry<String, Long> e : values.tailMap(prefix, true).entrySet()) {
            if (!e.getKey().startsWith(prefix)) break;
            if (e.getKey().endsWith(suffix)) n += e.getValue();
        }
        return n;
    }

    /** Безпосередні під-ключі: для {@code "maniac."} → {deathwood, butcher, ...}. */
    public java.util.Set<String> children(String prefix) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String k : values.tailMap(prefix, true).keySet()) {
            if (!k.startsWith(prefix)) break;
            String rest = k.substring(prefix.length());
            int dot = rest.indexOf('.');
            out.add(dot < 0 ? rest : rest.substring(0, dot));
        }
        return out;
    }

    public Map<String, Long> view() { return java.util.Collections.unmodifiableMap(values); }

    public void putAll(Map<String, Long> other) { other.forEach(this::set); }

    public boolean isEmpty() { return values.isEmpty(); }
}
