package dev.shaurmalib.anim.io;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.clip.BoneTrack;
import dev.shaurmalib.anim.clip.Channel;
import dev.shaurmalib.anim.clip.ClipEvent;
import dev.shaurmalib.anim.clip.HoldSpec;
import dev.shaurmalib.anim.clip.Keyframe;
import dev.shaurmalib.anim.clip.PropSpec;
import dev.shaurmalib.anim.clip.StateSpec;
import dev.shaurmalib.anim.math.Easing;
import dev.shaurmalib.anim.molang.Molang;
import dev.shaurmalib.anim.rig.BoneNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Читає кліпи з JSON. Один парсер на всі формати, які трапляються в проєктах:
 * <ul>
 *   <li><b>shaurma_anim</b> (власний; пише Blockbench-плагін {@code tools/blockbench}) — Bedrock-ключі
 *       плюс маска, шар, пропси, події, перша особа;</li>
 *   <li><b>Bedrock / GeckoLib</b> {@code .animation.json} ({@code format_version} 1.8.0);</li>
 *   <li><b>PlayerAnimator</b>-варіант Bedrock ({@code {"vector": [...]}}, {@code hold_on_last_frame}).</li>
 * </ul>
 * Помилки окремого кліпа не валять файл: кліп пропускається, причина йде у {@code warn}.
 */
public final class ClipParser {

    public static final String RIG_PLAYER = "player";

    private ClipParser() {}

    /**
     * @param namespace   простір імен ресурсу ({@code maniacmod})
     * @param fileBase    ім'я файлу без розширення (для id geo-кліпів і діагностики)
     * @param defaultRig  риг, якщо файл його не оголошує ({@code player} або {@code geo:<модель>})
     */
    public static List<AnimationClip> parse(String json, String namespace, String fileBase, String defaultRig,
                                            Consumer<String> warn) {
        Map<String, Object> root = Json.parseObject(json);
        String rig = Json.str(root.get("rig"), defaultRig);
        boolean own = "shaurma_anim".equals(Json.str(root.get("format"), ""));
        Map<String, Object> anims = Json.obj(root.get("animations"));
        List<AnimationClip> out = new ArrayList<>();
        if (anims == null) {
            warn.accept(fileBase + ": немає ключа \"animations\"");
            return out;
        }
        boolean player = RIG_PLAYER.equals(rig);
        for (Map.Entry<String, Object> e : anims.entrySet()) {
            String name = e.getKey();
            if (name.startsWith("animation.")) name = name.substring("animation.".length());
            Map<String, Object> a = Json.obj(e.getValue());
            if (a == null) continue;
            // id: кліпи гравця й власного формату — ns:name; geo-кліпи — ns:файл/name (у різних
            // моделей однакові імена idle/walk).
            String id = (player || own) ? namespace + ":" + name : namespace + ":" + fileBase + "/" + name;
            try {
                out.add(parseClip(id, rig, a, player, warn));
            } catch (RuntimeException ex) {
                warn.accept(fileBase + "/" + name + ": " + ex.getMessage());
            }
        }
        return out;
    }

    static AnimationClip parseClip(String id, String rig, Map<String, Object> a, boolean player, Consumer<String> warn) {
        float length = (float) Json.num(first(a, "length", "animation_length"), -1);
        AnimationClip.LoopMode loop = AnimationClip.LoopMode.parse(a.get("loop"));

        Map<String, BoneTrack> tracks = new LinkedHashMap<>();
        Map<String, Object> bones = Json.obj(a.get("bones"));
        if (bones != null) {
            for (Map.Entry<String, Object> b : bones.entrySet()) {
                Map<String, Object> ch = Json.obj(b.getValue());
                if (ch == null) continue;
                String bone = player ? BoneNames.player(b.getKey()) : b.getKey();
                Channel rot = channel(ch.get("rotation"), 0, id + "/" + bone, warn);
                Channel pos = channel(ch.get("position"), 0, id + "/" + bone, warn);
                Channel scl = channel(ch.get("scale"), 1, id + "/" + bone, warn);
                if (rot == null && pos == null && scl == null) continue;
                tracks.put(bone, new BoneTrack(rot, pos, scl));
            }
        }

        List<ClipEvent> events = new ArrayList<>();
        parseEvents(a, events);
        events.sort(Comparator.comparingDouble(ClipEvent::time));

        List<PropSpec> props = new ArrayList<>();
        Map<String, Object> propsObj = Json.obj(a.get("props"));
        if (propsObj != null) {
            for (Map.Entry<String, Object> p : propsObj.entrySet()) {
                Map<String, Object> po = Json.obj(p.getValue());
                if (po == null) continue;
                String parent = Json.str(po.get("parent"), player ? "right_arm" : null);
                if (PropSpec.isWorldParent(parent)) parent = PropSpec.WORLD;
                else if (player && parent != null) parent = BoneNames.player(parent);
                props.add(new PropSpec(p.getKey(), parent,
                    Json.str(po.get("model"), null), Json.str(po.get("texture"), null), Json.str(po.get("item"), null),
                    visibility(po.get("visible")), Json.str(po.get("hide_held"), null),
                    Json.bool(po.get("emissive"), false), Json.bool(po.get("translucent"), false)));
            }
        }

        // Метадані: власний формат пише їх на рівні кліпа; у Bedrock-файл можна покласти блок "shaurma".
        Map<String, Object> meta = Json.obj(a.get("shaurma"));
        Map<String, Object> m = meta != null ? meta : a;
        AnimationClip.Defaults defaults = new AnimationClip.Defaults(
            (float) Json.num(first(m, "blend_in", "fade_in"), Double.NaN),
            (float) Json.num(first(m, "blend_out", "fade_out"), Double.NaN),
            Json.str(m.get("mask"), null),
            Json.str(first(m, "slot", "layer"), null),
            (float) Json.num(first(m, "look", "look_weight"), Double.NaN),
            Json.bool(m.get("additive"), false) || "additive".equals(Json.str(a.get("blend_mode"), "")),
            (float) Json.num(m.get("speed"), 1),
            AnimationClip.FirstPerson.parse(m.get("first_person")),
            (float) Json.num(m.get("stride"), 1),
            (float) Json.num(m.get("first_person_pitch"), 0),
            vec3(m.get("first_person_offset"), AnimationClip.Defaults.DEFAULT_FP_OFFSET),
            (float) Json.num(m.get("camera"), 0),
            (float) Json.num(m.get("camera_rotation"), 0));

        HoldSpec hold = hold(m.get("hold") != null ? m.get("hold") : a.get("hold"));
        // стан тримання триває, поки предмет у руці: «once» тут означало б «зіграти й стрибнути назад»
        if (hold != null && loop == AnimationClip.LoopMode.ONCE) loop = AnimationClip.LoopMode.HOLD;
        StateSpec state = state(m.get("state") != null ? m.get("state") : a.get("state"), hold);
        if (state != null && hold == null && loop == AnimationClip.LoopMode.ONCE) loop = AnimationClip.LoopMode.HOLD;
        return new AnimationClip(id, rig, length, loop, tracks, events, props, defaults, hold, state);
    }

    // ── Канали й ключові кадри ───────────────────────────────────────────

    private static Channel channel(Object raw, float uniformDefault, String where, Consumer<String> warn) {
        if (raw == null) return null;
        List<Keyframe> keys = new ArrayList<>();
        Map<String, Object> obj = Json.obj(raw);
        if (obj != null && !isSingleValue(obj)) {
            for (Map.Entry<String, Object> k : obj.entrySet()) {
                float t;
                try {
                    t = Float.parseFloat(k.getKey().trim());
                } catch (NumberFormatException ex) {
                    warn.accept(where + ": поганий час ключа '" + k.getKey() + "'");
                    continue;
                }
                Keyframe kf = keyframe(t, k.getValue());
                if (kf != null) keys.add(kf);
            }
        } else {
            Keyframe kf = keyframe(0, raw);
            if (kf != null) keys.add(kf);
        }
        return keys.isEmpty() ? null : new Channel(keys);
    }

    /** {@code {"vector": ...}} або {@code {"post": ...}} — одне значення, а не мапа часів. */
    private static boolean isSingleValue(Map<String, Object> o) {
        return o.containsKey("vector") || o.containsKey("post") || o.containsKey("pre");
    }

    private static Keyframe keyframe(float time, Object v) {
        Map<String, Object> o = Json.obj(v);
        if (o == null) {
            Molang.Expr[] val = vec(v);
            return val == null ? null : new Keyframe(time, val, val, Keyframe.Interp.LINEAR, Easing.LINEAR);
        }
        Keyframe.Interp interp = Keyframe.Interp.parse(Json.str(o.get("lerp_mode"), null));
        Easing easing = Easing.parse(Json.str(o.get("easing"), null));
        Molang.Expr[] post, pre;
        if (o.containsKey("post") || o.containsKey("pre")) {
            post = vec(unwrap(o.get("post")));
            pre = vec(unwrap(o.get("pre")));
            if (post == null) post = pre;
            if (pre == null) pre = post;
        } else {
            post = pre = vec(unwrap(o.containsKey("vector") ? o.get("vector") : o.get("value")));
        }
        if (post == null) return null;
        return new Keyframe(time, pre, post, interp, easing);
    }

    private static Object unwrap(Object v) {
        Map<String, Object> o = Json.obj(v);
        if (o != null && o.containsKey("vector")) return o.get("vector");
        return v;
    }

    /** [x,y,z] / число (рівномірно) / рядок Molang → три вирази. */
    private static Molang.Expr[] vec(Object v) {
        if (v == null) return null;
        List<Object> l = Json.list(v);
        if (l != null) {
            if (l.isEmpty()) return null;
            Molang.Expr x = expr(l.get(0));
            Molang.Expr y = l.size() > 1 ? expr(l.get(1)) : x;
            Molang.Expr z = l.size() > 2 ? expr(l.get(2)) : y;
            return new Molang.Expr[] { x, y, z };
        }
        Molang.Expr e = expr(v);
        return new Molang.Expr[] { e, e, e };
    }

    private static Molang.Expr expr(Object v) {
        if (v instanceof Number n) return Molang.constant(n.doubleValue());
        return Molang.compile(Json.str(v, "0"));
    }

    // ── Події ────────────────────────────────────────────────────────────

    private static void parseEvents(Map<String, Object> a, List<ClipEvent> out) {
        List<Object> list = Json.list(a.get("events"));
        if (list != null) {
            for (Object o : list) {
                Map<String, Object> ev = Json.obj(o);
                if (ev == null) continue;
                out.add(new ClipEvent((float) Json.num(ev.get("time"), 0), Json.str(ev.get("type"), "custom"),
                    Json.str(first(ev, "value", "effect", "name"), ""), ev));
            }
        }
        timed(a.get("sound_effects"), "sound", out);
        timed(a.get("particle_effects"), "particle", out);
        Map<String, Object> timeline = Json.obj(a.get("timeline"));
        if (timeline != null) {
            for (Map.Entry<String, Object> e : timeline.entrySet()) {
                float t = (float) Json.num(e.getKey(), 0);
                List<Object> instr = Json.list(e.getValue());
                if (instr != null) for (Object i : instr) out.add(new ClipEvent(t, "custom", Json.str(i, ""), Map.of()));
                else out.add(new ClipEvent(t, "custom", Json.str(e.getValue(), ""), Map.of()));
            }
        }
    }

    /** Bedrock: {@code {"0.5": {"effect": "x"}}} або {@code {"0.5": [{...},{...}]}}. */
    private static void timed(Object raw, String type, List<ClipEvent> out) {
        Map<String, Object> m = Json.obj(raw);
        if (m == null) return;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            float t = (float) Json.num(e.getKey(), 0);
            List<Object> many = Json.list(e.getValue());
            List<Object> items = many != null ? many : List.of(e.getValue());
            for (Object it : items) {
                Map<String, Object> ev = Json.obj(it);
                if (ev == null) continue;
                out.add(new ClipEvent(t, type, Json.str(first(ev, "effect", "value", "name"), ""), ev));
            }
        }
    }

    /** {@code "hold": "ns:item"} або {@code {"item": "ns:item" | [...], "hand", "priority", "yield_swing", "yield_use"}}. */
    private static HoldSpec hold(Object raw) {
        if (raw == null) return null;
        Map<String, Object> o = Json.obj(raw);
        Object itemRaw = o != null ? first(o, "item", "items") : raw;
        List<String> items = new ArrayList<>();
        List<Object> list = Json.list(itemRaw);
        if (list != null) for (Object x : list) addItems(items, Json.str(x, ""));
        else addItems(items, Json.str(itemRaw, ""));
        if (items.isEmpty()) return null;
        if (o == null) return new HoldSpec(items, "main", 0, true, true);
        return new HoldSpec(items, Json.str(o.get("hand"), "main"), (int) Json.num(o.get("priority"), 0),
            Json.bool(o.get("yield_swing"), true), Json.bool(o.get("yield_use"), true));
    }

    /** {@code "state": true} або об'єкт з полями follow_move, follow_sneak, follow_idle, yield_swing, yield_use; стан тримання (hold) — завжди стан. */
    private static StateSpec state(Object raw, HoldSpec hold) {
        Map<String, Object> o = Json.obj(raw);
        StateSpec d = StateSpec.DEFAULT;
        if (o == null) {
            boolean on = raw instanceof Boolean b ? b : raw != null;
            if (!on && hold == null) return null;
            return hold != null ? new StateSpec(d.followMove(), d.followSneak(), d.followIdle(), hold.yieldSwing(), hold.yieldUse()) : d;
        }
        return new StateSpec(
            (float) Json.num(o.get("follow_move"), d.followMove()),
            (float) Json.num(o.get("follow_sneak"), d.followSneak()),
            (float) Json.num(o.get("follow_idle"), d.followIdle()),
            Json.bool(o.get("yield_swing"), hold == null || hold.yieldSwing()),
            Json.bool(o.get("yield_use"), hold == null || hold.yieldUse()));
    }

    private static void addItems(List<String> out, String s) {
        for (String part : s.split("[,\\s]+")) {
            String id = part.trim();
            if (id.isEmpty()) continue;
            out.add(id.contains(":") ? id : "minecraft:" + id);
        }
    }

    private static List<PropSpec.VisKey> visibility(Object raw) {
        List<PropSpec.VisKey> out = new ArrayList<>();
        Map<String, Object> m = Json.obj(raw);
        if (m == null) {
            if (raw instanceof Boolean b) out.add(new PropSpec.VisKey(0, b));
            return out;
        }
        for (Map.Entry<String, Object> e : m.entrySet()) {
            out.add(new PropSpec.VisKey((float) Json.num(e.getKey(), 0), Json.bool(e.getValue(), true)));
        }
        out.sort(Comparator.comparingDouble(PropSpec.VisKey::time));
        return out;
    }

    private static float[] vec3(Object raw, float[] def) {
        List<Object> l = Json.list(raw);
        if (l == null || l.size() < 3) return def;
        return new float[] { (float) Json.num(l.get(0), 0), (float) Json.num(l.get(1), 0), (float) Json.num(l.get(2), 0),
            l.size() > 3 ? (float) Json.num(l.get(3), 0) : def[3] };
    }

    private static Object first(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.containsKey(k)) return m.get(k);
        return null;
    }
}
