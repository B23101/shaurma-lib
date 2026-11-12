package dev.shaurmalib.forge.fx.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.DoubleSupplier;

/**
 * Серцебиття на низькому здоров'ї (порт EnhancedVisuals HeartbeatHandler): пульс повноекранної вінетки,
 * подвійний пульс розмиття й звук «тук-тук»; інтервал коротшає з падінням здоров'я.
 * Вмикається режимом: {@link ScreenFx#enableHeartbeat}.
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenFxHeartbeat {

    /**
     * @param threshold         частка здоров'я, нижче якої б'ється серце (0.3 = 30%)
     * @param overlayIntensity  яскравість вінетки на піку
     * @param overlayTicks      тривалість пульсу вінетки
     * @param blurIntensity     радіус розмиття на піку
     * @param blurTicks         тривалість пульсу розмиття
     * @param minLengthTicks    найкоротший цикл (майже нуль здоров'я)
     * @param timeFactor        на скільки тіків довший цикл при повному здоров'ї
     * @param volume            гучність звуку
     */
    public record Settings(float threshold, float overlayIntensity, int overlayTicks, float blurIntensity, int blurTicks,
                           int minLengthTicks, float timeFactor, float volume) {
        /** Значення снайперів (EnhancedVisuals за замовчуванням). */
        public static Settings defaults() { return new Settings(0.3f, 0.5f, 20, 50f, 5, 15, 100f, 1f); }
    }

    private static final ResourceLocation IN = new ResourceLocation("shaurma_lib", "screenfx_heartbeat_in");
    private static final ResourceLocation OUT = new ResourceLocation("shaurma_lib", "screenfx_heartbeat_out");

    private static Settings settings;
    private static DoubleSupplier health;
    private static int buffer;
    private static final ScreenFx.Pulse[] overlay = { null };
    private static final ScreenFx.Pulse[] blur = { null };

    private ScreenFxHeartbeat() {}

    static void enable(Settings s, DoubleSupplier healthFraction) {
        settings = s == null ? Settings.defaults() : s;
        health = healthFraction;
    }

    static void disable() { settings = null; reset(); }

    static void reset() {
        buffer = 0;
        overlay[0] = null;
        blur[0] = null;
        ScreenFxBlur.setSource("heartbeat", 0f);
    }

    /** Поточна прозорість вінетки (для оверлею). */
    static float overlayAlpha(float partial) { return overlay[0] == null ? 0f : overlay[0].value(partial); }

    static void tick() {
        if (overlay[0] != null && ++overlay[0].age > overlay[0].ticks) overlay[0] = null;
        if (blur[0] != null && ++blur[0].age > blur[0].ticks) blur[0] = null;
        ScreenFxBlur.setSource("heartbeat", blur[0] == null ? 0f : blur[0].value());
        if (settings == null) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.isPaused() || !p.isAlive()) { buffer = 0; return; }
        double frac = health != null ? health.getAsDouble() : p.getHealth() / Math.max(1f, p.getMaxHealth());
        if (frac >= settings.threshold() || frac < 0) { buffer = 0; return; }
        float intensity = (float) Math.min(0.7, (settings.threshold() - frac) * 2.0);
        if (buffer <= 0) {
            buffer = (int) (frac * settings.timeFactor() + settings.minLengthTicks());
            overlay[0] = new ScreenFx.Pulse(0, intensity * settings.overlayIntensity(), settings.overlayTicks());
            blur[0] = new ScreenFx.Pulse(0, intensity * settings.blurIntensity(), settings.blurTicks());
            ScreenFx.playSound(OUT, settings.volume(), 1f);
        } else if (buffer == 5) {
            blur[0] = new ScreenFx.Pulse(0, intensity * settings.blurIntensity(), settings.blurTicks());
            ScreenFx.playSound(IN, settings.volume(), 1f);
        }
        buffer--;
    }
}
