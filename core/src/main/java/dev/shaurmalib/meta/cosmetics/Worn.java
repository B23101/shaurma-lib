package dev.shaurmalib.meta.cosmetics;

import dev.shaurmalib.meta.io.J;

import java.util.LinkedHashMap;
import java.util.Map;

/** Одягнений предмет: що саме, якого кольору і як посаджений. Значення поза межами — обрізаються. */
public final class Worn {

    public String item;
    public int color;
    public final Map<String, Float> params = new LinkedHashMap<>();

    public Worn(String item, int color) {
        this.item = item;
        this.color = color & 0xFFFFFF;
    }

    public float param(CosmeticSlot slot, String key) {
        Float v = params.get(key);
        ParamSpec spec = slot.param(key);
        if (spec == null) return v == null ? 0f : v;
        return v == null ? spec.def() : spec.clamp(v);
    }

    public void setParam(CosmeticSlot slot, String key, float value) {
        ParamSpec spec = slot.param(key);
        if (spec == null) return;
        params.put(key, spec.clamp(value));
    }

    public Worn copy() {
        Worn w = new Worn(item, color);
        w.params.putAll(params);
        return w;
    }

    /** Обрізати все за межами слота (сервер робить це для кожного вхідного образу). */
    public void validate(CosmeticSlot slot) {
        color &= 0xFFFFFF;
        params.keySet().removeIf(k -> slot.param(k) == null);
        for (ParamSpec p : slot.params) params.put(p.key(), p.clamp(params.getOrDefault(p.key(), p.def())));
        if (slot == CosmeticSlot.PET) {
            float x = params.get("x"), z = params.get("z");
            double r = Math.hypot(x, z);
            if (r > CosmeticSlot.PET_MAX_RADIUS) {
                params.put("x", (float) (x * CosmeticSlot.PET_MAX_RADIUS / r));
                params.put("z", (float) (z * CosmeticSlot.PET_MAX_RADIUS / r));
            }
        }
    }

    public Map<String, Object> toJson() {
        Map<String, Object> p = new LinkedHashMap<>(params);
        return J.o("item", item, "color", color, "params", p);
    }

    public static Worn fromJson(Map<String, Object> o) {
        Worn w = new Worn(J.str(o, "item", ""), J.i(o, "color", 0xFFFFFF));
        for (Map.Entry<String, Object> e : J.obj(o, "params").entrySet()) {
            if (e.getValue() instanceof Number n) w.params.put(e.getKey(), n.floatValue());
        }
        return w;
    }

    @Override public boolean equals(Object o) {
        return o instanceof Worn w && w.item.equals(item) && w.color == color && w.params.equals(params);
    }

    @Override public int hashCode() { return item.hashCode() * 31 + color; }
}
