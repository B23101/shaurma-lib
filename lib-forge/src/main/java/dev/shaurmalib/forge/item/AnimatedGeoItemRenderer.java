package dev.shaurmalib.forge.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.shaurmalib.common.item.ArmOverride;
import dev.shaurmalib.common.item.HandSlot;
import dev.shaurmalib.common.item.ItemDefinition;
import dev.shaurmalib.common.item.ItemDefinitionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Базовий рендерер для анімованих предметів бібліотеки — узагальнення
 * {@code AnimatedGeoItemRenderer.java} з оригіналу snipers_shaurma.
 * <p>
 * КЛЮЧОВА ЗМІНА проти оригіналу: там кістки з іменами буквально
 * "LeftArm"/"RightArm" рендерились ЗАВЖДИ як руки гравця, незалежно від
 * того, який предмет це є і чи це насправді потрібно. Тут — рушій дивиться
 * в {@link ItemDefinitionRegistry} за класом предмета і бере
 * {@link ArmOverride}, якщо мод його явно задекларував; якщо для слоту
 * override не задано — кістка рендериться як звичайна ЧАСТИНА ГЕО-МОДЕЛІ
 * (тобто робить те, що в оригіналі робив окремий метод
 * {@code renderRecursivelyAsGeo} — цей рушій просто вибирає між двома
 * гілками автоматично, без явного викликання підкласом альтернативного
 * методу).
 */
@OnlyIn(Dist.CLIENT)
public class AnimatedGeoItemRenderer<T extends Item & AnimatedGeoItem> extends GeoItemRenderer<T> {

    protected MultiBufferSource currentBuffer;
    protected RenderType renderType;
    public ItemDisplayContext transformType;
    protected T animatable;
    private final Set<String> hiddenBones = new HashSet<>();

    private static final float SCALE = 1.0f / 16.0f;

    // Дефолтне позиціювання руки відносно кістки — як у оригіналі; мод
    // може перевизначити через переобчислені константи в підкласі, якщо
    // потрібне інше зміщення для конкретної моделі.
    protected float armOffsetX = 1.0f * SCALE;
    protected float armOffsetY = 2.0f * SCALE;
    protected float armOffsetZ = 0f;
    protected float armYaw = 0f;
    protected float armPitch = 0f;
    protected float armScale = 1.0f;

    public AnimatedGeoItemRenderer(GeoModel<T> model) {
        super(model);
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture,
                                     MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(getTextureLocation(animatable));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType,
                              PoseStack matrixStack, MultiBufferSource bufferIn,
                              int combinedLightIn, int p_239207_6_) {
        this.transformType = transformType;
        if (stack.getItem() instanceof AnimatedGeoItem item) {
            item.getTransformType(transformType);
        }
        super.renderByItem(stack, transformType, matrixStack, bufferIn, combinedLightIn, p_239207_6_);
    }

    @Override
    public void actuallyRender(PoseStack matrixStackIn, T animatable,
                                BakedGeoModel model, RenderType type,
                                MultiBufferSource renderTypeBuffer, VertexConsumer vertexBuilder,
                                boolean isRenderer, float partialTicks,
                                int packedLightIn, int packedOverlayIn,
                                float red, float green, float blue, float alpha) {
        this.currentBuffer = renderTypeBuffer;
        this.renderType = type;
        this.animatable = animatable;
        super.actuallyRender(matrixStackIn, animatable, model, type, renderTypeBuffer,
                vertexBuilder, isRenderer, partialTicks,
                packedLightIn, packedOverlayIn, red, green, blue, alpha);
    }

    @Override
    public void renderRecursively(PoseStack stack, T animatable,
                                   GeoBone bone, RenderType type,
                                   MultiBufferSource buffer, VertexConsumer bufferIn,
                                   boolean isReRender, float partialTick,
                                   int packedLightIn, int packedOverlayIn,
                                   float red, float green, float blue, float alpha) {
        String boneName = bone.getName();
        Optional<ArmOverride> override = findArmOverride(animatable, boneName);

        if (override.isPresent()) {
            bone.setHidden(true);
            renderArmSkinOverBone(stack, bone, override.get());
        } else {
            bone.setHidden(hiddenBones.contains(boneName));
        }

        super.renderRecursively(stack, animatable, bone, type, buffer, bufferIn,
                isReRender, partialTick, packedLightIn, packedOverlayIn,
                red, green, blue, alpha);
    }

    /** Шукає, чи ця кістка задекларована модом як заміна руки гравця для якогось слоту. */
    private Optional<ArmOverride> findArmOverride(T animatable, String boneName) {
        ItemDefinition<?, ?> def = ItemDefinitionRegistry
                .<Object, Object>get(animatable.getClass())
                .orElse(null);
        if (def == null) return Optional.empty();
        for (ArmOverride ov : def.armOverrides.values()) {
            if (ov.boneName().equals(boneName)) return Optional.of(ov);
        }
        return Optional.empty();
    }

    private void renderArmSkinOverBone(PoseStack stack, GeoBone bone, ArmOverride override) {
        if (this.transformType == null || !this.transformType.firstPerson()) return;

        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null) return;

        float armsAlpha = player.isInvisible() ? 0.15f : 1.0f;
        PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = playerRenderer.getModel();

        stack.pushPose();

        RenderUtils.translateMatrixToBone(stack, bone);
        RenderUtils.translateToPivotPoint(stack, bone);
        RenderUtils.rotateMatrixAroundBone(stack, bone);
        RenderUtils.scaleMatrixForBone(stack, bone);
        RenderUtils.translateAwayFromPivotPoint(stack, bone);

        ResourceLocation skin = player.getSkinTextureLocation();
        VertexConsumer armBuilder = currentBuffer.getBuffer(RenderType.entitySolid(skin));
        VertexConsumer sleeveBuilder = currentBuffer.getBuffer(RenderType.entityTranslucent(skin));

        boolean isRight = override.handSlot() == HandSlot.MAIN_HAND;
        float signedX = isRight ? armOffsetX : -armOffsetX;

        stack.translate(signedX, armOffsetY, armOffsetZ);
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(armYaw));
        stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(armPitch));
        stack.scale(armScale, armScale, armScale);

        if (isRight) {
            AnimUtils.renderPartOverBone(model.rightArm, bone, stack, armBuilder, packedLight(), OverlayTexture.NO_OVERLAY, armsAlpha);
            AnimUtils.renderPartOverBone(model.rightSleeve, bone, stack, sleeveBuilder, packedLight(), OverlayTexture.NO_OVERLAY, armsAlpha);
        } else {
            AnimUtils.renderPartOverBone(model.leftArm, bone, stack, armBuilder, packedLight(), OverlayTexture.NO_OVERLAY, armsAlpha);
            AnimUtils.renderPartOverBone(model.leftSleeve, bone, stack, sleeveBuilder, packedLight(), OverlayTexture.NO_OVERLAY, armsAlpha);
        }

        currentBuffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(this.animatable)));
        stack.popPose();
    }

    // packedLight у оригіналі проходив як параметр renderRecursively; тут беремо дефолт
    // повного світла для сегмента заміни руки — узгоджено з тим, як це вже було в оригіналі
    // (передавався той самий packedLightIn з батьківського виклику). Підклас може
    // перевизначити через збереження останнього packedLightIn, якщо потрібна точність.
    private int lastPackedLight = 15 << 20 | 15 << 4;

    private int packedLight() {
        return lastPackedLight;
    }

    @Override
    public ResourceLocation getTextureLocation(T instance) {
        return super.getTextureLocation(instance);
    }
}
