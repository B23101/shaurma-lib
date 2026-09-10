package dev.shaurmalib.forge.markers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.common.markers.BillboardOrientation;
import dev.shaurmalib.common.markers.BillboardOrientation.CameraFacingAxes;
import dev.shaurmalib.common.markers.BillboardOrientation.FixedYawAxes;
import dev.shaurmalib.common.markers.BillboardOrientation.Vec3Component;
import dev.shaurmalib.common.markers.MarkerVisibilityPolicy;
import dev.shaurmalib.common.overlay.Easing;
import net.minecraft.client.Camera;

import java.util.List;
import java.util.UUID;

/**
 * Рушій "коли і як показати мітку у світі" (план, п. 3.13) — узагальнення
 * циклу {@code for (мітка : усі_мітки) { culling; orientation; pushPose;
 * draw; popPose; }}, що дублювався незалежно в
 * {@code LeaderboardWorldRenderer.onRenderLevel} (fixed-yaw борд лідерборду)
 * і {@code C4MarkerRenderer.onRenderLevelStage} (camera-facing ромб). Обидва
 * оригінали робили ОДНАКОВІ 4 кроки різними рядками коду:
 * <ol>
 *   <li>дистанція до камери + cull, якщо задалеко;</li>
 *   <li>fade/LOD за дистанцією (у лідерборду — {@code appearAlpha} через
 *       {@link Easing#easeOutCubic}, у C4 — розмір ромба масштабується
 *       дистанцією);</li>
 *   <li>обчислення орієнтаційних осей ({@code right}/{@code forward} для
 *       fixed-yaw, {@code right}/{@code up} для camera-facing);</li>
 *   <li>{@code PoseStack.pushPose()/translate()/popPose()} навколо викликів
 *       {@link WorldBillboardPrimitives}.</li>
 * </ol>
 * Рушій виконує кроки 1, 3, 4 сам (чиста математика без домену); крок 2
 * (яку саме fade-криву застосувати, чи застосовувати взагалі) і "що саме
 * малювати" — консюмер, через {@link MarkerVisibilityPolicy} (окремий,
 * вже наявний контракт "кому показувати") і callback-функцію тут.
 * <p>
 * Рушій свідомо НЕ керує GL-станом (blend/depth/cull) — це лишається на
 * боці консюмера (як і в обох оригіналах: лідерборд рендериться з
 * depth-test, C4-мітка навмисно без нього, крізь стіни) — див. докстрінг
 * {@link WorldBillboardPrimitives#diamond}.
 *
 * @param <T> тип даних однієї мітки, повністю визначається консюмером.
 */
public final class MarkerRenderRules<T> {

    /** Мінімальна геометрична інформація про мітку, потрібна рушію для culling/orientation — консюмер або реалізує це на своєму DTO, або оборачує через {@link #of}. */
    public interface Located<T> {
        T data();

        double worldX();

        double worldY();

        double worldZ();
    }

    /** Мітка з фіксованим світовим yaw (борд лідерборду — стоїть на місці, не повертається до гравця). */
    public interface YawLocated<T> extends Located<T> {
        float yawDegrees();
    }

    @FunctionalInterface
    public interface FixedYawDrawer<T> {
        void draw(PoseStack ps, T data, FixedYawAxes axes, double distance, float alpha);
    }

    @FunctionalInterface
    public interface CameraFacingDrawer<T> {
        void draw(PoseStack ps, T data, CameraFacingAxes axes, double distance);
    }

    private final double cullDistance;
    private final double lodDistance;
    private final double backfaceThreshold;

    /**
     * @param cullDistance      мітки далі цієї відстані взагалі не малюються (лідерборд: 50.0, C4: без обмеження — передати {@code Double.MAX_VALUE}).
     * @param lodDistance       відстань, з якої консюмер може перейти на спрощений LOD-варіант малювання (лідерборд: 28.0); {@code Double.MAX_VALUE} — LOD не використовується.
     * @param backfaceThreshold порогове значення {@code dot(cameraForward, toTarget)} нижче якого мітка ззаду камери відкидається (лідерборд: {@code -0.2}); {@code -1.0} — вимкнути backface-culling (для camera-facing міток він не потрібен, вони завжди дивляться на камеру).
     */
    public MarkerRenderRules(double cullDistance, double lodDistance, double backfaceThreshold) {
        this.cullDistance = cullDistance;
        this.lodDistance = lodDistance;
        this.backfaceThreshold = backfaceThreshold;
    }

    /** Дефолт лідерборду (план 3.13, {@code LeaderboardWorldRenderer}): cull 50, LOD 28, backface -0.2. */
    public static <T> MarkerRenderRules<T> leaderboardDefaults() {
        return new MarkerRenderRules<>(50.0, 28.0, -0.2);
    }

    /** Дефолт camera-facing міток типу C4 (план 3.13, {@code C4MarkerRenderer}): без far-cull/LOD, backface не застосовується (мітка завжди лицем до камери). */
    public static <T> MarkerRenderRules<T> cameraFacingDefaults() {
        return new MarkerRenderRules<>(Double.MAX_VALUE, Double.MAX_VALUE, -1.0);
    }

    /**
     * {@code true}, якщо {@code distance} за порогом LOD — консюмер сам
     * вирішує, що саме спрощувати на цій дистанції (в оригіналі
     * лідерборду — це, наприклад, менш деталізований текст/без аватарів);
     * рушій лише повідомляє факт "далеко", не нав'язує спрощення.
     */
    public boolean isLod(double distance) {
        return distance > lodDistance;
    }

    /**
     * Виконує повний цикл fixed-yaw міток (лідерборд-стиль): для кожної
     * видимої (за {@link MarkerVisibilityPolicy}) мітки — дистанція, cull,
     * fade через {@link Easing#easeOutCubic}, backface-cull, побудова осей
     * {@link BillboardOrientation#fixedYaw}, {@code pushPose/translate},
     * викликати {@code drawer}, {@code popPose}. Alpha, що передається в
     * {@code drawer}, вже включає fade — консюмеру не треба рахувати його
     * самому (1:1 {@code OverlayStyle.easeOutCubic(board.appearAlpha)} з
     * оригіналу, де {@code appearAlpha} — власна анімація появи/зникнення
     * консюмера, що передається тут через {@code appearAlpha}-параметр
     * маркера, не рушія).
     *
     * @param markers      список міток консюмера цього кадру.
     * @param appearAlpha  функція "поточна власна анімація появи (0..1) цієї мітки" — рушій застосовує до неї {@link Easing#easeOutCubic}, консюмер лише тикає власний таймер (той самий {@code board.tick(dt)} з оригіналу лишається відповідальністю консюмера, до виклику цього методу).
     */
    public void renderFixedYaw(PoseStack ps, Camera camera, List<? extends YawLocated<T>> markers,
                                MarkerVisibilityPolicy<T> policy, UUID viewerPlayerId,
                                java.util.function.ToDoubleFunction<T> appearAlpha,
                                FixedYawDrawer<T> drawer) {
        double camX = camera.getPosition().x, camY = camera.getPosition().y, camZ = camera.getPosition().z;
        Vec3Component camForward = cameraForward(camera);

        for (YawLocated<T> marker : markers) {
            T data = marker.data();
            if (!policy.isVisible(data, viewerPlayerId)) continue;

            double dx = marker.worldX() - camX, dy = marker.worldY() - camY, dz = marker.worldZ() - camZ;
            double dist = BillboardOrientation.distance(dx, dy, dz);
            if (dist > cullDistance) continue;

            Vec3Component toTarget = new Vec3Component(dx, dy, dz).normalize();
            if (BillboardOrientation.isBehindCamera(camForward, toTarget, backfaceThreshold)) continue;

            float alpha = Easing.easeOutCubic((float) appearAlpha.applyAsDouble(data));
            if (alpha < 0.01f) continue;

            FixedYawAxes axes = BillboardOrientation.fixedYaw(marker.yawDegrees());

            ps.pushPose();
            ps.translate(dx, dy, dz);
            drawer.draw(ps, data, axes, dist, alpha);
            ps.popPose();
        }
    }

    /**
     * Виконує повний цикл camera-facing міток (C4-стиль): для кожної
     * видимої мітки — дистанція (з мінімальним cull на 0.3, той самий
     * "не малюй, якщо камера практично всередині мітки" інваріант
     * оригіналу), побудова осей {@link BillboardOrientation#cameraFacing},
     * {@code pushPose/translate}, викликати {@code drawer}, {@code popPose}.
     * Backface-culling тут НЕ застосовується (мітка сама завжди лицем до
     * камери — рушій узагалі не рахує {@code isBehindCamera} для цього
     * шляху, на відміну від {@link #renderFixedYaw}).
     */
    public void renderCameraFacing(PoseStack ps, Camera camera, List<? extends Located<T>> markers,
                                    MarkerVisibilityPolicy<T> policy, UUID viewerPlayerId,
                                    CameraFacingDrawer<T> drawer) {
        double camX = camera.getPosition().x, camY = camera.getPosition().y, camZ = camera.getPosition().z;
        Vec3Component camForward = cameraForward(camera);
        CameraFacingAxes axes = BillboardOrientation.cameraFacing(camForward);

        for (Located<T> marker : markers) {
            T data = marker.data();
            if (!policy.isVisible(data, viewerPlayerId)) continue;

            double dx = marker.worldX() - camX, dy = marker.worldY() - camY, dz = marker.worldZ() - camZ;
            double dist = BillboardOrientation.distance(dx, dy, dz);
            if (dist < 0.3) continue;

            ps.pushPose();
            ps.translate(dx, dy, dz);
            drawer.draw(ps, data, axes, dist);
            ps.popPose();
        }
    }

    private static Vec3Component cameraForward(Camera camera) {
        double yawRad = Math.toRadians(camera.getYRot());
        double pitchRad = Math.toRadians(camera.getXRot());
        double cosPitch = Math.cos(pitchRad);
        // Той самий базис, що Vec3.directionFromRotation(xRot, yRot) у ванілі
        // (перенесено як формула, а не залежність на net.minecraft.world.phys.Vec3,
        // щоб цей клас лишався єдиним місцем, де forge-Camera конвертується
        // у Forge-незалежний Vec3Component з common/markers).
        double x = -Math.sin(yawRad) * cosPitch;
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * cosPitch;
        return new Vec3Component(x, y, z).normalize();
    }

    /** Найпростіший {@link Located} з голими координатами, для консюмера, чий DTO ще не імплементує контракт напряму. */
    public static <T> Located<T> of(T data, double x, double y, double z) {
        return new Located<T>() {
            public T data() { return data; }
            public double worldX() { return x; }
            public double worldY() { return y; }
            public double worldZ() { return z; }
        };
    }

    /** Найпростіший {@link YawLocated} з голими координатами+yaw. */
    public static <T> YawLocated<T> of(T data, double x, double y, double z, float yawDegrees) {
        return new YawLocated<T>() {
            public T data() { return data; }
            public double worldX() { return x; }
            public double worldY() { return y; }
            public double worldZ() { return z; }
            public float yawDegrees() { return yawDegrees; }
        };
    }
}
