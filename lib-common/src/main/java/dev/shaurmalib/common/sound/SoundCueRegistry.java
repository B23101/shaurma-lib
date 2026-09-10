package dev.shaurmalib.common.sound;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SoundCueRegistry — каталог {@link SoundCue} мода (план, п. 3.5).
 * Консюмер реєструє кожен звук ОДИН раз (типово поруч зі своєю
 * {@code ModSounds}-реєстрацією {@code SoundEvent}); {@code
 * dev.shaurmalib.forge.sound.SoundCenter.play(soundId, ...)} шукає
 * визначення тут, щоб знати {@link SoundStage} і
 * {@link SoundCategoryId} без консюмерського membership-списку в
 * мережевому пакеті (див. клас-докстрінг {@link SoundCue}).
 * <p>
 * {@code SoundCenter.play(soundId, ...)} з невідомим {@code soundId}
 * (не зареєстрованим тут) не падає — грає з дефолтним
 * {@link SoundStage#NON_POSITIONAL} на повній гучності, як і
 * ванільний {@code SimpleSoundInstance} без прив'язки до категорії
 * мода; забутий {@code register(...)} лише вимикає category-slider
 * для цього конкретного звуку, не ламає відтворення.
 */
public final class SoundCueRegistry {

    private static final Map<String, SoundCue> CUES = new ConcurrentHashMap<>();

    private SoundCueRegistry() {}

    public static void register(SoundCue cue) {
        CUES.put(cue.soundId(), cue);
    }

    public static Optional<SoundCue> find(String soundId) {
        return Optional.ofNullable(CUES.get(soundId));
    }

    public static boolean isRegistered(String soundId) {
        return CUES.containsKey(soundId);
    }

    /** Для тестів/hot-reload консюмера. Нормальний рантайм цього не викликає. */
    public static void clear() {
        CUES.clear();
    }
}
