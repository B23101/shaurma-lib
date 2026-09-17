package dev.shaurmalib.forge.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.forge.ShaurmaLibMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Спільний Battlefield-style intro при вході у світ/на сервер.
 *
 * <p>Інтро активне за замовчуванням після {@link #attach}. Споживач може
 * вимкнути його в будь-який момент через {@code enabled}, тому кожен режим
 * не мусить дублювати власний екран і обробники входу.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class IntroOverlay {

    private static final long MUSIC_DELAY_MS = 100L;
    private static final long BOOTUP_MS = 400L;
    private static final long STATIC_MS = 500L;
    private static final long REVEAL_MS = 1300L;
    private static final long HOLD_MS = 1400L;
    private static final long EXIT_MS = 800L;
    private static final long PH1 = BOOTUP_MS;
    private static final long PH2 = PH1 + STATIC_MS;
    private static final long PH3 = PH2 + REVEAL_MS;
    private static final long PH4 = PH3 + HOLD_MS;
    private static final long TOTAL_MS = PH4 + EXIT_MS;
    private static final int ACCENT_GOLD = 0xFFD86A;
    private static final String CIPHER = "!@#$%^&*<>?/\\|01ABCDEFGHKLMNPQRSTUVWXYZ░▒▓█";

    private static volatile BooleanSupplier enabled = () -> true;
    private static volatile Supplier<SoundEvent> soundSupplier = () -> null;
    private static volatile DoubleSupplier volumeSupplier = () -> 1.0;
    private static volatile String title = "ШАУРМА";
    private static volatile String subtitle = "";
    private static boolean attached;
    private static boolean pending;
    private static int pendingTicks;
    private static boolean wasInWorld;
    private static boolean active;
    private static boolean soundPlayed;
    private static boolean soundsPaused;
    private static long startedAt;
    private static SimpleSoundInstance music;

    private IntroOverlay() {}

    public static synchronized void attach(BooleanSupplier enabled,
                                            Supplier<SoundEvent> sound,
                                            DoubleSupplier volume,
                                            String title,
                                            String subtitle) {
        IntroOverlay.enabled = enabled != null ? enabled : () -> true;
        soundSupplier = sound != null ? sound : () -> ShaurmaLibMod.INTRO_MUSIC.get();
        volumeSupplier = volume != null ? volume : () -> 1.0;
        IntroOverlay.title = title != null ? title : "ШАУРМА";
        IntroOverlay.subtitle = subtitle != null ? subtitle : "";
        if (attached) return;
        MinecraftForge.EVENT_BUS.register(IntroOverlay.class);
        OverlayEngine.register("shaurma_intro", IntroOverlay::render, () -> active);
        attached = true;
    }

    public static void activate() {
        if (!enabled.getAsBoolean() || active) return;
        active = true;
        soundPlayed = false;
        soundsPaused = false;
        startedAt = System.currentTimeMillis();
    }

    public static void deactivate() {
        active = false;
        stopMusic();
        soundsPaused = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isSoundPlayed() {
        return soundPlayed;
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        pending = true;
        pendingTicks = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = false;
        wasInWorld = false;
        deactivate();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.level != null && mc.player != null;
        if (inWorld && !wasInWorld) {
            // Keep the intro reliable even if the network login event was
            // fired before this shared listener was registered.
            pending = true;
            pendingTicks = 0;
        }
        wasInWorld = inWorld;
        if (!enabled.getAsBoolean()) {
            if (active) deactivate();
            return;
        }
        if (pending && inWorld && ++pendingTicks >= 2) {
            pending = false;
            activate();
        }
        if (active && !inWorld) deactivate();
    }

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui,
                               GuiGraphics g, float partialTick, int sw, int sh) {
        long t = System.currentTimeMillis() - startedAt;
        if (t >= TOTAL_MS) {
            deactivate();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!soundsPaused) {
            soundsPaused = true;
            mc.getSoundManager().stop(null, SoundSource.MUSIC);
            mc.getSoundManager().stop(null, SoundSource.AMBIENT);
        }
        if (!soundPlayed && t >= MUSIC_DELAY_MS) {
            soundPlayed = true;
            SoundEvent sound = soundSupplier.get();
            double volume = Math.max(0.0, Math.min(1.0, volumeSupplier.getAsDouble()));
            if (sound != null && volume > 0.0) {
                music = SimpleSoundInstance.forUI(sound, 1.0f, (float) volume);
                mc.getSoundManager().play(music);
            }
        }

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0, 0, 2000);
        float alpha = t > PH4 ? 1f - easeIn((t - PH4) / (float) EXIT_MS) : 1f;
        g.fill(0, 0, sw, sh, ((int) (alpha * 255) << 24));

        int cx = sw / 2;
        int cy = sh / 2;
        if (t < PH1) {
            float p = easeOut(t / (float) BOOTUP_MS);
            int half = sh / 2;
            int rows = (int) (half * p);
            for (int y = 0; y < rows; y += 3) {
                int a = (int) ((0.2f + 0.5f * (1f - y / (float) half)) * 255);
                g.fill(0, y, sw, y + 1, (a << 24) | 0xFFFFFF);
                g.fill(0, sh - y - 1, sw, sh - y, (a << 24) | 0xFFFFFF);
            }
        }
        if (t >= PH1) {
            float cornerAlpha = t < PH2 ? (t - PH1) / (float) STATIC_MS : 1f;
            drawCorners(g, sw, sh, cornerAlpha * alpha);
        }

        if (t >= PH1 && t < PH2) {
            float strength = 1f - (t - PH1) / (float) STATIC_MS;
            long bucket = t / 35L;
            for (int i = 0; i < 28; i++) {
                long seed = startedAt + bucket * 31L + i * 7331L;
                int bx = (int) (Math.abs(hash(seed)) % sw);
                int by = (int) (Math.abs(hash(seed + 1)) % sh);
                int bw = 40 + (int) (Math.abs(hash(seed + 2)) % 220);
                int bh = 2 + (int) (Math.abs(hash(seed + 3)) % 6);
                int a = (int) (strength * 0.55f * 255);
                int color = switch ((int) (Math.abs(hash(seed + 4)) % 4)) {
                    case 0 -> 0xFF2030;
                    case 1 -> 0x20FF60;
                    case 2 -> 0x3060FF;
                    default -> 0xFFFFFF;
                };
                g.fill(bx, by, bx + bw, by + bh, (a << 24) | color);
            }
        }

        float reveal = t < PH2 ? 0f : Math.min(1f, (t - PH2) / (float) REVEAL_MS);
        if (t >= PH2 && t < PH3) {
            drawTitle(g, mc, cx, cy, reveal, t, alpha);
        } else if (t >= PH3 && t < PH4) {
            drawTitle(g, mc, cx, cy, 1f, t, alpha);
            if (!subtitle.isEmpty()) {
                float subtitleProgress = Math.min(1f, (t - PH3) / 700f);
                int shown = (int) (subtitle.length() * subtitleProgress);
                String shownText = subtitle.substring(0, shown);
                int x = cx - (int) (mc.font.width(shownText) * 1.4f) / 2;
                PoseStack subtitlePose = g.pose();
                subtitlePose.pushPose();
                subtitlePose.translate(x, cy + 14, 0);
                subtitlePose.scale(1.4f, 1.4f, 1f);
                g.drawString(mc.font, shownText, 0, 0,
                        ((int) (alpha * 255) << 24) | 0xFFD86A, false);
                if (subtitleProgress < 1f) {
                    g.fill(mc.font.width(shownText), -1, mc.font.width(shownText) + 1,
                            mc.font.lineHeight + 1, 0xFFFFD86A);
                }
                subtitlePose.popPose();
            }
            if ((t / 80L) % 3 == 0) {
                renderGlitchBars(g, 0, cy - 30, sw, 60, startedAt, t, 0.25f);
            }
        } else if (t >= PH4) {
            float exitProgress = Math.min(1f, (t - PH4) / (float) EXIT_MS);
            drawTitle(g, mc, cx, cy, 1f, t, alpha * (1f - exitProgress));
            int lineH = (int) ((1f - easeIn(exitProgress)) * 24f);
            int lineW = (int) ((1f - exitProgress * exitProgress) * sw);
            int lineAlpha = (int) ((1f - exitProgress) * 255);
            g.fill(cx - lineW / 2, cy - 1, cx + lineW / 2, cy + 1,
                    (lineAlpha << 24) | 0xFFD86A);
            if (lineH > 2) {
                g.fill(cx - 1, cy - lineH, cx + 1, cy + lineH,
                        ((int) ((1f - exitProgress) * 0.4f * 255) << 24) | 0xFFD86A);
            }
        }
        ps.popPose();
    }

    private static void drawTitle(GuiGraphics g, Minecraft mc, int cx, int cy,
                                  float progress, long time, float alpha) {
        StringBuilder text = new StringBuilder();
        long flicker = time / 50L;
        for (int i = 0; i < title.length(); i++) {
            char real = title.charAt(i);
            if (real == ' ' || progress >= (i + 0.5f) / title.length()) {
                text.append(real);
            } else {
                text.append(CIPHER.charAt((int) (Math.abs(hash(startedAt + flicker * 17L + i * 991L))
                        % CIPHER.length())));
            }
        }
        float scale = 2.6f;
        int width = (int) (mc.font.width(text.toString()) * scale);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx - width / 2.0, cy - 14, 0);
        ps.scale(scale, scale, 1f);
        int a = (int) (alpha * 255);
        float chroma = progress < 1f ? (1f - progress) * 5f : 0f;
        if (alpha < 1f) chroma = (1f - alpha) * 8f;
        if (chroma > 0.5f) {
            int dx = (int) chroma;
            g.drawString(mc.font, text.toString(), -dx, 0, ((int) (a * 0.55f) << 24) | 0xFF3030, false);
            g.drawString(mc.font, text.toString(), dx, 0, ((int) (a * 0.55f) << 24) | 0x3060FF, false);
        }
        g.drawString(mc.font, text.toString(), 1, 1, ((int) (a * 0.7f) << 24), false);
        g.drawString(mc.font, text.toString(), 0, 0,
                (a << 24) | (progress >= 0.95f ? ACCENT_GOLD : 0xFFFFFF), false);
        ps.popPose();

        if (progress < 1f && alpha >= 1f) {
            int sweepLeft = cx - width / 2 - 20;
            int sweepRight = cx + width / 2 + 20;
            int sweepX = sweepLeft + (int) ((sweepRight - sweepLeft) * progress);
            int sweepAlpha = (int) ((1f - progress) * 0.9f * 255);
            if (sweepAlpha > 4) {
                g.fill(sweepX, cy - 18, sweepX + 2, cy + 18,
                        (sweepAlpha << 24) | 0xFFFFFF);
                int trailAlpha = (int) ((1f - progress) * 0.35f * 255);
                g.fill(sweepX - 18, cy - 18, sweepX, cy + 18,
                        (trailAlpha << 24) | 0xFFD86A);
            }
        }
    }

    private static void drawCorners(GuiGraphics g, int sw, int sh, float alpha) {
        int a = (int) (alpha * 255);
        int c = (a << 24) | (ACCENT_GOLD & 0xFFFFFF);
        int len = 28;
        int pad = 32;
        g.fill(pad, pad, pad + len, pad + 2, c);
        g.fill(pad, pad, pad + 2, pad + len, c);
        g.fill(sw - pad - len, pad, sw - pad, pad + 2, c);
        g.fill(sw - pad - 2, pad, sw - pad, pad + len, c);
        g.fill(pad, sh - pad - 2, pad + len, sh - pad, c);
        g.fill(pad, sh - pad - len, pad + 2, sh - pad, c);
        g.fill(sw - pad - len, sh - pad - 2, sw - pad, sh - pad, c);
        g.fill(sw - pad - 2, sh - pad - len, sw - pad, sh - pad, c);
    }

    private static void renderGlitchBars(GuiGraphics g, int x, int y, int w, int h,
                                         long seed, long age, float strength) {
        long bucket = age / 30L;
        int count = (int) (strength * 8f) + 2;
        for (int i = 0; i < count; i++) {
            long s = seed + bucket * 23L + i * 7331L;
            int by = y + (int) (Math.abs(hash(s)) % h);
            int bh = 1 + (int) (Math.abs(hash(s + 1)) % 2);
            int a = (int) (strength * 0.55f * 255);
            int color = switch ((int) (Math.abs(hash(s + 2)) % 3)) {
                case 0 -> (a << 24) | 0xFF2030;
                case 1 -> (a << 24) | 0x20FF60;
                default -> (a << 24) | 0x3060FF;
            };
            g.fill(x, by, x + w, by + bh, color);
        }
    }

    private static void stopMusic() {
        if (music != null) {
            Minecraft.getInstance().getSoundManager().stop(music);
            music = null;
        }
    }

    private static float easeOut(float value) {
        value = Math.max(0f, Math.min(1f, value));
        return 1f - (1f - value) * (1f - value);
    }

    private static float easeIn(float value) {
        value = Math.max(0f, Math.min(1f, value));
        return value * value * value;
    }

    private static long hash(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
