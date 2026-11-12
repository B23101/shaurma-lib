package dev.shaurmalib.forge.lock;

import dev.shaurmalib.common.lock.InteractionLockRegistry;
import dev.shaurmalib.common.lock.LockType;

/**
 * Публічний фасад {@link InteractionLockRegistry} для консюмерів
 * (план, п. 3.25) — доступний через
 * {@link dev.shaurmalib.forge.ShaurmaLib.Handle#interactionLockModule()}
 * після {@code withInteractionLock()} на {@code Builder}.
 * <p>
 * {@link InteractionLockRegistry} сам по собі вже статичний і доступний
 * без цього модуля, але, як і решта {@code withXxx()}-підключень лібу,
 * тут явний прапорець свідомого підключення потрібен для того, щоб
 * {@link InteractionLockHooks} (які завжди присутні в jar-і) мали сенс
 * лише тоді, коли консюмер справді має намір ними користуватись —
 * {@code isLocked()} все одно завжди повертає {@code false} для гравця,
 * якого ніхто не заблокував, тому фактичної шкоди від відсутності
 * підключення немає, але явний метод документує намір, як і решта модулів.
 */
public final class InteractionLockModule {

    public InteractionLockModule() {}

    public void lock(java.util.UUID playerId, LockType type, String reason) {
        InteractionLockRegistry.lock(playerId, type, reason);
    }

    public void unlock(java.util.UUID playerId, LockType type, String reason) {
        InteractionLockRegistry.unlock(playerId, type, reason);
    }

    public void unlockAll(java.util.UUID playerId, String reason) {
        InteractionLockRegistry.unlockAll(playerId, reason);
    }

    public void clearAll(java.util.UUID playerId) {
        InteractionLockRegistry.clearAll(playerId);
    }

    public boolean isLocked(java.util.UUID playerId, LockType type) {
        return InteractionLockRegistry.isLocked(playerId, type);
    }
}
