package dev.shaurmalib.forge.history;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NBT-кодек для {@code extra()}-мап {@link dev.shaurmalib.common.history.MatchRecord}/
 * {@link dev.shaurmalib.common.history.MatchPlayerEntry} (план, п. 3.20).
 * <p>
 * Оригінальний {@code MatchHistoryManager.serializeRecord(...)} писав
 * КОЖНЕ поле окремим {@code CompoundTag.putXxx(...)} викликом,
 * захардкодженим під конкретні snipers-поля ({@code capturesDone},
 * {@code contribPts}, {@code roundWins}, ...). Тут замість цього один
 * узагальнений кодек типізованої мапи — режим сам вирішує, які ключі
 * писати в {@code extra()}, кодек лише відповідає за те, щоб довільна
 * {@code Map<String,Object>} коректно пройшла крізь NBT і повернулась
 * назад із тим самим типом значення.
 * <p>
 * Підтримувані типи значень: {@link String}, {@link Integer},
 * {@link Long}, {@link Float}, {@link Double}, {@link Boolean}, і
 * {@code Map<String,Integer>} (для лічильників на кшталт weaponKills/
 * killsAgainst/deathsFrom з оригіналу). Кожне значення записується як
 * маленький {@code CompoundTag} з двома полями — {@code "t"} (однобуквений
 * тег типу) і {@code "v"} (саме значення), або, для вкладеної мапи,
 * {@code "v"} — це знову {@code CompoundTag} з {@code int}-полями.
 * Значення іншого типу серіалізується через {@code String.valueOf(...)}
 * з попередженням у лог, а не кидає виняток — одна невідома пара
 * ключ/значення не повинна зривати збереження всього матчу.
 */
public final class MatchExtraCodec {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/history");

    private MatchExtraCodec() {}

    public static CompoundTag write(Map<String, Object> extra) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, Object> e : extra.entrySet()) {
            CompoundTag entryTag = writeValue(e.getKey(), e.getValue());
            if (entryTag != null) out.put(e.getKey(), entryTag);
        }
        return out;
    }

    private static CompoundTag writeValue(String key, Object value) {
        CompoundTag t = new CompoundTag();
        if (value instanceof String s) {
            t.putString("t", "s");
            t.putString("v", s);
        } else if (value instanceof Integer i) {
            t.putString("t", "i");
            t.putInt("v", i);
        } else if (value instanceof Long l) {
            t.putString("t", "l");
            t.putLong("v", l);
        } else if (value instanceof Float f) {
            t.putString("t", "f");
            t.putFloat("v", f);
        } else if (value instanceof Double d) {
            t.putString("t", "d");
            t.putDouble("v", d);
        } else if (value instanceof Boolean b) {
            t.putString("t", "b");
            t.putBoolean("v", b);
        } else if (value instanceof Map<?, ?> map) {
            CompoundTag mapTag = new CompoundTag();
            for (Map.Entry<?, ?> me : map.entrySet()) {
                if (me.getKey() instanceof String mk && me.getValue() instanceof Integer mv) {
                    mapTag.putInt(mk, mv);
                } else {
                    LOGGER.warn("[MatchExtraCodec] Пропущено елемент вкладеної мапи '{}' у ключі '{}' "
                            + "— підтримуються лише Map<String,Integer>", me.getKey(), key);
                }
            }
            t.putString("t", "m");
            t.put("v", mapTag);
        } else if (value == null) {
            return null; // null-значення просто не пишемо, як відсутність ключа
        } else {
            LOGGER.warn("[MatchExtraCodec] Невідомий тип {} для ключа '{}', зберігаю як рядок",
                    value.getClass().getSimpleName(), key);
            t.putString("t", "s");
            t.putString("v", String.valueOf(value));
        }
        return t;
    }

    public static Map<String, Object> read(CompoundTag tag) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            if (!(tag.get(key) instanceof CompoundTag entryTag)) continue;
            Object value = readValue(key, entryTag);
            if (value != null) out.put(key, value);
        }
        return out;
    }

    private static Object readValue(String key, CompoundTag entryTag) {
        String type = entryTag.getString("t");
        try {
            return switch (type) {
                case "s" -> entryTag.getString("v");
                case "i" -> entryTag.getInt("v");
                case "l" -> entryTag.getLong("v");
                case "f" -> entryTag.getFloat("v");
                case "d" -> entryTag.getDouble("v");
                case "b" -> entryTag.getBoolean("v");
                case "m" -> readIntMap(entryTag.getCompound("v"));
                default -> {
                    LOGGER.warn("[MatchExtraCodec] Невідомий тип-тег '{}' для ключа '{}', пропускаю", type, key);
                    yield null;
                }
            };
        } catch (Exception e) {
            LOGGER.warn("[MatchExtraCodec] Помилка читання ключа '{}': {}", key, e.getMessage());
            return null;
        }
    }

    private static Map<String, Integer> readIntMap(CompoundTag mapTag) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String k : mapTag.getAllKeys()) {
            if (mapTag.get(k) instanceof Tag) {
                out.put(k, mapTag.getInt(k));
            }
        }
        return out;
    }
}
