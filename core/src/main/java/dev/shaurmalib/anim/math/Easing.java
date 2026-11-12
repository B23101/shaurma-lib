package dev.shaurmalib.anim.math;

import java.util.Locale;

/**
 * Криві згладжування (ті самі імена, що в Blockbench / GeckoLib / easings.net).
 * Вхід і вихід — частка 0..1 (back/elastic можуть виходити за межі — так і задумано).
 */
public enum Easing {
    LINEAR, STEP,
    EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
    EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
    EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
    EASE_IN_QUINT, EASE_OUT_QUINT, EASE_IN_OUT_QUINT,
    EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
    EASE_IN_CIRC, EASE_OUT_CIRC, EASE_IN_OUT_CIRC,
    EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
    EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
    EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE,
    /** Плавний старт і стоп без перевищення — дефолт для fade шарів. */
    SMOOTHSTEP;

    public double apply(double t) {
        if (t <= 0) return this == STEP ? 0 : (isOvershooting() ? f(t) : 0);
        if (t >= 1) return 1;
        return f(t);
    }

    private boolean isOvershooting() {
        return name().contains("BACK") || name().contains("ELASTIC");
    }

    private double f(double t) {
        final double c1 = 1.70158, c2 = c1 * 1.525, c3 = c1 + 1, c4 = (2 * Math.PI) / 3, c5 = (2 * Math.PI) / 4.5;
        switch (this) {
            case LINEAR: return t;
            case STEP: return t < 1 ? 0 : 1;
            case SMOOTHSTEP: return t * t * (3 - 2 * t);
            case EASE_IN_SINE: return 1 - Math.cos(t * Math.PI / 2);
            case EASE_OUT_SINE: return Math.sin(t * Math.PI / 2);
            case EASE_IN_OUT_SINE: return -(Math.cos(Math.PI * t) - 1) / 2;
            case EASE_IN_QUAD: return t * t;
            case EASE_OUT_QUAD: return 1 - (1 - t) * (1 - t);
            case EASE_IN_OUT_QUAD: return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case EASE_IN_CUBIC: return t * t * t;
            case EASE_OUT_CUBIC: return 1 - Math.pow(1 - t, 3);
            case EASE_IN_OUT_CUBIC: return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case EASE_IN_QUART: return t * t * t * t;
            case EASE_OUT_QUART: return 1 - Math.pow(1 - t, 4);
            case EASE_IN_OUT_QUART: return t < 0.5 ? 8 * Math.pow(t, 4) : 1 - Math.pow(-2 * t + 2, 4) / 2;
            case EASE_IN_QUINT: return Math.pow(t, 5);
            case EASE_OUT_QUINT: return 1 - Math.pow(1 - t, 5);
            case EASE_IN_OUT_QUINT: return t < 0.5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2;
            case EASE_IN_EXPO: return Math.pow(2, 10 * t - 10);
            case EASE_OUT_EXPO: return 1 - Math.pow(2, -10 * t);
            case EASE_IN_OUT_EXPO: return t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
            case EASE_IN_CIRC: return 1 - Math.sqrt(1 - t * t);
            case EASE_OUT_CIRC: return Math.sqrt(1 - Math.pow(t - 1, 2));
            case EASE_IN_OUT_CIRC: return t < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2
                                                  : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
            case EASE_IN_BACK: return c3 * t * t * t - c1 * t * t;
            case EASE_OUT_BACK: return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
            case EASE_IN_OUT_BACK: return t < 0.5 ? (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                                                  : (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
            case EASE_IN_ELASTIC: return -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * c4);
            case EASE_OUT_ELASTIC: return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
            case EASE_IN_OUT_ELASTIC: return t < 0.5 ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * c5)) / 2
                                                     : (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * c5)) / 2 + 1;
            case EASE_IN_BOUNCE: return 1 - bounceOut(1 - t);
            case EASE_OUT_BOUNCE: return bounceOut(t);
            case EASE_IN_OUT_BOUNCE: return t < 0.5 ? (1 - bounceOut(1 - 2 * t)) / 2 : (1 + bounceOut(2 * t - 1)) / 2;
            default: return t;
        }
    }

    private static double bounceOut(double t) {
        final double n1 = 7.5625, d1 = 2.75;
        if (t < 1 / d1) return n1 * t * t;
        if (t < 2 / d1) return n1 * (t -= 1.5 / d1) * t + 0.75;
        if (t < 2.5 / d1) return n1 * (t -= 2.25 / d1) * t + 0.9375;
        return n1 * (t -= 2.625 / d1) * t + 0.984375;
    }

    /** "easeInOutSine", "ease_in_out_sine", "EASE_IN_OUT_SINE", "linear" → значення; невідоме → LINEAR. */
    public static Easing parse(String name) {
        if (name == null || name.isEmpty()) return LINEAR;
        String n = name.trim();
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < n.length(); k++) {
            char c = n.charAt(k);
            if (Character.isUpperCase(c) && k > 0 && Character.isLowerCase(n.charAt(k - 1))) b.append('_');
            b.append(c == '-' || c == ' ' ? '_' : c);
        }
        try {
            return valueOf(b.toString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LINEAR;
        }
    }
}
