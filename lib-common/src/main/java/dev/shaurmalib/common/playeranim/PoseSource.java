package dev.shaurmalib.common.playeranim;

/**
 * Джерело анімації для одного {@link PoseLayerId}-шару (план, п. 3.24).
 * Чисті дані — не залежить від {@code dev.kosmx.playerAnim.*}, щоб
 * {@code lib-common} лишався бібліотеко-агностичним (той самий принцип,
 * що {@link dev.shaurmalib.common.item.ItemCameraTrack}: common описує
 * намір, lib-forge інтерпретує його через конкретне API).
 * <p>
 * <b>Hold vs one-shot не кодується цим записом.</b> Це властивість самого
 * файлу: {@code KeyframeAnimation.isInfinite} береться з поля
 * {@code looping} у {@code .json}. Кліп сам виставляє {@code isActive()=false}
 * по {@code stopTick}, який PAL рахує з довжини файлу, тож ніякого
 * {@code durationTicks} у коді консюмера не потрібно. Поле {@link #looping}
 * — лише <i>документація наміру</i> в місці виклику; розсинхрон із файлом
 * нешкідливий, бо рушій бере {@code isInfinite} з самого кліпа, а не звідси.
 * <p>
 * Fade: {@link #fadeInTicks} — плавна поява (0 = миттєво), {@link
 * #fadeOutTicks} — плавне згасання при {@code stop} і <b>перед природним
 * кінцем one-shot</b> (0 = миттєво). Обидва в межах {@code [0,
 * MAX_FADE_TICKS]}: fade довший за 2 с безглуздий, а надвеликі значення
 * тримали б шар активним без потреби.
 *
 * @param namespace     простір імен ресурсу (типово namespace консюмера)
 * @param resourceId    ідентифікатор {@code KeyframeAnimation}
 *                      ({@code assets/<ns>/player_animations/<name>.json})
 * @param looping       намір консюмера (див. вище), рушій не спирається на нього
 * @param fadeInTicks   тривалість появи в тіках, {@code 0..40}
 * @param fadeOutTicks  тривалість згасання в тіках, {@code 0..40}
 * @param ease          крива fade; {@code null} замінюється на {@link PoseEase#DEFAULT}
 */
public record PoseSource(String namespace, String resourceId, boolean looping,
                         int fadeInTicks, int fadeOutTicks, PoseEase ease) {

    /** Верхня межа fade у тіках (2 секунди). */
    public static final int MAX_FADE_TICKS = 40;

    public PoseSource {
        if (fadeInTicks < 0 || fadeInTicks > MAX_FADE_TICKS) {
            throw new IllegalArgumentException(
                    "fadeInTicks мусить бути в [0," + MAX_FADE_TICKS + "], а не " + fadeInTicks);
        }
        if (fadeOutTicks < 0 || fadeOutTicks > MAX_FADE_TICKS) {
            throw new IllegalArgumentException(
                    "fadeOutTicks мусить бути в [0," + MAX_FADE_TICKS + "], а не " + fadeOutTicks);
        }
        if (ease == null) {
            ease = PoseEase.DEFAULT;
        }
    }

    /** Сумісність зі старим API: без fade, поведінка як до появи рушія. */
    public PoseSource(String namespace, String resourceId, boolean looping) {
        this(namespace, resourceId, looping, 0, 0, PoseEase.DEFAULT);
    }

    /** Сумісність зі старим API. */
    public static PoseSource jsonAnimation(String namespace, String resourceId, boolean looping) {
        return new PoseSource(namespace, resourceId, looping);
    }

    /** Поза, що тримається, доки консюмер сам не викличе {@code stop} (файл із {@code looping:true}). */
    public static PoseSource hold(String namespace, String resourceId) {
        return new PoseSource(namespace, resourceId, true);
    }

    /** Одноразова поза: рушій сам завершить її по кінці кліпа (файл із {@code looping:false}). */
    public static PoseSource oneShot(String namespace, String resourceId) {
        return new PoseSource(namespace, resourceId, false);
    }

    public PoseSource withFade(int fadeIn, int fadeOut) {
        return new PoseSource(namespace, resourceId, looping, fadeIn, fadeOut, ease);
    }

    public PoseSource withFadeIn(int fadeIn) {
        return withFade(fadeIn, fadeOutTicks);
    }

    public PoseSource withFadeOut(int fadeOut) {
        return withFade(fadeInTicks, fadeOut);
    }

    public PoseSource withEase(PoseEase newEase) {
        return new PoseSource(namespace, resourceId, looping, fadeInTicks, fadeOutTicks, newEase);
    }
}
