package dev.shaurmalib.meta.cosmetics;

/** Налаштовуваний параметр посадки предмета (у блоках або множник). */
public record ParamSpec(String key, String name, float min, float max, float def) {

    public float clamp(float v) {
        return Float.isFinite(v) ? Math.max(min, Math.min(max, v)) : def;
    }

    /** Відсоток від значення за замовчуванням — для підказок «розмір 120%». */
    public int percent(float v) {
        return def == 0 ? Math.round(v * 100) : Math.round(v / def * 100);
    }
}
