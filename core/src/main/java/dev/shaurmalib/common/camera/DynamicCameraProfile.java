package dev.shaurmalib.common.camera;

/**
 * Конфігурований набір амплітуд/швидкостей фонової кінематографічної
 * камери — узагальнення констант із {@code DynamicCameraHandler.java}
 * (367 рядків в оригіналі: дихання/bob при ходьбі/tremor-шум/jitter/
 * landing-trauma/FOV-шок).
 * <p>
 * Оригінал мав ці значення захардкодженими константами в коді. Тут —
 * дані, які мод (snipers чи maniac) задає через {@code style.yml}, щоб
 * інший режим міг мати інше "відчуття" камери без форку класу.
 * <p>
 * Це — окремий, ЗАВЖДИ активний шар (на відміну від {@link dev.shaurmalib.common.item.ItemCameraSessionState},
 * який активний лише під час use-анімації конкретного предмета). Обидва
 * шари застосовуються АДИТИВНО: спершу ця фонова хитавиця, потім
 * offset предмета — порядок фіксується в lib-forge, щоб два джерела
 * зміщення не переписували одне одного.
 */
public record DynamicCameraProfile(
        float breathAmplitudeDeg,
        float breathSpeed,
        float bobAmplitudeX,
        float bobAmplitudeY,
        float bobSpeed,
        float tremorAmplitudeDeg,
        float jitterAmplitudeDeg,
        float landingTraumaMaxDeg,
        float landingTraumaDecaySpeed,
        float fovShockMaxDeg,
        float fovShockDecaySpeed
) {
    /** Значення з оригінального DynamicCameraHandler — дефолт для snipers_shaurma. */
    public static DynamicCameraProfile snipersDefault() {
        return new DynamicCameraProfile(
                0.35f, 1.2f,
                0.015f, 0.02f, 1.6f,
                0.08f,
                0.05f,
                4.0f, 6.0f,
                3.0f, 5.0f
        );
    }

    /** Пласкіший, "спокійніший" профіль — стартова точка для режиму, який хоче менш кінематографічну камеру. */
    public static DynamicCameraProfile calm() {
        return new DynamicCameraProfile(
                0.15f, 0.8f,
                0.006f, 0.008f, 1.2f,
                0.02f,
                0.015f,
                1.5f, 6.0f,
                1.0f, 5.0f
        );
    }
}
