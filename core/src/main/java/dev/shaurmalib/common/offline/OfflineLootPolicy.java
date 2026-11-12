package dev.shaurmalib.common.offline;


import dev.shaurmalib.platform.Platform;
import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.ref.ItemRef;
import dev.shaurmalib.platform.ref.LevelRef;
import java.util.ArrayList;
import java.util.List;

/**
 * Що випадає, коли тіло вбито (план, §4.10). Розділено на {@link #collect} і
 * {@link #drop}, бо в режимі предмети на землі можуть бути власною сутністю,
 * а не ванільним {@code ItemEntity}.
 */
public interface OfflineLootPolicy {

    /**
     * Що дропати. Типово — усе зі знімка (копії, без порожніх).
     * Можна відфільтрувати або доповнити.
     */
    default List<ItemRef> collect(OfflineRecord record, List<ItemRef> snapshot) {
        List<ItemRef> out = new ArrayList<>(snapshot.size());
        for (ItemRef stack : snapshot) {
            if (!stack.isEmpty()) {
                out.add(stack.copy());
            }
        }
        return out;
    }

    /** Як саме створити предмети на землі. */
    void drop(LevelRef level, Vec3d pos, List<ItemRef> items);

    /** Типова політика: усе зі знімка дропається як звичайні {@code ItemEntity}. */
    static OfflineLootPolicy standard() {
        return (level, pos, items) -> Platform.loot().dropItems(level, pos, items);
    }
}
