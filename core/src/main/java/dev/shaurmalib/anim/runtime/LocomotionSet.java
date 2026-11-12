package dev.shaurmalib.anim.runtime;

import java.util.EnumMap;
import java.util.Map;

/**
 * Які кліпи грають у шарі {@link Slot#LOCOMOTION} для кожного руху. Відсутній стан
 * падає на запасний ({@link State#fallback}); якщо не знайшлось нічого — частка ваги віддається
 * базі (ванільній позі), тож неповний набір не ламає модель, а просто «не перекриває».
 *
 * Ходьба/біг/присід-крок — ЦИКЛИ, фаза яких іде від пройденої відстані (як ноги у ванілі),
 * а не від часу: ноги не ковзають ні на якій швидкості.
 */
public final class LocomotionSet {

    public enum State {
        // плавання/їзда/елітри/драбина без власного кліпа — ванільна поза (база), а не чужий цикл ходьби
        IDLE(null), WALK(IDLE), RUN(WALK), SNEAK(IDLE), SNEAK_WALK(SNEAK), JUMP(IDLE), FALL(JUMP),
        SWIM(null), CRAWL(SWIM), CLIMB(null), RIDE(null), FLY(null);

        public final State fallback;

        State(State fallback) { this.fallback = fallback; }

        /** Цикли кроку — фаза від відстані. */
        public boolean strideDriven() { return this == WALK || this == RUN || this == SNEAK_WALK || this == CRAWL; }
    }

    final Map<State, String> clips = new EnumMap<>(State.class);
    String attackClip, useClip;
    String attackMask = "upper_body", useMask = "upper_body", mask = "full";
    boolean yieldArmsWhenUsing = true;

    public static LocomotionSet create() { return new LocomotionSet(); }

    public LocomotionSet set(State s, String clipId) {
        if (clipId == null) clips.remove(s); else clips.put(s, clipId);
        return this;
    }

    /** Удар (по фронту замаху) — одноразово в {@link Slot#ACTION}; маска за замовчуванням upper_body. */
    public LocomotionSet attack(String clipId, String maskSpec) {
        this.attackClip = clipId;
        if (maskSpec != null) this.attackMask = maskSpec;
        return this;
    }

    /** Поки предмет використовується (ПКМ утримано) — у {@link Slot#ACTION}. */
    public LocomotionSet use(String clipId, String maskSpec) {
        this.useClip = clipId;
        if (maskSpec != null) this.useMask = maskSpec;
        return this;
    }

    /** Маска всього шару локомоції (наприклад, лише ноги, якщо верх лишається ванільним). */
    public LocomotionSet mask(String spec) {
        this.mask = spec == null ? "full" : spec;
        return this;
    }

    /**
     * Поки предмет використовується (лук, щит, підзорна труба, їжа), руки локомоції плавно поступаються
     * ванільній позі рук — інакше цикл ходьби ламав би прицілювання. За замовчуванням увімкнено.
     */
    public LocomotionSet yieldArmsWhenUsing(boolean yield) {
        this.yieldArmsWhenUsing = yield;
        return this;
    }

    public String clip(State s) { return clips.get(s); }

    public String attackClip() { return attackClip; }

    public String useClip() { return useClip; }
}
