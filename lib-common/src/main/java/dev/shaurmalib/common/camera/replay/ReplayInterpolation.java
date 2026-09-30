package dev.shaurmalib.common.camera.replay;

/**
 * Як камера рухається між сусідніми точками {@link ReplayClip}.
 *
 * <ul>
 *   <li>{@link #LINEAR} — рівномірно, без прискорення (механічний «рейковий» проїзд);</li>
 *   <li>{@link #SMOOTH} — ease-in-out на КОЖНОМУ відрізку: камера м'яко
 *       розганяється й гальмує біля кожної точки (добре для «зупинок»
 *       на об'єктах);</li>
 *   <li>{@link #SPLINE} — одна плавна крива Catmull-Rom через усі точки без
 *       зупинок на них (класичний «дрон/долі» проліт). Дефолт.</li>
 * </ul>
 */
public enum ReplayInterpolation {
    LINEAR,
    SMOOTH,
    SPLINE
}
