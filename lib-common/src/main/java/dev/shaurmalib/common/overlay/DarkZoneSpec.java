package dev.shaurmalib.common.overlay;

/**
 * Специфікація "справжньої темряви" (план, {@code DarkZoneEffect}) —
 * НЕ спорідненість із {@link WorldTintSpec}. {@code WorldTintSpec} малює
 * плаский напівпрозорий колір ПОВЕРХ картинки (fade-in/hold/fade-out,
 * cap 25% альфи для кольорових тінтів) — консюмер бачить світ крізь
 * тонований шар скла.
 * <p>
 * {@code DarkZoneSpec} — інший фізичний ефект: множник, що зменшує
 * яскравість lightmap-текстури гри (те, через що рушій рахує, наскільки
 * світлим виглядає кожен блок/сутність при даному block-/sky-світлі).
 * Це не заливка екрана, а СПРАВЖНЄ зниження освітлення — той самий
 * прийом, що true darkness-моди (перехоплення {@code LightTexture}), а
 * не {@code ScreenFadeOverlay}-подібний оверлей. Тому нема кольору,
 * тексту чи fade-циклу з окремими fade-in/hold/fade-out фазами —
 * лишається один плавний перехід intensity 0..1 (див.
 * {@link #intensityStep(float, float, int)}).
 *
 * @param intensity     цільова сила затемнення, 0..1. 0 = без ефекту
 *                      (звичайне освітлення), 1 = максимум, налаштований
 *                      цим спеком (див. {@link #MAX_DARKEN_FACTOR}) —
 *                      НЕ повна чорнота: гравець і на максимумі
 *                      орієнтується по контурах і блок-світлу поруч.
 * @param transitionTicks за скільки тіків intensity плавно доїжджає від
 *                      поточного значення до {@code intensity} — інакше
 *                      вхід/вихід із зони виглядав би різким клацанням
 *                      lightmap.
 */
public record DarkZoneSpec(float intensity, int transitionTicks) {

    /**
     * Найбільше зниження яскравості lightmap при intensity=1 (частка
     * від 0..1, множиться на luminance у {@code darken(...)}).
     * 0.55 навмисно НЕ 1.0 (повна чорнота Darkness-мода на максимумі
     * налаштувань) — дизайн-вимога "гравець ще нормально орієнтується",
     * не "нічого не видно". Блок-світло поруч (факел, вогонь) лишається
     * помітно яскравішим за фон навіть на повній intensity.
     */
    public static final float MAX_DARKEN_FACTOR = 0.55f;

    public DarkZoneSpec {
        intensity = Math.max(0f, Math.min(1f, intensity));
        if (transitionTicks < 0) transitionTicks = 0;
    }

    /** Дефолт: помітно темніше, орієнтуватись і далі можна, плавний вхід/вихід за секунду. */
    public static DarkZoneSpec defaults() {
        return new DarkZoneSpec(1f, 20);
    }

    /**
     * Один крок лінійної інтерполяції {@code current → target} за
     * {@code transitionTicks} тіків загалом (викликається раз на тік
     * клієнтом, що тримає активний стан — див.
     * {@code dev.shaurmalib.forge.overlay.DarkZoneEffect}).
     */
    public float intensityStep(float current, float target, int transitionTicksTotal) {
        if (transitionTicksTotal <= 0) return target;
        float maxDelta = 1f / transitionTicksTotal;
        float delta = target - current;
        if (Math.abs(delta) <= maxDelta) return target;
        return current + Math.signum(delta) * maxDelta;
    }
}
