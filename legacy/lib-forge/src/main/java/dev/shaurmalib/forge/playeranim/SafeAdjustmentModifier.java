package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.shaurmalib.common.playeranim.BoneAdjustment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * {@link AdjustmentModifier} без NaN і без винятків провайдера.
 *
 * <h3>Баг PAL 1.0.2-rc1, який це виправляє</h3>
 * {@code AdjustmentModifier.getFadeIn} рахує {@code currentTick / (float)
 * beginTick}. При {@code beginTick == 0} на першому кадрі
 * ({@code tick=0, delta=0}) це {@code 0f/0f = NaN}; {@code Math.min(NaN, 1)}
 * лишає {@code NaN}, воно множиться на правку й потрапляє в {@code ModelPart}
 * — мерехтіння руки <i>на один кадр</i> саме при старті дії. Причому лише
 * для анімацій, намальованих з нульового кадру (тому тестова анімація з
 * ненульовим {@code beginTick} його б не показала). З {@code beginTick=3}
 * бага нема.
 *
 * <h3>Що додано</h3>
 * <ul>
 *   <li>{@link #getFadeIn}: при {@code beginTick <= 0} повертає 1.</li>
 *   <li>Власний fade-in ({@link #beginManualFadeIn}): коли рушій ставить
 *       fade-модифікатор над позою, {@code getAnim()} цього модифікатора —
 *       уже не {@code KeyframeAnimationPlayer}, і PAL-овий fade-in
 *       мовчки перестає працювати (правка з'являлась би на 100% одразу,
 *       поки сама поза ще тільки проявляється).</li>
 *   <li>{@link #transformVector}: будь-яке нескінченне/NaN значення (напр.
 *       динамічний провайдер повернув {@code player.getXRot()} у момент
 *       респавну) не потрапляє в модель.</li>
 *   <li>Провайдер, що кинув виняток, не валить клієнт: кістка лишається
 *       прозорою, попередження логується один раз.</li>
 * </ul>
 * {@code getFadeOut} PAL ділить лише за умови {@code length > 0} — безпечний.
 */
final class SafeAdjustmentModifier extends AdjustmentModifier {

    private static final Logger LOGGER = LogManager.getLogger("PoseAdjustment");

    private int manualFadeInLength = 0;
    private int manualElapsed = 0;
    private int manualFadeOutLength = 0;
    private int manualFadeOutElapsed = 0;
    private boolean fadingOut = false;
    private boolean warned = false;

    SafeAdjustmentModifier(BoneAdjustment.Provider provider) {
        super(null);
        // super(null) бо this-виклик у лямбді ще неможливий; source виставляємо одразу.
        this.source = bone -> safeApply(provider, bone);
    }

    private Optional<PartModifier> safeApply(BoneAdjustment.Provider provider, String bone) {
        try {
            Optional<BoneAdjustment.Part> part = provider.partFor(bone);
            if (part == null || part.isEmpty()) return Optional.empty();
            BoneAdjustment.Part p = part.get();
            return Optional.of(new PartModifier(
                    new Vec3f(finiteOrZero(p.rotX()), finiteOrZero(p.rotY()), finiteOrZero(p.rotZ())),
                    new Vec3f(finiteOrZero(p.offX()), finiteOrZero(p.offY()), finiteOrZero(p.offZ()))));
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("BoneAdjustment.Provider кинув виняток на кістці '{}' — кістку пропущено", bone, t);
            }
            return Optional.empty();
        }
    }

    private static float finiteOrZero(float v) {
        return Float.isFinite(v) ? v : 0f;
    }

    /** Плавна поява правки за {@code length} тіків (0 = вимкнено, діє логіка PAL). */
    void beginManualFadeIn(int length) {
        this.manualFadeInLength = Math.max(0, length);
        this.manualElapsed = 0;
    }

    /** true, якщо вже запущено fade-out — такий екземпляр не можна "розгасити", його треба замінити. */
    boolean isFadingOut() {
        return fadingOut;
    }

    /**
     * Лінійне згасання правки рівно за {@code length} тіків: множник доходить
     * до 0 <b>саме на тіку завершення</b> пози, коли рушій знімає модифікатор.
     * <p>
     * Власна реалізація замість {@code AdjustmentModifier.fadeOut}: PAL ставить
     * {@code remainingFadeout = n + 1}, тобто множник відстає на один тік і на
     * останньому кадрі лишається {@code 1/n} правки (для n=4 і правки в 0.5 рад
     * — ~7° на кадр), яку потім різко знімає завершення — видимий "поп" рівно
     * в кінці дії.
     */
    @Override
    public void fadeOut(int length) {
        this.manualFadeOutLength = Math.max(0, length);
        this.manualFadeOutElapsed = 0;
        this.fadingOut = length > 0;
    }

    @Override
    public void tick() {
        super.tick();
        manualElapsed++;
        if (manualFadeOutLength > 0) {
            manualFadeOutElapsed++;
        }
    }

    @Override
    protected float getFadeOut(float delta) {
        if (manualFadeOutLength > 0) {
            return clamp01(1f - (manualFadeOutElapsed + delta) / (float) manualFadeOutLength);
        }
        return super.getFadeOut(delta);
    }

    @Override
    protected float getFadeIn(float delta) {
        if (manualFadeInLength > 0) {
            return clamp01((manualElapsed + delta) / (float) manualFadeInLength);
        }
        IAnimation animation = this.getAnim();
        if (animation instanceof KeyframeAnimationPlayer) {
            KeyframeAnimationPlayer player = (KeyframeAnimationPlayer) animation;
            int begin = player.getData().beginTick;
            if (begin <= 0) return 1f; // саме тут PAL віддавав 0f/0f = NaN
            return clamp01((player.getTick() + delta) / (float) begin);
        }
        return 1f;
    }

    @Override
    protected Vec3f transformVector(Vec3f vector, TransformType type, PartModifier partModifier, float fade) {
        if (!Float.isFinite(fade)) return vector;
        Vec3f result = super.transformVector(vector, type, partModifier, fade);
        if (!Float.isFinite(result.getX()) || !Float.isFinite(result.getY()) || !Float.isFinite(result.getZ())) {
            return vector;
        }
        return result;
    }

    private static float clamp01(float v) {
        if (!Float.isFinite(v)) return 1f;
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
