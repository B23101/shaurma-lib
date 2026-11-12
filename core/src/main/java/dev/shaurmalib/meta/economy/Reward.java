package dev.shaurmalib.meta.economy;

import dev.shaurmalib.meta.io.J;

import java.util.Map;

/**
 * Одна видача: монети, предмет косметики, деталі для крафту або очки покращень.
 * Спільна мова для доріжки нагород, крафту, магазину й адмін-команд.
 */
public record Reward(Type type, String id, long amount) {

    public enum Type { CURRENCY, COSMETIC, MATERIAL, UPGRADE_POINTS, CHEST }

    public static Reward coins(String currency, long amount) { return new Reward(Type.CURRENCY, currency, amount); }
    public static Reward cosmetic(String id) { return new Reward(Type.COSMETIC, id, 1); }
    public static Reward material(String id, long amount) { return new Reward(Type.MATERIAL, id, amount); }
    public static Reward points(long amount) { return new Reward(Type.UPGRADE_POINTS, "", amount); }
    public static Reward chest(String id, long amount) { return new Reward(Type.CHEST, id, amount); }

    public Map<String, Object> toJson() { return J.o("type", type.name(), "id", id, "amount", amount); }

    public static Reward fromJson(Map<String, Object> o) {
        Type t;
        try { t = Type.valueOf(J.str(o, "type", "CURRENCY")); } catch (IllegalArgumentException e) { t = Type.CURRENCY; }
        return new Reward(t, J.str(o, "id", ""), J.num(o, "amount", 0));
    }
}
