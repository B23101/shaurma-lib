package dev.shaurmalib.anim.runtime;

import java.util.Locale;

/**
 * Шари рушія в порядку накладання (вищий пріоритет — пізніше, поверх). У кожному шарі
 * одночасно грає один кліп (попередній плавно перетікає в новий).
 *
 * <pre>
 *  LOCOMOTION  стояння / ходьба / біг / присід / стрибок (дерево змішування за швидкістю)
 *  IDLE        дрібні постійні рухи поверх локомоції (дихання, озирання)
 *  HOLD        тримання предмета (ліхтарик у витягнутій руці, ніж біля стегна) — зазвичай маска на руку
 *  ACTION      дія (аптечка, адреналін, ножиці, ремонт, удар) — зазвичай маска upper_body
 *  GESTURE     коротка реакція поверх дії (сіпання від удару)
 *  POSE        стан усього тіла (лежить, на гаку, несуть на плечі, лізе мотузкою)
 *  CINEMATIC   сцени (перемога, катсцени) — над усім
 * </pre>
 */
public enum Slot {
    LOCOMOTION(100), IDLE(200), HOLD(300), ACTION(500), GESTURE(600), POSE(800), CINEMATIC(1000);

    public final int priority;

    Slot(int priority) { this.priority = priority; }

    public static Slot parse(String s, Slot def) {
        if (s == null || s.isBlank()) return def;
        String n = s.trim().toUpperCase(Locale.ROOT);
        switch (n) {
            case "ITEM_ACTION": case "ITEM": case "USE": return ACTION;
            case "ITEM_HOLD": case "HELD": return HOLD;
            case "DEATH_LIE": case "STATE": case "FULL_BODY": return POSE;
            case "BASE": return LOCOMOTION;
            default:
                try { return valueOf(n); } catch (IllegalArgumentException e) { return def; }
        }
    }
}
