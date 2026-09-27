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
 * лишається один плавний перехід intensity (див.
 * {@link #intensityStep(float, float, int)}).
 *
 * @param intensity     цільова сила затемнення, 0+ (конфіг
 *                      {@code darkzones.intensity} дозволяє 0..3 — див.
 *                      {@code ConfigSchema.DARKZONE_INTENSITY}). 0 = без
 *                      ефекту (звичайне освітлення), 1 = "стандартний"
 *                      максимум {@link #MAX_DARKEN_FACTOR}; значення
 *                      понад 1 дозволяють НАБЛИЗИТИСЬ до повної
 *                      темряви — фактичний множник затемнення все одно
 *                      затискається до 1.0 у {@code darken(...)}
 *                      (mixin), тож "непроглядна чорнота" — це стеля,
 *                      а не помилка конфігурації.
 * @param transitionTicks за скільки тіків intensity плавно доїжджає від
 *                      поточного значення до {@code intensity} — інакше
 *                      вхід/вихід із зони виглядав би різким клацанням
 *                      lightmap.
 */
public record DarkZoneSpec(float intensity, int transitionTicks) {

    /**
     * Множник зниження яскравості lightmap при intensity=1 (частка
     * від 0..1, множиться на luminance у {@code darken(...)}).
     * <p>
     * Раніше 0.55 (дизайн-вимога "гравець ще нормально орієнтується").
     * Піднято до 0.92 на запит — майже повна темрява на дефолтній
     * intensity=1 конфігу, лишаючи мінімальний контраст, щоб контури
     * й блок-світло (факел, вогонь) не зникали геть повністю (0.92, не
     * 1.0 — при factor=1.0 lightmap гаситься до чорного НАВІТЬ там, де
     * поруч яскраве блок-світло, і "виявляється, лишень темно" замість
     * "темно, крім освітлених місць" — ефект перестає читатись як
     * темна зона й починає виглядати як баг чорного екрана).
     * <p>
     * Разом із {@code DARKZONE_INTENSITY} конфігом (0..3, дефолт 0.55)
     * дає реальний градієнт "трохи темніше" → "майже нічого не видно
     * без ліхтарика", а не однакову стелю на будь-якому intensity
     * понад ~1, як було раніше через клампінг у компактному
     * конструкторі.
     */
    public static final float MAX_DARKEN_FACTOR = 0.92f;

    public DarkZoneSpec {
        if (intensity < 0f) intensity = 0f;
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
    /**
     * Один крок лінійної інтерполяції {@code current → target} за
     * {@code transitionTicks} тіків загалом (викликається раз на тік
     * клієнтом, що тримає активний стан — див.
     * {@code dev.shaurmalib.forge.overlay.DarkZoneEffect}).
     * <p>
     * Крок — ЧАСТКА повної відстані {@code |target - current|} за тік
     * (1/transitionTicksTotal), а не фіксована абсолютна величина.
     * Раніше крок був фіксованим {@code 1/transitionTicksTotal} в
     * "одиницях intensity" — коректно, поки intensity обмежувався
     * {@code [0,1]} (перехід від 0 до 1 і справді займав рівно
     * {@code transitionTicksTotal} тіків), але з дозволеним {@code
     * intensity > 1} (конфіг {@code darkzones.intensity} до 3.0, див.
     * клас-докстрінг) той самий фіксований крок розтягував перехід до
     * target=3 утричі довше за налаштований transitionTicks. Частка
     * шляху тримає обіцяне "за transitionTicks тіків доїжджає до
     * цілі" правдивою для будь-якого масштабу intensity.
     */
    public float intensityStep(float current, float target, int transitionTicksTotal) {
        if (transitionTicksTotal <= 0) return target;
        float delta = target - current;
        float step = delta / transitionTicksTotal;
        if (Math.abs(delta) <= Math.abs(step) || Math.abs(delta) < 1e-4f) return target;
        return current + step;
    }
}
