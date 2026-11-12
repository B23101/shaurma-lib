package dev.shaurmalib.common.camera.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Послідовність Replay-клипів — «скільки завгодно Replay-анімацій».
 *
 * <h3>Час</h3>
 * Клипи йдуть один за одним. {@link #totalCutMs()} &gt; 0 — «у цей момент
 * весь сценарій ВСЕ ОДНО обривається»: навіть якщо клипи ще не догравали,
 * навіть якщо вони скінчились раніше (тоді камера стоїть на останньому кадрі).
 * Це і є жорстка стеля тривалості (наприклад 10 секунд).
 */
public final class ReplayScript {

    /** Де ми в сценарії в момент {@code t}. */
    public record Located(int clipIndex, long localMs, boolean finished) {}

    private final List<ReplayClip> clips;
    private final long totalCutMs;
    private final long[] clipStartMs;
    private final long plannedMs;

    private ReplayScript(List<ReplayClip> clips, long totalCutMs) {
        this.clips = Collections.unmodifiableList(new ArrayList<>(clips));
        this.totalCutMs = Math.max(0L, totalCutMs);
        this.clipStartMs = new long[clips.size()];
        long acc = 0;
        for (int i = 0; i < clips.size(); i++) {
            clipStartMs[i] = acc;
            acc += clips.get(i).durationMs();
        }
        this.plannedMs = acc;
    }

    public static ReplayScript of(long totalCutMs, List<ReplayClip> clips) {
        return new ReplayScript(clips, totalCutMs);
    }

    public static ReplayScript of(long totalCutMs, ReplayClip... clips) {
        return new ReplayScript(List.of(clips), totalCutMs);
    }

    public static ReplayScript empty() {
        return new ReplayScript(List.of(), 0L);
    }

    public List<ReplayClip> clips()   { return clips; }
    public boolean isEmpty()          { return clips.isEmpty(); }
    public long totalCutMs()          { return totalCutMs; }

    /** Сума тривалостей клипів без урахування стелі. */
    public long plannedMs()           { return plannedMs; }

    /** Реальна тривалість сценарію: стеля, якщо задана, інакше сума клипів. */
    public long totalMs()             { return totalCutMs > 0 ? totalCutMs : plannedMs; }

    public long clipStartMs(int index) { return clipStartMs[index]; }

    /** Знаходить клип і локальний час у ньому; {@code finished} — стеля вже минула. */
    public Located locate(long tMs) {
        if (clips.isEmpty() || tMs >= totalMs()) {
            return new Located(Math.max(0, clips.size() - 1), 0L, true);
        }
        long t = Math.max(0L, tMs);
        for (int i = clips.size() - 1; i >= 0; i--) {
            if (t >= clipStartMs[i]) return new Located(i, t - clipStartMs[i], false);
        }
        return new Located(0, 0L, false);
    }

    /** Поза камери в момент {@code tMs}; {@code null}, якщо сценарій закінчився. */
    public ReplayPose sampleAt(long tMs) {
        Located at = locate(tMs);
        if (at.finished()) return null;
        return clips.get(at.clipIndex()).sample(at.localMs());
    }

    /**
     * Позиції камери через кожні {@code stepMs} — план для сервера, який
     * заздалегідь вантажить чанки. Перша точка — t=0, остання — {@link #totalMs()}.
     */
    public List<ReplayWaypoint> waypoints(long stepMs) {
        List<ReplayWaypoint> out = new ArrayList<>();
        if (clips.isEmpty()) return out;
        long step = Math.max(50L, stepMs);
        long total = totalMs();
        for (long t = 0; t < total; t += step) {
            out.add(waypointAt(t));
        }
        out.add(waypointAt(Math.max(0L, total - 1L)));
        return out;
    }

    private ReplayWaypoint waypointAt(long t) {
        Located at = locate(t);
        ReplayClip clip = clips.get(at.clipIndex());
        long local = at.finished() ? clip.naturalDurationMs() : at.localMs();
        ReplayPose p = clip.sample(local);
        return new ReplayWaypoint(t, p.x(), p.y(), p.z());
    }
}
