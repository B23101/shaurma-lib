package dev.shaurmalib.forge.stamina;

/**
 * Правила stamina для одного гравця. Час задається в секундах, значення
 * витрати/відновлення — stamina за секунду.
 * <p>
 * <b>Спринт.</b> Поки stamina активна, бібліотека сама блокує спринт (і на
 * клієнті, і на сервері — див. {@code MixinLivingEntityStaminaSprint}):
 * <ul>
 *   <li>stamina {@code == 0} — бігти не можна;</li>
 *   <li>після повного виснаження гравець "перезаряджається" — бігти не
 *       можна, доки stamina не відновиться до
 *       {@link #sprintResumeFraction()} від максимуму;</li>
 *   <li>інакше спринт вмикається як у ваніли, але щотіку витрачає stamina
 *       (а не відновлює її).</li>
 * </ul>
 * Голод для блокування спринту НЕ використовується.
 */
public record StaminaRules(
        boolean active,
        float maxStamina,
        float drainPerSecond,
        float recoveryPerSecond,
        float emptyRecoveryDelaySeconds,
        float recoveryDelaySeconds,
        boolean recoveryEnabled,
        boolean forceFullHungerWhileActive,
        boolean blockJumpWhenDepleted,
        float sprintResumeFraction
) {
    public StaminaRules {
        if (!Float.isFinite(maxStamina)
                || !Float.isFinite(drainPerSecond)
                || !Float.isFinite(recoveryPerSecond)
                || !Float.isFinite(emptyRecoveryDelaySeconds)
                || !Float.isFinite(recoveryDelaySeconds)
                || maxStamina <= 0
                || drainPerSecond < 0
                || recoveryPerSecond < 0
                || emptyRecoveryDelaySeconds < 0
                || recoveryDelaySeconds < 0
                || !Float.isFinite(sprintResumeFraction)
                || sprintResumeFraction < 0
                || sprintResumeFraction > 1) {
            throw new IllegalArgumentException("Некоректні параметри stamina.");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StaminaRules defaults() {
        return builder().build();
    }

    public static final class Builder {
        private boolean active = true;
        private float maxStamina = 100.0f;
        private float drainPerSecond = 20.0f;
        private float recoveryPerSecond = 25.0f;
        private float emptyRecoveryDelaySeconds = 3.0f;
        private float recoveryDelaySeconds = 2.0f;
        private boolean recoveryEnabled = true;
        private boolean forceFullHungerWhileActive = true;
        private boolean blockJumpWhenDepleted = false;
        private float sprintResumeFraction = 0.2f;

        public Builder active(boolean value) { active = value; return this; }
        public Builder maxStamina(float value) { maxStamina = value; return this; }
        public Builder drainPerSecond(float value) { drainPerSecond = value; return this; }
        public Builder recoveryPerSecond(float value) { recoveryPerSecond = value; return this; }
        public Builder emptyRecoveryDelaySeconds(float value) { emptyRecoveryDelaySeconds = value; return this; }
        public Builder recoveryDelaySeconds(float value) { recoveryDelaySeconds = value; return this; }
        public Builder recoveryEnabled(boolean value) { recoveryEnabled = value; return this; }
        public Builder forceFullHungerWhileActive(boolean value) {
            forceFullHungerWhileActive = value;
            return this;
        }
        public Builder blockJumpWhenDepleted(boolean value) {
            blockJumpWhenDepleted = value;
            return this;
        }

        /**
         * Яку частку максимуму stamina треба відновити після повного
         * виснаження (0), щоб знову дозволити спринт. {@code 0} — спринт
         * заблокований лише поки stamina рівно 0; {@code 1} — доки не
         * відновиться повністю. За замовчуванням {@code 0.2}.
         */
        public Builder sprintResumeFraction(float value) {
            sprintResumeFraction = value;
            return this;
        }

        public StaminaRules build() {
            return new StaminaRules(active, maxStamina, drainPerSecond, recoveryPerSecond,
                    emptyRecoveryDelaySeconds, recoveryDelaySeconds, recoveryEnabled,
                    forceFullHungerWhileActive, blockJumpWhenDepleted, sprintResumeFraction);
        }
    }
}
