package dev.shaurmalib.common.sound;

import java.util.Objects;

/**
 * SoundCue — декларативний опис одного звукового кліпу мода (план,
 * п. 3.5): id звуку, {@link SoundStage} (2D/3D-фіксований/3D-рухомий),
 * {@link SoundCategoryId} (яким слайдером гравець регулює цей звук),
 * базова гучність і pitch.
 * <p>
 * Замінює патерн з оригіналу, де приналежність звуку до категорії
 * визначалась membership-перевіркою в трьох окремих
 * {@code Set.of(...)} константах усередині {@code PlaySoundPacket}
 * ({@code ANNOUNCEMENT_OVERLAY_SOUNDS}/{@code MOD_MUSIC_SOUNDS}/
 * {@code KILL_SOUNDS}) — тобто мережевий пакет мусив знати наперед
 * ідентифікатори конкретних звуків консюмера. Тут навпаки: консюмер
 * реєструє {@link SoundCue} один раз у {@link SoundCueRegistry} під
 * своїм soundId, {@link dev.shaurmalib.forge.sound.SoundCenter} лише
 * шукає визначення по id — бібліотека не містить жодного хардкодженого
 * імені звуку snipers-специфіки.
 *
 * <pre>{@code
 * SoundCueRegistry.register(SoundCue.builder("snipers_shaurma:kit_selection_music")
 *     .stage(SoundStage.NON_POSITIONAL)
 *     .category(modMusicCategory)
 *     .baseVolume(1.0f)
 *     .build());
 * }</pre>
 */
public final class SoundCue {

    private final String soundId;
    private final SoundStage stage;
    private final SoundCategoryId category;
    private final float baseVolume;
    private final float basePitch;

    private SoundCue(Builder b) {
        this.soundId = Objects.requireNonNull(b.soundId, "soundId");
        this.stage = Objects.requireNonNull(b.stage, "stage");
        this.category = Objects.requireNonNull(b.category, "category — кожен SoundCue має належати категорії");
        this.baseVolume = b.baseVolume;
        this.basePitch = b.basePitch;
    }

    public String soundId() {
        return soundId;
    }

    public SoundStage stage() {
        return stage;
    }

    public SoundCategoryId category() {
        return category;
    }

    public float baseVolume() {
        return baseVolume;
    }

    public float basePitch() {
        return basePitch;
    }

    public static Builder builder(String soundId) {
        return new Builder(soundId);
    }

    public static final class Builder {
        private final String soundId;
        private SoundStage stage = SoundStage.NON_POSITIONAL;
        private SoundCategoryId category;
        private float baseVolume = 1.0f;
        private float basePitch = 1.0f;

        private Builder(String soundId) {
            this.soundId = soundId;
        }

        public Builder stage(SoundStage stage) {
            this.stage = stage;
            return this;
        }

        public Builder category(SoundCategoryId category) {
            this.category = category;
            return this;
        }

        public Builder baseVolume(float baseVolume) {
            this.baseVolume = baseVolume;
            return this;
        }

        public Builder basePitch(float basePitch) {
            this.basePitch = basePitch;
            return this;
        }

        public SoundCue build() {
            return new SoundCue(this);
        }
    }
}
