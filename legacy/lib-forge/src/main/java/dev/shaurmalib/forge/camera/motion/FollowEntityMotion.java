package dev.shaurmalib.forge.camera.motion;

import dev.shaurmalib.common.camera.CameraCurveMath;

/**
 * Камера, що тримається на змінюваній (за easing-кривою) дистанції від
 * ЖИВОЇ/РУХОМОЇ референс-точки (типово: позиція гравця/трупа щотік), а не
 * від фіксованих world-координат — на відміну від {@link PathMotion}/
 * {@link BezierApproachMotion}/{@link LoopPathMotion}, які рухаються між
 * заданими один раз точками простору.
 * <p>
 * Джерело узагальнення: два незалежно написаних, але структурно
 * ідентичних патерни в snipers_shaurma —
 * {@code DropAnimationHandler} (камера над гравцем, що падає: TOP
 * 10→4 блоки за easeOutCubic, HOLD фіксовано, ZOOM 4→0.3 за easeOutCubic,
 * yaw згладжений експоненційним lerp за гравцем, pitch завжди прямовисно
 * вниз) і {@code DeathCameraHandler.tickRise()} (камера над трупом:
 * вертикальний підйом 0→3 блоки за easeOutCubic, yaw ЗАФІКСОВАНИЙ один
 * раз на вході — уникнення сингулярності atan2 при строго вертикальному
 * підйомі — pitch перераховується щотік через look-at на референс-точку).
 * Обидва — "дистанція від рухомої точки, що змінюється за easing-кривою,
 * з окремо контрольованим yaw/pitch" — але з РІЗНИМИ комбінаціями
 * "зафіксований чи перерахований" для yaw і pitch, тому цей клас не
 * нав'язує одну жорстку формулу кута: він рахує лише ДИСТАНЦІЮ
 * ({@link #distanceAt}), а куди саме її прикласти (яка вісь, яким боком)
 * і як рахувати yaw/pitch — вирішує консюмер, так само як
 * {@code moveSmooth()} у {@code FreeCameraEntity} приймає готову позу, а
 * не рахує її сам. Для yaw-згладжування "слідом за рухомою ціллю" (як у
 * {@code DropAnimationHandler.smoothYaw}) консюмент використовує вже
 * наявний {@link dev.shaurmalib.common.camera.CameraCurveMath#lerpAngleShortest}
 * з коефіцієнтом-часткою замість t=1 — та сама формула, підтверджено
 * математично еквівалентна.
 * <p>
 * Немає стану, окрім переданих у конструктор параметрів фази — консюмер
 * сам зберігає екземпляр (один на кожну фазу зі своєю тривалістю/
 * дистанціями) і викликає {@link #distanceAt(long)} щотік, додаючи
 * результат до потрібної координати референс-точки:
 * <pre>{@code
 * FollowEntityMotion topPhase = new FollowEntityMotion(10.0, 4.0, 20); // TOP: 10→4 блоки за 20 тіків (1с)
 * // щотік, referencePoint = гравець/труп ПОТОЧНА позиція (не кешована):
 * double distance = topPhase.distanceAt(elapsedMs);
 * double camY = player.getY() + distance;
 * }</pre>
 */
public final class FollowEntityMotion {

    private final double fromDistance;
    private final double toDistance;
    private final int durationMs;

    public FollowEntityMotion(double fromDistance, double toDistance, int durationMs) {
        this.fromDistance = fromDistance;
        this.toDistance = toDistance;
        this.durationMs = Math.max(1, durationMs);
    }

    /**
     * Поточна дистанція від референс-точки на момент {@code elapsedMs} з
     * початку фази (easeOutCubic, затиснута до [0, durationMs] — та сама
     * крива, що й в обох оригіналах). Консюмер додає це значення до
     * координати референс-точки за потрібною віссю (типово Y — підйом/
     * опускання вздовж вертикалі, як в обох джерелах цього класу).
     */
    public double distanceAt(long elapsedMs) {
        float t = Math.min(1f, (float) elapsedMs / durationMs);
        float eased = CameraCurveMath.easeOutCubic(t);
        return lerp(fromDistance, toDistance, eased);
    }

    /** true, коли фаза досягла {@code toDistance} (elapsedMs >= durationMs). */
    public boolean isFinished(long elapsedMs) {
        return elapsedMs >= durationMs;
    }

    public int durationMs() {
        return durationMs;
    }

    public double fromDistance() { return fromDistance; }
    public double toDistance() { return toDistance; }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * Math.max(0.0, Math.min(1.0, t));
    }
}
