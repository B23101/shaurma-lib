package dev.shaurmalib.common.sound;

/**
 * VolumeSource — постачальник поточної гучності (0..1) для однієї
 * {@link SoundCategoryId}, наданий консюмером при реєстрації категорії.
 * <p>
 * Бібліотека НЕ зберігає власний стан слайдерів — це продуктова частина
 * консюмера (свій екран налаштувань, своя структура
 * {@code ClientModOptions}). {@link SoundCategoryRegistry} лише тримає
 * функціональний посилання: коли {@link dev.shaurmalib.forge.sound.SoundCenter}
 * рахує фінальну гучність звуку цієї категорії, він викликає
 * {@link #currentVolume()} консюмера щоразу (не кешує), тому зміна
 * слайдера гравцем застосовується миттєво до наступного відтворення
 * без додаткової синхронізації.
 */
@FunctionalInterface
public interface VolumeSource {

    /** Поточна гучність категорії, 0..1. Викликається на клієнті лише в клієнтському потоці. */
    float currentVolume();

    /** Категорія завжди на повній гучності — зручно для категорій без власного слайдера. */
    static VolumeSource always(float value) {
        return () -> value;
    }
}
