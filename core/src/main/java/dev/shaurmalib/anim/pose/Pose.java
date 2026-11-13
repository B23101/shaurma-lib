package dev.shaurmalib.anim.pose;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.clip.PropSpec;
import dev.shaurmalib.anim.rig.Rig;

import java.util.ArrayList;
import java.util.List;

/**
 * Підсумкова поза кадру для одного ригу. Пишеться {@code Animator.evaluate}, читається адаптером.
 *
 * {@link #touched} — чи хоч один шар рушія змінив кістку (інакше адаптер лишає ванільне значення
 * без жодного перезапису — так рушій не ламає чужі моди, коли нічого не грає).
 */
public final class Pose {

    private final Rig rig;
    private final BoneTransform[] bones;
    private final boolean[] touched;
    private final List<ActiveProp> props = new ArrayList<>();
    /** Наскільки голова лишається за камерою (0..1) — для адаптерів, що ставлять погляд самі. */
    public float lookWeight = 1f;
    /** Кліп, що просить свій вигляд від першої особи (або null). */
    public AnimationClip firstPersonClip;
    public float firstPersonWeight;
    public AnimationClip.FirstPerson firstPersonMode = AnimationClip.FirstPerson.VANILLA;
    public float firstPersonPitch;
    public float[] firstPersonOffset = AnimationClip.Defaults.DEFAULT_FP_OFFSET;
    public boolean hideRightHeld, hideLeftHeld;
    /**
     * Кліп-стан веде цю руку: адаптер прибирає ванільну позу «тримаю предмет» (рука трохи вперед), щоб
     * поза зі стану стояла як у Blockbench, а не на 18° вище. Змах при ходьбі/шифт лишаються в базі.
     */
    public boolean stateRightArm, stateLeftArm;
    /** Камера першої особи за головою: вага позиції і повороту (0 — камера ванільна). */
    public float cameraWeight, cameraRotWeight;
    /** Голова без процедурного погляду (камера сама і є погляд — щоб не рахувати його двічі). */
    public final BoneTransform headNoLook = new BoneTransform();

    public Pose(Rig rig) {
        this.rig = rig;
        this.bones = new BoneTransform[rig.size()];
        this.touched = new boolean[rig.size()];
        for (int i = 0; i < bones.length; i++) bones[i] = new BoneTransform();
    }

    public Rig rig() { return rig; }

    public BoneTransform bone(int i) { return bones[i]; }

    public BoneTransform bone(String name) {
        int i = rig.indexOf(name);
        return i < 0 ? null : bones[i];
    }

    public boolean touched(int i) { return touched[i]; }

    public boolean touched(String name) {
        int i = rig.indexOf(name);
        return i >= 0 && touched[i];
    }

    public void markTouched(int i) { touched[i] = true; }

    public boolean anyTouched() {
        for (boolean t : touched) if (t) return true;
        return !props.isEmpty();
    }

    public List<ActiveProp> props() { return props; }

    public void reset() {
        for (int i = 0; i < bones.length; i++) {
            bones[i].identity();
            touched[i] = false;
        }
        props.clear();
        lookWeight = 1f;
        firstPersonClip = null;
        firstPersonWeight = 0;
        firstPersonMode = AnimationClip.FirstPerson.VANILLA;
        firstPersonPitch = 0;
        firstPersonOffset = AnimationClip.Defaults.DEFAULT_FP_OFFSET;
        hideRightHeld = hideLeftHeld = false;
        stateRightArm = stateLeftArm = false;
        cameraWeight = cameraRotWeight = 0;
        headNoLook.identity();
    }

    /** Видимий пропс: що малювати, на якій кістці, і на якому часі кліпа брати треки його кісток. */
    public record ActiveProp(PropSpec spec, AnimationClip clip, float clipTime, float weight) {}
}
