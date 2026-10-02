package dev.shaurmalib.common.offline;

import net.minecraft.nbt.CompoundTag;

/**
 * Як режим хоче бачити конкретного гравця, коли той вийде (план, §4.2).
 * Повертається з {@code spawnWhen}.
 * <p>
 * {@link #extra()} — довільні дані режиму (роль, HP на момент виходу тощо),
 * які повертаються в колбеках. На диск НЕ пишеться (реєстр живе лише в пам'яті).
 */
public final class OfflineSpec {

    private static final OfflineSpec NONE = new OfflineSpec(false, false, true, -1, 20.0f, new CompoundTag());

    private final boolean spawnAvatar;
    private final boolean invulnerable;
    private final boolean holdsChunk;
    private final int maxAbsenceTicks;
    private final float initialHealth;
    private final CompoundTag extra;

    private OfflineSpec(boolean spawnAvatar, boolean invulnerable, boolean holdsChunk,
                        int maxAbsenceTicks, float initialHealth, CompoundTag extra) {
        this.spawnAvatar = spawnAvatar;
        this.invulnerable = invulnerable;
        this.holdsChunk = holdsChunk;
        this.maxAbsenceTicks = maxAbsenceTicks;
        this.initialHealth = initialHealth;
        this.extra = extra;
    }

    /** Тіло не потрібне: режим поводиться як раніше. */
    public static OfflineSpec none() {
        return NONE;
    }

    /** Тіло з типовими налаштуваннями (вразливе, тримає чанк, без ліміту відсутності). */
    public static OfflineSpec avatar() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Чи створювати тіло ({@code false} = поводитися як раніше). */
    public boolean spawnAvatar() {
        return spawnAvatar;
    }

    /**
     * Примусова невразливість. За замовчуванням {@code false}: вразливість
     * визначає та сама політика, що й для гравця (паритет, план §0).
     */
    public boolean invulnerable() {
        return invulnerable;
    }

    /** Тримати чанк тіла завантаженим (за замовчуванням {@code true}). */
    public boolean holdsChunk() {
        return holdsChunk;
    }

    /** {@code -1} = без ліміту; інакше по завершенні тіло переходить у {@link OfflineStatus#EXPIRED}. */
    public int maxAbsenceTicks() {
        return maxAbsenceTicks;
    }

    /** Початкове HP запису для {@link OfflineDamagePolicy#vanillaLike()}. Режими з власною моделлю HP його не використовують. */
    public float initialHealth() {
        return initialHealth;
    }

    /** Довільні дані режиму. Повертається копія. */
    public CompoundTag extra() {
        return extra.copy();
    }

    public static final class Builder {
        private boolean spawnAvatar = true;
        private boolean invulnerable = false;
        private boolean holdsChunk = true;
        private int maxAbsenceTicks = -1;
        private float initialHealth = 20.0f;
        private CompoundTag extra = new CompoundTag();

        private Builder() {}

        public Builder spawnAvatar(boolean value) {
            this.spawnAvatar = value;
            return this;
        }

        public Builder invulnerable(boolean value) {
            this.invulnerable = value;
            return this;
        }

        public Builder holdsChunk(boolean value) {
            this.holdsChunk = value;
            return this;
        }

        /** {@code -1} — без ліміту, інакше додатне число тіків. */
        public Builder maxAbsenceTicks(int ticks) {
            this.maxAbsenceTicks = ticks;
            return this;
        }

        public Builder initialHealth(float health) {
            this.initialHealth = health;
            return this;
        }

        public Builder extra(CompoundTag tag) {
            this.extra = tag == null ? new CompoundTag() : tag.copy();
            return this;
        }

        public OfflineSpec build() {
            if (maxAbsenceTicks != -1 && maxAbsenceTicks <= 0) {
                throw new IllegalStateException(
                        "OfflineSpec.maxAbsenceTicks має бути -1 (без ліміту) або додатним, отримано " + maxAbsenceTicks);
            }
            if (!(initialHealth > 0.0f)) {
                throw new IllegalStateException("OfflineSpec.initialHealth має бути додатним, отримано " + initialHealth);
            }
            return new OfflineSpec(spawnAvatar, invulnerable, holdsChunk, maxAbsenceTicks, initialHealth, extra.copy());
        }
    }
}
