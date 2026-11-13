package dev.shaurmalib.anim.runtime;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.clip.BoneTrack;
import dev.shaurmalib.anim.clip.Channel;
import dev.shaurmalib.anim.clip.ClipEvent;
import dev.shaurmalib.anim.clip.PropSpec;
import dev.shaurmalib.anim.clip.StateSpec;
import dev.shaurmalib.anim.io.ClipParser;
import dev.shaurmalib.anim.molang.MolangContext;
import dev.shaurmalib.anim.pose.BoneTransform;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.BoneMask;
import dev.shaurmalib.anim.rig.BoneNames;
import dev.shaurmalib.anim.rig.Rig;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Аніматор однієї сутності: шари, локомоція, правила, події, погляд.
 *
 * <h2>Час</h2>
 * {@link #tick} рухає годинник на 1/20 с (події, правила, згладжування локомоції);
 * {@link #evaluate} семплить будь-яку точку між тіками ({@code partial}) — тому рух плавний на
 * будь-якому FPS, а результат детермінований (той самий стан ⇒ та сама поза).
 *
 * <h2>Змішування кістки (знизу вгору за {@link Slot})</h2>
 * <pre>
 *   значення = база (ваніль або спокій)
 *   для кожного шару, для кожного кліпа шару:
 *       w = огинаюча(fade) × маска(кістка) × вага
 *       override:  значення = lerp(значення, кліп, w)
 *       additive:  значення += кліп × w
 * </pre>
 * Кістка, якої кліп не має, — прозора: її веде нижчий шар. Звідси «ноги ходять, руки лікують».
 *
 * <h2>Погляд</h2>
 * Голова дивиться за камерою поверх будь-якого кліпа з силою {@code look} (0..1, задається в кліпі):
 * лежачий може крутити головою наполовину, несений — ледь-ледь, а аптечка не заважає зовсім.
 */
public final class Animator {

    /** Ванільний цикл кроку: ноги ходять як {@code cos(limbSwing·0.6662)} → період 2π/0.6662. */
    public static final float VANILLA_STRIDE = (float) (2 * Math.PI / 0.6662);
    public static final float DT = 1f / 20f;

    private final UUID id;
    private final Rig rig;
    private final ClipLibrary library;
    private final AnimInputs inputs = new AnimInputs();
    private final MolangContext molang = new MolangContext();
    private final AnimLayer[] layers;
    private final Map<Slot, String> bindingActive = new EnumMap<>(Slot.class);
    private final int lookBone;
    /** Руки гравця (для позначки «руку веде стан»); -1 у geo-ригах без таких кісток. */
    private final int rArm, lArm;
    private final float[] tmp = new float[3];

    private double clock, prevClock;
    private LocomotionSet loco;
    private final int stateCount = LocomotionSet.State.values().length;
    private final float[] locoW = new float[stateCount], locoPrevW = new float[stateCount], locoTarget = new float[stateCount];
    private float[] accum, accumW;
    private int airTicks;
    private double airStart;
    private boolean wasSwinging;
    private float useSmooth, prevUseSmooth;
    /** Згладжені «зараз удар/використання» — стан тримання поступається ванільній руці (0..1). */
    private float holdSwing, prevHoldSwing, holdUse, prevHoldUse;
    private float frameSwing, frameUse;
    private float lookYawLimit = 180f, lookPitchLimit = 180f;

    public Animator(UUID id, Rig rig, ClipLibrary library) {
        this.id = id;
        this.rig = rig;
        this.library = library;
        this.layers = new AnimLayer[Slot.values().length];
        for (Slot s : Slot.values()) layers[s.ordinal()] = new AnimLayer(s);
        int head = rig.indexOf("head");
        if (head < 0) head = rig.indexOf("Head");
        this.lookBone = head;
        this.rArm = rig.indexOf("right_arm");
        this.lArm = rig.indexOf("left_arm");
    }

    public UUID id() { return id; }
    public Rig rig() { return rig; }
    public AnimInputs inputs() { return inputs; }
    public MolangContext molang() { return molang; }
    public double clock() { return clock; }
    public LocomotionSet locomotion() { return loco; }

    /** Набір локомоції (null — шар локомоції вимкнено, база проходить як є). */
    public Animator locomotion(LocomotionSet set) {
        this.loco = set;
        return this;
    }

    /** Межі повороту голови (градуси) — для geo-моделей, у яких шия не крутиться на 180. */
    public Animator lookLimits(float yaw, float pitch) {
        this.lookYawLimit = yaw;
        this.lookPitchLimit = pitch;
        return this;
    }

    // ── Керування ────────────────────────────────────────────────────────

    /** @return false — кліпа з таким id немає (перевір шлях/ім'я у файлі). */
    public boolean play(PlayRequest r) {
        AnimationClip clip = library.get(r.clipId);
        if (clip == null) return false;
        Slot slot = r.resolveSlot(clip);
        AnimLayer layer = layers[slot.ordinal()];
        ClipPlayer cur = layer.current();
        if (cur != null && cur.clip.id().equals(clip.id()) && !r.restart) return true;
        float speed = Math.max(1e-3f, r.resolveSpeed(clip));
        BoneMask mask = rig.mask(r.resolveMask(clip));
        boolean additive = r.additive != null ? r.additive : clip.defaults().additive();
        ClipPlayer p = new ClipPlayer(clip, mask, r.loop != null ? r.loop : clip.loop(),
            clock - r.startAt / speed, speed, r.resolveFadeIn(clip), r.resolveFadeOut(clip),
            Math.max(0, Math.min(1, r.weight)), r.resolveLook(clip), additive, r.tag);
        p.camPos = Math.max(0, Math.min(1, r.resolveCamPos(clip)));
        p.camRot = Math.max(0, Math.min(1, r.resolveCamRot(clip)));
        layer.play(p, clock);
        return true;
    }

    public void stop(Slot slot) { stop(slot, Float.NaN); }

    /** Плавно гасить шар (NaN — fade-out кліпа). */
    public void stop(Slot slot, float fadeOut) {
        layers[slot.ordinal()].stop(clock, fadeOut, null);
        bindingActive.remove(slot);
    }

    /** Гасить конкретний кліп, де б він не грав. */
    public void stopClip(String clipId, float fadeOut) {
        for (AnimLayer l : layers) {
            for (ClipPlayer p : l.entries) {
                if (p.clip.id().equals(clipId)) p.beginFadeOut(clock, Float.isNaN(fadeOut) ? p.fadeOut : fadeOut);
            }
        }
    }

    public void stopAll(float fadeOut) {
        for (AnimLayer l : layers) l.stop(clock, fadeOut, null);
        bindingActive.clear();
    }

    public boolean isPlaying(String clipId) {
        for (AnimLayer l : layers) {
            ClipPlayer c = l.current();
            if (c != null && c.clip.id().equals(clipId)) return true;
        }
        return false;
    }

    public ClipPlayer current(Slot slot) { return layers[slot.ordinal()].current(); }

    /** Чи грає в шарі щось, крім локомоції (для адаптерів: чи взагалі чіпати модель). */
    public boolean busy() {
        if (loco != null) return true;
        for (AnimLayer l : layers) if (!l.isEmpty()) return true;
        return false;
    }

    // ── Тік ──────────────────────────────────────────────────────────────

    public void tick(AnimSubject subject, Iterable<AnimBinding> bindings, AnimEventSink sink) {
        prevClock = clock;
        clock += DT;

        if (sink != null) fireEvents(subject, sink);
        prevHoldSwing = holdSwing;
        prevHoldUse = holdUse;
        float k = (float) (1 - Math.exp(-25.0 * DT));
        holdSwing += ((inputs.swinging ? 1f : 0f) - holdSwing) * k;
        holdUse += ((inputs.usingItem ? 1f : 0f) - holdUse) * k;
        updateLocomotion();
        reactions();
        if (subject != null && bindings != null) applyBindings(subject, bindings);
        for (AnimLayer l : layers) l.prune(clock);
    }

    private void fireEvents(AnimSubject subject, AnimEventSink sink) {
        for (AnimLayer l : layers) {
            for (ClipPlayer p : l.entries) {
                List<ClipEvent> evs = p.clip.events();
                if (evs.isEmpty() || p.fadingOut()) continue;
                boolean fresh = p.start > prevClock - 1e-9;
                float a = fresh ? -1e-4f : p.rawTime(prevClock);
                float b = p.rawTime(clock);
                float len = p.clip.length();
                for (ClipEvent e : evs) {
                    if (p.loop == AnimationClip.LoopMode.LOOP && len > 0) {
                        // усі повтори e.time() + k·len, що потрапили в (a, b]
                        double k0 = Math.ceil((a - e.time()) / len + 1e-7);
                        for (double k = Math.max(0, k0); e.time() + k * len <= b; k++) {
                            if (e.time() + k * len > a) sink.onEvent(subject, p.clip, e);
                        }
                    } else if (e.time() > a && e.time() <= b && e.time() <= len) {
                        sink.onEvent(subject, p.clip, e);
                    }
                }
            }
        }
    }

    private void updateLocomotion() {
        if (loco == null) return;
        System.arraycopy(locoW, 0, locoPrevW, 0, stateCount);
        AnimInputs in = inputs;
        boolean air = !in.onGround && !in.swimming && !in.riding && !in.climbing && !in.flying && !in.inWater;
        airTicks = air ? airTicks + 1 : 0;
        if (airTicks == 1) airStart = clock;

        java.util.Arrays.fill(locoTarget, 0);
        float move = clamp01(in.limbSwingAmount * 1.5f);
        if (in.riding) target(LocomotionSet.State.RIDE, 1);
        else if (in.climbing && !in.onGround) target(LocomotionSet.State.CLIMB, 1);
        else if (in.crawling) target(LocomotionSet.State.CRAWL, 1);
        else if (in.swimming) target(LocomotionSet.State.SWIM, 1);
        else if (in.flying) target(LocomotionSet.State.FLY, 1);
        else if (airTicks >= 3) target(in.verticalSpeed > 0 ? LocomotionSet.State.JUMP : LocomotionSet.State.FALL, 1);
        else if (in.sneaking) {
            target(LocomotionSet.State.SNEAK, 1 - move);
            target(LocomotionSet.State.SNEAK_WALK, move);
        } else {
            float run = in.sprinting ? 1 : 0;
            target(LocomotionSet.State.IDLE, 1 - move);
            target(LocomotionSet.State.WALK, move * (1 - run));
            target(LocomotionSet.State.RUN, move * run);
        }
        float alpha = (float) (1 - Math.exp(-12.0 * DT));
        for (int s = 0; s < stateCount; s++) locoW[s] += (locoTarget[s] - locoW[s]) * alpha;
        prevUseSmooth = useSmooth;
        useSmooth += ((in.usingItem && loco.yieldArmsWhenUsing ? 1f : 0f) - useSmooth) * (float) (1 - Math.exp(-16.0 * DT));
    }

    /** Вага стану з урахуванням запасних: немає кліпа — віддати вагу запасному. */
    private void target(LocomotionSet.State s, float w) {
        if (w <= 0) return;
        for (LocomotionSet.State c = s; c != null; c = c.fallback) {
            String clip = loco.clip(c);
            if (clip != null && library.has(clip)) {
                locoTarget[c.ordinal()] += w;
                return;
            }
        }
        // нічого не знайшлось — ця частка ваги лишається базі (ванілі)
    }

    private void reactions() {
        if (loco == null) {
            wasSwinging = inputs.swinging;
            return;
        }
        AnimLayer action = layers[Slot.ACTION.ordinal()];
        ClipPlayer cur = action.current();
        boolean actionFree = cur == null || cur.tag.startsWith("loco:");
        if (loco.attackClip != null && inputs.swinging && !wasSwinging && actionFree) {
            play(PlayRequest.of(loco.attackClip).slot(Slot.ACTION).mask(loco.attackMask).restart(true)
                .fade(0.06f, 0.15f).tag("loco:attack"));
        }
        wasSwinging = inputs.swinging;

        if (loco.useClip != null) {
            cur = action.current();
            boolean useActive = cur != null && "loco:use".equals(cur.tag);
            if (inputs.usingItem && (cur == null) && !useActive) {
                play(PlayRequest.of(loco.useClip).slot(Slot.ACTION).mask(loco.useMask).tag("loco:use"));
            } else if (!inputs.usingItem && useActive) {
                action.stop(clock, Float.NaN, "loco:use");
            }
        }
    }

    private void applyBindings(AnimSubject subject, Iterable<AnimBinding> bindings) {
        Map<Slot, AnimBinding> winners = new EnumMap<>(Slot.class);
        Map<Slot, String> winnerClip = new EnumMap<>(Slot.class);
        for (AnimBinding b : bindings) {
            if (!b.appliesTo(rig.name())) continue;
            boolean ok;
            try {
                ok = b.condition.test(subject);
            } catch (RuntimeException e) {
                ok = false; // крива умова одного мода не валить анімації всіх
            }
            if (!ok) continue;
            String clip = b.clipFor(subject);
            if (clip == null || !library.has(clip)) continue;
            AnimBinding cur = winners.get(b.slot);
            if (cur == null || b.priority > cur.priority) {
                winners.put(b.slot, b);
                winnerClip.put(b.slot, clip);
            }
        }
        for (Slot slot : Slot.values()) {
            AnimBinding w = winners.get(slot);
            String activeId = bindingActive.get(slot);
            AnimLayer layer = layers[slot.ordinal()];
            ClipPlayer cur = layer.current();
            if (w != null) {
                String clip = winnerClip.get(slot);
                boolean manualBusy = cur != null && !cur.tag.startsWith("binding:") && !cur.tag.startsWith("loco:");
                if (manualBusy) {
                    bindingActive.remove(slot); // ручна дія важливіша; повернемось, коли скінчиться
                    continue;
                }
                if (cur != null && cur.tag.equals(w.tag()) && cur.clip.id().equals(clip)) {
                    bindingActive.put(slot, w.id);
                    continue;
                }
                play(w.request(clip));
                bindingActive.put(slot, w.id);
            } else if (activeId != null) {
                layer.stop(clock, Float.NaN, "binding:" + activeId);
                bindingActive.remove(slot);
            }
        }
    }

    // ── Обчислення пози ──────────────────────────────────────────────────

    /**
     * @param partial     частка між тіками (0..1)
     * @param base        поза під кліпами (ванільна поза моделі) або null — поза спокою
     * @param baseHasLook чи база вже містить поворот голови за камерою (ваніль — так; geo — ні)
     */
    public void evaluate(float partial, Pose base, boolean baseHasLook, Pose out) {
        double fc = prevClock + (clock - prevClock) * clamp01(partial);
        frameSwing = prevHoldSwing + (holdSwing - prevHoldSwing) * clamp01(partial);
        frameUse = prevHoldUse + (holdUse - prevHoldUse) * clamp01(partial);
        out.reset();
        if (base != null && base.rig() == rig) {
            for (int i = 0; i < rig.size(); i++) out.bone(i).set(base.bone(i));
        }
        AnimInputs in = inputs;
        molang.set("query.life_time", fc);
        molang.set("query.ground_speed", in.horizontalSpeed * 20);
        molang.set("query.modified_move_speed", in.limbSwingAmount);
        molang.set("query.modified_distance_moved", in.limbSwing);
        molang.set("query.is_on_ground", in.onGround ? 1 : 0);
        molang.set("query.is_sneaking", in.sneaking ? 1 : 0);
        molang.set("query.is_sprinting", in.sprinting ? 1 : 0);
        molang.set("query.head_x_rotation", in.headPitch);
        molang.set("query.head_y_rotation", in.netHeadYaw);
        molang.set("query.vertical_speed", in.verticalSpeed * 20);

        float headShare = 1f, lookAllow = 1f;
        for (Slot slot : Slot.values()) {
            if (slot == Slot.LOCOMOTION && loco != null) {
                headShare *= applyLocomotion(fc, partial, out);
            }
            AnimLayer layer = layers[slot.ordinal()];
            List<ClipPlayer> entries = layer.entries;
            for (int k = 0; k < entries.size(); k++) {
                ClipPlayer p = entries.get(k);
                float env = p.envelope(fc);
                if (env <= 1e-4f) continue;
                float t = p.sampleTime(fc);
                molang.set("query.anim_time", t);
                AnimationClip clip = p.clip;
                StateSpec state = p.additive ? null : clip.state();
                float follow = state == null ? 0 : state.follow(in.sneaking, in.limbSwingAmount * 1.5f);
                float yield = holdYield(clip);
                if (state != null && env > 0.01f && yield > 0.01f) {
                    if (rArm >= 0 && clip.animates(rig.bone(rArm)) && p.mask.weight(rArm) > 0) out.stateRightArm = true;
                    if (lArm >= 0 && clip.animates(rig.bone(lArm)) && p.mask.weight(lArm) > 0) out.stateLeftArm = true;
                }
                for (String name : clip.bones()) {
                    int idx = rig.indexOf(name);
                    if (idx < 0) continue; // кістка пропса — її семплить рендер пропса
                    float m = p.mask.weight(idx) * p.weight * yield;
                    if (m <= 0) continue;
                    BoneTrack tr = clip.track(name);
                    for (BoneTrack.ChannelType type : BoneTrack.ChannelType.values()) {
                        Channel ch = tr.get(type);
                        if (ch == null || !ch.sample(t, molang, tmp)) continue;
                        float w = env * m;
                        if (!p.additive && newerCovers(entries, k, name, idx, type, fc)) w = m;
                        if (state != null && follow != 0) addFollow(out.bone(idx), type, tmp, follow);
                        apply(out.bone(idx), type, tmp, w, p.additive);
                        out.markTouched(idx);
                        if (idx == lookBone && type == BoneTrack.ChannelType.ROTATION && !p.additive) {
                            headShare *= (1 - w);
                            lookAllow += (p.look - lookAllow) * w;
                        }
                    }
                }
                if (env > 0.01f) {
                    for (PropSpec spec : clip.props()) {
                        if (!spec.visibleAt(t)) continue;
                        out.props().add(new Pose.ActiveProp(spec, clip, t, env));
                        if (spec.hidesRight()) out.hideRightHeld = true;
                        if (spec.hidesLeft()) out.hideLeftHeld = true;
                    }
                }
                if (!p.additive) {
                    out.cameraWeight += (p.camPos - out.cameraWeight) * env;
                    out.cameraRotWeight += (p.camRot - out.cameraRotWeight) * env;
                }
                AnimationClip.FirstPerson fp = clip.defaults().firstPerson();
                if (fp != AnimationClip.FirstPerson.VANILLA && env >= out.firstPersonWeight) {
                    out.firstPersonClip = clip;
                    out.firstPersonWeight = env;
                    out.firstPersonMode = fp;
                    out.firstPersonPitch = clip.defaults().firstPersonPitch();
                    out.firstPersonOffset = clip.defaults().firstPersonOffset();
                }
            }
        }

        if (lookBone >= 0) out.headNoLook.set(out.bone(lookBone)); // для камери: голова без процедурного погляду
        if (lookBone >= 0) {
            float amount = (baseHasLook ? 1 - headShare : 1) * clamp01(lookAllow);
            if (amount > 1e-4f) {
                BoneTransform h = out.bone(lookBone);
                h.rx += clamp(in.headPitch, -lookPitchLimit, lookPitchLimit) * amount;
                h.ry += clamp(in.netHeadYaw, -lookYawLimit, lookYawLimit) * amount;
                out.markTouched(lookBone);
            }
        }
        out.lookWeight = clamp01(lookAllow);
    }

    /** Чи новіший кліп цього ж шару перекриває канал (тоді старий під ним — на повну, для кросфейду). */
    private boolean newerCovers(List<ClipPlayer> entries, int k, String name, int idx, BoneTrack.ChannelType type, double fc) {
        for (int j = k + 1; j < entries.size(); j++) {
            ClipPlayer q = entries.get(j);
            if (q.additive) continue;
            BoneTrack tr = q.clip.track(name);
            if (tr != null && tr.has(type) && q.mask.weight(idx) > 0 && q.envelope(fc) > 0) return true;
        }
        return false;
    }

    /** @return частка голови, що лишилась від бази (для погляду). */
    private float applyLocomotion(double fc, float partial, Pose out) {
        int n = rig.size();
        if (accum == null) {
            accum = new float[n * 9];
            accumW = new float[n * 3];
        }
        java.util.Arrays.fill(accum, 0);
        java.util.Arrays.fill(accumW, 0);
        BoneMask mask = rig.mask(loco.mask);
        BoneMask arms = rig.mask("arms");
        float yieldArms = prevUseSmooth + (useSmooth - prevUseSmooth) * clamp01(partial);
        float total = 0;
        for (LocomotionSet.State s : LocomotionSet.State.values()) {
            float w = locoPrevW[s.ordinal()] + (locoW[s.ordinal()] - locoPrevW[s.ordinal()]) * clamp01(partial);
            if (w <= 1e-4f) continue;
            AnimationClip clip = library.get(loco.clip(s));
            if (clip == null) continue;
            total += w;
            float t;
            if (s.strideDriven()) {
                double phase = inputs.limbSwing / VANILLA_STRIDE * clip.defaults().stride();
                t = (float) ((phase - Math.floor(phase)) * clip.length());
            } else if (s == LocomotionSet.State.JUMP || s == LocomotionSet.State.FALL) {
                t = clip.wrap((float) Math.max(0, fc - airStart));
            } else {
                t = clip.wrap((float) fc);
            }
            molang.set("query.anim_time", t);
            for (String name : clip.bones()) {
                int idx = rig.indexOf(name);
                if (idx < 0) continue;
                BoneTrack tr = clip.track(name);
                for (BoneTrack.ChannelType type : BoneTrack.ChannelType.values()) {
                    Channel ch = tr.get(type);
                    if (ch == null || !ch.sample(t, molang, tmp)) continue;
                    int c = type.ordinal();
                    int a = (idx * 3 + c) * 3;
                    accum[a] += tmp[0] * w;
                    accum[a + 1] += tmp[1] * w;
                    accum[a + 2] += tmp[2] * w;
                    accumW[idx * 3 + c] += w;
                }
            }
        }
        float headShare = 1;
        if (total <= 0) return headShare;
        for (int idx = 0; idx < n; idx++) {
            float m = mask.weight(idx) * (1 - yieldArms * arms.weight(idx));
            if (m <= 0) continue;
            for (BoneTrack.ChannelType type : BoneTrack.ChannelType.values()) {
                int c = type.ordinal();
                float ws = accumW[idx * 3 + c];
                if (ws <= 0) continue;
                int a = (idx * 3 + c) * 3;
                tmp[0] = accum[a] / ws;
                tmp[1] = accum[a + 1] / ws;
                tmp[2] = accum[a + 2] / ws;
                float w = Math.min(1, ws) * m;
                apply(out.bone(idx), type, tmp, w, false);
                out.markTouched(idx);
                if (idx == lookBone && type == BoneTrack.ChannelType.ROTATION) headShare *= (1 - w);
            }
        }
        return headShare;
    }

    private static void apply(BoneTransform b, BoneTrack.ChannelType type, float[] v, float w, boolean additive) {
        switch (type) {
            case ROTATION -> {
                if (additive) { b.rx += v[0] * w; b.ry += v[1] * w; b.rz += v[2] * w; }
                else { b.rx += (v[0] - b.rx) * w; b.ry += (v[1] - b.ry) * w; b.rz += (v[2] - b.rz) * w; }
            }
            case POSITION -> {
                if (additive) { b.px += v[0] * w; b.py += v[1] * w; b.pz += v[2] * w; }
                else { b.px += (v[0] - b.px) * w; b.py += (v[1] - b.py) * w; b.pz += (v[2] - b.pz) * w; }
            }
            case SCALE -> {
                if (additive) { b.sx *= 1 + (v[0] - 1) * w; b.sy *= 1 + (v[1] - 1) * w; b.sz *= 1 + (v[2] - 1) * w; }
                else { b.sx += (v[0] - b.sx) * w; b.sy += (v[1] - b.sy) * w; b.sz += (v[2] - b.sz) * w; }
            }
        }
    }

    /** Вага стану локомоції (для налагодження/HUD). */
    public float locomotionWeight(LocomotionSet.State s) { return locoW[s.ordinal()]; }

    /** Стан: поза + сила × рух знизу (обертання й зсув; масштаб стану абсолютний). */
    private static void addFollow(BoneTransform below, BoneTrack.ChannelType type, float[] v, float f) {
        if (type == BoneTrack.ChannelType.ROTATION) { v[0] += below.rx * f; v[1] += below.ry * f; v[2] += below.rz * f; }
        else if (type == BoneTrack.ChannelType.POSITION) { v[0] += below.px * f; v[1] += below.py * f; v[2] += below.pz * f; }
    }

    /** Стан тримання під час удару/використання поступається ванільній позі (видно змах з предметом у руці). */
    private float holdYield(AnimationClip clip) {
        StateSpec h = clip.state();
        if (h == null) return 1f;
        float y = 0;
        if (h.yieldSwing()) y = Math.max(y, frameSwing);
        if (h.yieldUse()) y = Math.max(y, frameUse);
        return 1f - y;
    }

    /** Семпл кістки пропса (не з ригу) в кліпі на час t — для рендера пропсів. */
    public boolean samplePropBone(AnimationClip clip, String bone, BoneTrack.ChannelType type, float t, float[] out) {
        BoneTrack tr = clip.track(bone);
        // кліпи гравця зберігають імена треків у каноні (snake_case): "Lid" у geo → трек "lid"
        if (tr == null && ClipParser.RIG_PLAYER.equals(clip.rig())) tr = clip.track(BoneNames.player(bone));
        if (tr == null) return false;
        Channel ch = tr.get(type);
        if (ch == null) return false;
        molang.set("query.anim_time", t);
        return ch.sample(t, molang, out);
    }

    private static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
