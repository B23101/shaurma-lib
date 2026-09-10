package dev.shaurmalib.forge.camera;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Вільна камера, керована ВВОДОМ ГРАВЦЯ (WASD + рух миші для напрямку,
 * регульована швидкість польоту) — на відміну від решти контролерів у
 * пакеті {@code camera} (усі — scripted/сценарні: {@link StationaryCameraController},
 * {@link CinematicPathController}, {@link EntitySpectateController}),
 * цей керується напряму гравцем у реальному часі. У snipers_shaurma
 * такого механізму не було взагалі — усі камери там або нерухомі, або
 * рухаються за заздалегідь заданим сценарієм; це нова функціональність,
 * потрібна для інструментів на кшталт реплею матчу/огляду карти в
 * майбутньому режимі-другові, де людина сама вирішує, куди летіти.
 * <p>
 * Використовує ту саму {@link FreeCameraEntity} і той самий
 * {@link CameraOwnershipRegistry}, що й scripted-камери — під час
 * вільного польоту інші camera-системи (KIT_SELECT, EndGame-кінематика)
 * автоматично не можуть перехопити керування, доки {@link #disable()}
 * не звільнить власність.
 * <p>
 * Клавіші руху беруться з {@code Minecraft.options} (ті самі WASD/
 * Space/Shift, що гравець уже налаштував для ходьби — жодних нових
 * keybind-ів реєструвати не треба); напрямок погляду — сирий рух миші,
 * зчитаний через {@code InputEvent.MouseInputEvent}/{@code MouseButton}-
 * незалежний {@code delta}, тому працює навіть коли звичайний ігровий
 * рух гравця заблокований (гравець фізично "заморожений"
 * {@code InteractionLockRegistry}, а камера вільно літає окремо).
 */
@OnlyIn(Dist.CLIENT)
public final class FreeFlyCameraController implements CameraOwner {

    /** Швидкість польоту в блоках/секунду за замовчуванням. */
    public static final float DEFAULT_SPEED = 10f;
    /** Множник швидкості, коли затиснуто "прискорення" (те саме sprint-keybind, що й у гравця). */
    public static final float SPRINT_MULTIPLIER = 3f;
    private static final float MIN_SPEED = 1f;
    private static final float MAX_SPEED = 200f;
    /** Крок зміни швидкості колесом миші, у частках поточної швидкості. */
    private static final float SCROLL_SPEED_STEP = 0.15f;

    private FreeCameraEntity freeCamera;
    private CameraOwnershipRegistry.OwnershipToken token;
    private float speed = DEFAULT_SPEED;

    /** Вмикає вільний політ на заданій стартовій позиції/напрямку. Якщо вже активний — спершу вимикає попередній. */
    public void enable(float x, float y, float z, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (token != null) disableInternal(false);

        ClientLevel level = (ClientLevel) mc.level;
        freeCamera = new FreeCameraEntity(level, x, y, z, yaw, pitch);
        freeCamera.spawn();
        mc.setCameraEntity(freeCamera);
        token = CameraOwnershipRegistry.acquire(this);
        speed = DEFAULT_SPEED;
    }

    /** Вимикає вільний політ і повертає камеру гравцю (або наступному власнику в стеку, якщо такий є). */
    public void disable() {
        disableInternal(true);
    }

    private void disableInternal(boolean releaseOwnership) {
        if (freeCamera != null) {
            freeCamera.despawn();
            freeCamera = null;
        }
        if (releaseOwnership && token != null) {
            CameraOwnershipRegistry.release(token);
            token = null;
        }
    }

    public boolean isActive() {
        return token != null;
    }

    public float speed() {
        return speed;
    }

    /** Поточна позиція вільної камери, якщо активна — інакше {@code null}. */
    public Vec3 currentPosition() {
        return freeCamera != null ? freeCamera.position() : null;
    }

    /** Пряме встановлення швидкості польоту (затискається до [{@link #MIN_SPEED}, {@link #MAX_SPEED}]). */
    public void setSpeed(float blocksPerSecond) {
        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, blocksPerSecond));
    }

    /**
     * Колесо миші змінює швидкість польоту мультиплікативно (природніше
     * ніж адитивно — однаково зручно і на 1 блок/сек, і на 100 блок/сек).
     * Викликати з {@code InputEvent.MouseScrollingEvent} консюмера,
     * лише поки {@link #isActive()}.
     */
    public void adjustSpeedByScroll(double scrollDelta) {
        float factor = 1f + (float) scrollDelta * SCROLL_SPEED_STEP;
        setSpeed(speed * Math.max(0.1f, factor));
    }

    /**
     * Оновлює позицію камери на основі поточного стану клавіш руху й
     * напрямку погляду (напрямок погляду сам гравець контролює миш'ю —
     * ванільний Minecraft уже обертає {@code freeCamera} як camera
     * entity через звичайний {@code mouseX/mouseY} рух, це не потребує
     * окремої обробки тут; цей метод рухає лише ПОЗИЦІЮ вздовж поточного
     * напрямку погляду). Викликати щотік ({@code ClientTickEvent}), лише
     * поки {@link #isActive()}.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (token == null || freeCamera == null) return;
        if (!CameraOwnershipRegistry.isActiveOwner(this)) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        float forward = axisValue(mc.options.keyUp, mc.options.keyDown);
        float strafe = axisValue(mc.options.keyRight, mc.options.keyLeft);
        float vertical = axisValue(mc.options.keyJump, mc.options.keyShift);

        if (forward == 0f && strafe == 0f && vertical == 0f) return;

        float currentSpeed = speed;
        if (mc.options.keySprint.isDown()) {
            currentSpeed *= SPRINT_MULTIPLIER;
        }

        float yawRad = (float) Math.toRadians(freeCamera.getYRot());
        float distancePerTick = currentSpeed / 20f; // 20 тіків/сек

        double dx = (-Math.sin(yawRad) * forward + Math.cos(yawRad) * strafe) * distancePerTick;
        double dz = (Math.cos(yawRad) * forward + Math.sin(yawRad) * strafe) * distancePerTick;
        double dy = vertical * distancePerTick;

        double newX = freeCamera.getX() + dx;
        double newY = freeCamera.getY() + dy;
        double newZ = freeCamera.getZ() + dz;

        freeCamera.moveSmooth(newX, newY, newZ, freeCamera.getYRot(), freeCamera.getXRot());
    }

    private static float axisValue(KeyMapping positive, KeyMapping negative) {
        float value = 0f;
        if (positive.isDown()) value += 1f;
        if (negative.isDown()) value -= 1f;
        return value;
    }

    /**
     * {@link CameraOwnershipRegistry} викликає це, коли вільний політ
     * знову стає активним власником після того, як власник над ним
     * звільнив камеру. Відновлює {@code mc.setCameraEntity(...)} на
     * власну {@link FreeCameraEntity}, якщо вона все ще існує.
     */
    @Override
    public void resumeOwnership() {
        Minecraft mc = Minecraft.getInstance();
        if (freeCamera != null && mc.getCameraEntity() != freeCamera) {
            mc.setCameraEntity(freeCamera);
        }
    }
}
