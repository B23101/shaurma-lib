package dev.shaurmalib.common.animation;

/**
 * Один фрейм записаного шляху руху (план, п. 3.16) — узагальнення
 * {@code AnimRecordFrame}/{@code RecordedPath.PathFrame} snipers_shaurma.
 * <p>
 * {@code bodyYaw} навмисно відсутнє й НЕ записується — під час відтворення
 * рушій рахує його сам через ванільну механіку сутності, так само, як в
 * оригіналі (коментар джерела: "bodyYaw не пишемо, vanilla рахує його сам
 * під час playback").
 */
public record RecordedPathFrame(double x, double y, double z, float yaw, float pitch) {
}
