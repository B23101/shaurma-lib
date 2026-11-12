package dev.shaurmalib.common.offline;

/**
 * Причина закриття scope (план, §4.8). Лише для логів і колбека
 * {@link OfflineListener#onScopeClosed}: поведінка однакова — тіла
 * зникають безшумно, без дропу і без подій «вбито».
 */
public enum OfflineCloseReason {
    MATCH_ENDED,
    MODE_DEACTIVATED,
    SERVER_STOPPING,
    MANUAL
}
