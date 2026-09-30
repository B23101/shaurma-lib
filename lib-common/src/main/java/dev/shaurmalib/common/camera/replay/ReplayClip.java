package dev.shaurmalib.common.camera.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Один Replay: впорядкований список точок камери + правила часу.
 *
 * <h3>Скільки завгодно точок</h3>
 * Мінімум одна (тоді камера просто стоїть {@link #durationMs()} мс).
 * Верхньої межі немає.
 *
 * <h3>Інтервали — два способи, які можна мішати</h3>
 * <ul>
 *   <li><b>Спільний інтервал</b> — {@link #sharedIntervalMs()} діє на всі
 *       відрізки, де в точки не задано власний.</li>
 *   <li><b>Свій інтервал</b> — {@link ReplayPoint#intervalToNextMs()} &gt; 0
 *       перекриває спільний для відрізка «ця точка → наступна».</li>
 * </ul>
 *
 * <h3>Примусове переривання</h3>
 * {@link #cutAtMs()} &gt; 0 — «у цей момент клип ВСЕ ОДНО закінчується»:
 * <ul>
 *   <li>коротше за природну тривалість — клип обривається посеред руху;</li>
 *   <li>довше — камера стоїть на останній точці до цього моменту.</li>
 * </ul>
 * {@code 0} — грати рівно природну тривалість (суму інтервалів).
 *
 * <p>Клас незмінний і не залежить від Minecraft — його можна тестувати й
 * використовувати поза грою.</p>
 */
public final class ReplayClip {

    /** Спільний інтервал за замовчуванням, якщо не задано нічого. */
    public static final int DEFAULT_SHARED_INTERVAL_MS = 2000;

    private final String id;
    private final List<ReplayPoint> points;
    private final int sharedIntervalMs;
    private final int cutAtMs;
    private final ReplayInterpolation interpolation;
    private final ReplayTransition transition;
    private final int dipMs;
    private final float shake;
    private final String audience;

    /** Кут yaw «розкручений» без стрибків через ±180 — щоб інтерполяція йшла коротким шляхом. */
    private final float[] yawUnwrapped;
    /** Початок кожного відрізка (мс від початку клипу); довжина = points.size(), останній елемент = природна тривалість. */
    private final long[] segmentStart;

    private ReplayClip(Builder b) {
        if (b.points.isEmpty()) {
            throw new IllegalArgumentException("ReplayClip '" + b.id + "' має містити хоча б одну точку");
        }
        this.id = Objects.requireNonNull(b.id, "id");
        this.points = Collections.unmodifiableList(new ArrayList<>(b.points));
        this.sharedIntervalMs = b.sharedIntervalMs > 0 ? b.sharedIntervalMs : DEFAULT_SHARED_INTERVAL_MS;
        this.cutAtMs = Math.max(0, b.cutAtMs);
        this.interpolation = b.interpolation;
        this.transition = b.transition;
        this.dipMs = Math.max(0, b.dipMs);
        this.shake = Math.max(0f, b.shake);
        this.audience = b.audience == null ? "ALL" : b.audience;

        int n = points.size();
        yawUnwrapped = new float[n];
        yawUnwrapped[0] = points.get(0).yaw();
        for (int i = 1; i < n; i++) {
            float prev = yawUnwrapped[i - 1];
            float diff = wrap180(points.get(i).yaw() - prev);
            yawUnwrapped[i] = prev + diff;
        }
        segmentStart = new long[n];
        long acc = 0;
        for (int i = 0; i < n; i++) {
            segmentStart[i] = acc;
            if (i < n - 1) acc += segmentIntervalMs(i);
        }
    }

    // ── Доступ ───────────────────────────────────────────────────────────

    public String id()                       { return id; }
    public List<ReplayPoint> points()        { return points; }
    public int sharedIntervalMs()            { return sharedIntervalMs; }
    public int cutAtMs()                     { return cutAtMs; }
    public ReplayInterpolation interpolation() { return interpolation; }
    public ReplayTransition transition()     { return transition; }
    public int dipMs()                       { return dipMs; }
    public float shake()                     { return shake; }
    /** Довільний тег для споживача (напр. {@code ALL/SURVIVOR/MANIAC}) — бібліотека його не читає. */
    public String audience()                 { return audience; }

    /** Інтервал відрізка «точка i → точка i+1». */
    public int segmentIntervalMs(int i) {
        int own = points.get(i).intervalToNextMs();
        return own > 0 ? own : sharedIntervalMs;
    }

    /** Сума інтервалів усіх відрізків — скільки камера їде, якщо її не обривати. */
    public long naturalDurationMs() {
        return segmentStart[points.size() - 1];
    }

    /**
     * Скільки клип займає в сценарії: {@link #cutAtMs()}, якщо задано, інакше
     * природна тривалість (для однієї точки — спільний інтервал як «витримка»).
     */
    public long durationMs() {
        if (cutAtMs > 0) return cutAtMs;
        if (points.size() < 2) return sharedIntervalMs;
        return naturalDurationMs();
    }

    // ── Вибірка ──────────────────────────────────────────────────────────

    /**
     * Поза камери на момент {@code tMs} від початку клипу. Значення за межами
     * {@code [0, naturalDuration]} затискаються — камера стоїть на краю.
     */
    public ReplayPose sample(long tMs) {
        int n = points.size();
        if (n == 1) {
            ReplayPoint p = points.get(0);
            return new ReplayPose(p.x(), p.y(), p.z(), wrap180(p.yaw()), clampPitch(p.pitch()), Math.max(0f, p.fov()));
        }
        long natural = naturalDurationMs();
        long t = Math.min(Math.max(tMs, 0L), natural);

        int seg = n - 2;
        for (int i = 0; i < n - 1; i++) {
            if (t < segmentStart[i + 1]) { seg = i; break; }
        }
        long segLen = Math.max(1L, segmentStart[seg + 1] - segmentStart[seg]);
        double u = Math.min(1.0, Math.max(0.0, (double) (t - segmentStart[seg]) / segLen));
        double e = interpolation == ReplayInterpolation.SMOOTH ? u * u * (3.0 - 2.0 * u) : u;

        ReplayPoint a = points.get(seg);
        ReplayPoint b = points.get(seg + 1);

        double x, y, z;
        float yaw, pitch;
        if (interpolation == ReplayInterpolation.SPLINE) {
            ReplayPoint p0 = points.get(Math.max(0, seg - 1));
            ReplayPoint p3 = points.get(Math.min(n - 1, seg + 2));
            x = catmull(p0.x(), a.x(), b.x(), p3.x(), u);
            y = catmull(p0.y(), a.y(), b.y(), p3.y(), u);
            z = catmull(p0.z(), a.z(), b.z(), p3.z(), u);
            yaw = (float) catmull(yawUnwrapped[Math.max(0, seg - 1)], yawUnwrapped[seg],
                yawUnwrapped[seg + 1], yawUnwrapped[Math.min(n - 1, seg + 2)], u);
            pitch = (float) catmull(p0.pitch(), a.pitch(), b.pitch(), p3.pitch(), u);
        } else {
            x = a.x() + (b.x() - a.x()) * e;
            y = a.y() + (b.y() - a.y()) * e;
            z = a.z() + (b.z() - a.z()) * e;
            yaw = (float) (yawUnwrapped[seg] + (yawUnwrapped[seg + 1] - yawUnwrapped[seg]) * e);
            pitch = (float) (a.pitch() + (b.pitch() - a.pitch()) * e);
        }

        float fov;
        if (a.fov() > 0f && b.fov() > 0f) fov = (float) (a.fov() + (b.fov() - a.fov()) * e);
        else if (a.fov() > 0f) fov = a.fov();
        else if (b.fov() > 0f) fov = b.fov();
        else fov = 0f;

        return new ReplayPose(x, y, z, wrap180(yaw), clampPitch(pitch), fov);
    }

    // ── Математика ───────────────────────────────────────────────────────

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t
            + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
            + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    static float wrap180(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private final List<ReplayPoint> points = new ArrayList<>();
        private int sharedIntervalMs = DEFAULT_SHARED_INTERVAL_MS;
        private int cutAtMs = 0;
        private ReplayInterpolation interpolation = ReplayInterpolation.SPLINE;
        private ReplayTransition transition = ReplayTransition.CUT;
        private int dipMs = 600;
        private float shake = 0f;
        private String audience = "ALL";

        private Builder(String id) { this.id = id; }

        public Builder point(ReplayPoint p)               { points.add(p); return this; }
        public Builder point(double x, double y, double z, float yaw, float pitch) {
            return point(ReplayPoint.of(x, y, z, yaw, pitch));
        }
        public Builder point(double x, double y, double z, float yaw, float pitch, int intervalToNextMs) {
            return point(ReplayPoint.of(x, y, z, yaw, pitch).withInterval(intervalToNextMs));
        }
        public Builder points(List<ReplayPoint> all)      { points.addAll(all); return this; }
        /** Один інтервал для всіх відрізків, де в точки не задано свій. */
        public Builder sharedIntervalMs(int ms)           { this.sharedIntervalMs = ms; return this; }
        /** Момент примусового завершення клипу (мс від його початку); 0 — не обривати. */
        public Builder cutAtMs(int ms)                    { this.cutAtMs = ms; return this; }
        public Builder interpolation(ReplayInterpolation i) { this.interpolation = i; return this; }
        public Builder transition(ReplayTransition t, int dipMs) { this.transition = t; this.dipMs = dipMs; return this; }
        public Builder transition(ReplayTransition t)     { this.transition = t; return this; }
        /** Амплітуда «руки оператора» в градусах (0 — статично). Розумні значення: 0.2 … 1.5. */
        public Builder shake(float degrees)               { this.shake = degrees; return this; }
        public Builder audience(String tag)               { this.audience = tag; return this; }

        public ReplayClip build()                         { return new ReplayClip(this); }
    }
}
