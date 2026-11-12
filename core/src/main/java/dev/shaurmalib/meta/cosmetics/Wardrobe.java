package dev.shaurmalib.meta.cosmetics;

import dev.shaurmalib.meta.io.J;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Гардероб гравця: окремий образ на кожен «варіант» режиму (мафія — «Гравець»; маніяк — «Виживий» і «Маніяк»)
 * плюс {@link #PRESETS} збережених образів. Який варіант зараз на гравці — вирішує сервер режиму
 * ({@code MetaServer#setVariant}), а не клієнт.
 */
public final class Wardrobe {

    public static final int PRESETS = 3;

    private final Map<String, Look> looks = new LinkedHashMap<>();
    private final Look[] presets = new Look[PRESETS];

    public Look look(String variant) { return looks.computeIfAbsent(variant, k -> new Look()); }

    public void setLook(String variant, Look look) { looks.put(variant, look == null ? new Look() : look); }

    public Look preset(int slot) { return slot >= 0 && slot < PRESETS ? presets[slot] : null; }

    public void setPreset(int slot, Look look) { if (slot >= 0 && slot < PRESETS) presets[slot] = look; }

    public Map<String, Look> looks() { return java.util.Collections.unmodifiableMap(looks); }

    public Wardrobe copy() {
        Wardrobe w = new Wardrobe();
        looks.forEach((k, v) -> w.looks.put(k, v.copy()));
        for (int i = 0; i < PRESETS; i++) w.presets[i] = presets[i] == null ? null : presets[i].copy();
        return w;
    }

    public void validate(CosmeticCatalog catalog, java.util.function.Predicate<String> owned, List<String> variants) {
        looks.keySet().removeIf(k -> !variants.contains(k));
        for (Look l : looks.values()) l.validate(catalog, owned);
        for (Look p : presets) if (p != null) p.validate(catalog, null);
    }

    /** Зняти предмет з усіх образів (наприклад, якщо його прибрали з каталогу). */
    public void strip(String itemId) {
        for (Look l : looks.values()) l.view().entrySet().stream()
                .filter(e -> e.getValue().item.equals(itemId)).map(Map.Entry::getKey).toList().forEach(l::remove);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> l = new LinkedHashMap<>();
        looks.forEach((k, v) -> l.put(k, v.toJson()));
        List<Object> p = new ArrayList<>();
        for (Look x : presets) p.add(x == null ? null : x.toJson());
        return J.o("looks", l, "presets", p);
    }

    public static Wardrobe fromJson(Map<String, Object> o) {
        Wardrobe w = new Wardrobe();
        for (Map.Entry<String, Object> e : J.obj(o, "looks").entrySet()) w.looks.put(e.getKey(), Look.fromJson(J.asObj(e.getValue())));
        List<Object> p = J.arr(o, "presets");
        for (int i = 0; i < Math.min(PRESETS, p.size()); i++) w.presets[i] = p.get(i) instanceof Map ? Look.fromJson(J.asObj(p.get(i))) : null;
        return w;
    }
}
