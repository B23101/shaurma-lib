package dev.shaurmalib.anim.molang;

import java.util.HashMap;
import java.util.Map;

/**
 * Значення змінних для Molang. Рушій оновлює стандартні {@code query.*} кожен кадр;
 * мод може класти свої {@code variable.*} (наприклад {@code variable.charge}).
 */
public class MolangContext {

    private final Map<String, Double> values = new HashMap<>();

    public double get(String key) {
        Double v = values.get(key);
        return v == null ? 0 : v;
    }

    public MolangContext set(String key, double value) {
        values.put(key, value);
        return this;
    }

    public void clear() { values.clear(); }
}
