package dev.shaurmalib.forge.camera.motion;

import dev.shaurmalib.common.camera.CameraPose;
import dev.shaurmalib.common.camera.Vec3Like;
import net.minecraft.world.phys.Vec3;

/**
 * Конвертація {@code net.minecraft.world.phys.Vec3} у/з
 * {@link Vec3Like}/{@link CameraPose} — щоб {@code lib-common} лишався
 * без Minecraft-залежності (Architecture Sniffer, п. 2.1), а forge-
 * споживачам motion-класів (п. 3.15) не треба було писати цю конверсію
 * вручну щоразу.
 */
public final class CameraVecAdapter {
    private CameraVecAdapter() {}

    public static Vec3Like toVec3Like(Vec3 v) {
        return new Vec3Like(v.x, v.y, v.z);
    }

    public static Vec3 toMcVec3(Vec3Like v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    public static CameraPose pose(Vec3 pos, float yaw, float pitch) {
        return CameraPose.of(pos.x, pos.y, pos.z, yaw, pitch);
    }
}
