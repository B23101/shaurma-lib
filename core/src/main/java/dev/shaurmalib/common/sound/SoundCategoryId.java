package dev.shaurmalib.common.sound;

import java.util.Objects;

/**
 * SoundCategoryId — ідентифікатор категорії звуку, яку реєструє
 * консюмер (план, п. 3.5, "звуковий центр").
 * <p>
 * Категорія бібліотеки — НЕ те саме, що ванільний {@code SoundSource}
 * (MASTER/MUSIC/PLAYERS/...). Ванільних 12 категорій — загальні на всю
 * гру; категорія {@link SoundCategoryId} — власна, специфічна для мода
 * "смуга" гучності, яку гравець регулює окремим слайдером у своєму UI
 * налаштувань (те, що в snipers_shaurma було {@code ModSoundCategory}:
 * MOD_MUSIC/MOD_SFX/KILL/ANNOUNCER/DEATH_CONCUSSION/TRAINING_MUSIC/
 * MENU_UI — довільний набір, специфічний для режиму). Кожен консюмер
 * (snipers, maniac) реєструє свій власний набір категорій — бібліотека
 * не нав'язує фіксований enum.
 * <p>
 * {@code namespace} — modId консюмера, що зареєстрував категорію (для
 * діагностики конфліктів, якщо два різні моди спробують зареєструвати
 * категорію з однаковим {@code key} — див. {@link SoundCategoryRegistry}).
 */
public final class SoundCategoryId {

    private final String namespace;
    private final String key;

    private SoundCategoryId(String namespace, String key) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.key = Objects.requireNonNull(key, "key");
    }

    public static SoundCategoryId of(String namespace, String key) {
        return new SoundCategoryId(namespace, key);
    }

    public String namespace() {
        return namespace;
    }

    public String key() {
        return key;
    }

    /** Повний ідентифікатор у форматі {@code namespace:key} — використовується як ключ реєстру і в мережі. */
    public String asString() {
        return namespace + ":" + key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SoundCategoryId other)) return false;
        return namespace.equals(other.namespace) && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }

    @Override
    public String toString() {
        return asString();
    }
}
