package dev.shaurmalib.meta.economy;

import dev.shaurmalib.meta.cosmetics.Rarity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Скриня: при відкритті випадає косметика випадкової рідкості з каталогу режиму (крім стартових і
 * прихованих). Гарант: якщо {@code pityEvery} відкриттів поспіль не було {@code pityRarity} або кращого —
 * наступне відкриття дає саме її («Гарант епічної 0/15»). Дублікат → уламки ({@code MetaMode#shardCurrency}).
 *
 * @param weights вага кожної рідкості (відсутня = 0)
 */
public record ChestDef(String id, String name, Rarity rarity, Map<Rarity, Integer> weights,
                       int pityEvery, Rarity pityRarity, String model) {

    public ChestDef {
        weights = Map.copyOf(new EnumMap<>(weights));
        if (model == null || model.isEmpty()) model = "minecraft:chest";
    }

    public static ChestDef of(String id, String name, Rarity rarity, int pityEvery, Rarity pityRarity, Object... rarityWeight) {
        Map<Rarity, Integer> w = new EnumMap<>(Rarity.class);
        for (int i = 0; i + 1 < rarityWeight.length; i += 2) w.put((Rarity) rarityWeight[i], ((Number) rarityWeight[i + 1]).intValue());
        return new ChestDef(id, name, rarity, w, pityEvery, pityRarity, "");
    }

    /** Предмет-модель скрині для меню (будь-який предмет гри: {@code minecraft:ender_chest}, власна модель режиму). */
    public ChestDef model(String itemId) { return new ChestDef(id, name, rarity, weights, pityEvery, pityRarity, itemId); }

    /** Ключ лічильника гаранту в статистиці профілю. */
    public String pityKey() { return "meta.pity." + id; }
}
