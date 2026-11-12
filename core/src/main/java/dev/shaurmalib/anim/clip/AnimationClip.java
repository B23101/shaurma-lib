package dev.shaurmalib.anim.clip;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Незмінний кліп анімації. Створюється парсером ({@link dev.shaurmalib.anim.io.ClipParser}),
 * спільний для всіх сутностей (стан програвання живе в {@code ClipPlayer}).
 *
 * Значення треків зберігаються в «просторі Blockbench» (градуси, пікселі, як у файлі):
 * перетворення в простір конкретної моделі (ванільний {@code ModelPart} чи geo-кістка)
 * робить адаптер версії — так один файл однаково працює всюди.
 */
public final class AnimationClip {

    public enum LoopMode {
        /** Один раз; сам згасає наприкінці (fade-out закінчується рівно на останньому кадрі). */
        ONCE,
        /** Циклічно, поки не зупинять. */
        LOOP,
        /** До кінця і застигає на останньому кадрі, поки не зупинять (пози: лежання, гак). */
        HOLD;

        public static LoopMode parse(Object raw) {
            if (raw instanceof Boolean b) return b ? LOOP : ONCE;
            if (raw == null) return ONCE;
            String s = String.valueOf(raw).trim().toLowerCase(java.util.Locale.ROOT);
            return switch (s) {
                case "true", "loop", "repeat" -> LOOP;
                case "hold", "hold_on_last_frame", "hold_last" -> HOLD;
                default -> ONCE;
            };
        }
    }

    /** Як кліп показується від першої особи. */
    public enum FirstPerson {
        /** Ванільні руки й предмет (кліп видно лише ззовні). */
        VANILLA,
        /** Рушій сам малює анімовані руки, предмети й пропси від першої особи. */
        MODEL,
        /** Сховати руки від першої особи, поки грає кліп. */
        HIDE;

        public static FirstPerson parse(Object raw) {
            if (raw instanceof Boolean b) return b ? MODEL : VANILLA;
            if (raw == null) return VANILLA;
            String s = String.valueOf(raw).trim().toLowerCase(java.util.Locale.ROOT);
            return switch (s) {
                case "model", "true", "arms", "body" -> MODEL;
                case "hide", "hidden", "none" -> HIDE;
                default -> VANILLA;
            };
        }
    }

    private final String id;
    private final String rig;
    private final float length;
    private final LoopMode loop;
    private final Map<String, BoneTrack> tracks;
    private final List<ClipEvent> events;
    private final List<PropSpec> props;
    private final Defaults defaults;

    /**
     * Налаштування, які автор кліпа може задати у файлі (а мод — перекрити в {@code PlayRequest}).
     * {@code NaN}/{@code null} — «не задано, бери з запиту або дефолт рушія».
     *
     * @param lookWeight наскільки голова продовжує дивитися за камерою поверх кліпа (0..1)
     * @param stride     для циклів ходьби: скільки циклів кліпа на один ванільний крок
     * @param cameraPosition камера першої особи йде за оком анімованої голови (0 — ні, 1 — повністю)
     * @param cameraRotation камера повертається за головою/тілом (0..1); мишка працює поверх
     */
    public record Defaults(float blendIn, float blendOut, String mask, String slot, float lookWeight,
                           boolean additive, float speed, FirstPerson firstPerson, float stride,
                           float firstPersonPitch, float[] firstPersonOffset,
                           float cameraPosition, float cameraRotation) {
        /**
         * «Вʼюмодель» від першої особи: {@code [x, y, z, нахил°]} — зсув у пікселях (x вправо, y вгору, z назад)
         * і нахил моделі вгору навколо ока. Руки з кліпа третьої особи тримають предмет на рівні грудей —
         * це ~50° нижче погляду, поза кадром рук (±35°). Нахил 30° піднімає їх у нижню частину кадру, а плечі
         * при цьому йдуть за площину камери (не заступають екран). Перевірено скріншотами в грі.
         */
        public static final float[] DEFAULT_FP_OFFSET = { 0f, 0f, 2.5f, 30f };
        public static final Defaults NONE = new Defaults(Float.NaN, Float.NaN, null, null, Float.NaN,
            false, 1f, FirstPerson.VANILLA, 1f, 0f, DEFAULT_FP_OFFSET, 0f, 0f);
    }

    public AnimationClip(String id, String rig, float length, LoopMode loop, Map<String, BoneTrack> tracks,
                         List<ClipEvent> events, List<PropSpec> props, Defaults defaults) {
        this.id = id;
        this.rig = rig;
        this.tracks = Collections.unmodifiableMap(tracks);
        this.events = List.copyOf(events);
        this.props = List.copyOf(props);
        this.defaults = defaults == null ? Defaults.NONE : defaults;
        float maxKey = 0;
        for (BoneTrack t : tracks.values()) maxKey = Math.max(maxKey, t.lastTime());
        for (ClipEvent e : events) maxKey = Math.max(maxKey, e.time());
        this.length = length > 0 ? length : Math.max(maxKey, 1f / 20f);
        this.loop = loop;
    }

    public String id() { return id; }
    public String rig() { return rig; }
    public float length() { return length; }
    public LoopMode loop() { return loop; }
    public Defaults defaults() { return defaults; }
    public List<ClipEvent> events() { return events; }
    public List<PropSpec> props() { return props; }
    public Set<String> bones() { return tracks.keySet(); }

    public BoneTrack track(String bone) { return tracks.get(bone); }

    public boolean animates(String bone) { return tracks.containsKey(bone); }

    /** Локальний час кліпа з урахуванням циклу/утримання. */
    public float wrap(float t) {
        if (t < 0) return 0;
        return switch (loop) {
            case LOOP -> length <= 0 ? 0 : t % length;
            case HOLD, ONCE -> Math.min(t, length);
        };
    }

    @Override
    public String toString() { return "AnimationClip[" + id + ", " + length + "s, " + loop + ", " + tracks.size() + " bones]"; }
}
