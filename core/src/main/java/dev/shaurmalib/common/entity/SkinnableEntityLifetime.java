package dev.shaurmalib.common.entity;

/**
 * Параметри часу життя і "анти-кік" fade-out fази skinnable-сутності
 * (план, п. 3.22) — узагальнення захардкоджених констант
 * {@code DeathCorpseEntity} ({@code lifetimeTicks = 300},
 * {@code FADE_OUT_TICKS = 10}).
 * <p>
 * {@code fadeOutTicks} — це не косметика: за {@code fadeOutTicks} тіків до
 * фактичного {@code discard()} сутність стає невидимою й непіклабельною
 * ЗАЗДАЛЕГІДЬ, а не миттєво зникає в останній тік. Це задокументований
 * фікс race condition, коли клієнт встигає надіслати
 * {@code ServerboundInteractPacket} (клік по трупу) за 1-2 тіки до
 * дискарду через мережеву затримку — без запасу сервер бачить вже
 * видалений {@code Entity} і кікає гравця з "Attempting to attack an
 * invalid entity". {@code SkinnableEntityBase} (forge-шар) застосовує
 * цей запис як вбудовану поведінку — консюмеру не треба відтворювати цей
 * фікс самому в кожній новій skinnable-сутності.
 *
 * @param lifetimeTicks загальний час життя сутності перед {@code discard()}.
 *                      Оригінал: {@code 300} (15с).
 * @param fadeOutTicks  запас часу ДО кінця {@code lifetimeTicks}, протягом
 *                      якого сутність вже невидима/непіклабельна, але ще
 *                      технічно існує. Оригінал: {@code 10} (0.5с — запас
 *                      на мережеву затримку).
 */
public record SkinnableEntityLifetime(int lifetimeTicks, int fadeOutTicks) {

    public SkinnableEntityLifetime {
        if (fadeOutTicks >= lifetimeTicks) {
            throw new IllegalArgumentException(
                    "fadeOutTicks (" + fadeOutTicks + ") мусить бути меншим за lifetimeTicks (" + lifetimeTicks + ")");
        }
    }

    /** Ті самі значення, що {@code DeathCorpseEntity}: 300 тіків життя, 10 тіків fade-out. */
    public static SkinnableEntityLifetime standard() {
        return new SkinnableEntityLifetime(300, 10);
    }
}
