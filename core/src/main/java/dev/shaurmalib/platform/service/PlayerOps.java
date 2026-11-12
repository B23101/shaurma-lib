package dev.shaurmalib.platform.service;

import dev.shaurmalib.platform.ref.PlayerRef;

/**
 * Стан і керування гравцем, що потрібні логіці ядра. Реалізує адаптер версії ({@code mc:*}):
 * імена методів ванілі (isSprinting, getFoodData, tickCount…) однакові на Fabric/Forge/NeoForge,
 * але змінюються між версіями — тому ядро бачить лише цей інтерфейс.
 */
public interface PlayerOps {
    boolean isCreativeOrSpectator(PlayerRef p);
    boolean isSprinting(PlayerRef p);
    void setSprinting(PlayerRef p, boolean sprinting);
    boolean isRiding(PlayerRef p);
    /** Лічильник тіків життя гравця ({@code tickCount}). */
    int tickCount(PlayerRef p);
    void setFood(PlayerRef p, int foodLevel, float saturation);
}
