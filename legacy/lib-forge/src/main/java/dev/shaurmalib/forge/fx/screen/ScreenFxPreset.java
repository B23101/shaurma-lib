package dev.shaurmalib.forge.fx.screen;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Опис одного екранного ефекту: що з'являється поверх HUD, коли сервер або клієнт викликає
 * {@link ScreenFx#play(String, float)}. Ефект = будь-яка комбінація шарів:
 * <ul>
 *   <li>{@link Spray} — розкидані по екрану текстури (кров, іній, пил), кожна згасає сама;</li>
 *   <li>{@link Flash} — кольорова заливка всього екрана, що згасає (червоний спалах удару, чорне затемнення);</li>
 *   <li>{@link Blur} — пульс розмиття (власний PostChain, не конфліктує з ванільними шейдерами);</li>
 *   <li>{@link Sound} — звук у вухах гравця.</li>
 * </ul>
 * {@code intensity} у {@link ScreenFx#play} масштабує кількість частинок, прозорість спалаху й силу розмиття.
 * Пресети реєструються на клієнті ({@link ScreenFx#register}); вбудовані — у {@link ScreenFx}.
 */
public final class ScreenFxPreset {

    /**
     * Текстури, розкидані по екрану. Розміри — «як намальовано» для GUI-екрана шириною 960 і
     * масштабуються під реальний екран.
     *
     * @param edgeOnly лише по краях екрана (кров не перекриває приціл)
     * @param tint     колір 0xRRGGBB або null (без тонування)
     */
    public record Spray(ResourceLocation[] textures, int count, int baseSize, double scaleMin, double scaleMax,
                        int lifeMinTicks, int lifeMaxTicks, double opacity, Integer tint,
                        boolean rotate, boolean edgeOnly) {}

    /** Заливка екрана кольором {@code rgb}: одразу {@code alpha}, лінійно до 0 за {@code ticks}. */
    public record Flash(int rgb, float alpha, int ticks) {}

    /** Розмиття: радіус {@code radius} (як uniform Radius у blur.json), лінійно до 0 за {@code ticks}. */
    public record Blur(float radius, int ticks) {}

    public record Sound(ResourceLocation id, float volume, float pitch) {}

    final List<Spray> sprays;
    final Flash flash;
    final Blur blur;
    final Sound sound;

    private ScreenFxPreset(Builder b) {
        this.sprays = List.copyOf(b.sprays);
        this.flash = b.flash;
        this.blur = b.blur;
        this.sound = b.sound;
    }

    public List<Spray> sprays() { return sprays; }
    public Flash flash() { return flash; }
    public Blur blur() { return blur; }
    public Sound sound() { return sound; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<Spray> sprays = new ArrayList<>();
        private Flash flash;
        private Blur blur;
        private Sound sound;

        /** Розкидати текстури по краях екрана (типово для крові). */
        public Builder spray(ResourceLocation[] textures, int count, int baseSize, int lifeMinTicks, int lifeMaxTicks,
                             double opacity, Integer tint) {
            return spray(new Spray(textures, count, baseSize, 0.2, 1.0, lifeMinTicks, lifeMaxTicks, opacity, tint,
                    true, true));
        }

        public Builder spray(Spray s) { sprays.add(s); return this; }

        public Builder flash(int rgb, float alpha, int ticks) { this.flash = new Flash(rgb, alpha, ticks); return this; }

        public Builder blur(float radius, int ticks) { this.blur = new Blur(radius, ticks); return this; }

        public Builder sound(ResourceLocation id, float volume, float pitch) {
            this.sound = new Sound(id, volume, pitch);
            return this;
        }

        public ScreenFxPreset build() { return new ScreenFxPreset(this); }
    }
}
