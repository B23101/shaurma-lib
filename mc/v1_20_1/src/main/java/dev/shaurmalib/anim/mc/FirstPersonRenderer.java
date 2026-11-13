package dev.shaurmalib.anim.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.pose.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Перша особа для кліпів з {@code first_person: "model"}: замість ванільних рук рушій малює
 * анімовані руки гравця (його скін), предмети на кістках {@code right_item/left_item} і пропси —
 * тобто те саме, що бачать інші, але з камери. Відкрити аптечку, дістати бинт — видно від першої особи.
 *
 * Геометрія: при тілі, поверненому за камерою, вигляд-з-моделі = {@code Rx(pitch)·T(0,−eye,0)·S(−1,−1,1)·T(0,−1.501,0)}.
 * {@code first_person_pitch} 0 — руки «прикручені» до камери (як вʼюмодель у шутерах), 1 — висять у світі.
 */
public final class FirstPersonRenderer {

    private FirstPersonRenderer() {}

    /**
     * Викликається замість ванільного {@code renderHandsWithItems}.
     * @return true — рушій сам намалював (або сховав) руки; ваніль пропустити
     */
    public static boolean render(float partial, PoseStack ps, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light) {
        // вимкнено модом або є first-person мод зі справжнім тілом — нічого не підміняти (ні руки, ні hide)
        if (!dev.shaurmalib.anim.ShaurmaAnim.firstPersonArms()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson() || player.isScoping()) return false;
        Pose pose = McAnim.evaluate(player, partial, null, true);
        if (pose == null || pose.firstPersonClip == null || pose.firstPersonWeight < 0.5f) return false;
        if (pose.firstPersonMode == AnimationClip.FirstPerson.HIDE) {
            buffers.endBatch();
            return true;
        }
        EntityRenderer<? super LocalPlayer> r = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(r instanceof PlayerRenderer pr)) return false;
        PlayerModel<AbstractClientPlayer> model = pr.getModel();

        ps.pushPose();
        // та сама легка інерція камери, що у ванільних рук
        float xBob = Mth.lerp(partial, player.xBobO, player.xBob);
        float yBob = Mth.lerp(partial, player.yBobO, player.yBob);
        ps.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partial) - xBob) * 0.1f));
        ps.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partial) - yBob) * 0.1f));
        float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        if (pose.firstPersonPitch != 0) ps.mulPose(Axis.XP.rotationDegrees(pitch * pose.firstPersonPitch));
        float[] off = pose.firstPersonOffset;
        ps.translate(off[0] / 16f, off[1] / 16f, off[2] / 16f);
        if (off.length > 3 && off[3] != 0) ps.mulPose(Axis.XP.rotationDegrees(off[3]));
        ps.translate(0, -player.getEyeHeight(), 0);
        ps.scale(-1f, -1f, 1f);
        ps.translate(0, -1.501f, 0);

        PlayerModelBridge.reset(model);
        model.attackTime = 0;
        model.crouching = false;
        PlayerModelBridge.applyArms(model, pose);
        McAnimHooks.markDirty(model);

        var skin = player.getSkinTextureLocation();
        var solid = buffers.getBuffer(RenderType.entitySolid(skin));
        model.rightArm.render(ps, solid, light, OverlayTexture.NO_OVERLAY);
        model.leftArm.render(ps, solid, light, OverlayTexture.NO_OVERLAY);
        var translucent = buffers.getBuffer(RenderType.entityTranslucent(skin));
        model.rightSleeve.render(ps, translucent, light, OverlayTexture.NO_OVERLAY);
        model.leftSleeve.render(ps, translucent, light, OverlayTexture.NO_OVERLAY);

        renderHeld(ps, buffers, light, player, model, pose, HumanoidArm.RIGHT);
        renderHeld(ps, buffers, light, player, model, pose, HumanoidArm.LEFT);
        PropRenderer.renderProps(ps, buffers, light, player, model, pose, false);
        ps.popPose();
        buffers.endBatch();
        return true;
    }

    private static void renderHeld(PoseStack ps, MultiBufferSource buffers, int light, LocalPlayer player,
                                   PlayerModel<AbstractClientPlayer> model, Pose pose, HumanoidArm arm) {
        if (arm == HumanoidArm.RIGHT ? pose.hideRightHeld : pose.hideLeftHeld) return;
        ItemStack stack = arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
        if (stack.isEmpty()) return;
        boolean left = arm == HumanoidArm.LEFT;
        ps.pushPose();
        model.translateToHand(arm, ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90f));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
        PlayerModelBridge.applyItemBone(ps, pose, arm);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
            left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, ps, buffers, light);
        ps.popPose();
    }
}
