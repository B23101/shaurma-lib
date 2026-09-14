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

    /**
     * Стартує камеру на {@code from} і починає рух до {@code to} за
     * {@code durationMs}. Якщо вже активна (рестарт кінематики поки
     * попередня ще не {@code stop()}) — тихо знімає стару camera entity й
     * старий запис у {@link CameraOwnershipRegistry} через
     * {@link CameraOwnershipRegistry#discardSilently} (БЕЗ виклику
     * resumeOwnership/падіння на гравця), щоб не було видимого проміжного
     * кадру між старою і новою сесією — той самий нюанс, що й у
     * {@code StationaryCameraController.enable()}.
     */
    public void start(Vec3 from, float fromYaw, float fromPitch, Vec3 to, float toYaw, float toPitch, int durationMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (token != null) {
            if (freeCamera != null) {
                freeCamera.despawn();
                freeCamera = null;
            }
            CameraOwnershipRegistry.discardSilently(token);
            token = null;
        }

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

    /**
     * Вимикає камеру і повертає керування гравцю (консюмер сам ховає
     * перехід, напр. fade-to-black).
     * <p>
     * ВАЖЛИВО (той самий антистрибок фікс, що й у
     * {@code StationaryCameraController.disableInternal} — джерело:
     * оригінальний {@code KitSelectCameraManager.disableInternal}):
     * {@link CameraOwnershipRegistry#release} викликається ПЕРШИМ — воно
     * саме перемикає {@code mc.setCameraEntity(...)} на наступного
     * власника/гравця — і лише ПІСЛЯ цього деспаунимо {@link #freeCamera}.
     * Зворотний порядок залишає мікрофрейм, де Minecraft сам автоматично
     * скидає камеру на гравця одразу після {@code discard()}, а вже потім
     * {@code release()} перебиває цей авто-скид власним викликом — гравець
     * встигає побачити зайвий кадр на власному тілі перед стрибком до
     * фактичного наступного власника.
     */
    public void stop() {
        if (token != null) {
            CameraOwnershipRegistry.release(token);
            token = null;
        }
        if (freeCamera != null) {
            freeCamera.despawn();
            freeCamera = null;
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
