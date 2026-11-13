package dev.shaurmalib.anim.clip;

import java.util.List;
import java.util.Locale;

/**
 * «Стан тримання» з самого кліпа (ставиться в Blockbench, без Java-коду): поки гравець тримає предмет —
 * кліп грає сам (шар кліпа, за замовчуванням HOLD, з його маскою), і зникає з кросфейдом, коли предмет прибрано.
 *
 * <pre>
 * "hold": { "item": "maniacmod:fuel_canister", "hand": "main", "yield_swing": true, "yield_use": true }
 * "hold": "maniacmod:fuel_canister"                     // коротко
 * "hold": { "item": ["maniacmod:fuel", "maniacmod:fuel_full"] }
 * </pre>
 *
 * @param items      id предметів ({@code ns:path}); будь-який з них вмикає кліп
 * @param hand       {@code main} (основна рука), {@code off} (друга), {@code any}
 * @param priority   з кількох станів одного шару грає вищий
 * @param yieldSwing під час удару/змаху поза плавно поступається ванільній руці (видно удар з предметом)
 * @param yieldUse   під час використання (пити, їсти, натягувати) — те саме
 */
public record HoldSpec(List<String> items, String hand, int priority, boolean yieldSwing, boolean yieldUse) {

    public HoldSpec {
        items = List.copyOf(items);
        hand = hand == null ? "main" : hand.trim().toLowerCase(Locale.ROOT);
    }

    public boolean matches(String mainHandItem, String offHandItem) {
        boolean main = !"off".equals(hand) && mainHandItem != null && items.contains(mainHandItem);
        boolean off = ("off".equals(hand) || "any".equals(hand)) && offHandItem != null && items.contains(offHandItem);
        return main || off;
    }
}
