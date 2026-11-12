package dev.shaurmalib.forge.fx.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Двигун екранних ефектів ShaurmaLib (клієнт): кров/іній/пил по краях екрана, кольорові спалахи,
 * пульси розмиття, серцебиття на низькому здоров'ї. Перенесено з EnhancedVisuals snipers_shaurma —
 * без ефектів середовища (дощ, пісок, бризки води): ті лишаються в режимі, якому потрібні.
 *
 * <pre>{@code
 * // клієнт (FMLClientSetupEvent): свій ефект і коли він дозволений
 * ScreenFx.register("mymod:hit", ScreenFxPreset.builder()
 *     .spray(ScreenFxTextures.SLASH, 30, 128, 200, 600, 0.6, 0x4D0303)
 *     .flash(0x8A0000, 0.35f, 10).blur(25f, 8).build());
 * ScreenFx.setActiveGate(() -> MyState.inMatch());
 * // сервер: показати гравцю
 * ScreenFxServer.play(player, "mymod:hit", 1f);
 * }</pre>
 * Оверлей, тік і розмиття бібліотека підключає сама ({@link ScreenFxClient}).
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenFx {

    private static final Logger LOG = LoggerFactory.getLogger("ShaurmaScreenFx");
    static final Random RANDOM = new Random();
    /** Ширина GUI-екрана, для якої задані розміри пресетів (GUI Scale 2 на 1080p). */
    private static final double REFERENCE_WIDTH = 960.0;
    private static final int BLOOD = 0x4D0303;

    private static final Map<String, ScreenFxPreset> PRESETS = new HashMap<>();
    static final List<Particle> PARTICLES = new ArrayList<>();
    static final List<Pulse> FLASHES = new ArrayList<>();
    private static final List<Pulse> BLURS = new ArrayList<>();
    private static BooleanSupplier gate = () -> true;

    private ScreenFx() {}

    static {
        // Вбудовані пресети (для ванільного урону — ScreenFxServer.enableVanillaDamage)
        register("shaurma_lib:blood_slash", blood(ScreenFxTextures.SLASH));
        register("shaurma_lib:blood_impact", blood(ScreenFxTextures.IMPACT));
        register("shaurma_lib:blood_pierce", blood(ScreenFxTextures.PIERCE));
        register("shaurma_lib:blood_splatter", blood(ScreenFxTextures.SPLATTER));
        register("shaurma_lib:fire", ScreenFxPreset.builder()
            .spray(new ScreenFxPreset.Spray(ScreenFxTextures.FIRE, 1, 64, 0.2, 1.0, 100, 1000, 1.0, null, true, false)).build());
        register("shaurma_lib:freeze", ScreenFxPreset.builder()
            .spray(new ScreenFxPreset.Spray(ScreenFxTextures.ICE, 4, 96, 0.2, 1.0, 10, 15, 1.0, 0xC8FFFF, true, false)).build());
        register("shaurma_lib:explosion", ScreenFxPreset.builder()
            .spray(new ScreenFxPreset.Spray(ScreenFxTextures.DUST, 12, 128, 0.4, 1.2, 60, 200, 0.8, null, true, false))
            .flash(0xFFFFFF, 0.35f, 8).blur(30f, 20)
            .sound(new ResourceLocation("shaurma_lib", "screenfx_ringing_short"), 0.7f, 1f).build());
    }

    private static ScreenFxPreset blood(ResourceLocation[] tex) {
        // Кількість множиться на intensity (≈ урон × множник здоров'я) — див. ScreenFxServer.damage.
        return ScreenFxPreset.builder().spray(tex, 1, 128, 500, 1500, 0.35, BLOOD).build();
    }

    // ── API ─────────────────────────────────────────────────────────────

    /** Зареєструвати (або перекрити) пресет. Id — {@code namespace:назва}. */
    public static void register(String id, ScreenFxPreset preset) { PRESETS.put(id, preset); }

    public static boolean has(String id) { return PRESETS.containsKey(id); }

    /** Умова режиму, коли ефекти дозволені (матч іде, гравець грає). За замовчуванням — завжди. */
    public static void setActiveGate(BooleanSupplier g) { gate = g == null ? () -> true : g; }

    /** Чи зараз можна показувати ефекти. */
    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()) return false;
        try {
            return gate.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Відтворити пресет. {@code intensity} 1 — як описано; 0.5 — пів частинок і пів яскравості. */
    public static void play(String id, float intensity) {
        ScreenFxPreset p = PRESETS.get(id);
        if (p == null) {
            LOG.warn("[ScreenFx] невідомий ефект {}", id);
            return;
        }
        if (!isActive() || intensity <= 0f) return;
        for (ScreenFxPreset.Spray s : p.sprays) spray(s, intensity);
        if (p.flash != null) FLASHES.add(new Pulse(p.flash.rgb(), Math.min(1f, p.flash.alpha() * Math.min(1f, intensity)), p.flash.ticks()));
        if (p.blur != null) BLURS.add(new Pulse(0, p.blur.radius() * Math.min(1.5f, intensity), p.blur.ticks()));
        if (p.sound != null) playSound(p.sound.id(), p.sound.volume(), p.sound.pitch());
    }

    /** Ванільний урон: кількість крові росте з уроном і падінням здоров'я (формула EnhancedVisuals). */
    public static void damage(ScreenFxDamageKind kind, float damage, float healthAfter) {
        if (damage <= 0f || kind == ScreenFxDamageKind.NONE) return;
        double rate = Math.max(0, lerp(healthAfter, 0, 3, 12, 1.5));
        float count = (float) Math.min(200, damage * rate);
        switch (kind) {
            case SLASH -> play("shaurma_lib:blood_slash", count);
            case IMPACT -> play("shaurma_lib:blood_impact", count);
            case PIERCE -> play("shaurma_lib:blood_pierce", count);
            case SPLATTER -> play("shaurma_lib:blood_splatter", Math.min(20, damage) * (float) rate);
            case FIRE -> play("shaurma_lib:fire", 1f);
            case FREEZE -> play("shaurma_lib:freeze", 1f);
            default -> { }
        }
    }

    /** Миттєво прибрати все (зміна фази, смерть, вихід зі світу). */
    public static void clear() {
        PARTICLES.clear();
        FLASHES.clear();
        BLURS.clear();
        ScreenFxBlur.setSource("pulse", 0f);
        ScreenFxHeartbeat.reset();
    }

    /**
     * Серцебиття на низькому здоров'ї: пульс вінетки, розмиття, звук «тук-тук», частішає з падінням здоров'я.
     * {@code healthFraction} — частка здоров'я 0..1 (для режимів зі своєю системою HP); null — ванільне здоров'я.
     */
    public static void enableHeartbeat(ScreenFxHeartbeat.Settings settings, DoubleSupplier healthFraction) {
        ScreenFxHeartbeat.enable(settings, healthFraction);
    }

    public static void disableHeartbeat() { ScreenFxHeartbeat.disable(); }

    // ── всередині ───────────────────────────────────────────────────────

    static void tick() {
        if (!isActive()) {
            if (!PARTICLES.isEmpty() || !FLASHES.isEmpty() || !BLURS.isEmpty()) clear();
            ScreenFxBlur.clearSources();
            return;
        }
        PARTICLES.removeIf(p -> ++p.age > p.life);
        FLASHES.removeIf(f -> ++f.age > f.ticks);
        float blur = 0f;
        for (Iterator<Pulse> it = BLURS.iterator(); it.hasNext(); ) {
            Pulse b = it.next();
            if (++b.age > b.ticks) { it.remove(); continue; }
            blur = Math.max(blur, b.value());
        }
        ScreenFxBlur.setSource("pulse", blur);
        ScreenFxHeartbeat.tick();
    }

    private static void spray(ScreenFxPreset.Spray s, float intensity) {
        if (s.textures().length == 0) return;
        int count = Math.min(200, Math.round(s.count() * intensity));
        if (count <= 0 && intensity > 0f && s.count() > 0) count = 1;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        double screenScale = sw / REFERENCE_WIDTH;
        for (int i = 0; i < count; i++) {
            ResourceLocation tex = s.textures()[RANDOM.nextInt(s.textures().length)];
            double scale = between(s.scaleMin(), s.scaleMax()) * screenScale;
            int size = Math.max(1, (int) (s.baseSize() * scale));
            int x = s.edgeOnly() ? edge(sw, size) : spread(sw, size);
            int y = s.edgeOnly() ? edge(sh, size) : spread(sh, size);
            int life = s.lifeMinTicks() + (s.lifeMaxTicks() > s.lifeMinTicks() ? RANDOM.nextInt(s.lifeMaxTicks() - s.lifeMinTicks() + 1) : 0);
            PARTICLES.add(new Particle(tex, x, y, size, s.rotate() ? RANDOM.nextFloat() * 360f : 0f, life,
                (float) s.opacity(), s.tint() == null ? 0xFFFFFF : s.tint()));
        }
    }

    static void playSound(ResourceLocation id, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        SoundEvent e = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (mc.player == null) return;
        if (e == null) e = SoundEvent.createVariableRangeEvent(id);
        mc.player.playSound(e, volume, pitch);
    }

    private static double between(double a, double b) { return b <= a ? a : a + RANDOM.nextDouble() * (b - a); }

    static double lerp(double x, double x0, double y0, double x1, double y1) {
        return x1 == x0 ? y1 : y0 + (y1 - y0) * (x - x0) / (x1 - x0);
    }

    /** По всьому екрану, тяжіє до країв (як в оригіналі). */
    private static int spread(int len, int size) {
        int half = len / 2;
        float m = (float) (1 - Math.pow(RANDOM.nextDouble(), 2));
        float c = RANDOM.nextBoolean() ? half + half * m : half - half * m;
        return (int) (c - size / 2f);
    }

    /** Лише зовнішні 25% з кожного боку. */
    private static int edge(int len, int size) {
        int e = (int) (len * 0.25);
        int pos = RANDOM.nextBoolean() ? RANDOM.nextInt(Math.max(1, e)) : len - e + RANDOM.nextInt(Math.max(1, e));
        return Math.max(0, Math.min(len - size, pos));
    }

    static final class Particle {
        final ResourceLocation texture;
        final int x, y, size, life, tint;
        final float rotation, peak;
        int age;

        Particle(ResourceLocation texture, int x, int y, int size, float rotation, int life, float peak, int tint) {
            this.texture = texture; this.x = x; this.y = y; this.size = size; this.rotation = rotation;
            this.life = Math.max(1, life); this.peak = peak; this.tint = tint;
        }

        /** Миттєвий пік, лінійне згасання (DecimalCurve(0, peak, life, 0) оригіналу). */
        float opacity(float partial) { return peak * Math.max(0f, 1f - (age + partial) / life); }
    }

    static final class Pulse {
        final int rgb, ticks;
        final float peak;
        int age;

        Pulse(int rgb, float peak, int ticks) { this.rgb = rgb; this.peak = peak; this.ticks = Math.max(1, ticks); }

        float value() { return peak * Math.max(0f, 1f - (float) age / ticks); }

        float value(float partial) { return peak * Math.max(0f, 1f - (age + partial) / ticks); }
    }
}
