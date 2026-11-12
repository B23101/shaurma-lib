package dev.shaurmalib.forge.offline;

import dev.shaurmalib.common.offline.OfflineScopeId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Фізичне тіло гравця, який вийшов із сервера (план, §5.4). Порт
 * {@code PersistentPlayerEntity} з Open-Persistence (1.20.1): перевірена в грі
 * база (despawn-імунітет, нульова швидкість, синхронізація скіна/імені/моделі),
 * до якої додано scope-токен, гравцеві звуки, самознищення без запису в реєстрі
 * та делегування урону в {@link OfflinePresenceModule}.
 * <p>
 * <b>Походження:</b> Порт {@code PersistentPlayerEntity} з Open-Persistence (com.norwood.openpersistence,
 * 1.20.1; у його build.gradle.kts заявлено GPL-3.0). Питання ліцензії
 * перенесеного коду відкрите — див. план, §12.4.
 * <p>
 * <b>Правда живе в реєстрі, не тут.</b> Сутність без запису в
 * {@link dev.shaurmalib.common.offline.OfflineRegistry} сама себе знищує
 * безшумно. Повний інвентар для дропу зберігається в {@link OfflineAvatarSnapshot}
 * модуля (пам'ять), а не в NBT сутності: після рестарту всі тіла однаково
 * прибираються, тож предмети в NBT лише створювали б шлях для дюпа.
 * <p>
 * <b>Реєстрація (консюмер):</b> {@code EntityType} з розміром {@code sized(0.6F, 1.8F)}
 * і {@code MobCategory.MISC} (не MONSTER), атрибути через
 * {@link #createAttributes()} на {@code EntityAttributeCreationEvent}, рендерер —
 * {@code OfflineAvatarRendererBase}. Підклас може бути порожнім.
 * <p>
 * Ванільне HP не зменшується: {@link #hurt} повністю віддає удар модулю, стан HP веде режим.
 * Через це ванільні {@code LivingAttackEvent}/{@code LivingHurtEvent} для тіла не
 * спрацьовують взагалі.
 */
public abstract class OfflineAvatarBase extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> PLAYER_UUID =
            SynchedEntityData.defineId(OfflineAvatarBase.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> PLAYER_NAME =
            SynchedEntityData.defineId(OfflineAvatarBase.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> PLAYER_MODEL =
            SynchedEntityData.defineId(OfflineAvatarBase.class, EntityDataSerializers.BYTE);
    /** Base64 {@code textures} гравця на момент виходу: тіло зберігає справжній скін, хоча власника вже немає в табі клієнтів. */
    private static final EntityDataAccessor<String> SKIN_TEXTURE =
            SynchedEntityData.defineId(OfflineAvatarBase.class, EntityDataSerializers.STRING);
    /** Підпис Mojang для {@link #SKIN_TEXTURE} ("" для непідписаного, напр. offline-mode). */
    private static final EntityDataAccessor<String> SKIN_SIGNATURE =
            SynchedEntityData.defineId(OfflineAvatarBase.class, EntityDataSerializers.STRING);
    /** Кастомна поза режиму (0 = немає). Мапінг у рендері — {@code OfflineAvatarRendererBase#applyCustomPose}. */
    private static final EntityDataAccessor<Integer> CUSTOM_POSE_ID =
            SynchedEntityData.defineId(OfflineAvatarBase.class, EntityDataSerializers.INT);

    /** Як часто (тіки) тіло перевіряє, що воно ще є в реєстрі. */
    private static final int REGISTRY_CHECK_INTERVAL = 20;

    @Nullable
    private OfflineScopeId scope;
    private boolean internalDeath;

    protected OfflineAvatarBase(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    // ── Застосування знімка ────────────────────────────────────────────────

    /** Перенесення знімка на свіжостворене тіло (аналог {@code fromPlayer} з оригіналу). */
    void applySnapshot(OfflineAvatarSnapshot snapshot, @Nullable OfflineScopeId scope) {
        this.scope = scope;
        setPlayerName(snapshot.ownerName());
        setPlayerUUID(snapshot.ownerUuid());
        setSkinTexture(snapshot.skinTexture(), snapshot.skinSignature());
        setPlayerModelByte(snapshot.modelMask());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, snapshot.equipment(slot));
        }

        this.moveTo(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
        this.yHeadRot = snapshot.yHeadRot();
        this.yHeadRotO = snapshot.yHeadRot();
        this.yBodyRot = snapshot.yBodyRot();
        this.yBodyRotO = snapshot.yBodyRot();
        this.setPose(snapshot.pose());
        this.setHealth(this.getMaxHealth());
        this.setAirSupply(snapshot.airSupply());
        this.setRemainingFireTicks(snapshot.fireTicks());
        for (MobEffectInstance effect : snapshot.effects()) {
            this.addEffect(new MobEffectInstance(effect));
        }
        this.setLeftHanded(snapshot.leftHanded());
    }

    @Nullable
    public OfflineScopeId getScope() {
        return scope;
    }

    // ── Despawn-імунітет (peaceful / doMobSpawning=false / далеко від гравців) ──

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Ніколи не зникає: ні на peaceful, ні далеко, ні при вимкненому mob spawning.
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ── Поведінка ──────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        // Без власного руху й без «озирання»: тіло стоїть як статуя. Лише не тоне, як у оригіналі.
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Повідок на тіло гравця накласти не можна. */
    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isPickable() {
        // Під час анімації смерті тіло вже не ціль: лишає менше вікна для «Attempting to attack an invalid entity».
        return !this.isDeadOrDying() && super.isPickable();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.tickCount % REGISTRY_CHECK_INTERVAL != 0) {
            return;
        }
        OfflinePresenceModule module = OfflinePresenceModule.forAvatar(this);
        if (module == null || !module.isRegistered(this)) {
            // Немає запису в реєстрі: тіло мертве, прибираємо без дропу й без подій.
            this.discard();
            return;
        }
        module.onAvatarTick(this);
    }

    // ── Урон і смерть ──────────────────────────────────────────────────────

    /**
     * Увесь урон віддається модулю (а той — політиці режиму). Ванільного
     * зменшення HP немає.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || this.isDeadOrDying()) {
            return false;
        }
        OfflinePresenceModule module = OfflinePresenceModule.forAvatar(this);
        if (module == null) {
            return false;
        }
        return module.handleHurt(this, source, amount);
    }

    /**
     * Візуал зараховного удару: анімація/звук ушкодження й відкидання, як у гравця.
     * Підклас режиму може доповнити. Викликається лише з сервера, коли політика повернула HIT.
     */
    protected void onHitVisual(DamageSource source, float amount) {
        this.level().broadcastDamageEvent(this, source);
        Entity attacker = source.getEntity();
        if (attacker != null) {
            double dx = attacker.getX() - this.getX();
            double dz = attacker.getZ() - this.getZ();
            while (dx * dx + dz * dz < 1.0E-4D) {
                dx = (this.random.nextDouble() - this.random.nextDouble()) * 0.01D;
                dz = (this.random.nextDouble() - this.random.nextDouble()) * 0.01D;
            }
            this.knockback(0.4D, dx, dz);
            this.markHurt();
        }
    }

    /** Точка входу для {@link OfflinePresenceModule}: показ візуалу удару. */
    final void playHitVisual(DamageSource source, float amount) {
        onHitVisual(source, amount);
    }

    /**
     * Анімація смерті. Призначено лише для виклику з {@link OfflinePresenceModule}:
     * лут і події там. Далі ванільний {@code tickDeath} сам прибере тіло.
     */
    final void playDeath() {
        this.internalDeath = true;
        try {
            this.setHealth(0.0F);
            this.die(this.damageSources().generic());
        } finally {
            this.internalDeath = false;
        }
    }

    /**
     * Смерть, що прийшла повз {@link #hurt} (наприклад, чужий мод викликав {@code die}
     * напряму), перенаправляється в модуль: лут випаде за правилами й рівно один раз.
     */
    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide && !this.internalDeath) {
            OfflinePresenceModule module = OfflinePresenceModule.forAvatar(this);
            if (module != null) {
                module.killByAvatar(this, cause.getEntity());
            }
            return;
        }
        super.die(cause);
    }

    // ── Звуки як у гравця ──────────────────────────────────────────────────

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return SoundEvents.PLAYER_HURT_ON_FIRE;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return SoundEvents.PLAYER_HURT_DROWN;
        }
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ── Розмір і висота очей як у гравця ───────────────────────────────────

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return switch (pose) {
            case SLEEPING, DYING -> EntityDimensions.fixed(0.2F, 0.2F);
            case FALL_FLYING, SWIMMING, SPIN_ATTACK -> EntityDimensions.scalable(0.6F, 0.6F);
            case CROUCHING -> EntityDimensions.scalable(0.6F, 1.5F);
            default -> EntityDimensions.scalable(0.6F, 1.8F);
        };
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return switch (pose) {
            case SWIMMING, FALL_FLYING, SPIN_ATTACK -> 0.4F;
            case CROUCHING -> 1.27F;
            default -> 1.62F;
        };
    }

    @Override
    public Component getName() {
        String name = getPlayerName();
        if (name == null || name.trim().isEmpty()) {
            return Component.literal("Player");
        }
        return Component.literal(name);
    }

    // ── Synched data ───────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PLAYER_UUID, Optional.empty());
        this.entityData.define(PLAYER_NAME, "");
        this.entityData.define(PLAYER_MODEL, (byte) 0);
        this.entityData.define(SKIN_TEXTURE, "");
        this.entityData.define(SKIN_SIGNATURE, "");
        this.entityData.define(CUSTOM_POSE_ID, 0);
    }

    public Optional<UUID> getPlayerUUID() {
        return this.entityData.get(PLAYER_UUID);
    }

    public void setPlayerUUID(UUID uuid) {
        this.entityData.set(PLAYER_UUID, Optional.ofNullable(uuid));
    }

    public String getPlayerName() {
        return this.entityData.get(PLAYER_NAME);
    }

    public void setPlayerName(String name) {
        this.entityData.set(PLAYER_NAME, name == null ? "" : name);
    }

    public byte getPlayerModelByte() {
        return this.entityData.get(PLAYER_MODEL);
    }

    public void setPlayerModelByte(byte value) {
        this.entityData.set(PLAYER_MODEL, value);
    }

    public boolean isModelPartShown(PlayerModelPart part) {
        return (getPlayerModelByte() & part.getMask()) == part.getMask();
    }

    public String getSkinTexture() {
        return this.entityData.get(SKIN_TEXTURE);
    }

    public String getSkinSignature() {
        return this.entityData.get(SKIN_SIGNATURE);
    }

    public void setSkinTexture(String value, String signature) {
        this.entityData.set(SKIN_TEXTURE, value == null ? "" : value);
        this.entityData.set(SKIN_SIGNATURE, signature == null ? "" : signature);
    }

    /** Ідентифікатор кастомної пози режиму (0 = немає). Виставляє режим через {@code OfflineActors}. */
    public int getCustomPoseId() {
        return this.entityData.get(CUSTOM_POSE_ID);
    }

    public void setCustomPoseId(int poseId) {
        this.entityData.set(CUSTOM_POSE_ID, poseId);
    }

    // ── NBT: лише власник і токен scope (решту відновлює реєстр) ───────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getPlayerUUID().ifPresent(uuid -> tag.putUUID("PlayerUUID", uuid));
        tag.putString("PlayerName", getPlayerName());
        tag.putByte("PlayerModel", getPlayerModelByte());
        tag.putString("SkinTexture", getSkinTexture());
        tag.putString("SkinSignature", getSkinSignature());
        if (scope != null) {
            tag.putUUID("ScopeId", scope.value());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("PlayerUUID")) {
            setPlayerUUID(tag.getUUID("PlayerUUID"));
        }
        setPlayerName(tag.getString("PlayerName"));
        setPlayerModelByte(tag.getByte("PlayerModel"));
        setSkinTexture(tag.getString("SkinTexture"), tag.getString("SkinSignature"));
        this.scope = tag.hasUUID("ScopeId") ? new OfflineScopeId(tag.getUUID("ScopeId")) : null;
    }
}
