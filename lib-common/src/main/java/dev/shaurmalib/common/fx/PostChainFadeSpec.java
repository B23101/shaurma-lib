package dev.shaurmalib.common.fx;

/**
 * Тайминги плавного переходу для повноекранного post-chain ефекту (план:
 * "затемнення — перемикач з плавним виходом і появою"). Керує тим, як
 * {@code intensity}-uniform шейдера рухається між 0 (ефект невидимий) і 1
 * (повна сила ефекту) при вмиканні/вимиканні через
 * {@link PostChainEffectCauses#activate}/{@link PostChainEffectCauses#deactivate}.
 * <p>
 * Чиста структура даних (0 Minecraft/Forge-імпортів), як і
 * {@link PostChainEffectSpec} — фактичний розрахунок інтенсивності по часу
 * робить {@code dev.shaurmalib.forge.fx.ScreenEffectPostChain} на клієнті.
 *
 * @param fadeInMs  тривалість наростання інтенсивності 0→1 при активації, мс.
 *                  0 — миттєва поява без переходу.
 * @param fadeOutMs тривалість згасання інтенсивності 1→0 при деактивації, мс.
 *                  0 — миттєве зникнення без переходу.
 */
public record PostChainFadeSpec(int fadeInMs, int fadeOutMs) {
    public PostChainFadeSpec {
        if (fadeInMs < 0 || fadeOutMs < 0) {
            throw new IllegalArgumentException("Тривалість fade не може бути від'ємною.");
        }
    }

    /** Миттєвий перемикач без жодного переходу — попередня поведінка модуля. */
    public static PostChainFadeSpec instant() {
        return new PostChainFadeSpec(0, 0);
    }

    /** Однакова тривалість появи і зникнення. */
    public static PostChainFadeSpec symmetric(int ms) {
        return new PostChainFadeSpec(ms, ms);
    }
}
