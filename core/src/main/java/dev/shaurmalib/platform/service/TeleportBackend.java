package dev.shaurmalib.platform.service;

import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.ref.PlayerRef;

/**
 * Версійна частина телепорту. Сигнатури ванілі різні:
 * 1.20.1 {@code teleportTo(level,x,y,z,yRot,xRot)}, 1.21.1 з {@code Set<RelativeMovement>},
 * 26.x — {@code teleport(TeleportTransition)}. Ядро (оркестрація, події) від цього не залежить.
 */
public interface TeleportBackend {
    void teleport(PlayerRef player, LevelRef target, double x, double y, double z, float yaw, float pitch);
    void ensureChunkLoaded(LevelRef level, double x, double z);
    void resetMotionAndFall(PlayerRef player);
    boolean hasPassengers(PlayerRef player);
    boolean isPassenger(PlayerRef player);
    void ejectPassengers(PlayerRef player);
}
