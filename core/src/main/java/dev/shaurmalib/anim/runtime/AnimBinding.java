package dev.shaurmalib.anim.runtime;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Декларативне «коли X — грай Y». Замість ручних тік-циклів «знайти гравця → перевірити стан →
 * запустити/зупинити шар» мод реєструє одне правило, а рушій сам:
 * <ul>
 *   <li>щотіка перевіряє умову для кожної сутності свого ригу;</li>
 *   <li>вмикає кліп з кросфейдом, коли умова стала true, і плавно гасить, коли false;</li>
 *   <li>з кількох правил одного шару обирає вищий {@link #priority};</li>
 *   <li>не перебиває ручний {@code play} (аптечка) — правило повернеться, щойно той скінчиться.</li>
 * </ul>
 * Умова — чиста функція від стану: однаковий стан ⇒ та сама анімація, тож нічого не «застрягає».
 *
 * <pre>
 * ShaurmaAnim.bind(AnimBinding.when("maniacmod:downed", s -> isFallen(s.uuid()))
 *     .play("maniacmod:downed_lie").slot(Slot.POSE));
 * ShaurmaAnim.bind(AnimBinding.holding("maniacmod:flashlight", "maniacmod:flashlight_hold")
 *     .slot(Slot.HOLD).mask("right_arm"));
 * </pre>
 */
public final class AnimBinding {

    final String id;
    final Predicate<AnimSubject> condition;
    String clipId;
    java.util.function.Function<AnimSubject, String> clipSelector;
    Slot slot = Slot.ACTION;
    String mask;
    String rig;
    float fadeIn = Float.NaN, fadeOut = Float.NaN, speed = Float.NaN, camPos = Float.NaN, camRot = Float.NaN;
    int priority;

    private AnimBinding(String id, Predicate<AnimSubject> condition) {
        this.id = Objects.requireNonNull(id);
        this.condition = Objects.requireNonNull(condition);
    }

    public static AnimBinding when(String id, Predicate<AnimSubject> condition) {
        return new AnimBinding(id, condition);
    }

    /** Поки в основній руці предмет {@code itemId} ({@code ns:path}) — грати кліп (поза тримання). */
    public static AnimBinding holding(String itemId, String clipId) {
        return new AnimBinding("holding:" + itemId + "->" + clipId,
            s -> itemId.equals(s.inputs().mainHandItem)).play(clipId).slot(Slot.HOLD);
    }

    public AnimBinding play(String clipId) { this.clipId = clipId; return this; }

    /** Кліп обирається з контексту (ремонт vs залив на тому самому шарі). null — «не грати». */
    public AnimBinding play(java.util.function.Function<AnimSubject, String> selector) { this.clipSelector = selector; return this; }

    public AnimBinding slot(Slot s) { this.slot = s; return this; }
    public AnimBinding mask(String spec) { this.mask = spec; return this; }
    public AnimBinding fade(float in, float out) { this.fadeIn = in; this.fadeOut = out; return this; }
    public AnimBinding speed(float s) { this.speed = s; return this; }
    public AnimBinding priority(int p) { this.priority = p; return this; }
    /** Камера першої особи йде за анімованою головою (позиція, поворот 0..1). */
    public AnimBinding camera(float position, float rotation) { this.camPos = position; this.camRot = rotation; return this; }
    /** Лише для сутностей цього ригу ({@code player}, {@code geo:maniacmod:test_maniac}...); null — будь-який. */
    public AnimBinding rig(String rigName) { this.rig = rigName; return this; }

    public String id() { return id; }
    public Slot slot() { return slot; }

    String tag() { return "binding:" + id; }

    String clipFor(AnimSubject s) {
        if (clipSelector != null) return clipSelector.apply(s);
        return clipId;
    }

    boolean appliesTo(String rigName) {
        return rig == null || rig.equals(rigName) || (rig.endsWith("*") && rigName.startsWith(rig.substring(0, rig.length() - 1)));
    }

    PlayRequest request(String clip) {
        PlayRequest r = PlayRequest.of(clip).slot(slot).tag(tag());
        if (mask != null) r.mask(mask);
        if (!Float.isNaN(fadeIn)) r.fadeIn(fadeIn);
        if (!Float.isNaN(fadeOut)) r.fadeOut(fadeOut);
        if (!Float.isNaN(speed)) r.speed(speed);
        if (!Float.isNaN(camPos)) r.camera(camPos, Float.isNaN(camRot) ? 0 : camRot);
        return r;
    }
}
