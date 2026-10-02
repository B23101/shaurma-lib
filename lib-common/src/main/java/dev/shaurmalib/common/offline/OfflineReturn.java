package dev.shaurmalib.common.offline;

/**
 * Що бачить режим, коли власник повернувся (план, §4.7).
 *
 * @param finalStatus стан тіла на момент повернення: {@link OfflineStatus#STANDING}
 *                    (гравець живий), {@link OfflineStatus#KILLED} або {@link OfflineStatus#EXPIRED}.
 * @param absentTicks скільки тіків гравця не було на сервері.
 */
public record OfflineReturn(OfflineStatus finalStatus, OfflineSpec spec,
                            double x, double y, double z, float yaw, float pitch,
                            String dimensionKey, long absentTicks) {

    /**
     * Будує результат із запису, для якого вже викликано
     * {@link OfflineRegistry#consumeReturn}.
     */
    public static OfflineReturn from(OfflineRecord record, long nowTick) {
        OfflineStatus status = record.statusAtReturn().orElse(record.status());
        return new OfflineReturn(status, record.spec(),
                record.x(), record.y(), record.z(), record.yaw(), record.pitch(),
                record.dimensionKey(), Math.max(0L, nowTick - record.loggedOutTick()));
    }
}
