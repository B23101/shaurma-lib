package dev.shaurmalib.common.animation;

/**
 * Один дельта-кадр lookaround-анімації (план, п. 3.16) — узагальнення
 * {@code SDLookaroundPath.Frame} snipers_shaurma. На відміну від
 * {@link RecordedPathFrame} (абсолютні x/y/z/yaw/pitch під одну незмінну
 * точку — формат SCN), тут зберігається лише ДЕЛЬТА кута відносно базового
 * yaw/pitch у момент старту запису — одна записана анімація застосовується
 * до будь-якої кількості точок з довільним базовим кутом, без потреби
 * записувати окремий файл на кожну точку (формат SD).
 */
public record DeltaAngleFrame(float yawDelta, float pitchDelta) {

    /** Нормалізує yaw у діапазон [-180, 180). */
    public static float normalizeYaw(float yaw) {
        float y = yaw % 360f;
        if (y >= 180f) y -= 360f;
        if (y < -180f) y += 360f;
        return y;
    }

    public static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    /** Застосовує цю дельту до базового кута точки — той самий розрахунок,
     *  що клієнт робив при відтворенні в оригіналі. */
    public float appliedYaw(float basePointYaw) {
        return normalizeYaw(basePointYaw + yawDelta);
    }

    public float appliedPitch(float basePointPitch) {
        return clampPitch(basePointPitch + pitchDelta);
    }
}
