package dev.shaurmalib.common.item;

/**
 * Чиста стейт-машина відтворення {@link ItemCameraTrack} — узагальнення
 * {@code DeminingCameraHandler} (367 рядків у оригіналі; клас окремий на
 * кожен предмет із власною камерою). Тут — ОДИН стан на клієнта, бо в
 * один момент активна щонайбільше одна сесія камери предмета (як і в
 * оригіналі: {@code trigger()}/{@code cancelActive()} на статичному класі).
 * <p>
 * Збережено обидва нюанси, які оригінал явно реалізовував правильно:
 * <ul>
 *   <li>плавний lerp offset-у (не миттєве стрибання між keyframe-значеннями
 *       інтерпольованими треком і поточним render-offset-ом камери);</li>
 *   <li>плавне згасання (fade-out) при достроковому {@link #cancel}, а не
 *       різкий стрибок у 0 — той самий {@code LERP_SPEED}-підхід.</li>
 * </ul>
 * <p>
 * <b>ВАЖЛИВО — лерп навмисно per-frame, НЕ per-second (точна відповідність
 * оригіналу):</b> {@code DeminingCameraHandler.onComputeCameraAngles}
 * застосовував {@code smoothYaw += (desiredYaw - smoothYaw) * LERP_SPEED}
 * (LERP_SPEED=0.35) на КОЖНОМУ виклику {@code ViewportEvent.ComputeCameraAngles}
 * (рендер-кадр, не фіксований тік) — БЕЗ множення на delta-час. Це робить
 * швидкість збіжності залежною від частоти кадрів (на 60+ fps дає дуже
 * швидке, майже недемпфоване вирівнювання: залишок ~10⁻¹² від різниці вже
 * після 1 секунди). Перша версія цього класу помилково "покращила" це до
 * framerate-незалежного {@code lerpSpeed * deltaSeconds}, що на порядки
 * сповільнило кінематографічний ефект порівняно з оригіналом і змінило
 * "відчуття" камери, до якого гравці snipers вже звикли. Виправлено:
 * {@link #advance} тепер приймає лише сам факт кадру (без delta), лерп
 * застосовується per-frame, точно як в оригіналі. secondsPerTick лишається
 * параметром окремо (не для лерпу — лише для просування {@code elapsedSeconds}
 * по треку keyframes, де оригінал теж рахував реальний час
 * {@code (now - startMs)/1000f}).
 */
public final class ItemCameraSessionState {

    /** Точна відповідність {@code DeminingCameraHandler.LERP_SPEED} — застосовується per-frame, не per-second. */
    private static final float DEFAULT_LERP_SPEED = 0.35f;

    private ItemCameraTrack activeTrack;
    private float elapsedSeconds;
    private boolean cancelling;

    private float currentYaw;
    private float currentPitch;

    private final float lerpSpeed;

    public ItemCameraSessionState() {
        this(DEFAULT_LERP_SPEED);
    }

    public ItemCameraSessionState(float lerpSpeed) {
        this.lerpSpeed = lerpSpeed;
    }

    public void trigger(ItemCameraTrack track) {
        this.activeTrack = track;
        this.elapsedSeconds = 0f;
        this.cancelling = false;
    }

    public boolean isActive() {
        return activeTrack != null;
    }

    /** Достроково перериває сесію (напр. гравець помер під час анімації) — не миттєво, з плавним згасанням. */
    public void cancel() {
        if (activeTrack != null) {
            this.cancelling = true;
        }
    }

    /**
     * Просуває стан на {@code deltaSeconds} (використовується лише для
     * позиції на треку keyframes — {@code elapsedSeconds}, як і оригінальний
     * {@code (now - startMs)/1000f}) і повертає поточне (вже lerp-згладжене)
     * зміщення [yaw, pitch]. Лерп застосовується PER-FRAME
     * ({@code currentYaw += (target - currentYaw) * lerpSpeed}), БЕЗ
     * множення на {@code deltaSeconds} — точна відповідність оригіналу
     * (див. клас-докстрінг). Після завершення треку або повного згасання
     * після cancel — сесія автоматично завершується і подальші виклики
     * повертають [0, 0] до наступного {@link #trigger}.
     */
    public float[] advance(float deltaSeconds) {
        if (activeTrack == null) {
            return new float[]{0f, 0f};
        }

        float targetYaw;
        float targetPitch;

        if (cancelling) {
            targetYaw = 0f;
            targetPitch = 0f;
        } else {
            elapsedSeconds += deltaSeconds;
            float[] target = activeTrack.interpolate(elapsedSeconds);
            targetYaw = target[0];
            targetPitch = target[1];
            if (elapsedSeconds >= activeTrack.durationSeconds()) {
                cancelling = true; // трек дограно — тепер плавно згасаємо до 0
            }
        }

        // Per-frame лерп, БЕЗ множення на deltaSeconds — 1:1 з оригіналом.
        currentYaw += (targetYaw - currentYaw) * lerpSpeed;
        currentPitch += (targetPitch - currentPitch) * lerpSpeed;

        if (cancelling && Math.abs(currentYaw) < 0.001f && Math.abs(currentPitch) < 0.001f) {
            activeTrack = null;
            currentYaw = 0f;
            currentPitch = 0f;
        }

        return new float[]{currentYaw, currentPitch};
    }
}
