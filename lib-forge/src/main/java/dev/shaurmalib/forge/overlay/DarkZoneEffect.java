package dev.shaurmalib.forge.overlay;

import dev.shaurmalib.common.overlay.DarkZoneSpec;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Клієнтський стан "справжньої темряви" (не {@link WorldTintOverlay} —
 * див. докстрінг {@link DarkZoneSpec}). Один активний канал за замовчуванням
 * ({@code "default"}), як і {@link WorldTintOverlay} — консюмер, що хоче
 * кілька незалежних причин затемнення одночасно, реєструє власний
 * {@code zoneId}.
 * <p>
 * {@link #currentDarkenFactor()} читає {@code MixinLightTextureDarkZone}
 * щотіку lightmap-апдейту — множник 0..{@link DarkZoneSpec#MAX_DARKEN_FACTOR}
 * готовий без додаткового рахунку в mixin-і.
 */
@OnlyIn(Dist.CLIENT)
public final class DarkZoneEffect {

    private static final java.util.Map<String, Channel> channels = new java.util.HashMap<>();
    private static boolean tickListenerRegistered = false;

    private static final class Channel {
        DarkZoneSpec spec = DarkZoneSpec.defaults();
        boolean targetActive = false;
        float currentIntensity = 0f;
    }

    private DarkZoneEffect() {}

    /**
     * Консюмер викликає це щотіку зі свого власного тікера (сервер
     * визначає "гравець у зоні чи ні" і шле клієнту булевий стан —
     * плавність переходу рахується тут, локально, а не мережею).
     */
    public static void setActive(boolean active) {
        setActive("default", active, DarkZoneSpec.defaults());
    }

    public static void setActive(String zoneId, boolean active, DarkZoneSpec spec) {
        ensureTickListener();
        Channel channel = channels.computeIfAbsent(zoneId, id -> new Channel());
        channel.spec = spec;
        channel.targetActive = active;
    }

    /** Поточний множник затемнення (0 = звичайне освітлення) — макс. серед усіх активних каналів. */
    public static float currentDarkenFactor() {
        float max = 0f;
        for (Channel channel : channels.values()) {
            float factor = channel.currentIntensity * DarkZoneSpec.MAX_DARKEN_FACTOR;
            if (factor > max) max = factor;
        }
        return max;
    }

    public static boolean isAnyActive() {
        for (Channel channel : channels.values()) {
            if (channel.targetActive || channel.currentIntensity > 0f) return true;
        }
        return false;
    }

    private static void ensureTickListener() {
        if (tickListenerRegistered) return;
        tickListenerRegistered = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new TickHandler());
    }

    private static final class TickHandler {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            for (Channel channel : channels.values()) {
                float target = channel.targetActive ? channel.spec.intensity() : 0f;
                channel.currentIntensity = channel.spec.intensityStep(
                    channel.currentIntensity, target, channel.spec.transitionTicks());
            }
        }
    }
}
