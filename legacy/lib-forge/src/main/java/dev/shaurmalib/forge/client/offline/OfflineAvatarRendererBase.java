package dev.shaurmalib.forge.client.offline;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.forge.offline.OfflineAvatarBase;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Рендерер тіла офлайн-гравця (план, §5.11): модель {@code PlayerModel}
 * (wide/slim з властивості {@code textures}), шари броні, предмета в руках,
 * єлітр і голови. Перенесено з {@code PersistentPlayerRenderer}
 * (Open-Persistence), без «сну» як окремої поведінки: поза тепер приходить із
 * синхронізованої {@code Pose} сутності (ваніль: стоїть, присів, плаває, лежить).
 * <p>
 * <b>Походження:</b> Порт {@code PersistentPlayerRenderer} з Open-Persistence (com.norwood.openpersistence,
 * 1.20.1; у його build.gradle.kts заявлено GPL-3.0). Питання ліцензії
 * перенесеного коду відкрите — див. план, §12.4.
 * <p>
 * Ім'я над головою не малюється ніколи ({@link #shouldShowName}).
 * <p>
 * Реєстрація (консюмер, {@code EntityRenderersEvent.RegisterRenderers}):
 * <pre>{@code
 * event.registerEntityRenderer(ModEntities.OFFLINE_AVATAR.get(), OfflineAvatarRendererBase::new);
 * }</pre>
 * Кастомні пози режиму (поранений, повзе) підклас додає через {@link #applyCustomPose}.
 */
@OnlyIn(Dist.CLIENT)
public class OfflineAvatarRendererBase<T extends OfflineAvatarBase>
        extends LivingEntityRenderer<T, PlayerModel<T>> {

    private final PlayerModel<T> wideModel;
    private final PlayerModel<T> slimModel;

    public OfflineAvatarRendererBase(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, context.getModelSet()));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = OfflineSkinResolver.isSlim(entity) ? this.slimModel : this.wideModel;
        applyModelVisibility(entity, this.model);
        applyArmPoses(entity, this.model);
        this.model.crouching = entity.isCrouching();
        applyCustomPose(entity, this.model, entity.getCustomPoseId());
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * Хук для кастомних поз режиму (0 = немає пози). За замовчуванням нічого не робить:
     * ванільні пози ({@code Pose}) обробляє сам рендерер.
     */
    protected void applyCustomPose(T entity, PlayerModel<T> model, int poseId) {
    }

    private static <E extends OfflineAvatarBase> void applyModelVisibility(E entity, PlayerModel<E> model) {
        model.setAllVisible(true);
        model.hat.visible = entity.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = entity.isModelPartShown(PlayerModelPart.JACKET);
        model.leftPants.visible = entity.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = entity.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = entity.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = entity.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
    }

    /** Рука з предметом трохи піднята, як у гравця (спрощена версія {@code PlayerRenderer#getArmPose}). */
    private static <E extends OfflineAvatarBase> void applyArmPoses(E entity, PlayerModel<E> model) {
        ItemStack mainHand = entity.getMainHandItem();
        ItemStack offHand = entity.getOffhandItem();
        HumanoidModel.ArmPose mainPose =
                mainHand.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        HumanoidModel.ArmPose offPose =
                offHand.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.rightArmPose = offPose;
            model.leftArmPose = mainPose;
        }
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected boolean shouldShowName(T entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return OfflineSkinResolver.skinLocation(entity);
    }
}
