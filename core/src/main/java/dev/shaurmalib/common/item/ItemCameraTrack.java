package dev.shaurmalib.common.item;

import java.util.ArrayList;
import java.util.List;

/**
 * Незалежний keyframe-трек зміщення камери (yaw/pitch) під час use-анімації
 * анімованого предмета.
 * <p>
 * Джерело підходу: {@code DeminingCameraHandler.java} (оригінал snipers_shaurma).
 * У коментарі оригінального класу задокументований АРХІТЕКТУРНИЙ ФАКТ, який
 * ця бібліотека фіксує буквально, а не переізобретає:
 * <p>
 * читання обертання GeckoLib-кістки ({@code GeoBone.getRotX/Y()}) напряму в
 * момент рендеру камери НАВМИСНО уникається — задокументований баг GeckoLib
 * 4.x (issue #452) ламає UV-мапінг і дає нестабільні кути при такому читанні.
 * Тому замість "прив'язати камеру до кістки в реальному часі" — незалежний
 * keyframe-трек (yaw/pitch по секундах від моменту старту анімації), лише
 * вручну синхронізований з фазами анімації рук (за коментарями/дизайном json).
 * <p>
 * Алгоритм інтерполяції — лінійна між сусідніми keyframe, як в оригіналі.
 */
public final class ItemCameraTrack {

    /** @param timeSeconds час від старту анімації; yawOffset/pitchOffset — зміщення в градусах. */
    public record Keyframe(float timeSeconds, float yawOffset, float pitchOffset) {}

    private final List<Keyframe> keyframes;

    private ItemCameraTrack(List<Keyframe> keyframes) {
        if (keyframes.size() < 2) {
            throw new IllegalArgumentException("ItemCameraTrack потребує щонайменше 2 keyframe");
        }
        this.keyframes = List.copyOf(keyframes);
    }

    /** Загальна тривалість треку в секундах (час останнього keyframe). */
    public float durationSeconds() {
        return keyframes.get(keyframes.size() - 1).timeSeconds();
    }

    /**
     * Лінійна інтерполяція yaw/pitch зміщення на момент {@code elapsedSeconds}
     * від старту анімації. Поза межами треку — тримає крайні значення (clamp),
     * а не екстраполює.
     */
    public float[] interpolate(float elapsedSeconds) {
        if (elapsedSeconds <= keyframes.get(0).timeSeconds()) {
            Keyframe first = keyframes.get(0);
            return new float[]{first.yawOffset(), first.pitchOffset()};
        }
        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (elapsedSeconds >= last.timeSeconds()) {
            return new float[]{last.yawOffset(), last.pitchOffset()};
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe a = keyframes.get(i);
            Keyframe b = keyframes.get(i + 1);
            if (elapsedSeconds >= a.timeSeconds() && elapsedSeconds <= b.timeSeconds()) {
                float span = b.timeSeconds() - a.timeSeconds();
                float t = span <= 0f ? 0f : (elapsedSeconds - a.timeSeconds()) / span;
                float yaw = a.yawOffset() + (b.yawOffset() - a.yawOffset()) * t;
                float pitch = a.pitchOffset() + (b.pitchOffset() - a.pitchOffset()) * t;
                return new float[]{yaw, pitch};
            }
        }
        // Не має статись через межові перевірки вище, але про всяк випадок:
        return new float[]{last.yawOffset(), last.pitchOffset()};
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Keyframe> keyframes = new ArrayList<>();

        public Builder keyframe(float timeSeconds, float yawOffset, float pitchOffset) {
            keyframes.add(new Keyframe(timeSeconds, yawOffset, pitchOffset));
            return this;
        }

        public ItemCameraTrack build() {
            keyframes.sort((a, b) -> Float.compare(a.timeSeconds(), b.timeSeconds()));
            return new ItemCameraTrack(keyframes);
        }
    }
}
