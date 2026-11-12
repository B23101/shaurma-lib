package dev.shaurmalib.meta.economy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Рецепт крафту: деталі (+ необов'язково монети) → результат (зазвичай косметика).
 *
 * @param materials деталь → скільки треба
 * @param currency  валюта доплати ({@code null}/порожньо — без доплати)
 */
public record Recipe(String id, Reward output, Map<String, Integer> materials, String currency, long cost) {

    public Recipe {
        materials = Map.copyOf(new LinkedHashMap<>(materials));
        if (currency == null) currency = "";
    }

    public static Recipe of(String id, Reward output, String currency, long cost, Object... matAmount) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < matAmount.length; i += 2) m.put((String) matAmount[i], ((Number) matAmount[i + 1]).intValue());
        return new Recipe(id, output, m, currency, cost);
    }
}
