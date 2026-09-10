package dev.shaurmalib.forge.item;

import dev.shaurmalib.common.camera.DynamicCameraProfile;
import dev.shaurmalib.common.item.ItemCameraSessionState;
import dev.shaurmalib.common.item.ItemCameraTrack;
import dev.shaurmalib.forge.camera.DynamicCameraController;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Центральний клієнтський сервіс камери анімованих предметів — узагальнення
 * {@code DeminingCameraHandler.java} (оригінал мав ОКРЕМИЙ такий клас на
 * КОЖЕН новий предмет із власною камерою; тут — ОДНА підписка на
 * {@code ViewportEvent.ComputeCameraAngles} для всієї бібліотеки).
 * <p>
 * {@code trigger(track)}/{@code cancelActive()} — той самий публічний
 * контракт що {@code onUseTriggered()}/{@code cancelActive()} в оригіналі,
 * але параметризований треком замість захардкодженого в тілі класу.
 * <p>
 * Порядок застосування offset-ів (задокументовано в плані, 3.24b/3.24c):
 * спершу базова {@link DynamicCameraProfile}-хитавиця (дихання/bob/tremor,
 * рушій {@link DynamicCameraController}), потім {@link ItemCameraTrack}
 * предмета зверху — обидва шари адитивні, жоден не переписує інший.
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT)
public final class ItemCameraController {

    private static final ItemCameraSessionState STATE = new ItemCameraSessionState();
    private static long lastEventTickNanos = -1;

    private ItemCameraController() {}

    public static void trigger(ItemCameraTrack track) {
        STATE.trigger(track);
    }

    public static void cancelActive() {
        STATE.cancel();
    }

    public static boolean isActive() {
        return STATE.isActive();
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float deltaSeconds = resolveDeltaSeconds();

        // Шар 1: фонова кінематографічна камера (окремий рушій, 3.24c) —
        // застосовується першим, якщо зареєстрований.
        DynamicCameraController.applyIfEnabled(event, deltaSeconds);

        // Шар 2: активний трек предмета (адитивно, зверху шару 1).
        if (!STATE.isActive()) return;
        float[] offset = STATE.advance(deltaSeconds);
        event.setYaw(event.getYaw() + offset[0]);
        event.setPitch(event.getPitch() + offset[1]);
    }

    private static float resolveDeltaSeconds() {
        long now = System.nanoTime();
        if (lastEventTickNanos < 0) {
            lastEventTickNanos = now;
            return 1f / 20f; // перший кадр — припускаємо 1 tick (20 tps)
        }
        float delta = (now - lastEventTickNanos) / 1_000_000_000f;
        lastEventTickNanos = now;
        // Захист від аномально великих delta (лаг-спайк/пауза меню) — clamp як у оригіналі
        // не документований явно, але типова практика для camera-lerp рушіїв.
        return Math.min(delta, 0.25f);
    }
}
