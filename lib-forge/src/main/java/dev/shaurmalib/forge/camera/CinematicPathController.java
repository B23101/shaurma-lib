package dev.shaurmalib.forge.camera;

import dev.shaurmalib.common.camera.CameraPose;
import dev.shaurmalib.forge.camera.motion.CameraVecAdapter;
import dev.shaurmalib.forge.camera.motion.PathMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Готовий контролер "спавнить {@link FreeCameraEntity} на точці A, плавно
 * переміщує до точки B, завмирає" — узагальнення {@code EndGameCameraManager}
 * (оригінал: end-game реплей-камера статистики). Обгортає {@link PathMotion}
 * + {@link FreeCameraEntity} + {@link CameraOwnershipRegistry} в один
 * готовий до використання клас, щоб консюменту не треба було склеювати
 * ці три частини вручну для найпоширенішого сценарію "показати сцену від
 * точки A до точки B за N мілісекунд". Для складніших кінематик (idle-
 * петля + approach + possess, як у SD) консюмер компонує
 * {@code LoopPathMotion}/{@code BezierApproachMotion} самостійно тим
 * самим способом.
 * <p>
 * Не статичний (на відміну від оригінального {@code EndGameCameraManager})
 * — кожен консюмер (end-game статистика, вступна кінематика карти,
 * catscene нагородження) створює власний екземпляр.
 */
@OnlyIn(Dist.CLIENT)
public final class CinematicPathController implements CameraOwner {

    private FreeCameraEntity freeCamera;
    private CameraOwnershipRegistry.OwnershipToken token;
    private PathMotion motion;
    private long startTimeMs;
    private boolean moving;

    /** Стартує камеру на {@code from} і починає рух до {@code to} за {@code durationMs}. */
    public void start(Vec3 from, float fromYaw, float fromPitch, Vec3 to, float toYaw, float toPitch, int durationMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (token != null) stopInternal(false);

        CameraPose fromPose = CameraVecAdapter.pose(from, fromYaw, fromPitch);
        CameraPose toPose = CameraVecAdapter.pose(to, toYaw, toPitch);
        motion = new PathMotion(fromPose, toPose, durationMs);

        ClientLevel level = (ClientLevel) mc.level;
        freeCamera = new FreeCameraEntity(level, (float) from.x, (float) from.y, (float) from.z, fromYaw, fromPitch);
        freeCamera.spawn();
        mc.setCameraEntity(freeCamera);
        token = CameraOwnershipRegistry.acquire(this);
        moving = true;
        startTimeMs = System.currentTimeMillis();
    }

    /** Викликати щотік (клієнтський тік) поки рух активний. */
    public void tick() {
        if (!moving || freeCamera == null || motion == null) return;

        long elapsed = System.currentTimeMillis() - startTimeMs;
        CameraPose pose = motion.poseAt(elapsed);
        freeCamera.moveSmooth(pose.position().x(), pose.position().y(), pose.position().z(), pose.yaw(), pose.pitch());

        if (motion.isFinished(elapsed)) {
            moving = false; // рух завершено — камера завмирає на кінцевій позиції
        }
    }

    public boolean isMoving() { return moving; }
    public boolean isActive() { return token != null; }

    /** Вимикає камеру і повертає керування гравцю (консюмер сам ховає перехід, напр. fade-to-black). */
    public void stop() {
        stopInternal(true);
    }

    private void stopInternal(boolean releaseOwnership) {
        if (freeCamera != null) {
            freeCamera.despawn();
            freeCamera = null;
        }
        if (releaseOwnership && token != null) {
            CameraOwnershipRegistry.release(token);
            token = null;
        }
        moving = false;
    }

    @Override
    public void resumeOwnership() {
        Minecraft mc = Minecraft.getInstance();
        if (freeCamera != null && mc.getCameraEntity() != freeCamera) {
            mc.setCameraEntity(freeCamera);
        }
    }
}
