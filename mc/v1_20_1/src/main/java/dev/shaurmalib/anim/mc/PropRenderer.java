package dev.shaurmalib.anim.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.clip.BoneTrack;
import dev.shaurmalib.anim.clip.PropSpec;
import dev.shaurmalib.anim.mesh.GeoMesh;
import dev.shaurmalib.anim.mesh.PropBaker;
import dev.shaurmalib.anim.pose.BoneTransform;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.Rig;
import dev.shaurmalib.anim.runtime.Animator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Малює пропси кліпа (модель з Blockbench з власною текстурою, або предмет гри) на кістках
 * ванільної моделі гуманоїда. Кістки самого пропса (кришка аптечки, лезо ножиць) анімуються
 * треками того ж кліпа — у Blockbench це просто групи всередині групи пропса.
 */
public final class PropRenderer {

    private PropRenderer() {}

    private static final Map<String, ItemStack> ITEM_CACHE = new HashMap<>();

    public static void renderProps(PoseStack ps, MultiBufferSource buffers, int light, LivingEntity entity,
                                   HumanoidModel<?> model, Pose pose) {
        if (pose == null || pose.props().isEmpty()) return;
        Animator animator = ShaurmaAnim.find(entity.getUUID());
        if (animator == null) return;
        for (Pose.ActiveProp prop : pose.props()) {
            try {
                renderProp(ps, buffers, light, entity, model, pose, animator, prop);
            } catch (RuntimeException ignored) {
                // кривий пропс не має валити рендер гравця
            }
        }
    }

    private static void renderProp(PoseStack ps, MultiBufferSource buffers, int light, LivingEntity entity,
                                   HumanoidModel<?> model, Pose pose, Animator animator, Pose.ActiveProp prop) {
        PropSpec spec = prop.spec();
        String parent = spec.parentBone() == null ? "right_arm" : spec.parentBone();
        int parentIdx = Rig.PLAYER.indexOf(parent);
        if (parentIdx < 0) return;

        ps.pushPose();
        if (parentIdx == PlayerModelBridge.RIGHT_ITEM || parentIdx == PlayerModelBridge.LEFT_ITEM) {
            HumanoidArm arm = parentIdx == PlayerModelBridge.RIGHT_ITEM ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
            model.translateToHand(arm, ps);
            float[] a = PlayerModelBridge.itemAnchor(arm);
            ps.translate(a[0] / 16f, a[1] / 16f, a[2] / 16f);
            if (pose.touched(parentIdx)) PlayerModelBridge.applyLocal(ps, pose.bone(parentIdx));
        } else if (parentIdx == PlayerModelBridge.ROOT) {
            ps.translate(0, 12f / 16f, 0);
        } else {
            ModelPart part = PlayerModelBridge.part(model, parentIdx);
            if (part == null) { ps.popPose(); return; }
            part.translateAndRotate(ps);
        }

        float t = prop.clipTime();
        float[] scratch = new float[3];
        if (spec.item() != null) {
            // предмет гри: власна кістка пропса (ім'я = id пропса) може його зсувати
            BoneTransform b = new BoneTransform();
            boolean any = false;
            if (animator.samplePropBone(prop.clip(), spec.id(), BoneTrack.ChannelType.POSITION, t, scratch)) {
                b.px = scratch[0]; b.py = scratch[1]; b.pz = scratch[2]; any = true;
            }
            if (animator.samplePropBone(prop.clip(), spec.id(), BoneTrack.ChannelType.ROTATION, t, scratch)) {
                b.rx = scratch[0]; b.ry = scratch[1]; b.rz = scratch[2]; any = true;
            }
            if (any) PlayerModelBridge.applyLocal(ps, b);
            ps.mulPose(Axis.XP.rotationDegrees(-90f));
            ps.mulPose(Axis.YP.rotationDegrees(180f));
            Minecraft.getInstance().getItemRenderer().renderStatic(entity, item(spec.item()),
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers, entity.level(), light,
                OverlayTexture.NO_OVERLAY, entity.getId());
        } else {
            GeoMesh mesh = ShaurmaAnim.clips().mesh(spec.model());
            ResourceLocation tex = spec.texture() == null ? null : ResourceLocation.tryParse(spec.texture());
            if (mesh != null && tex != null) {
                ps.mulPose(Axis.ZP.rotationDegrees(180f)); // сцена Blockbench → простір ModelPart
                RenderType type = spec.emissive() ? RenderType.eyes(tex) : RenderType.entityCutoutNoCull(tex);
                VertexConsumer vc = buffers.getBuffer(type);
                Matrix4f m4 = ps.last().pose();
                Matrix3f m3 = ps.last().normal();
                int lit = spec.emissive() ? LightTexture.FULL_BRIGHT : light;
                PropBaker.bake(mesh, Rig.PLAYER.pivot(parentIdx),
                    (bone, type2, out) -> animator.samplePropBone(prop.clip(), bone, type2, t, out),
                    (pos, uv, nx, ny, nz) -> {
                        for (int v = 0; v < 4; v++) {
                            vc.vertex(m4, pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2])
                                .color(1f, 1f, 1f, 1f)
                                .uv(uv[v * 2], uv[v * 2 + 1])
                                .overlayCoords(OverlayTexture.NO_OVERLAY)
                                .uv2(lit)
                                .normal(m3, nx, ny, nz)
                                .endVertex();
                        }
                    });
            }
        }
        ps.popPose();
    }

    private static ItemStack item(String id) {
        return ITEM_CACHE.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            Item it = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
            return new ItemStack(it);
        });
    }
}
