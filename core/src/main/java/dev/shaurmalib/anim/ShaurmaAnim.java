package dev.shaurmalib.anim;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.clip.ClipEvent;
import dev.shaurmalib.anim.net.AnimMessage;
import dev.shaurmalib.anim.rig.Rig;
import dev.shaurmalib.anim.runtime.AnimBinding;
import dev.shaurmalib.anim.runtime.AnimEventSink;
import dev.shaurmalib.anim.runtime.AnimSubject;
import dev.shaurmalib.anim.runtime.Animator;
import dev.shaurmalib.anim.runtime.ClipLibrary;
import dev.shaurmalib.anim.runtime.LocomotionSet;
import dev.shaurmalib.anim.runtime.PlayRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Точка входу рушія анімацій ShaurmaLib (клієнтська частина; сервер лише шле {@link AnimMessage}).
 *
 * <h2>Типові задачі</h2>
 * <pre>
 * // одноразова дія (аптечка): маска, шар і fade — з файлу кліпа
 * ShaurmaAnim.play(player.getUUID(), PlayRequest.of("maniacmod:medkit_use"));
 *
 * // стан тіла, поки умова true (лежить, несуть, ремонтує)
 * ShaurmaAnim.bind(AnimBinding.when("maniacmod:downed", s -&gt; isFallen(s.uuid()))
 *     .play("maniacmod:downed_lie").slot(Slot.POSE));
 *
 * // поза тримання предмета, решта тіла живе своїм життям
 * ShaurmaAnim.bind(AnimBinding.holding("maniacmod:flashlight", "maniacmod:flashlight_hold").mask("right_arm"));
 *
 * // замінити ванільну локомоцію гравця на кліпи рушія
 * ShaurmaAnim.setPlayerLocomotion(true);
 * </pre>
 */
public final class ShaurmaAnim {

    private ShaurmaAnim() {}

    private static final ClipLibrary CLIPS = new ClipLibrary();
    private static final Map<UUID, Animator> ANIMATORS = new ConcurrentHashMap<>();
    private static final List<AnimBinding> BINDINGS = new CopyOnWriteArrayList<>();
    private static final List<AnimEventSink> SINKS = new CopyOnWriteArrayList<>();
    private static final Map<String, LocomotionSet> LOCOMOTION = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Pending>> PENDING = new ConcurrentHashMap<>();
    private static volatile boolean playerLocomotion;

    // Перша особа: явний вибір мода (null — не задавав) перекриває автоматику клею лоадера.
    private static volatile Boolean firstPersonArmsExplicit, headCameraExplicit;
    private static volatile boolean firstPersonArmsAuto = true, headCameraAuto = true;
    private static volatile String compatNote = "";

    private record Pending(PlayRequest request, long expiresAtNanos) {}

    private static final AnimEventSink BROADCAST = (subject, clip, event) -> {
        for (AnimEventSink s : SINKS) {
            try {
                s.onEvent(subject, clip, event);
            } catch (RuntimeException ignored) {
                // подія одного мода не валить анімацію
            }
        }
    };

    // ── Бібліотека ───────────────────────────────────────────────────────

    public static ClipLibrary clips() { return CLIPS; }

    public static AnimationClip clip(String id) { return CLIPS.get(id); }

    // ── Аніматори ────────────────────────────────────────────────────────

    /** Аніматор сутності; створюється при першому зверненні або якщо риг змінився (гравець став маньяком). */
    public static Animator animator(UUID id, Rig rig) {
        Animator a = ANIMATORS.get(id);
        if (a != null && a.rig() == rig) return a;
        Animator fresh = new Animator(id, rig, CLIPS);
        LocomotionSet set = locomotionFor(rig);
        if (set != null) fresh.locomotion(set);
        ANIMATORS.put(id, fresh);
        List<Pending> pend = PENDING.remove(id);
        if (pend != null) {
            long now = System.nanoTime();
            for (Pending p : pend) if (p.expiresAtNanos > now) fresh.play(p.request);
        }
        return fresh;
    }

    public static Animator find(UUID id) { return ANIMATORS.get(id); }

    /** Прибрати аніматори сутностей, яких більше немає у світі. */
    public static void retainOnly(Collection<UUID> alive) {
        Set<UUID> keep = alive instanceof Set<UUID> s ? s : new HashSet<>(alive);
        ANIMATORS.keySet().removeIf(id -> !keep.contains(id));
        long now = System.nanoTime();
        PENDING.values().removeIf(l -> { l.removeIf(p -> p.expiresAtNanos <= now); return l.isEmpty(); });
    }

    public static void clearAll() {
        ANIMATORS.clear();
        PENDING.clear();
    }

    /** Тік однієї сутності (викликає адаптер раз на ігровий тік). */
    public static void tick(AnimSubject subject) {
        subject.animator().tick(subject, BINDINGS, BROADCAST);
    }

    // ── Програвання ──────────────────────────────────────────────────────

    /** Грати на сутності. Якщо її ще не видно (пакет прийшов раніше за сутність) — відкладається на 3 с. */
    public static boolean play(UUID entity, PlayRequest request) {
        Animator a = ANIMATORS.get(entity);
        if (a != null) return a.play(request);
        if (!CLIPS.has(request.clipId())) return false;
        PENDING.computeIfAbsent(entity, k -> new CopyOnWriteArrayList<>())
            .add(new Pending(request, System.nanoTime() + 3_000_000_000L));
        return true;
    }

    public static void stop(UUID entity, String clipId, float fadeOut) {
        Animator a = ANIMATORS.get(entity);
        if (a != null) a.stopClip(clipId, fadeOut);
        List<Pending> p = PENDING.get(entity);
        if (p != null) p.removeIf(x -> x.request.clipId().equals(clipId));
    }

    /** Застосувати повідомлення з сервера. */
    public static void handle(AnimMessage m) {
        switch (m.op()) {
            case PLAY -> play(m.entity(), m.toRequest());
            case STOP_CLIP -> stop(m.entity(), m.clip(), m.fadeOut());
            case STOP_SLOT -> {
                Animator a = ANIMATORS.get(m.entity());
                if (a != null && m.slot() != null) a.stop(m.slot(), m.fadeOut());
            }
            case STOP_ALL -> {
                Animator a = ANIMATORS.get(m.entity());
                if (a != null) a.stopAll(m.fadeOut());
                PENDING.remove(m.entity());
            }
        }
    }

    // ── Правила, події, локомоція ────────────────────────────────────────

    public static AnimBinding bind(AnimBinding b) {
        BINDINGS.removeIf(x -> x.id().equals(b.id()));
        BINDINGS.add(b);
        return b;
    }

    public static void unbind(String id) { BINDINGS.removeIf(x -> x.id().equals(id)); }

    public static List<AnimBinding> bindings() { return List.copyOf(BINDINGS); }

    public static void onEvent(AnimEventSink sink) { SINKS.add(sink); }

    /** Набір локомоції для ригу ({@code player} або {@code geo:ns:модель}). */
    public static void setLocomotion(String rigName, LocomotionSet set) {
        if (set == null) LOCOMOTION.remove(rigName); else LOCOMOTION.put(rigName, set);
        for (Iterator<Animator> it = ANIMATORS.values().iterator(); it.hasNext(); ) {
            Animator a = it.next();
            if (a.rig().name().equals(rigName)) a.locomotion(locomotionFor(a.rig()));
        }
    }

    /** Чи замінює рушій ванільні ходьбу/біг/стояння гравця власними кліпами ({@code shaurma_lib:player/*}). */
    public static void setPlayerLocomotion(boolean enabled) {
        playerLocomotion = enabled;
        for (Animator a : ANIMATORS.values()) {
            if (a.rig() == Rig.PLAYER) a.locomotion(locomotionFor(Rig.PLAYER));
        }
    }

    public static boolean playerLocomotion() { return playerLocomotion; }

    // ── Перша особа ──────────────────────────────────────────────────────

    /**
     * Чи підміняє рушій ванільні руки першої особи для кліпів з {@code first_person: model/hide}.
     * Вимкнено — від першої особи рушій нічого не чіпає (ні руки, ні {@code hide}), лишаються ванільні руки
     * або руки first-person мода. Явний виклик перекриває автодетект (First-person Model, Real Camera).
     */
    public static void setFirstPersonArms(boolean enabled) { firstPersonArmsExplicit = enabled; }

    public static boolean firstPersonArms() {
        Boolean e = firstPersonArmsExplicit;
        return e != null ? e : firstPersonArmsAuto;
    }

    /**
     * Камера першої особи за анімованою головою ({@code camera}/{@code camera_rotation} у кліпі/запиті/правилі).
     * Окрема від рук: з модом справжнього тіла (First-person Model) вона саме доречна — камера стоїть в оці
     * того тіла, яке видно. Автоматично вимикається лише з модом, що сам веде камеру за головою (Real Camera):
     * два міксини на {@code Camera.setup} тягнули б камеру в різні боки.
     */
    public static void setHeadCamera(boolean enabled) { headCameraExplicit = enabled; }

    public static boolean headCamera() {
        Boolean e = headCameraExplicit;
        return e != null ? e : headCameraAuto;
    }

    /** Повернути автоматичний вибір (скасувати {@link #setFirstPersonArms}/{@link #setHeadCamera}). */
    public static void resetFirstPersonOverrides() {
        firstPersonArmsExplicit = null;
        headCameraExplicit = null;
    }

    /**
     * Для клею лоадера: знайдено мод першої особи.
     * @param drawsBody   мод малює справжнє тіло від першої особи — руки рушія зайві
     * @param drivesCamera мод сам ставить камеру в голову моделі — камера рушія конфліктує
     * @param note        що знайдено (для {@code /sanim info})
     */
    public static void applyFirstPersonCompat(boolean drawsBody, boolean drivesCamera, String note) {
        firstPersonArmsAuto = !drawsBody;
        headCameraAuto = !drivesCamera;
        compatNote = note == null ? "" : note;
    }

    private static LocomotionSet locomotionFor(Rig rig) {
        if (rig == Rig.PLAYER && !playerLocomotion) return null;
        LocomotionSet s = LOCOMOTION.get(rig.name());
        if (s == null && rig == Rig.PLAYER) s = defaultPlayerLocomotion();
        return s;
    }

    /** Вбудований набір бібліотеки ({@code assets/shaurma_lib/shaurma_anim/player_locomotion.json}). */
    public static LocomotionSet defaultPlayerLocomotion() {
        String p = "shaurma_lib:player/";
        return LocomotionSet.create()
            .set(LocomotionSet.State.IDLE, p + "idle")
            .set(LocomotionSet.State.WALK, p + "walk")
            .set(LocomotionSet.State.RUN, p + "run")
            .set(LocomotionSet.State.SNEAK, p + "sneak")
            .set(LocomotionSet.State.SNEAK_WALK, p + "sneak_walk")
            .set(LocomotionSet.State.JUMP, p + "jump")
            .set(LocomotionSet.State.FALL, p + "fall")
            .set(LocomotionSet.State.SWIM, p + "swim")
            .set(LocomotionSet.State.CLIMB, p + "climb")
            .attack(p + "attack", "right_arm+torso*0.6+head*0.3");
    }

    /** Діагностика (команда /shaurma anim). */
    public static List<String> debugLines() {
        List<String> out = new ArrayList<>();
        out.add("clips=" + CLIPS.size() + " animators=" + ANIMATORS.size() + " bindings=" + BINDINGS.size()
            + " playerLocomotion=" + playerLocomotion);
        out.add("firstPersonArms=" + firstPersonArms() + (firstPersonArmsExplicit != null ? " (мод)" : " (авто)")
            + " headCamera=" + headCamera() + (headCameraExplicit != null ? " (мод)" : " (авто)")
            + (compatNote.isEmpty() ? "" : " compat=" + compatNote));
        return out;
    }

    /** Подія для зовнішніх слухачів (тести/налагодження). */
    public static void emit(AnimSubject s, AnimationClip c, ClipEvent e) { BROADCAST.onEvent(s, c, e); }
}
