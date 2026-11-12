package dev.shaurmalib.meta.cosmetics;

import dev.shaurmalib.meta.io.J;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Образ: по одному предмету на слот. */
public final class Look {

    private final EnumMap<CosmeticSlot, Worn> worn = new EnumMap<>(CosmeticSlot.class);

    public Worn get(CosmeticSlot slot) { return worn.get(slot); }

    public boolean has(CosmeticSlot slot) { return worn.containsKey(slot); }

    public boolean wears(String itemId) {
        for (Worn w : worn.values()) if (w.item.equals(itemId)) return true;
        return false;
    }

    public void put(CosmeticSlot slot, Worn w) {
        if (w == null) worn.remove(slot); else worn.put(slot, w);
    }

    public void remove(CosmeticSlot slot) { worn.remove(slot); }

    public void clear() { worn.clear(); }

    public boolean isEmpty() { return worn.isEmpty(); }

    public Map<CosmeticSlot, Worn> view() { return java.util.Collections.unmodifiableMap(worn); }

    public Look copy() {
        Look l = new Look();
        worn.forEach((k, v) -> l.worn.put(k, v.copy()));
        return l;
    }

    /** Лише один слот — для мініатюр карток у гардеробі. */
    public Look only(CosmeticSlot slot, Worn w) {
        Look l = new Look();
        if (w != null) l.worn.put(slot, w.copy());
        return l;
    }

    /**
     * Сервер: прибрати все, чого гравець не має, не того слота чи забороненого кольору; обрізати параметри.
     * @param owned перевірка володіння (null — не перевіряти, для пресетів у прев'ю)
     */
    public void validate(CosmeticCatalog catalog, java.util.function.Predicate<String> owned) {
        worn.entrySet().removeIf(e -> {
            CosmeticDef def = catalog.get(e.getValue().item);
            if (def == null || def.slot() != e.getKey()) return true;
            if (owned != null && !owned.test(def.id())) return true;
            if (!def.allowsColor(e.getValue().color)) e.getValue().color = def.defaultColor();
            e.getValue().validate(e.getKey());
            return false;
        });
    }

    public Map<String, Object> toJson() {
        Map<String, Object> o = new LinkedHashMap<>();
        worn.forEach((k, v) -> o.put(k.name(), v.toJson()));
        return o;
    }

    public static Look fromJson(Map<String, Object> o) {
        Look l = new Look();
        if (o == null) return l;
        for (Map.Entry<String, Object> e : o.entrySet()) {
            CosmeticSlot slot = CosmeticSlot.byName(e.getKey());
            if (slot != null && e.getValue() instanceof Map) l.worn.put(slot, Worn.fromJson(J.asObj(e.getValue())));
        }
        return l;
    }

    @Override public boolean equals(Object o) { return o instanceof Look l && l.worn.equals(worn); }

    @Override public int hashCode() { return worn.hashCode(); }
}
