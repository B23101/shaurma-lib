package dev.shaurmalib.forge.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Перенесено 1:1 з {@code core/util/AnimUtils.java} оригіналу
 * snipers_shaurma — чиста рендер-утиліта без жодних snipers-специфічних
 * даних, лише GeckoLib bone → vanilla ModelPart зіставлення.
 */
@OnlyIn(Dist.CLIENT)
public final class AnimUtils {

    private AnimUtils() {}

    public static void renderPartOverBone(ModelPart model, GeoBone bone,
                                           PoseStack stack, VertexConsumer buffer,
                                           int packedLightIn, int packedOverlayIn, float alpha) {
        renderPartOverBone(model, bone, stack, buffer, packedLightIn, packedOverlayIn, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void renderPartOverBone(ModelPart model, GeoBone bone,
                                           PoseStack stack, VertexConsumer buffer,
                                           int packedLightIn, int packedOverlayIn,
                                           float r, float g, float b, float a) {
        setupModelFromBone(model, bone);
        model.render(stack, buffer, packedLightIn, packedOverlayIn, r, g, b, a);
    }

    /**
     * Pivot кістки з geo.json задає базову позицію ванільної руки у
     * bone-просторі. Анімаційні position-keyframe'и додаються поверх
     * pivot через GeckoLib.
     */
    public static void setupModelFromBone(ModelPart model, GeoBone bone) {
        model.setPos(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
        model.xRot = 0.0f;
        model.yRot = 0.0f;
        model.zRot = 0.0f;
    }

    /**
     * Скидає ротації і позицію ModelPart до нуля. Використовується коли
     * позиція вже виставлена через poseStack (напр. через RenderUtils),
     * а не через pivot кістки — щоб уникнути подвійного зміщення.
     */
    public static void resetModelPart(ModelPart model) {
        model.setPos(0f, 0f, 0f);
        model.xRot = 0.0f;
        model.yRot = 0.0f;
        model.zRot = 0.0f;
    }
}
