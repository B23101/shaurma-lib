package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.shaurmalib.common.playeranim.BoneAdjustment;
import dev.shaurmalib.common.playeranim.PoseEase;
import dev.shaurmalib.common.playeranim.PoseLifecycle;
import dev.shaurmalib.common.playeranim.PoseSource;
import dev.shaurmalib.harness.T;

import java.util.Optional;

/**
 * {@link PoseLayerRuntime} проти <b>справжнього</b> PAL (без Minecraft).
 * {@link Sim} відтворює порядок реальної гри: спершу PAL тікає стек
 * ({@code PlayerEntityMixin.tick} на HEAD Player.tick), потім наш
 * {@code ClientTickEvent(END)} тікає рантайм.
 */
public final class PoseLayerRuntimeTest {

    /** Мінімальна імітація "гравець + AnimationStack + один шар". */
    static final class Sim {
        final AnimationStack stack = new AnimationStack();
        final ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        final PoseLayerRuntime rt = new PoseLayerRuntime(layer);

        Sim() {
            stack.addAnimLayer(700, layer);
        }

        void step() {
            stack.tick();
            rt.tick();
        }

        void steps(int n) {
            for (int i = 0; i < n; i++) step();
        }

        float rot(String bone) {
            return rotAt(bone, 0f);
        }

        float rotAt(String bone, float delta) {
            return stack.get3DTransform(bone, TransformType.ROTATION, delta, Vec3f.ZERO).getX();
        }
    }

    private static PoseSource src(boolean loop) {
        return loop ? PoseSource.hold("t", "x").withEase(PoseEase.LINEAR)
                    : PoseSource.oneShot("t", "x").withEase(PoseEase.LINEAR);
    }

    public static void run() {
        hold();
        fadeIn();
        oneShotPreemptive();
        stopWithFade();
        interruptions();
        noDangling();
        adjustment();
        adjustmentRobustness();
        angleWrap();
        cameraFollow();
        hardStopAndMisc();
    }

    // ───────────────────────────────────────────────────────────────────

    private static void hold() {
        T.section("HOLD без fade");
        Sim s = new Sim();
        KeyframeAnimation clip = Clips.arm(0, 10, true, 1.0f);
        s.rt.start(clip, src(true), true);
        T.eq("state PLAYING", s.rt.state(), PoseLifecycle.State.PLAYING);
        T.ok("isActive", s.rt.isActive());
        T.ok("стек активний", s.stack.isActive());
        T.eq("fade-ів нема (fadeIn=0)", s.rt.fadeModifierCount(), 0);
        T.eq("поза видна одразу (rot=1.0)", s.rot("rightArm"), 1.0, 1e-5);
        s.steps(500);
        T.eq("HOLD пережив 500 тіків", s.rt.state(), PoseLifecycle.State.PLAYING);
        T.ok("стек досі активний", s.stack.isActive());
        T.eq("rot досі 1.0", s.rot("rightArm"), 1.0, 1e-5);
        T.eq("інші кістки прозорі (head=0)", s.rot("head"), 0.0, 1e-6);
    }

    private static void fadeIn() {
        T.section("Fade-in «з нічого» (інваріант 4)");
        Sim s = new Sim();
        s.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeIn(4), true);
        T.eq("fade створено (PAL сам би його пропустив)", s.rt.fadeModifierCount(), 1);
        T.eq("k=0: rot=0 (з нейтралі)", s.rot("rightArm"), 0.0, 1e-5);
        for (int k = 1; k <= 4; k++) {
            s.step();
            T.eq("k=" + k + ": rot=" + (k / 4f), s.rot("rightArm"), k / 4.0, 1e-4);
        }
        s.steps(2);
        T.eq("після fade rot=1.0", s.rot("rightArm"), 1.0, 1e-5);
        T.eq("fade сам прибрався (canRemove)", s.rt.fadeModifierCount(), 0);

        Sim s2 = new Sim();
        s2.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeIn(4), false);
        T.eq("allowFadeIn=false (relog): fade не створюється", s2.rt.fadeModifierCount(), 0);
        T.eq("…і поза одразу повна", s2.rot("rightArm"), 1.0, 1e-5);
    }

    private static void oneShotPreemptive() {
        T.section("ONE_SHOT: пре-емптивний fade-out (§1.1)");
        // endTick=10 → stopTick=13 (PAL гарантує +3). fadeOut=3.
        Sim s = new Sim();
        KeyframeAnimation clip = Clips.arm(0, 10, false, 1.0f);
        T.eq("PAL: stopTick = endTick+3", clip.stopTick, 13);
        s.rt.start(clip, src(false).withFadeOut(3), true);

        boolean alwaysActive = true;
        boolean fadeSeen = false;
        int finishedAt = -1;
        float prev = s.rot("rightArm");
        float maxJump = 0;
        for (int k = 1; k <= 20; k++) {
            s.step();
            if (s.rt.state() == PoseLifecycle.State.FADING_OUT) fadeSeen = true;
            if (s.rt.state() == PoseLifecycle.State.IDLE) {
                if (finishedAt < 0) finishedAt = k;
            } else {
                alwaysActive &= s.stack.isActive();
            }
            float cur = s.rot("rightArm");
            maxJump = Math.max(maxJump, Math.abs(cur - prev));
            prev = cur;
            if (k == 10) {
                T.eq("k=10: fade-out стартував", s.rt.state(), PoseLifecycle.State.FADING_OUT);
                T.eq("k=10: fade рівно один", s.rt.fadeModifierCount(), 1);
            }
        }
        T.ok("fade-out справді був", fadeSeen);
        T.ok("шар лишався активним увесь час (поза не зникла миттєво)", alwaysActive);
        T.eq("завершилось рівно в момент stopTick (k=13)", finishedAt, 13);
        // fade-out рушія МНОЖИТЬСЯ на власний 3-тіковий хвіст кліпа (stopTick=endTick+3), тому крок
        // більший за лінійні 1/3. Перевіряємо головне: ніякого «пропадання» (стрибок у повні 1.0).
        T.ok("без «пропадання» між тіками (maxJump<0.6, було " + maxJump + ")", maxJump < 0.6f);
        T.eq("після завершення fade-ів нема", s.rt.fadeModifierCount(), 0);
        T.ok("після завершення анімація знята", s.layer.getAnimation() == null);
        T.ok("стек неактивний", !s.stack.isActive());
        T.eq("rot повернувся до нейтралі", s.rot("rightArm"), 0.0, 1e-6);

        // Докази тези §1.1: fade постфактум — неможливий.
        ModifierLayer<IAnimation> raw = new ModifierLayer<>();
        KeyframeAnimationPlayer done = new KeyframeAnimationPlayer(Clips.arm(0, 3, false, 1.0f));
        raw.setAnimation(done);
        for (int i = 0; i < 10; i++) done.tick();
        T.ok("[доказ §1.1] one-shot сам зупинився", !done.isActive());
        raw.replaceAnimationWithFade(dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier
                .standardFadeIn(3, dev.kosmx.playerAnim.core.util.Ease.LINEAR), null);
        T.eq("[доказ §1.1] fade постфактум НЕ додається (modifiers=0)", raw.size(), 0);

        // fadeOut=0: без fade, кінець рівно по stopTick.
        Sim s0 = new Sim();
        s0.rt.start(Clips.arm(0, 10, false, 1.0f), src(false).withFadeOut(0), true);
        s0.steps(12);
        T.eq("fadeOut=0: ще PLAYING на k=12", s0.rt.state(), PoseLifecycle.State.PLAYING);
        T.eq("fadeOut=0: fade-ів нема", s0.rt.fadeModifierCount(), 0);
        s0.step();
        T.eq("fadeOut=0: IDLE на k=13", s0.rt.state(), PoseLifecycle.State.IDLE);

        // fadeOut довший за кліп → скорочується до залишку.
        Sim sl = new Sim();
        sl.rt.start(Clips.arm(0, 10, false, 1.0f), src(false).withFadeOut(40), true);
        sl.steps(14);
        T.eq("fadeOut=40 на кліпі в 13 тіків: завершено до k=14", sl.rt.state(), PoseLifecycle.State.IDLE);
        T.eq("…без висячих fade-ів", sl.rt.fadeModifierCount(), 0);

        // Дуже короткий one-shot (endTick=1): не ламається.
        Sim ss = new Sim();
        ss.rt.start(Clips.arm(0, 1, false, 1.0f), src(false).withFadeOut(10), true);
        ss.steps(10);
        T.eq("ультракороткий кліп: IDLE", ss.rt.state(), PoseLifecycle.State.IDLE);
        T.eq("ультракороткий кліп: чисто", ss.rt.fadeModifierCount(), 0);
    }

    private static void stopWithFade() {
        T.section("stop() з fade-out");
        Sim s = new Sim();
        s.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(5), true);
        s.steps(3);
        s.rt.requestStop();
        T.eq("→ FADING_OUT", s.rt.state(), PoseLifecycle.State.FADING_OUT);
        T.ok("isActive=false (консюмер може перезапустити)", !s.rt.isActive());
        T.ok("isVisible=true (ще видно)", s.rt.isVisible());
        T.eq("рівно один fade", s.rt.fadeModifierCount(), 1);
        float prev = s.rot("rightArm");
        T.eq("k=0: ще повна поза", prev, 1.0, 1e-5);
        boolean monotone = true;
        for (int k = 1; k <= 5; k++) {
            s.step();
            float cur = s.rot("rightArm");
            monotone &= cur <= prev + 1e-6f;
            prev = cur;
        }
        T.ok("згасання монотонне", monotone);
        T.eq("після 5 тіків IDLE", s.rt.state(), PoseLifecycle.State.IDLE);
        T.eq("fade-ів нема", s.rt.fadeModifierCount(), 0);
        T.eq("rot=0", s.rot("rightArm"), 0.0, 1e-6);
        T.ok("стек неактивний", !s.stack.isActive());

        Sim s2 = new Sim();
        s2.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(0), true);
        s2.steps(2);
        s2.rt.requestStop();
        T.eq("fadeOut=0: миттєво IDLE", s2.rt.state(), PoseLifecycle.State.IDLE);
        T.eq("fadeOut=0: rot=0 одразу", s2.rot("rightArm"), 0.0, 1e-6);

        Sim s3 = new Sim();
        s3.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(6), true);
        s3.rt.requestStop();
        s3.steps(2);
        int remaining = 6 - 2;
        s3.rt.requestStop(); // повторний stop не відтягує кінець
        s3.steps(remaining);
        T.eq("повторний stop під час fade не відтягує кінець", s3.rt.state(), PoseLifecycle.State.IDLE);
    }

    private static void interruptions() {
        T.section("Нова поза посеред fade (інваріант 3)");
        Sim s = new Sim();
        s.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(6), true);
        s.steps(2);
        s.rt.requestStop();
        s.steps(3); // посеред fade-out
        float before = s.rot("rightArm");
        T.ok("посеред згасання: 0 < rot < 1 (" + before + ")", before > 0.05f && before < 0.95f);

        s.rt.start(Clips.arm(0, 10, true, 2.0f), src(true).withFadeIn(4).withFadeOut(6), true);
        T.eq("одразу після start rot НЕ стрибнув до нейтралі", s.rot("rightArm"), before, 1e-4);
        T.eq("state PLAYING", s.rt.state(), PoseLifecycle.State.PLAYING);
        T.eq("рівно один fade рушія (не накопичуються)", s.rt.fadeModifierCount(), 1);
        int maxFades = 0;
        for (int k = 0; k < 8; k++) {
            s.step();
            maxFades = Math.max(maxFades, s.rt.fadeModifierCount());
        }
        T.ok("протягом переходу fade-ів ≤ 1", maxFades <= 1);
        T.eq("збіглось до нової пози (rot=2.0)", s.rot("rightArm"), 2.0, 1e-4);
        T.eq("fade-ів нема після переходу", s.rt.fadeModifierCount(), 0);

        // start посеред fade-IN: теж неперервно.
        Sim s2 = new Sim();
        s2.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeIn(8), true);
        s2.steps(3);
        float mid = s2.rot("rightArm");
        s2.rt.start(Clips.arm(0, 10, true, 3.0f), src(true).withFadeIn(4), true);
        T.eq("посеред fade-in → start: без стрибка", s2.rot("rightArm"), mid, 1e-4);
        T.eq("один fade", s2.rt.fadeModifierCount(), 1);

        // start без fade посеред fade-out: миттєва заміна, без сміття.
        Sim s3 = new Sim();
        s3.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(6), true);
        s3.rt.requestStop();
        s3.steps(2);
        s3.rt.start(Clips.arm(0, 10, true, 2.5f), src(true), true);
        T.eq("start без fade: старий fade прибрано", s3.rt.fadeModifierCount(), 0);
        T.eq("start без fade: поза повна одразу", s3.rot("rightArm"), 2.5, 1e-5);

        // 200 циклів start/stop: fade-и не накопичуються (S5).
        Sim s4 = new Sim();
        int peak = 0;
        for (int i = 0; i < 200; i++) {
            s4.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeIn(3).withFadeOut(3), true);
            peak = Math.max(peak, s4.rt.fadeModifierCount());
            s4.steps(i % 4);
            s4.rt.requestStop();
            peak = Math.max(peak, s4.rt.fadeModifierCount());
            s4.steps(i % 5);
        }
        T.ok("200 циклів start/stop: fade-ів ніколи > 1 (пік " + peak + ")", peak <= 1);
        s4.steps(20);
        T.eq("…і після затихання 0", s4.rt.fadeModifierCount(), 0);
        T.eq("…і IDLE", s4.rt.state(), PoseLifecycle.State.IDLE);
    }

    private static void noDangling() {
        T.section("Немає висячих fade-ів, коли PAL не тікає шар (інваріант 1, §1.2)");

        // Відтворення проблеми на «голому» PAL: обидві анімації fade-у неактивні → canRemove() ніколи не перевіряється.
        AnimationStack st = new AnimationStack();
        ModifierLayer<IAnimation> raw = new ModifierLayer<>();
        st.addAnimLayer(700, raw);
        KeyframeAnimationPlayer p = new KeyframeAnimationPlayer(Clips.arm(0, 10, true, 1.0f));
        raw.setAnimation(p);
        raw.replaceAnimationWithFade(dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier
                .standardFadeIn(3, dev.kosmx.playerAnim.core.util.Ease.LINEAR), null);
        p.stop();
        for (int i = 0; i < 100; i++) st.tick();
        T.eq("[доказ §1.2] голий PAL: fade висить вічно (size=1 після 100 тіків)", raw.size(), 1);
        raw.replaceAnimationWithFade(dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier
                .standardFadeIn(3, dev.kosmx.playerAnim.core.util.Ease.LINEAR), null, true);
        T.eq("[доказ §1.2] голий PAL: наступний fade накопичується (size=2)", raw.size(), 2);

        // Рантайм: PAL не тікає взагалі, тікає лише рушій — і все одно завершується чисто.
        Sim s = new Sim();
        s.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(4), true);
        s.rt.requestStop();
        T.eq("FADING_OUT", s.rt.state(), PoseLifecycle.State.FADING_OUT);
        for (int i = 0; i < 4; i++) s.rt.tick(); // лише рушій, без stack.tick()
        T.eq("рушій сам завершив fade власним лічильником", s.rt.state(), PoseLifecycle.State.IDLE);
        T.eq("fade-ів нема", s.rt.fadeModifierCount(), 0);
        T.ok("анімація знята", s.layer.getAnimation() == null);

        // Страховка в IDLE: підкинемо «висячий» fade ззовні — tick() у IDLE його прибере.
        Sim s2 = new Sim();
        s2.layer.addModifierLast(dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier
                .standardFadeIn(3, dev.kosmx.playerAnim.core.util.Ease.LINEAR));
        T.eq("підкинули fade у порожній шар", s2.layer.size(), 1);
        s2.rt.tick();
        T.eq("IDLE-страховка прибрала висячий fade", s2.layer.size(), 0);
    }

    private static void adjustment() {
        T.section("Динамічна правка кісток (AdjustmentModifier)");

        // Регресія PAL-бага: 0f/0f = NaN на кадрі tick=0, delta=0.
        ModifierLayer<IAnimation> raw = new ModifierLayer<>();
        raw.setAnimation(new KeyframeAnimationPlayer(Clips.arm(0, 10, true, 1.0f)));
        raw.addModifierBefore(new AdjustmentModifier(b -> Optional.of(new AdjustmentModifier.PartModifier(
                new Vec3f(0.5f, 0, 0), new Vec3f(0, 0, 0)))));
        float rawVal = raw.get3DTransform("rightArm", TransformType.ROTATION, 0f, Vec3f.ZERO).getX();
        T.ok("[PAL-баг] «голий» AdjustmentModifier при beginTick=0 → NaN на першому кадрі (" + rawVal + ")",
                Float.isNaN(rawVal));

        Sim s = new Sim();
        s.rt.setAdjustment(BoneAdjustment.ofBone("rightArm", BoneAdjustment.Part.rotation(0.5f, 0, 0)));
        s.rt.start(Clips.arm(0, 10, true, 1.0f), src(true), true);
        float v0 = s.rot("rightArm");
        T.ok("SafeAdjustmentModifier: перший кадр скінченний (без NaN)", Float.isFinite(v0));
        T.eq("additive: 1.0 (кліп) + 0.5 (правка) = 1.5", v0, 1.5, 1e-5);
        T.eq("кістка без правки прозора (head=0)", s.rot("head"), 0.0, 1e-6);
        T.eq("кістка leftArm без правки прозора", s.rot("leftArm"), 0.0, 1e-6);
        T.ok("adjustment встановлено", s.rt.hasAdjustment());
        T.eq("fade-ів рушія 0 (adjustment не рахується)", s.rt.fadeModifierCount(), 0);

        // Adjustment + fade-in: правка з'являється разом із позою (інваріант 2 + manual fade-in).
        Sim f = new Sim();
        f.rt.setAdjustment(BoneAdjustment.ofBone("rightArm", BoneAdjustment.Part.rotation(0.5f, 0, 0)));
        f.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeIn(4), true);
        T.eq("adjustment+fade: k=0 → 0", f.rot("rightArm"), 0.0, 1e-5);
        f.steps(2);
        T.eq("adjustment+fade: k=2 → 0.5 (поза) + 0.25 (правка) = 0.75", f.rot("rightArm"), 0.75, 1e-4);
        f.steps(4);
        T.eq("adjustment+fade: після fade → 1.5", f.rot("rightArm"), 1.5, 1e-4);
        T.ok("adjustment ПЕРЕЖИВ чистку fade-ів (інваріант 2)", f.rt.hasAdjustment());
        T.eq("fade-ів рушія 0", f.rt.fadeModifierCount(), 0);
        T.eq("модифікаторів у шарі рівно 1 (лише adjustment)", f.layer.size(), 1);

        // Adjustment згасає разом із позою при stop.
        Sim o = new Sim();
        o.rt.setAdjustment(BoneAdjustment.ofBone("rightArm", BoneAdjustment.Part.rotation(0.5f, 0, 0)));
        o.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(4), true);
        o.steps(2);
        o.rt.requestStop();
        float prev = o.rot("rightArm");
        T.eq("перед згасанням 1.5", prev, 1.5, 1e-4);
        boolean mono = true;
        for (int k = 1; k <= 3; k++) {
            o.step();
            float cur = o.rot("rightArm");
            mono &= cur <= prev + 1e-5f;
            prev = cur;
        }
        T.ok("правка і поза згасають монотонно, разом", mono);
        o.step();
        T.eq("після fade-out: IDLE", o.rt.state(), PoseLifecycle.State.IDLE);
        T.ok("adjustment скинуто на завершенні (сесія пози)", !o.rt.hasAdjustment());
        T.eq("шар порожній (0 модифікаторів)", o.layer.size(), 0);
        T.eq("rot=0", o.rot("rightArm"), 0.0, 1e-6);

        // Правка сама (без пози під нею) має дійти до 0 саме на тіку завершення, без «попу» (1/n лишку PAL).
        Sim e = new Sim();
        e.rt.setAdjustment(BoneAdjustment.ofBone("head", BoneAdjustment.Part.rotation(1.0f, 0, 0)));
        e.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(4), true);
        T.eq("head: лише правка (кліп head не чіпає) = 1.0", e.rot("head"), 1.0, 1e-5);
        e.rt.requestStop();
        for (int k = 1; k <= 3; k++) {
            e.step();
            T.eq("правка head згасає лінійно: k=" + k, e.rot("head"), 1.0 - k / 4.0, 1e-4);
        }
        e.step();
        T.eq("після завершення head=0 без залишку (нема «попу»)", e.rot("head"), 0.0, 1e-6);

        // Повторний setAdjustment з тим самим провайдером — no-op (безпечно щотік).
        Sim r = new Sim();
        BoneAdjustment.Provider prov = BoneAdjustment.ofBone("rightArm", BoneAdjustment.Part.rotation(0.1f, 0, 0));
        r.rt.setAdjustment(prov);
        r.rt.setAdjustment(prov);
        r.rt.setAdjustment(prov);
        T.eq("той самий провайдер ×3 → один модифікатор", r.layer.size(), 1);
        BoneAdjustment.Provider prov2 = BoneAdjustment.ofBone("rightArm", BoneAdjustment.Part.rotation(0.2f, 0, 0));
        r.rt.setAdjustment(prov2);
        T.eq("інший провайдер → замінено, не додано", r.layer.size(), 1);
        r.rt.clearAdjustment();
        T.eq("clearAdjustment → 0", r.layer.size(), 0);
        r.rt.setAdjustment(null);
        T.eq("setAdjustment(null) не падає", r.layer.size(), 0);

        // start посеред fade-out при активній правці: правка «розгасає».
        Sim re = new Sim();
        BoneAdjustment.Provider pv = BoneAdjustment.ofBone("rightArm", BoneAdjustment.Part.rotation(0.5f, 0, 0));
        re.rt.setAdjustment(pv);
        re.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(6), true);
        re.steps(1);
        re.rt.requestStop();
        re.steps(4);
        re.rt.start(Clips.arm(0, 10, true, 1.0f), src(true), true);
        re.steps(2);
        T.eq("рестарт під час fade-out: правка відновилась повністю (1.5)", re.rot("rightArm"), 1.5, 1e-4);
        T.eq("рівно один adjustment", re.layer.size(), 1);
        T.eq("провайдер збережено", re.rt.adjustmentProvider() == pv, true);

        // setAdjustment посеред fade-out нової правки.
        Sim mid = new Sim();
        mid.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeOut(6), true);
        mid.rt.requestStop();
        mid.steps(2);
        mid.rt.setAdjustment(pv);
        T.ok("правка, додана посеред fade-out, теж згасає (не лишається на 100%)",
                mid.rot("rightArm") < 1.5f);
    }

    private static void adjustmentRobustness() {
        T.section("Стійкість провайдера правок");
        Sim t = new Sim();
        t.rt.setAdjustment(bone -> {
            throw new IllegalStateException("бум");
        });
        t.rt.start(Clips.arm(0, 10, true, 1.0f), src(true), true);
        float v = 0;
        boolean threw = false;
        try {
            v = t.rot("rightArm");
        } catch (Throwable e) {
            threw = true;
        }
        T.ok("провайдер кинув виняток → не валить рендер", !threw);
        T.eq("…кістка прозора (лише кліп = 1.0)", v, 1.0, 1e-5);

        Sim n = new Sim();
        n.rt.setAdjustment(bone -> Optional.of(BoneAdjustment.Part.rotation(Float.NaN, Float.POSITIVE_INFINITY, 0)));
        n.rt.start(Clips.arm(0, 10, true, 1.0f), src(true), true);
        float nv = n.rot("rightArm");
        T.ok("NaN/Infinity у правці не потрапляє в модель (" + nv + ")", Float.isFinite(nv));
        T.eq("…нефінітні значення → 0 правки (1.0)", nv, 1.0, 1e-5);

        Sim nn = new Sim();
        nn.rt.setAdjustment(bone -> null);
        nn.rt.start(Clips.arm(0, 10, true, 1.0f), src(true), true);
        T.eq("провайдер повернув null (не Optional) → прозоро", nn.rot("rightArm"), 1.0, 1e-5);
    }

    private static void cameraFollow() {
        T.section("Опційний зсув камери за кісткою head");

        Sim off = new Sim();
        off.rt.start(Clips.head(0, 10, true, 0.5f), src(true), true);
        T.ok("за замовчуванням (cameraFollow=0) зсуву нема", off.rt.cameraOffset(0f) == null);
        T.eq("…але сама голова моделі рухається (rot=0.5)", off.rot("head"), 0.5, 1e-5);

        Sim on = new Sim();
        on.rt.start(Clips.head(0, 10, true, 0.5f), src(true).withCameraFollow(1f), true);
        T.eq("cameraFollow=1: pitch=0.5", on.rt.cameraOffset(0f).getX(), 0.5, 1e-5);
        T.eq("yaw=0", on.rt.cameraOffset(0f).getY(), 0.0, 1e-6);

        Sim half = new Sim();
        half.rt.start(Clips.head(0, 10, true, 0.5f), src(true).withCameraFollow(0.5f), true);
        T.eq("cameraFollow=0.5: pitch=0.25", half.rt.cameraOffset(0f).getX(), 0.25, 1e-5);

        Sim neg = new Sim();
        neg.rt.start(Clips.head(0, 10, true, 0.5f), src(true).withCameraFollow(-1f), true);
        T.eq("cameraFollow=-1 віддзеркалює знак", neg.rt.cameraOffset(0f).getX(), -0.5, 1e-5);

        Sim noHead = new Sim();
        noHead.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withCameraFollow(1f), true);
        T.eq("кліп без keyframe-ів head → зсув 0 (камеру не чіпаємо)", noHead.rt.cameraOffset(0f).getX(), 0.0, 1e-6);

        // fade-in: зсув з'являється разом із позою
        Sim fi = new Sim();
        fi.rt.start(Clips.head(0, 10, true, 0.8f), src(true).withFadeIn(4).withCameraFollow(1f), true);
        T.eq("fade-in k=0 → 0", fi.rt.cameraOffset(0f).getX(), 0.0, 1e-5);
        fi.steps(2);
        T.eq("fade-in k=2 → 0.4", fi.rt.cameraOffset(0f).getX(), 0.4, 1e-4);
        fi.steps(4);
        T.eq("після fade-in → 0.8", fi.rt.cameraOffset(0f).getX(), 0.8, 1e-4);

        // fade-out: зсув згасає й зникає
        Sim fo = new Sim();
        fo.rt.start(Clips.head(0, 10, true, 0.8f), src(true).withFadeOut(4).withCameraFollow(1f), true);
        fo.steps(1);
        fo.rt.requestStop();
        fo.steps(2);
        float mid = fo.rt.cameraOffset(0f).getX();
        T.ok("під час fade-out зсув між 0 і 0.8 (" + mid + ")", mid > 0.05f && mid < 0.75f);
        fo.steps(2);
        T.ok("після завершення зсуву нема (null)", fo.rt.cameraOffset(0f) == null);

        Sim hs = new Sim();
        hs.rt.start(Clips.head(0, 10, true, 0.5f), src(true).withCameraFollow(1f), true);
        hs.rt.hardStop();
        T.ok("після hardStop зсуву нема", hs.rt.cameraOffset(0f) == null);
        T.ok("порожній шар → null", new Sim().rt.cameraOffset(0f) == null);

        // one-shot: після авто-завершення камера повертається
        Sim os = new Sim();
        os.rt.start(Clips.head(0, 10, false, 0.5f), src(false).withFadeOut(3).withCameraFollow(1f), true);
        os.steps(14);
        T.ok("one-shot завершився → зсуву нема", os.rt.cameraOffset(0f) == null);
    }

    private static void angleWrap() {
        T.section("Властивість PAL: кути кліпа загортаються за ±π");
        Sim w = new Sim();
        w.rt.start(Clips.arm(0, 10, true, 5.0f), src(true), true);
        T.eq("keyframe-значення 5.0 рад читається як 5.0−2π (тому в тестах ≤ π)", w.rot("rightArm"), 5.0 - 2 * Math.PI, 1e-4);
    }

    private static void hardStopAndMisc() {
        T.section("hardStop і решта");
        Sim s = new Sim();
        s.rt.setAdjustment(BoneAdjustment.ofBone("rightArm", BoneAdjustment.Part.rotation(0.5f, 0, 0)));
        s.rt.start(Clips.arm(0, 10, true, 1.0f), src(true).withFadeIn(4).withFadeOut(6), true);
        s.steps(2);
        s.rt.hardStop();
        T.eq("hardStop → IDLE", s.rt.state(), PoseLifecycle.State.IDLE);
        T.eq("hardStop: модифікаторів 0 (навіть adjustment)", s.layer.size(), 0);
        T.ok("hardStop: анімація знята", s.layer.getAnimation() == null);
        T.ok("hardStop: стек неактивний", !s.stack.isActive());
        T.eq("hardStop: миттєво rot=0 (без fade)", s.rot("rightArm"), 0.0, 1e-6);
        T.ok("hardStop: activeSource=null", s.rt.activeSource() == null);
        s.rt.hardStop();
        T.ok("hardStop двічі — безпечно", true);
        s.rt.requestStop();
        s.rt.tick();
        T.ok("stop/tick на порожньому — безпечно", s.rt.state() == PoseLifecycle.State.IDLE);

        // Перезапуск після завершення.
        Sim r = new Sim();
        r.rt.start(Clips.arm(0, 5, false, 1.0f), src(false), true);
        r.steps(10);
        T.eq("one-shot завершився", r.rt.state(), PoseLifecycle.State.IDLE);
        r.rt.start(Clips.arm(0, 5, false, 1.0f), src(false), true);
        T.eq("перезапуск після завершення → PLAYING", r.rt.state(), PoseLifecycle.State.PLAYING);
        T.eq("…поза видна", r.rot("rightArm"), 1.0, 1e-5);
        T.ok("activeSource встановлено", r.rt.activeSource() != null);

        // Кілька шарів у одному стеку: вищий шар перекриває, порожня кістка прозора (§0.1).
        AnimationStack st = new AnimationStack();
        ModifierLayer<IAnimation> low = new ModifierLayer<>();
        ModifierLayer<IAnimation> high = new ModifierLayer<>();
        st.addAnimLayer(100, low);
        st.addAnimLayer(700, high);
        PoseLayerRuntime rl = new PoseLayerRuntime(low);
        PoseLayerRuntime rh = new PoseLayerRuntime(high);
        rl.start(Clips.arm(0, 10, true, 1.0f), src(true), true);
        T.eq("низький шар діє сам", st.get3DTransform("rightArm", TransformType.ROTATION, 0f, Vec3f.ZERO).getX(), 1.0, 1e-5);
        rh.start(Clips.arm(0, 10, true, 2.5f), src(true), true);
        T.eq("вищий шар ПЕРЕКРИВАЄ (override, не сума 1.0+2.5)", st.get3DTransform("rightArm", TransformType.ROTATION, 0f, Vec3f.ZERO).getX(), 2.5, 1e-5);
        rh.hardStop();
        T.eq("зняли вищий → знов діє низький", st.get3DTransform("rightArm", TransformType.ROTATION, 0f, Vec3f.ZERO).getX(), 1.0, 1e-5);
    }
}
