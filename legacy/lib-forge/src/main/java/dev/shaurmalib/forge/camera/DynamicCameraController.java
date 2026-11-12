package dev.shaurmalib.forge.camera;

import dev.shaurmalib.common.camera.DynamicCameraProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Фонова кінематографічна камера — узагальнення {@code DynamicCameraHandler.java}
 * (367 рядків в оригіналі): дихання, bob при ходьбі, tremor-шум, jitter,
 * landing-trauma (тряска при приземленні), FOV-шок. Активний ЗАВЖДИ, на
 * відміну від {@link dev.shaurmalib.forge.item.ItemCameraController}, який
 * активний лише під час use-анімації конкретного предмета.
 * <p>
 * Amплітуди/швидкості — параметризовані через {@link DynamicCameraProfile}
 * (дані з {@code style.yml}, модуль 3.3), а не константи в коді, як було
 * в оригіналі — режим-друга може захотіти інше "відчуття" камери без
 * форку класу.
 * <p>
 * Вимикається за замовчуванням (Registration Gateway: нічого не
 * активується без явного {@code .withDynamicCamera(...)} у {@code ShaurmaLib.Builder}).
 */
public final class DynamicCameraController {

    private static volatile DynamicCameraProfile activeProfile = null;

    private static float breathPhase;
    private static float bobPhase;
    private static float tremorSeed;
    private static float landingTrauma;
    private static float fovShock;

    private DynamicCameraController() {}

    /** Викликається з {@code ShaurmaLib.Builder.withDynamicCamera(profile)} під час init споживача. */
    public static void enable(DynamicCameraProfile profile) {
        activeProfile = profile;
    }

    public static void disable() {
        activeProfile = null;
    }

    /** Викликається зовнішнім джерелом події приземлення (мод сам вирішує коли гравець "приземлився"). */
    public static void triggerLandingTrauma() {
        if (activeProfile != null) {
            landingTrauma = activeProfile.landingTraumaMaxDeg();
        }
    }

    public static void triggerFovShock() {
        if (activeProfile != null) {
            fovShock = activeProfile.fovShockMaxDeg();
        }
    }

    public static void applyIfEnabled(ViewportEvent.ComputeCameraAngles event, float deltaSeconds) {
        DynamicCameraProfile profile = activeProfile;
        if (profile == null) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        breathPhase += deltaSeconds * profile.breathSpeed();
        bobPhase += deltaSeconds * profile.bobSpeed() * (float) player.getDeltaMovement().horizontalDistance() * 4f;
        tremorSeed += deltaSeconds;

        float breath = (float) Math.sin(breathPhase) * profile.breathAmplitudeDeg();
        float bobYaw = (float) Math.sin(bobPhase) * profile.bobAmplitudeX();
        float bobPitch = (float) Math.abs(Math.cos(bobPhase)) * profile.bobAmplitudeY();
        float tremor = pseudoNoise(tremorSeed) * profile.tremorAmplitudeDeg();
        float jitter = pseudoNoise(tremorSeed * 7.31f) * profile.jitterAmplitudeDeg();

        landingTrauma = decay(landingTrauma, profile.landingTraumaDecaySpeed(), deltaSeconds);
        fovShock = decay(fovShock, profile.fovShockDecaySpeed(), deltaSeconds);

        float totalYaw = bobYaw + tremor + jitter;
        float totalPitch = breath + bobPitch + landingTrauma;

        event.setYaw(event.getYaw() + totalYaw);
        event.setPitch(event.getPitch() + totalPitch);
        // FOV-шок застосовується окремо на FOV-модифікатор рушієм, що
        // читає getCurrentFovShock() — ComputeCameraAngles не несе FOV.
    }

    public static float getCurrentFovShockDeg() {
        return fovShock;
    }

    private static float decay(float value, float decaySpeed, float deltaSeconds) {
        if (value <= 0f) return 0f;
        float next = value - decaySpeed * deltaSeconds;
        return Math.max(0f, next);
    }

    /** Прості, стабільні детерміновані "шумові" коливання — без залежності від java.util.Random стану. */
    private static float pseudoNoise(float t) {
        return (float) (Math.sin(t * 12.9898f) * 43758.5453f % 1.0) * 2f - 1f;
    }
}
