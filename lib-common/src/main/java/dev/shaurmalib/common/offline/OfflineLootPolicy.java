package dev.shaurmalib.common.offline;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Що випадає, коли тіло вбито (план, §4.10). Розділено на {@link #collect} і
 * {@link #drop}, бо в режимі предмети на землі можуть бути власною сутністю,
 * а не ванільним {@link ItemEntity}.
 */
public interface OfflineLootPolicy {

    /**
     * Що дропати. Типово — усе зі знімка (копії, без порожніх).
     * Можна відфільтрувати або доповнити.
     */
    default List<ItemStack> collect(OfflineRecord record, List<ItemStack> snapshot) {
        List<ItemStack> out = new ArrayList<>(snapshot.size());
        for (ItemStack stack : snapshot) {
            if (!stack.isEmpty()) {
                out.add(stack.copy());
            }
        }
        return out;
    }

    /** Як саме створити предмети на землі. */
    void drop(ServerLevel level, Vec3 pos, List<ItemStack> items);

    /** Типова політика: усе зі знімка дропається як звичайні {@link ItemEntity}. */
    static OfflineLootPolicy standard() {
        return (level, pos, items) -> {
            for (ItemStack stack : items) {
                ItemEntity entity = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
            }
        };
    }
}
