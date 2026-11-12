package dev.shaurmalib.common.stamina;

/**
 * Чиста state-machine stamina (було: {@code StaminaService.tick}, тісно переплетена з {@code ServerPlayer}).
 * Одна функція {@link #step} на тік; рішення про зовнішній світ повертаються як {@link Result}, а не виконуються тут.
 * Тому однакова на всіх версіях/лоадерах і повністю тестується без гри.
 */
public final class StaminaEngine {
    private StaminaEngine() {}

    /** Що ядро знає про гравця на цьому тіку. */
    public record Input(int tick, boolean sprinting, boolean riding) {}

    /** Що треба зробити зовні. */
    public record Result(boolean stopSprinting) {}

    public static Result step(StaminaRules rules, StaminaState s, Input in) {
        boolean stop = false;
        boolean exhausted = updateExhausted(rules, s, s.value);
        if (exhausted && in.sprinting()) stop = true;

        boolean sprintingNow = !exhausted && in.sprinting() && !in.riding();
        if (sprintingNow && rules.drainPerSecond() > 0) {
            s.value -= rules.drainPerSecond() / 20.0f;
            s.lastUseTick = in.tick();
            if (s.value <= 0) { s.value = 0; stop = true; }
            updateExhausted(rules, s, s.value);
        } else if (rules.recoveryEnabled() && s.value < rules.maxStamina()) {
            if (s.lastUseTick == StaminaState.UNSET) s.lastUseTick = in.tick();
            float delay = s.value <= 0 ? rules.emptyRecoveryDelaySeconds() : rules.recoveryDelaySeconds();
            if (in.tick() - s.lastUseTick >= Math.round(delay * 20.0f)) {
                s.value = Math.min(rules.maxStamina(), s.value + rules.recoveryPerSecond() / 20.0f);
                updateExhausted(rules, s, s.value);
            }
        }
        return new Result(stop);
    }

    /**
     * 0 stamina вмикає «перезарядку», відновлення до {@code max * sprintResumeFraction} вимикає.
     * При fraction == 0 спринт заблокований лише поки stamina рівно 0.
     */
    public static boolean updateExhausted(StaminaRules rules, StaminaState s, float stamina) {
        if (stamina <= 0f) { s.exhausted = true; return true; }
        if (s.exhausted) {
            if (stamina >= rules.maxStamina() * rules.sprintResumeFraction()) { s.exhausted = false; return false; }
            return true;
        }
        return false;
    }

    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
}
