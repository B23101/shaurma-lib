package dev.shaurmalib.common.offline;

import dev.shaurmalib.platform.data.DataTag;
import dev.shaurmalib.platform.ref.ItemRef;

/**
 * Куди {@link OfflineSnapshotContributor} дописує дані режиму (план, §4.11).
 */
public interface OfflineSnapshotWriter {

    /** Додає предмет, який має випасти, коли тіло вб'ють (phantom-слоти, додаткові слоти). */
    void addItem(ItemRef stack);

    /** Додає довільний NBT режиму до знімка. */
    void putTag(String key, DataTag tag);
}
