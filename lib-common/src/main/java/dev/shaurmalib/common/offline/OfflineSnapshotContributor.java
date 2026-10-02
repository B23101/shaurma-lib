package dev.shaurmalib.common.offline;

import net.minecraft.server.level.ServerPlayer;

/**
 * Розширення знімка стану гравця даними режиму (план, §4.11).
 */
public interface OfflineSnapshotContributor {

    /** Викликається при виході гравця: дописати предмети/NBT режиму. */
    default void capture(ServerPlayer player, OfflineSnapshotWriter out) {
    }

    /**
     * Викликається, коли власник повернувся після смерті тіла: прибрати з
     * живого гравця те, що вже випало (додаткові слоти режиму). Ванільний
     * інвентар бібліотека очищає сама.
     */
    default void clearAfterKill(ServerPlayer player) {
    }

    static OfflineSnapshotContributor none() {
        return new OfflineSnapshotContributor() {
        };
    }
}
