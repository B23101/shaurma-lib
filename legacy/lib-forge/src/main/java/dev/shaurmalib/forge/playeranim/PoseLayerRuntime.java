package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.shaurmalib.common.playeranim.BoneAdjustment;
import dev.shaurmalib.common.playeranim.PoseLifecycle;
import dev.shaurmalib.common.playeranim.PoseSource;

/**
 * Уся PAL-логіка <b>одного</b> шару: старт, fade-in, пре-емптивний
 * fade-out, чистка модифікаторів, динамічна правка кісток. Не знає ні про
 * гравця, ні про Minecraft/Forge — тому тестується проти справжнього PAL
 * ({@code tools/playeranim-harness}). {@link PlayerPoseController} —
 * тонка обгортка, що додає лише "гравець → шар" і relog-фікс.
 *
 * <h3>Інваріанти (усі перевірені харнесом)</h3>
 * <ol>
 *   <li>Після завершення життєвого циклу в шарі <b>немає</b> висячих fade-ів.
 *       Причина: {@code AnimationStack.tick} тікає лише активні шари, а
 *       {@code canRemove()} fade-а перевіряється в {@code ModifierLayer.tick()}.
 *       Якщо обидві анімації fade-у неактивні, {@code canRemove()} не
 *       перевіряється <i>ніколи</i> — модифікатор висів би вічно, а наступний
 *       накопичувався б поверх.</li>
 *   <li>{@link SafeAdjustmentModifier} завжди на індексі 0 (вимога PAL:
 *       "make sure this instance is the very first one") і переживає чистку
 *       fade-ів: чистка лишає {@code keep = adjustment ? 1 : 0} модифікаторів
 *       з початку.</li>
 *   <li>Нова поза посеред fade попередньої не стрибає до нейтралі: бачений
 *       зараз результат (поточний fade або анімація) стає {@code
 *       beginAnimation} нового fade-у, тож перехід неперервний. У списку
 *       завжди <b>щонайбільше один</b> fade рушія.</li>
 *   <li>Fade-in "з нічого" (шар був порожній) PAL за замовчуванням
 *       пропускає ({@code fadeFromNothing=false}), тому fade створюється
 *       вручну — інакше {@code fadeInTicks} мовчки не діяв би на першому
 *       запуску.</li>
 *   <li>Не-{@code KeyframeAnimationPlayer} у шарі: авто-завершення не
 *       підтримується (довжини немає) — лише HOLD. Це обмеження, не помилка.</li>
 *   <li>Динамічна правка кісток живе одну "сесію" пози: скидається на
 *       завершенні/жорсткому скиданні, щоб не просочитись у чужу позу.</li>
 * </ol>
 */
final class PoseLayerRuntime {

    private final ModifierLayer<IAnimation> layer;
    private final PoseLifecycle lifecycle = new PoseLifecycle();

    /** Єдиний fade рушія в шарі (або null). Завжди в списку після adjustment. */
    private AbstractFadeModifier fade;
    private SafeAdjustmentModifier adjustment;
    private BoneAdjustment.Provider adjustmentProvider;
    private PoseSource active;

    PoseLayerRuntime(ModifierLayer<IAnimation> layer) {
        this.layer = layer;
    }

    ModifierLayer<IAnimation> layer() {
        return layer;
    }

    // ───────────────────────────── запуск ─────────────────────────────

    /**
     * Запускає (або перезапускає) позу.
     *
     * @param allowFadeIn {@code false} для переносу пози на новий шар при
     *                    relog: гравець уже "був у позі", fade виглядав би як
     *                    хибне повторне натискання
     */
    void start(KeyframeAnimation clip, PoseSource source, boolean allowFadeIn) {
        KeyframeAnimationPlayer player = new KeyframeAnimationPlayer(clip); // може кинути — тому першим
        int fadeIn = allowFadeIn ? source.fadeInTicks() : 0;

        IAnimation visual = visualSource();   // те, що гравець бачить просто зараз
        detachFade();
        if (adjustment != null && adjustment.isFadingOut()) {
            reinstallAdjustment();            // "розгасити" fade-out PAL не вміє — міняємо екземпляр
        }

        if (fadeIn > 0) {
            AbstractFadeModifier f = AbstractFadeModifier.standardFadeIn(fadeIn, PoseEaseMapper.toPal(source.ease()));
            f.setBeginAnimation(visual);      // null → fade із нейтралі (fadeFromNothing)
            layer.addModifierLast(f);
            fade = f;
        }
        if (adjustment != null) {
            adjustment.beginManualFadeIn(fadeIn);
        }
        layer.setAnimation(player);           // тепер лінкує й новий fade → player

        lifecycle.begin(clip.isInfinite, source.fadeOutTicks());
        active = source;
    }

    // ───────────────────────────── зупинка ────────────────────────────

    /** М'яка зупинка: fade-out за {@code fadeOutTicks} пози (або миттєво, якщо 0). */
    void requestStop() {
        if (lifecycle.state() == PoseLifecycle.State.IDLE) return;
        boolean clipActive = false;
        int toStop = PoseLifecycle.UNBOUNDED;
        IAnimation a = layer.getAnimation();
        if (a instanceof KeyframeAnimationPlayer) {
            KeyframeAnimationPlayer kp = (KeyframeAnimationPlayer) a;
            clipActive = kp.isActive();
            toStop = ticksToStop(kp);
        } else if (a != null) {
            clipActive = a.isActive();
        }
        apply(lifecycle.requestStop(clipActive, toStop));
    }

    /** Жорстка зупинка (смерть, logout): без fade, нічого не лишається в шарі. */
    void hardStop() {
        detachFade();
        layer.setAnimation(null);
        removeAdjustmentModifier();
        lifecycle.reset();
        active = null;
    }

    // ────────────────────────────── тік ───────────────────────────────

    /** Викликається рушієм щотік гравця (не PAL-ом — див. чому в клас-докстрінгу). */
    void tick() {
        if (lifecycle.state() == PoseLifecycle.State.IDLE) {
            // Страховка інваріанта 1: у порожньому шарі не має бути fade-ів.
            if (layer.size() > keepCount() && layer.getAnimation() == null) {
                detachFade();
            }
            return;
        }
        boolean clipActive = false;
        int toStop = 0;
        IAnimation a = layer.getAnimation();
        if (a instanceof KeyframeAnimationPlayer) {
            KeyframeAnimationPlayer kp = (KeyframeAnimationPlayer) a;
            clipActive = kp.isActive();
            toStop = ticksToStop(kp);
        } else if (a != null) {
            clipActive = a.isActive();
            toStop = PoseLifecycle.UNBOUNDED;
        }
        apply(lifecycle.tick(clipActive, toStop));
    }

    private static int ticksToStop(KeyframeAnimationPlayer kp) {
        KeyframeAnimation data = kp.getData();
        return data.isInfinite ? PoseLifecycle.UNBOUNDED : data.stopTick - kp.getTick();
    }

    private void apply(PoseLifecycle.Decision decision) {
        switch (decision) {
            case START_FADE_OUT:
                startFadeOut(lifecycle.currentFadeLength());
                break;
            case FINISH:
                finish();
                break;
            case NONE:
            default:
                break;
        }
    }

    private void startFadeOut(int length) {
        IAnimation visual = visualSource();
        detachFade();
        AbstractFadeModifier f = AbstractFadeModifier.standardFadeIn(length, PoseEaseMapper.toPal(active.ease()));
        f.setBeginAnimation(visual);          // старий кліп (або його fade-in) дограє під час згасання
        layer.addModifierLast(f);
        fade = f;
        layer.setAnimation(null);             // null = згасання до нейтралі
        if (adjustment != null) {
            adjustment.fadeOut(length);       // правка згасає разом із позою
        }
    }

    private void finish() {
        detachFade();
        layer.setAnimation(null);
        removeAdjustmentModifier();
        lifecycle.reset();
        active = null;
    }

    // ─────────────────────── fade-и: візуал і чистка ───────────────────

    /**
     * Що гравець бачить у шарі просто зараз: незавершений fade рушія (він
     * уже містить і стару, і нову анімацію) або, якщо fade-у нема чи він
     * дограв, — сама анімація шару.
     */
    private IAnimation visualSource() {
        if (fade != null && !fade.canRemove()) return fade;
        return layer.getAnimation();
    }

    /**
     * Прибирає всі fade-и рушія, лишаючи adjustment (індекс 0). Відокремлений
     * fade зберігає власні внутрішні посилання ({@code anim}/{@code
     * beginAnimation}), тож його ще можна використати як {@code beginAnimation}
     * наступного fade-у.
     */
    private void detachFade() {
        int keep = keepCount();
        while (layer.size() > keep) {
            layer.removeModifier(layer.size() - 1);
        }
        fade = null;
    }

    private int keepCount() {
        return adjustment != null ? 1 : 0;
    }

    // ───────────────────── динамічна правка кісток ─────────────────────

    /**
     * Ставить (або замінює) динамічну правку кісток поверх шару. Повторний
     * виклик з тим самим провайдером — no-op (безпечно викликати щотік).
     * Для плавної появи правки викликайте <b>до</b> {@link #start}.
     */
    void setAdjustment(BoneAdjustment.Provider provider) {
        if (provider == null) {
            clearAdjustment();
            return;
        }
        if (adjustment != null && adjustmentProvider == provider && !adjustment.isFadingOut()) return;
        removeAdjustmentModifier();
        installAdjustment(provider);
        if (lifecycle.state() == PoseLifecycle.State.FADING_OUT) {
            adjustment.fadeOut(lifecycle.fadeRemaining());
        }
    }

    void clearAdjustment() {
        removeAdjustmentModifier();
    }

    BoneAdjustment.Provider adjustmentProvider() {
        return adjustmentProvider;
    }

    private void installAdjustment(BoneAdjustment.Provider provider) {
        SafeAdjustmentModifier m = new SafeAdjustmentModifier(provider);
        layer.addModifierBefore(m);           // індекс 0 — вимога PAL
        adjustment = m;
        adjustmentProvider = provider;
    }

    private void reinstallAdjustment() {
        BoneAdjustment.Provider provider = adjustmentProvider;
        removeAdjustmentModifier();
        installAdjustment(provider);
    }

    private void removeAdjustmentModifier() {
        if (adjustment != null) {
            layer.removeModifier(0);
            adjustment = null;
            adjustmentProvider = null;
        }
    }

    // ─────────────────── зсув камери за кісткою head ───────────────────

    /**
     * Поворот кістки {@code head} цього шару (радіани: x=pitch, y=yaw, z=roll),
     * помножений на {@link PoseSource#cameraFollow()}; {@code null}, якщо шар
     * порожній або стеження вимкнено. Береться з самого шару (разом із fade-in/out
     * і правкою), тому зсув камери з'являється й згасає разом із позою.
     * Значення {@code value0=ZERO}: кістка без keyframe-ів дає 0, тобто
     * "нічого не додавати".
     */
    Vec3f cameraOffset(float delta) {
        PoseSource src = active;
        if (src == null || !src.followsCamera() || lifecycle.state() == PoseLifecycle.State.IDLE) return null;
        Vec3f r = layer.get3DTransform("head", TransformType.ROTATION, delta, Vec3f.ZERO);
        float k = src.cameraFollow();
        float x = r.getX() * k, y = r.getY() * k, z = r.getZ() * k;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return null;
        return new Vec3f(x, y, z);
    }

    // ───────────────────────────── стан ───────────────────────────────

    /** Поза запитана й не знімається (PLAYING). Під час fade-out — {@code false}, щоб консюмер міг перезапустити. */
    boolean isActive() {
        return lifecycle.state() == PoseLifecycle.State.PLAYING;
    }

    /** Щось ще видно в шарі: PLAYING або FADING_OUT. */
    boolean isVisible() {
        return lifecycle.state() != PoseLifecycle.State.IDLE;
    }

    PoseLifecycle.State state() {
        return lifecycle.state();
    }

    PoseSource activeSource() {
        return active;
    }

    /** Скільки fade-ів рушія зараз у шарі (для тестів інваріанта 1). */
    int fadeModifierCount() {
        return layer.size() - keepCount();
    }

    boolean hasAdjustment() {
        return adjustment != null;
    }
}
