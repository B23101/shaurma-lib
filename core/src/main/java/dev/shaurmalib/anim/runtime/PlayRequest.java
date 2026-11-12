package dev.shaurmalib.anim.runtime;

import dev.shaurmalib.anim.clip.AnimationClip;

/**
 * Що й як грати. Незадані поля ({@code null}/{@code NaN}) беруться з кліпа (його {@code Defaults}),
 * а якщо й там нема — дефолти рушія. Тобто аніматор налаштовує маску/шар/fade у Blockbench,
 * а код пише лише {@code PlayRequest.of("maniacmod:medkit_use")}.
 */
public final class PlayRequest {

    public static final float DEFAULT_FADE_IN = 0.15f;
    public static final float DEFAULT_FADE_OUT = 0.2f;

    final String clipId;
    Slot slot;
    String mask;
    float fadeIn = Float.NaN, fadeOut = Float.NaN, speed = Float.NaN, look = Float.NaN;
    float weight = 1f, startAt;
    float camPos = Float.NaN, camRot = Float.NaN;
    AnimationClip.LoopMode loop;
    Boolean additive;
    boolean restart;
    String tag = "api";

    private PlayRequest(String clipId) { this.clipId = clipId; }

    public static PlayRequest of(String clipId) { return new PlayRequest(clipId); }

    public PlayRequest slot(Slot s) { this.slot = s; return this; }
    public PlayRequest mask(String spec) { this.mask = spec; return this; }
    public PlayRequest fade(float in, float out) { this.fadeIn = in; this.fadeOut = out; return this; }
    public PlayRequest fadeIn(float s) { this.fadeIn = s; return this; }
    public PlayRequest fadeOut(float s) { this.fadeOut = s; return this; }
    public PlayRequest speed(float s) { this.speed = s; return this; }
    public PlayRequest weight(float w) { this.weight = w; return this; }
    public PlayRequest look(float w) { this.look = w; return this; }
    public PlayRequest loop(AnimationClip.LoopMode l) { this.loop = l; return this; }
    public PlayRequest additive(boolean a) { this.additive = a; return this; }
    /** Якщо цей кліп уже грає в шарі — почати заново (за замовчуванням продовжує). */
    public PlayRequest restart(boolean r) { this.restart = r; return this; }
    /** Почати не з нуля (секунди) — для синхронізації з мережі, щоб пізній пакет не відставав. */
    public PlayRequest startAt(float seconds) { this.startAt = Math.max(0, seconds); return this; }
    public PlayRequest tag(String t) { this.tag = t; return this; }
    /** Камера першої особи йде за головою: позиція і поворот, 0..1 (перекриває значення з кліпа). */
    public PlayRequest camera(float position, float rotation) { this.camPos = position; this.camRot = rotation; return this; }

    public String clipId() { return clipId; }
    public String tag() { return tag; }

    Slot resolveSlot(AnimationClip clip) {
        if (slot != null) return slot;
        return Slot.parse(clip.defaults().slot(), Slot.ACTION);
    }

    String resolveMask(AnimationClip clip) {
        if (mask != null) return mask;
        return clip.defaults().mask() != null ? clip.defaults().mask() : "full";
    }

    float resolveFadeIn(AnimationClip clip) {
        if (!Float.isNaN(fadeIn)) return Math.max(0, fadeIn);
        float d = clip.defaults().blendIn();
        return Float.isNaN(d) ? DEFAULT_FADE_IN : Math.max(0, d);
    }

    float resolveFadeOut(AnimationClip clip) {
        if (!Float.isNaN(fadeOut)) return Math.max(0, fadeOut);
        float d = clip.defaults().blendOut();
        return Float.isNaN(d) ? DEFAULT_FADE_OUT : Math.max(0, d);
    }

    float resolveSpeed(AnimationClip clip) {
        return Float.isNaN(speed) ? clip.defaults().speed() : speed;
    }

    float resolveCamPos(AnimationClip clip) { return Float.isNaN(camPos) ? clip.defaults().cameraPosition() : camPos; }

    float resolveCamRot(AnimationClip clip) { return Float.isNaN(camRot) ? clip.defaults().cameraRotation() : camRot; }

    float resolveLook(AnimationClip clip) {
        if (!Float.isNaN(look)) return look;
        float d = clip.defaults().lookWeight();
        return Float.isNaN(d) ? 1f : d;
    }
}
