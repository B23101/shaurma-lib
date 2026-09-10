package dev.shaurmalib.common.sound;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SoundCategoryRegistry — реєстр категорій звуку мода (план, п. 3.5).
 * <p>
 * Консюмер реєструє свої категорії ОДИН раз (типово в клієнтському
 * setup), кожна з власним {@link VolumeSource}, що читає значення з
 * власного екрана налаштувань консюмера:
 *
 * <pre>{@code
 * SoundCategoryRegistry.register(
 *     SoundCategoryId.of("snipers_shaurma", "mod_music"),
 *     () -> ClientModOptions.get().audio.modMusicVolume);
 * SoundCategoryRegistry.register(
 *     SoundCategoryId.of("snipers_shaurma", "menu_ui"),
 *     () -> ClientModOptions.get().audio.menuUiVolume);
 * }</pre>
 * <p>
 * {@link dev.shaurmalib.forge.sound.SoundCenter} звертається сюди за
 * {@link VolumeSource#currentVolume()} кожної категорії, яку вказує
 * {@link SoundCue#category()} — незареєстрована категорія трактується
 * як гучність 1.0 (не блокує звук), щоб забутий {@code register(...)}
 * не глушив увесь звуковий рушій мовчки.
 */
public final class SoundCategoryRegistry {

    private static final Map<String, VolumeSource> CATEGORIES = new ConcurrentHashMap<>();

    private SoundCategoryRegistry() {}

    public static void register(SoundCategoryId id, VolumeSource volumeSource) {
        CATEGORIES.put(id.asString(), volumeSource);
    }

    /** Поточна гучність категорії. 1.0f, якщо категорія не зареєстрована. */
    public static float volumeOf(SoundCategoryId id) {
        VolumeSource source = CATEGORIES.get(id.asString());
        return source != null ? source.currentVolume() : 1.0f;
    }

    public static boolean isRegistered(SoundCategoryId id) {
        return CATEGORIES.containsKey(id.asString());
    }

    /** Для тестів/hot-reload консюмера — знімає всі реєстрації. Нормальний рантайм цього не викликає. */
    public static void clear() {
        CATEGORIES.clear();
    }
}
