package dev.shaurmalib.common.offline;

import net.minecraft.world.entity.Entity;

/**
 * Подієві колбеки офлайн-присутності (план, §4.12). Усі методи мають
 * порожній {@code default}: режим переозначує лише потрібні.
 * Виняток у колбеку логується й не залишає тіло в напівстані.
 */
public interface OfflineListener {

    default void onAvatarSpawned(OfflineRecord record) {
    }

    /** @param killer той, хто вбив (може бути {@code null}: /kill, порожнеча, режимне правило). */
    default void onAvatarKilled(OfflineRecord record, Entity killer) {
    }

    default void onAvatarExpired(OfflineRecord record) {
    }

    default void onOwnerReturned(OfflineRecord record, OfflineReturn result) {
    }

    default void onScopeClosed(OfflineCloseReason reason, int removed) {
    }

    static OfflineListener none() {
        return new OfflineListener() {
        };
    }
}
