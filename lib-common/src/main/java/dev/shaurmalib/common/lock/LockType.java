package dev.shaurmalib.common.lock;

/**
 * Незалежні категорії блокування дій гравця (план, п. 3.25). Узагальнює
 * те, що в оригіналі snipers_shaurma було розкидано по кожному
 * Phase-класу окремо (кожен сам блокував рух/атаку/взаємодію власним
 * ad-hoc способом — {@code MovementBlockHandler}, {@code BlockInteractHandler},
 * {@code PhantomModeWeaponGuard} кожен по-своєму).
 * <p>
 * Кожен тип лочиться/розлочується незалежно — гравець може мати
 * заблокований {@link #MOVEMENT} з однієї причини і {@link #ATTACK} з
 * іншої одночасно, і зняття однієї причини не має розлочити тип, якщо
 * лишається інша активна причина (дивись {@link InteractionLockRegistry}).
 */
public enum LockType {
    /** Рух гравця — заморозка через {@code PlayerFreezeService}-подібний примітив. */
    MOVEMENT,
    /** Атака (ЛКМ по сутності) — {@code AttackEntityEvent}. */
    ATTACK,
    /** Використання предмета (ПКМ у повітря/на предмет, use-tick). */
    USE_ITEM,
    /** Взаємодія з блоком (ПКМ/ЛКМ по блоку, розміщення/лам. блоків). */
    BLOCK_INTERACT,
    /** Надсилання повідомлень у чат. */
    CHAT
}
