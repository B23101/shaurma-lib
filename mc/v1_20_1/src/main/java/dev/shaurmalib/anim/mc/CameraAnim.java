package dev.shaurmalib.anim.mc;

import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.runtime.CameraSolver;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Камера першої особи за анімованою головою: впав — камера падає разом з головою, лягає на спину —
 * дивиться вгору, нахилився — нахиляється. Вмикається вагою {@code camera}/{@code camera_rotation}
 * у кліпі, запиті або правилі; переходить плавно разом з fade кліпа. Мишка працює поверх (кути додаються).
 *
 * Ваніль-рівень: міксин {@code Camera.setup} TAIL ставить позицію й yaw/pitch, міксин
 * {@code GameRenderer.bobHurt} додає крен — однаково на Forge/Fabric/NeoForge.
 */
public final class CameraAnim {

    private CameraAnim() {}

    private static final CameraSolver.Frame FRAME = new CameraSolver.Frame();
    private static float roll;

    /** Результат для {@code Camera.setup}: null — камеру не чіпати. */
    public record Result(Vec3 position, float yRot, float xRot) {}

    public static Result apply(Entity entity, boolean detached, float partial, Vec3 camPos, float camYRot, float camXRot) {
        roll = 0;
        if (detached || !dev.shaurmalib.anim.ShaurmaAnim.headCamera() || !(entity instanceof LivingEntity e)) return null;
        if (McAnim.rigOf(e) == null || dev.shaurmalib.anim.ShaurmaAnim.find(e.getUUID()) == null) return null;
        Pose pose = McAnim.evaluate(e, partial, null, true);
        if (pose == null || !CameraSolver.solve(pose, FRAME)) return null;

        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        // сцена Blockbench: +X праворуч, +Y вгору, −Z вперед → світ за поворотом тіла
        double fx = -Mth.sin(bodyYaw), fz = Mth.cos(bodyYaw);   // вперед
        double rx = -Mth.cos(bodyYaw), rz = -Mth.sin(bodyYaw);  // праворуч
        double lx = FRAME.eyeX / 16.0, ly = FRAME.eyeY / 16.0, lz = FRAME.eyeZ / 16.0;
        Vec3 base = e.getPosition(partial);
        Vec3 target = new Vec3(base.x + rx * lx + fx * -lz, base.y + ly, base.z + rz * lx + fz * -lz);
        Vec3 pos = camPos.lerp(target, FRAME.positionWeight);

        float w = FRAME.rotationWeight;
        roll = FRAME.roll * w;
        return new Result(pos, camYRot + FRAME.yaw * w, camXRot + FRAME.pitch * w);
    }

    /** Крен поточного кадру (градуси) для {@code GameRenderer.bobHurt}. */
    public static float roll() { return roll; }
}
