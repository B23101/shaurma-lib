package dev.shaurmalib.common.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Типізована обгортка над сирим SnakeYAML {@code Map<?,?>}.
 * <p>
 * Узагальнення патерну, який у snipers_shaurma вже існує локально в
 * {@code core.config.ItemsConfig.ItemEntry} ({@code getInt}/{@code getLong}/
 * {@code getDouble} з дефолтним значенням над {@code Map<String,Object> properties})
 * — тут той самий підхід піднятий на бібліотечний рівень, щоб кожен новий
 * конфіг-клас (у snipers і в майбутньому maniac) не писав свою копію цих
 * трьох методів заново.
 * <p>
 * Не кидає виключень при відсутньому/некоректному типі ключа — завжди
 * повертає дефолт, як і оригінальний {@code ItemEntry}. Для випадків, коли
 * відсутність поля має бути помилкою конфігурації, викликач сам порівнює
 * результат з дефолтом і кидає {@link ConfigException}.
 */
public final class YamlConfigSection {

    private final Map<String, Object> raw;

    public YamlConfigSection(Map<String, Object> raw) {
        this.raw = raw != null ? raw : new LinkedHashMap<>();
    }

    /** Порожня секція — зручно як безпечний дефолт замість {@code null}. */
    public static YamlConfigSection empty() {
        return new YamlConfigSection(new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public static YamlConfigSection ofNested(Map<String, Object> root, String key) {
        Object v = root.get(key);
        return v instanceof Map ? new YamlConfigSection((Map<String, Object>) v) : empty();
    }

    public boolean has(String key) {
        return raw.containsKey(key);
    }

    public Object getRaw(String key) {
        return raw.get(key);
    }

    public Map<String, Object> asMap() {
        return raw;
    }

    public int getInt(String key, int def) {
        Object val = raw.get(key);
        return val instanceof Number ? ((Number) val).intValue() : def;
    }

    public long getLong(String key, long def) {
        Object val = raw.get(key);
        return val instanceof Number ? ((Number) val).longValue() : def;
    }

    public double getDouble(String key, double def) {
        Object val = raw.get(key);
        return val instanceof Number ? ((Number) val).doubleValue() : def;
    }

    public boolean getBool(String key, boolean def) {
        Object val = raw.get(key);
        return val instanceof Boolean ? (Boolean) val : def;
    }

    public String getString(String key, String def) {
        Object val = raw.get(key);
        return val instanceof String ? (String) val : def;
    }

    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object val = raw.get(key);
        return val instanceof List ? (List<Object>) val : List.of();
    }

    public YamlConfigSection getSection(String key) {
        return ofNested(raw, key);
    }
}
