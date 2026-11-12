package dev.shaurmalib.anim.rig;

import java.util.Arrays;
import java.util.Set;

/**
 * Маска шару: вага 0..1 для кожної кістки ригу. Саме вона дає «ноги ходять, руки лікуються»:
 * шар аптечки з маскою {@code upper_body} не чіпає ніг, і їх далі веде локомоція.
 *
 * Синтаксис специфікації (терми через {@code +}, {@code ,} або пробіл):
 * <pre>
 *   upper_body                 група (або кістка + її нащадки)
 *   right_arm+left_arm         об'єднання
 *   full-legs                  {@code -терм} віднімає
 *   upper_body+torso*0.5       {@code *w} (або {@code :w}) — вага терма
 *   body!                      {@code !} — лише сама кістка, без нащадків
 * </pre>
 * Кістки поза ригом (кістки пропсів) маска завжди пропускає з вагою 1.
 */
public final class BoneMask {

    private final float[] weights;
    private final String spec;

    private BoneMask(float[] weights, String spec) {
        this.weights = weights;
        this.spec = spec;
    }

    public float weight(int boneIndex) {
        return boneIndex < 0 || boneIndex >= weights.length ? 1f : weights[boneIndex];
    }

    public boolean isEmpty() {
        for (float w : weights) if (w > 0) return false;
        return true;
    }

    public String spec() { return spec; }

    static BoneMask parse(Rig rig, String spec) {
        float[] w = new float[rig.size()];
        String s = spec.replace(',', '+').replace(' ', '+');
        // "-legs" на початку = "full-legs"
        if (s.startsWith("-")) s = "full" + s;
        int i = 0;
        boolean add = true;
        StringBuilder term = new StringBuilder();
        while (i <= s.length()) {
            char c = i < s.length() ? s.charAt(i) : '+';
            if (c == '+' || c == '-') {
                if (term.length() > 0) apply(rig, w, term.toString(), add);
                term.setLength(0);
                add = c == '+';
            } else {
                term.append(c);
            }
            i++;
        }
        return new BoneMask(w, spec);
    }

    private static void apply(Rig rig, float[] w, String term, boolean add) {
        float weight = 1f;
        int star = Math.max(term.indexOf('*'), term.indexOf(':'));
        if (star > 0) {
            try { weight = Float.parseFloat(term.substring(star + 1)); } catch (NumberFormatException ignored) { }
            term = term.substring(0, star);
        }
        boolean only = term.endsWith("!");
        if (only) term = term.substring(0, term.length() - 1);
        Set<Integer> bones = rig.resolve(term, only);
        for (int b : bones) w[b] = add ? Math.max(0, Math.min(1, weight)) : 0f;
    }

    public static BoneMask full(Rig rig) { return rig.mask("full"); }

    @Override
    public String toString() { return "BoneMask[" + spec + " " + Arrays.toString(weights) + "]"; }
}
