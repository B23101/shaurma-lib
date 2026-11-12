package dev.shaurmalib.common.playeranim;

/**
 * Крива згасання/появи пози (fade-in / fade-out) без залежності від
 * {@code dev.kosmx.playerAnim.core.util.Ease} — {@code lib-common}
 * лишається PAL-агностичним. Forge-шар мапить {@link #palId()} у
 * {@code Ease.getEase(byte)}; відповідність імен і id гарантує
 * {@code PoseEaseParityTest} (див. {@code tools/playeranim-harness}).
 * <p>
 * <b>Свідомо НЕ включено</b> (перевірено запуском реального PAL 1.0.2-rc1):
 * <ul>
 *   <li>{@code CONSTANT} — завжди повертає 0: як fade це "поза ніколи не
 *       з'явиться", тобто помилка конфігурації. Для миттєвої зміни
 *       існує {@code fadeTicks = 0}.</li>
 *   <li>{@code OUTBOUNCE} — у PAL це баг: зареєстровано як
 *       {@code Easing::outBack}, тобто робить не те, що написано в назві
 *       ({@code OUTBOUNCE(0.5) == OUTBACK(0.5) == 1.0877}). Не публікуємо
 *       криву, що бреше назвою.</li>
 * </ul>
 * Криві з "перельотом" ({@code *BACK}, {@code *ELASTIC}) допустимі, але
 * дають короткочасний вихід за межі пози — це естетичний вибір консюмера.
 */
public enum PoseEase {
    LINEAR(0),
    INSINE(6), OUTSINE(7), INOUTSINE(8),
    INCUBIC(9), OUTCUBIC(10), INOUTCUBIC(11),
    INQUAD(12), OUTQUAD(13), INOUTQUAD(14),
    INQUART(15), OUTQUART(16), INOUTQUART(17),
    INQUINT(18), OUTQUINT(19), INOUTQUINT(20),
    INEXPO(21), OUTEXPO(22), INOUTEXPO(23),
    INCIRC(24), OUTCIRC(25), INOUTCIRC(26),
    INBACK(27), OUTBACK(28), INOUTBACK(29),
    INELASTIC(30), OUTELASTIC(31), INOUTELASTIC(32),
    INBOUNCE(33), INOUTBOUNCE(35);

    /** Розумний дефолт для fade пози: м'який вхід і вихід без перельоту. */
    public static final PoseEase DEFAULT = INOUTSINE;

    private final byte palId;

    PoseEase(int palId) {
        this.palId = (byte) palId;
    }

    /** Числовий id відповідної кривої в PlayerAnimator ({@code Ease.getId()}). */
    public byte palId() {
        return palId;
    }
}
