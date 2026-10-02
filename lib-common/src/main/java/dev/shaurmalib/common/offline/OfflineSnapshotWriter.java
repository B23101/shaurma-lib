package dev.shaurmalib.common.offline;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Куди {@link OfflineSnapshotContributor} дописує дані режиму (план, §4.11).
 */
public interface OfflineSnapshotWriter {

    /** Додає предмет, який має випасти, коли тіло вб'ють (phantom-слоти, додаткові слоти). */
    void addItem(ItemStack stack);

    /** Додає довільний NBT режиму до знімка. */
    void putTag(String key, Tag tag);
}
