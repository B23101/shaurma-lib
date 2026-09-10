package dev.shaurmalib.client.cinematic;

import java.util.ArrayList;
import java.util.List;

/**
 * Один крок {@link CineTimeline} — узагальнення одного {@code State}
 * enum-значення {@code SCCinematicSequencer} (HOLD/WHITEN_IN/
 * MISSION_OVERLAY/COUNTDOWN/WHITEN_OUT/GO_SHOW). Різниця: тривалість,
 * кʼю на вхід/вихід і умова завершення — дані цього об'єкта, не гілки
 * switch у ручному коді, тому додавання нового кроку для нового
 * режиму не вимагає редагування enum і switch в іншому файлі.
 *
 * Два типи кроку:
 *  - {@link #timed}  — фіксована тривалість у мс, як HOLD/WHITEN_IN/
 *    COUNTDOWN/WHITEN_OUT/GO_SHOW у снайперах.
 *  - {@link #awaitingSignal} — чекає зовнішній сигнал (сервер надіслав
 *    {@code CinematicStartCountdownPacket}) АБО захисний timeout —
 *    точна відповідність MISSION_OVERLAY-кроку, який мав власний
 *    {@code MISSION_OVERLAY_TIMEOUT_MS} захист від "сервер так і не
 *    надіслав старт".
 */
public final class CineStep {

    public enum Kind { TIMED, AWAITING_SIGNAL }

    private final String id;
    private final Kind kind;
    private final long durationMs;      // для TIMED — тривалість; для AWAITING_SIGNAL — timeout
    private final List<CineCue> onEnter = new ArrayList<>();

    private CineStep(String id, Kind kind, long durationMs) {
        this.id = id;
        this.kind = kind;
        this.durationMs = durationMs;
    }

    /** Крок фіксованої тривалості — переходить далі сам, коли час вичерпано. */
    public static CineStep timed(String id, long durationMs) {
        return new CineStep(id, Kind.TIMED, durationMs);
    }

    /**
     * Крок, що чекає {@link CineTimeline#signal} з тим самим id, або
     * timeoutMs захисного вимикача — той самий подвійний вихід, що
     * MISSION_OVERLAY мав у snipers (сигнал сервера АБО 6000ms).
     */
    public static CineStep awaitingSignal(String id, long timeoutMs) {
        return new CineStep(id, Kind.AWAITING_SIGNAL, timeoutMs);
    }

    /** Кʼю, що виконується один раз при вході в крок (аналог "if (!flagX) { flagX = true; ... }" у снайперах). */
    public CineStep onEnter(CineCue cue) {
        onEnter.add(cue);
        return this;
    }

    /** Скорочення для кʼю без потреби розрізняти catchingUp. */
    public CineStep onEnter(Runnable action) {
        return onEnter(CineCue.simple(action));
    }

    /**
     * Копія цього кроку з іншою тривалістю, ті самі onEnter-кʼю й
     * {@code kind} — використовується {@link CineRecording#replay}
     * для підміни живих тривалостей на записані з реплею.
     */
    public CineStep withDuration(long newDurationMs) {
        CineStep copy = new CineStep(id, kind, newDurationMs);
        copy.onEnter.addAll(this.onEnter);
        return copy;
    }

    String id() { return id; }
    Kind kind() { return kind; }
    long durationMs() { return durationMs; }
    List<CineCue> onEnterCues() { return onEnter; }
}
