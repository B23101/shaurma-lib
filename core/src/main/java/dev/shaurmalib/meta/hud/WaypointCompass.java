package dev.shaurmalib.meta.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Компас цілей: стан на клієнті + чиста математика напряму. Без Minecraft — тестується як звичайна функція.
 * Кут {@code 0} = ціль прямо перед гравцем, додатний — праворуч. Yaw — як у Minecraft (0 = +Z, 90 = −X).
 */
public final class WaypointCompass {

    public enum Vertical { LEVEL, ABOVE, BELOW }

    /** @param relYaw кут до цілі відносно погляду, −180..180 (праворуч — додатний) */
    public record Reading(Waypoint target, double relYaw, double distance, Vertical vertical, double dy) {
        public String verticalText() {
            return switch (vertical) { case ABOVE -> "вище"; case BELOW -> "нижче"; default -> ""; };
        }
        public String distanceText() {
            return distance >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f км", distance / 1000.0)
                                    : Math.round(distance) + " м";
        }
    }

    /** Різниця висот, після якої пишемо «вище/нижче». */
    public static final double VERTICAL_THRESHOLD = 4.0;

    private static final Map<String, Waypoint> ACTIVE = new LinkedHashMap<>();
    private static long changedAt;

    private WaypointCompass() {}

    public static Reading read(Waypoint wp, double px, double py, double pz, float yawDeg) {
        double dx = wp.x() - px, dz = wp.z() - pz, dy = wp.y() - py;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        // напрям на ціль у системі yaw Minecraft
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = wrap(targetYaw - yawDeg);
        Vertical v = dy > VERTICAL_THRESHOLD ? Vertical.ABOVE : dy < -VERTICAL_THRESHOLD ? Vertical.BELOW : Vertical.LEVEL;
        return new Reading(wp, rel, Math.sqrt(horiz * horiz + dy * dy), v, dy);
    }

    public static double wrap(double deg) {
        deg %= 360.0;
        if (deg >= 180.0) deg -= 360.0;
        if (deg < -180.0) deg += 360.0;
        return deg;
    }

    // ── стан клієнта ─────────────────────────────────────────────────────

    public static synchronized void set(Waypoint wp) {
        ACTIVE.put(wp.id(), wp);
        changedAt = System.currentTimeMillis();
    }

    public static synchronized void remove(String id) {
        if (ACTIVE.remove(id) != null) changedAt = System.currentTimeMillis();
    }

    public static synchronized void clear() { ACTIVE.clear(); }

    public static synchronized void replaceAll(List<Waypoint> list) {
        ACTIVE.clear();
        for (Waypoint w : list) ACTIVE.put(w.id(), w);
        changedAt = System.currentTimeMillis();
    }

    /** Найважливіша ціль (або null). */
    public static synchronized Waypoint primary() {
        return ACTIVE.values().stream().max(Comparator.comparingInt(Waypoint::priority)).orElse(null);
    }

    public static synchronized List<Waypoint> all() { return new ArrayList<>(ACTIVE.values()); }

    public static long changedAt() { return changedAt; }
}
