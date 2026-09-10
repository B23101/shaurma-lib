package dev.shaurmalib.forge.teleport;

/**
 * Причина телепортації (план, п. 3.2 — "API з reason-enum"). Документує
 * навіщо телепорт відбувся (в оригіналі snipers_shaurma це розкидано по
 * коментарях різних Phase-класів — TeleportUtil сам по собі нічого не
 * знав про причину виклику), і дозволяє на рівні лога/дебагу відстежити
 * джерело бага, якщо він виникне знову.
 * <p>
 * Список початковий і НЕ вичерпний — споживач може завжди передати
 * {@link #CUSTOM} і власний рядок-опис через
 * {@link TeleportedEvent#customReasonDetail()}.
 */
public enum TeleportReason {
    LOBBY_JOIN,
    RESPAWN,
    KIT_SELECT_ZONE,
    ZIPLINE,
    FREECAM_TOGGLE,
    SPECTATOR_TOGGLE,
    ADMIN_COMMAND,
    TRAINING_ROOM,
    MOVEMENT_LOCK_RETURN,
    ENDGAME_CAMERA,
    CUSTOM
}
