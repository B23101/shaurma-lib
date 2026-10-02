package dev.shaurmalib.forge.offline;

import com.mojang.authlib.properties.Property;
import dev.shaurmalib.common.offline.OfflineSnapshotContributor;
import dev.shaurmalib.common.offline.OfflineSnapshotWriter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Незмінний знімок стану гравця в момент виходу (план, §5.5). Після створення
 * тіла оригінальний гравець не потрібен.
 * <p>
 * Розмежування: те, що бачать інші ({@code textures}, екіпірування, поза), і те,
 * що дропається ({@link #dropItems()} — повний інвентар + предмети режиму) — різні поля.
 * Знімок живе в пам'яті {@link OfflinePresenceModule}, а не в NBT сутності:
 * правда в реєстрі, сутність лише фізичне тіло.
 */
public final class OfflineAvatarSnapshot {

    private final UUID ownerUuid;
    private final String ownerName;
    private final String skinTexture;
    private final String skinSignature;
    private final byte modelMask;
    private final Map<EquipmentSlot, ItemStack> equipment;
    private final List<ItemStack> dropItems;
    private final CompoundTag extraTags;
    private final String dimensionKey;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float yBodyRot;
    private final float yHeadRot;
    private final Pose pose;
    private final int airSupply;
    private final int fireTicks;
    private final List<MobEffectInstance> effects;
    private final boolean leftHanded;

    private OfflineAvatarSnapshot(UUID ownerUuid, String ownerName, String skinTexture, String skinSignature,
                                  byte modelMask, Map<EquipmentSlot, ItemStack> equipment, List<ItemStack> dropItems,
                                  CompoundTag extraTags, String dimensionKey, double x, double y, double z,
                                  float yaw, float pitch, float yBodyRot, float yHeadRot, Pose pose,
                                  int airSupply, int fireTicks, List<MobEffectInstance> effects, boolean leftHanded) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
        this.modelMask = modelMask;
        this.equipment = equipment;
        this.dropItems = dropItems;
        this.extraTags = extraTags;
        this.dimensionKey = dimensionKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.yBodyRot = yBodyRot;
        this.yHeadRot = yHeadRot;
        this.pose = pose;
        this.airSupply = airSupply;
        this.fireTicks = fireTicks;
        this.effects = effects;
        this.leftHanded = leftHanded;
    }

    /**
     * Знімає стан гравця.
     *
     * @param includeDrops {@code false} для налагоджувального {@code /offline spawn}:
     *                     тіло виглядає як гравець, але нічого не дропає (інакше вбивство
     *                     тіла онлайн-гравця дублювало б його предмети).
     */
    public static OfflineAvatarSnapshot capture(ServerPlayer player, OfflineSnapshotContributor contributor,
                                                boolean includeDrops) {
        String texture = "";
        String signature = "";
        Collection<Property> textures = player.getGameProfile().getProperties().get("textures");
        if (!textures.isEmpty()) {
            Property property = textures.iterator().next();
            texture = property.getValue() == null ? "" : property.getValue();
            signature = property.getSignature() == null ? "" : property.getSignature();
        }

        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.put(slot, player.getItemBySlot(slot).copy());
        }

        List<ItemStack> drops = new ArrayList<>();
        CompoundTag extraTags = new CompoundTag();
        if (includeDrops) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                }
            }
        }
        if (contributor != null) {
            OfflineSnapshotWriter writer = new OfflineSnapshotWriter() {
                @Override
                public void addItem(ItemStack stack) {
                    if (includeDrops && stack != null && !stack.isEmpty()) {
                        drops.add(stack.copy());
                    }
                }

                @Override
                public void putTag(String key, Tag tag) {
                    if (key != null && tag != null) {
                        extraTags.put(key, tag.copy());
                    }
                }
            };
            contributor.capture(player, writer);
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(new MobEffectInstance(effect));
        }

        return new OfflineAvatarSnapshot(
                player.getUUID(), player.getGameProfile().getName(), texture, signature,
                modelCustomisation(player), equipment, Collections.unmodifiableList(drops), extraTags,
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.yBodyRot, player.yHeadRot, player.getPose(),
                player.getAirSupply(), player.getRemainingFireTicks(), effects,
                player.getMainArm() == HumanoidArm.LEFT);
    }

    /** Побудова бітової маски видимих частин моделі з публічного {@code isModelPartShown} (як у Open-Persistence). */
    private static byte modelCustomisation(ServerPlayer player) {
        byte mask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) {
                mask |= part.getMask();
            }
        }
        return mask;
    }

    /** Копія знімка з новою позицією — для респавну тіла там, де воно стояло востаннє. */
    public OfflineAvatarSnapshot atPosition(String dimensionKey, double x, double y, double z, float yaw, float pitch) {
        return new OfflineAvatarSnapshot(ownerUuid, ownerName, skinTexture, skinSignature, modelMask, equipment,
                dropItems, extraTags, dimensionKey, x, y, z, yaw, pitch, yBodyRot, yHeadRot, pose,
                airSupply, fireTicks, effects, leftHanded);
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerName;
    }

    public String skinTexture() {
        return skinTexture;
    }

    public String skinSignature() {
        return skinSignature;
    }

    public byte modelMask() {
        return modelMask;
    }

    public ItemStack equipment(EquipmentSlot slot) {
        ItemStack stack = equipment.get(slot);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    /** Усе, що має випасти, коли тіло вб'ють (повний інвентар + предмети режиму). Незмінний список. */
    public List<ItemStack> dropItems() {
        return dropItems;
    }

    /** NBT режиму, дописаний через {@link OfflineSnapshotWriter#putTag}. Повертається копія. */
    public CompoundTag extraTags() {
        return extraTags.copy();
    }

    public String dimensionKey() {
        return dimensionKey;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float yBodyRot() {
        return yBodyRot;
    }

    public float yHeadRot() {
        return yHeadRot;
    }

    public Pose pose() {
        return pose;
    }

    public int airSupply() {
        return airSupply;
    }

    public int fireTicks() {
        return fireTicks;
    }

    public List<MobEffectInstance> effects() {
        return effects;
    }

    public boolean leftHanded() {
        return leftHanded;
    }
}
