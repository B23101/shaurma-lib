package dev.shaurmalib.anim.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.Rig;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Усе, що роблять міксини 1.20.1 (кожен міксин — 1–3 рядки виклику сюди). Порт на іншу версію —
 * це нові міксини з тими ж викликами, логіка тут і в ядрі не змінюється.
 *
 * Порядок за кадр для гравця:
 * <ol>
 *   <li>{@code LivingEntityRenderer.render} перед {@code prepareMobModel} → {@link #beginRender}: корінь тіла;</li>
 *   <li>{@code setupAnim} HEAD → {@link #beforeSetupAnim}: прибрати зсуви минулого кадру (модель спільна);</li>
 *   <li>{@code setupAnim} TAIL → {@link #afterSetupAnim}: ванільна поза = база, поверх — шари рушія;</li>
 *   <li>{@code ItemInHandLayer} → {@link #itemBone}/{@link #hideHeld}; шар пропсів;</li>
 *   <li>кінець {@code render} → {@link #endRender}.</li>
 * </ol>
 */
public final class McAnimHooks {

    private McAnimHooks() {}

    private static final Set<EntityModel<?>> DIRTY = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<HumanoidModel<?>, Pose> BASES = new WeakHashMap<>();
    private static float partial;

    static void markDirty(EntityModel<?> m) { DIRTY.add(m); }

    public static void beginRender(LivingEntity e, float partialTick, PoseStack ps, EntityModel<?> model) {
        McAnim.renderingEntity = null;
        if (!(model instanceof HumanoidModel<?>) || McAnim.renderRigOf(e) != Rig.PLAYER) return;
        McAnim.renderingEntity = e;
        partial = partialTick;
        Pose pose = McAnim.evaluate(e, partialTick, null, true);
        PlayerModelBridge.applyRoot(ps, pose);
    }

    public static void endRender() {
        McAnim.renderingEntity = null;
    }

    public static void beforeSetupAnim(HumanoidModel<?> model) {
        if (DIRTY.remove(model)) PlayerModelBridge.reset(model);
    }

    public static void afterSetupAnim(HumanoidModel<?> model, LivingEntity e) {
        if (e != McAnim.renderingEntity || e == null) return;
        Pose base = BASES.computeIfAbsent(model, k -> new Pose(Rig.PLAYER));
        PlayerModelBridge.capture(model, base);
        Pose pose = McAnim.evaluate(e, partial, base, true);
        if (pose != null && PlayerModelBridge.apply(model, pose)) DIRTY.add(model);
    }

    /** Кістка предмета в руці (третя особа). */
    public static void itemBone(LivingEntity e, PoseStack ps, HumanoidArm arm) {
        if (e != McAnim.renderingEntity) return;
        PlayerModelBridge.applyItemBone(ps, McAnim.framePose(e), arm);
    }

    /** Пропс замінює предмет у руці — ванільний не малювати. */
    public static boolean hideHeld(LivingEntity e, HumanoidArm arm) {
        if (e != McAnim.renderingEntity) return false;
        Pose p = McAnim.framePose(e);
        return p != null && (arm == HumanoidArm.RIGHT ? p.hideRightHeld : p.hideLeftHeld);
    }
}
