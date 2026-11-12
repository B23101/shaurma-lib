package dev.shaurmalib.meta.hud;

import dev.shaurmalib.meta.io.J;

import java.util.Map;

/**
 * Ціль компаса: «Галявина · 211 м · вище». Ставить сервер режиму (пакет WAYPOINTS) або код клієнта.
 *
 * @param id       ключ (повторна установка з тим самим id замінює ціль)
 * @param label    підпис у плашці
 * @param icon     arrow (стрілка напряму), heli, flag, skull, star
 * @param color    акцент (RGB)
 * @param priority у компасі показується ціль із найбільшим пріоритетом; решта — маркерами на шкалі
 * @param pulse    привертати увагу пульсом (щойно з'явилась/подія відбулась)
 */
public record Waypoint(String id, String label, double x, double y, double z, String icon, int color,
                       int priority, boolean pulse) {

    public Map<String, Object> toJson() {
        return J.o("id", id, "label", label, "x", x, "y", y, "z", z, "icon", icon, "color", color,
                "priority", priority, "pulse", pulse);
    }

    public static Waypoint fromJson(Map<String, Object> o) {
        Object x = o.get("x"), y = o.get("y"), z = o.get("z");
        return new Waypoint(J.str(o, "id", ""), J.str(o, "label", ""),
                x instanceof Number n ? n.doubleValue() : 0, y instanceof Number n ? n.doubleValue() : 0,
                z instanceof Number n ? n.doubleValue() : 0, J.str(o, "icon", "arrow"), J.i(o, "color", 0xFFFFFF),
                J.i(o, "priority", 0), J.bool(o, "pulse", false));
    }
}
