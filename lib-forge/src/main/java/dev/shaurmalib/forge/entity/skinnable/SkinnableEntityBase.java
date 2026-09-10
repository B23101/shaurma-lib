package dev.shaurmalib.forge.entity.skinnable;

import dev.shaurmalib.common.entity.FallDirection;
import dev.shaurmalib.common.entity.SkinnableEntityLifetime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Random;
import java.util.UUID;

/**
 * Базовий клас "сутність з рантайм-скіном гравця" (план, п. 3.22) —
 * узагальнення {@code DeathCorpseEntity} snipers_shaurma (496 рядків).
 * Розширює {@link Entity} напряму (не {@code LivingEntity}) — той самий
 * вибір, що в оригіналі: нульовий/мінімальний hitbox, без health/AI/
 * pathfinding, суто "тіло, що показує скін гравця і програє одну
 * анімацію до кінця". GeckoLib-частина (контролер/{@code RawAnimation})
 * НЕ винесена в цю базу — консюмер сам реалізує {@code GeoEntity} у
 * своєму підкласі (той самий підхід, що {@code InvisibleZoneEntityBase}
 * не тягне GeckoLib, а {@code AnimatedBlockEntityBase} тягне: тут
 * анімаційний репертуар (скільки кліпів, які напрямки) надто варіативний
 * між можливими консюмерами, щоб фіксувати конкретний
 * {@code AnimationController}-виклик у базі).
 * <p>
 * Три задокументовані в оригіналі механізми переносяться як вбудована
 * поведінка бази:
 * <ol>
 *   <li><b>Рантайм-скін через {@code UUID}, не текстуру напряму</b> —
 *       {@link #setSkinUUID}/{@link #getSkinUUID} синхронізують
 *       {@code String}-представлення UUID через {@link SynchedEntityData}
 *       (не {@code Optional<UUID>} — той тип не має стандартного
 *       serializer'а в 1.20.1 {@code EntityDataSerializers}). Консюмерський
 *       рендерер сам звертається до {@code SkinManager}/{@code PlayerInfo}
 *       за текстурою цього UUID — база лише переносить значення мережею.</li>
 *   <li><b>Знімок броні через повні {@link ItemStack}, не лише ID</b> —
 *       {@link #setArmor}/{@link #getArmorHead} і решта слотів
 *       синхронізують повний stack (не item id), щоб консюмерський
 *       рендерер міг показати колір шкіряної броні, trim, enchant glint —
 *       усе, що показує ванільний {@code HumanoidArmorLayer} для живих
 *       гравців. Порожній {@link ItemStack#EMPTY} у слоті = рендерер
 *       просто не малює цей шар.</li>
 *   <li><b>Fade-out anti-kick фікс</b> (див. {@link SkinnableEntityLifetime}
 *       докстрінг) — за {@code fadeOutTicks} до кінця життя сутність стає
 *       {@code invisible}+{@code noPhysics} ЗАЗДАЛЕГІДЬ, а не миттєво
 *       зникає в останній тік, усуваючи задокументований race condition
 *       ("Attempting to attack an invalid entity" кік через мережеву
 *       затримку). {@link #isFadingOut()} дозволяє консюмерському
 *       interact/attack-хендлеру ігнорувати взаємодію під час цієї фази.</li>
 * </ol>
 * <p>
 * <b>Що НЕ перенесено з {@code DeathCorpseEntity}, і чому:</b>
 * {@code HeadRotationTracker} (332 рядки матричної bone-композиції
 * root→Waist→Body→Head з захардкодженими keyframe-даними для
 * {@code death.front}/{@code death.back}) лишається продуктовим кодом
 * консюмера. Причина та сама, що для {@code ItemCameraTrack} (план 3.24b):
 * keyframe-числа належать КОНКРЕТНІЙ Blockbench-анімації snipers
 * (death_corpse.animation.json), а не є переюзабельним рушієм — інший
 * консюмер із власною смертельною анімацією має власні кути й, цілком
 * можливо, іншу кількість/ієрархію кісток. Переносити варто підхід
 * (не читати {@code GeoBone} напряму в рендер-циклі через задокументований
 * баг GeckoLib 4.x issue #452, а рахувати напрямок з незалежного
 * keyframe-треку), а не самі числа. {@link FallDirection} (геометрія
 * "звідки прийшов удар") переноситься окремо, бо це чиста, дійсно
 * переюзабельна математика без прив'язки до конкретної анімації.
 */
public abstract class SkinnableEntityBase extends Entity {

    private static final EntityDataAccessor<String> SKIN_UUID =
            SynchedEntityData.defineId(SkinnableEntityBase.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> ANIM_DONE =
            SynchedEntityData.defineId(SkinnableEntityBase.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> ARMOR_HEAD =
            SynchedEntityData.defineId(SkinnableEntityBase.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> ARMOR_CHEST =
            SynchedEntityData.defineId(SkinnableEntityBase.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> ARMOR_LEGS =
            SynchedEntityData.defineId(SkinnableEntityBase.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> ARMOR_FEET =
            SynchedEntityData.defineId(SkinnableEntityBase.class, EntityDataSerializers.ITEM_STACK);

    private static final Random RNG = new Random();

    private SkinnableEntityLifetime lifetime = SkinnableEntityLifetime.standard();
    private int lifetimeTick = 0;
    private boolean fadingOut = false;

    /** Власний аналог {@code LivingEntity.yBodyRot} — {@link Entity} його не має.
     *  Встановлюється один раз при спавні, тіло після цього не обертається
     *  (лише консюмерська камера/голова можуть рухатись поверх). */
    private float bodyYaw = 0f;
    private boolean bodyYawSet = false;

    private double rotVelocityYaw;
    private double rotVelocityPitch;

    protected SkinnableEntityBase(EntityType<? extends SkinnableEntityBase> type, Level level) {
        super(type, level);
        this.setSilent(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SKIN_UUID, "");
        entityData.define(ANIM_DONE, false);
        entityData.define(ARMOR_HEAD, ItemStack.EMPTY);
        entityData.define(ARMOR_CHEST, ItemStack.EMPTY);
        entityData.define(ARMOR_LEGS, ItemStack.EMPTY);
        entityData.define(ARMOR_FEET, ItemStack.EMPTY);
    }

    // ── Конфігурація консюмера ───────────────────────────────────────────

    /**
     * Перевизначає {@link SkinnableEntityLifetime#standard()} — викликати
     * (за потреби) одразу після спавну, до першого {@link #tick()}.
     */
    public final void setLifetime(SkinnableEntityLifetime lifetime) {
        this.lifetime = lifetime;
    }

    // ── Скін ──────────────────────────────────────────────────────────────

    public final void setSkinUUID(UUID uuid) {
        entityData.set(SKIN_UUID, uuid != null ? uuid.toString() : "");
    }

    public final UUID getSkinUUID() {
        String s = entityData.get(SKIN_UUID);
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Броня (повні ItemStack, план п.2 вище) ──────────────────────────────

    public final void setArmor(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
        entityData.set(ARMOR_HEAD, head != null ? head.copy() : ItemStack.EMPTY);
        entityData.set(ARMOR_CHEST, chest != null ? chest.copy() : ItemStack.EMPTY);
        entityData.set(ARMOR_LEGS, legs != null ? legs.copy() : ItemStack.EMPTY);
        entityData.set(ARMOR_FEET, feet != null ? feet.copy() : ItemStack.EMPTY);
    }

    public final ItemStack getArmorHead() {
        return entityData.get(ARMOR_HEAD);
    }

    public final ItemStack getArmorChest() {
        return entityData.get(ARMOR_CHEST);
    }

    public final ItemStack getArmorLegs() {
        return entityData.get(ARMOR_LEGS);
    }

    public final ItemStack getArmorFeet() {
        return entityData.get(ARMOR_FEET);
    }

    // ── Анімаційний прапорець (консюмерський рендерер/GeckoLib-контролер
    //    сам вирішує, яку анімацію грати; ця база лише тримає "чи вже
    //    завершена" мережею синхронізованим прапорцем, як оригінал) ──────

    /** Викликається консюмером (наприклад, коли клієнт підтвердив кінець
     *  анімації власним пакетом) — база сама це значення не виставляє. */
    public final void setAnimDone(boolean done) {
        entityData.set(ANIM_DONE, done);
    }

    public final boolean isAnimDone() {
        return entityData.get(ANIM_DONE);
    }

    public final float getBodyYaw() {
        return bodyYaw;
    }

    /**
     * Викликати один раз при спавні (типово: yaw гравця в момент смерті/
     * події). Якщо не викликати явно — {@link #tick()} підставить поточний
     * {@code getYRot()} сутності при першому виклику.
     */
    public final void setBodyYaw(float yaw) {
        this.bodyYaw = yaw;
        this.bodyYawSet = true;
    }

    // ── Нульовий/мінімальний hitbox (план: перенесено 1:1) ──────────────────

    @Override
    public final boolean isPickable() {
        return false;
    }

    @Override
    public final boolean isPushable() {
        return false;
    }

    @Override
    public final float getPickRadius() {
        return 0f;
    }

    @Override
    public final boolean shouldRenderAtSqrDistance(double distSqr) {
        return true;
    }

    /**
     * Великий culling box за замовчуванням — консюмер може перевизначити,
     * якщо його модель суттєво більша/менша за людську. Оригінал:
     * {@code (-4,-1,-4)..(+4,+3,+4)} відносно позиції.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 4, y - 1, z - 4, x + 4, y + 3, z + 4);
    }

    // ── Безсмертність + explosion knockback (план: перенесено 1:1) ─────────

    /**
     * Абсолютно невразлива до будь-якого урону, окрім відкидання від
     * вибуху (без знищення). Явна заборона, а не покладання на "Entity
     * не має health" — деякі сторонні моди можуть викликати видалення
     * іншими шляхами, тому оригінал (і ця база) явно повертає {@code false}.
     */
    @Override
    public final boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            applyExplosionKnockback(source);
        }
        return false;
    }

    private void applyExplosionKnockback(DamageSource source) {
        Vec3 explosionPos = null;
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            explosionPos = direct.position();
        }
        if (explosionPos == null) {
            try {
                explosionPos = source.getSourcePosition();
            } catch (Exception ignored) {
            }
        }
        if (explosionPos == null) return;

        double dx = this.getX() - explosionPos.x;
        double dy = this.getY() + 0.5 - explosionPos.y;
        double dz = this.getZ() - explosionPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return;

        double strength = 0.6 / Math.max(dist, 1.0);
        double vx = (dx / dist) * strength;
        double vy = (dy / dist) * strength + 0.15;
        double vz = (dz / dist) * strength;

        this.setDeltaMovement(
                this.getDeltaMovement().x + vx,
                this.getDeltaMovement().y + vy,
                this.getDeltaMovement().z + vz
        );

        double horDist = Math.sqrt(dx * dx + dz * dz);
        if (horDist > 0.01) {
            double rotStrength = 8.0 / Math.max(dist, 1.0);
            rotVelocityYaw += ((RNG.nextDouble() - 0.5) * 2.0) * rotStrength;
            rotVelocityPitch += ((RNG.nextDouble() - 0.5) * 2.0) * rotStrength * 0.6;
        }
    }

    // ── Tick: фізика падіння + обертання від вибуху + lifetime/fade-out ────

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide() && !bodyYawSet) {
            bodyYaw = this.getYRot();
            bodyYawSet = true;
        }

        if (Math.abs(rotVelocityYaw) > 0.01 || Math.abs(rotVelocityPitch) > 0.01) {
            this.xRotO = this.getXRot();
            this.yRotO = this.getYRot();
            this.setYRot(this.getYRot() + (float) rotVelocityYaw);
            this.setXRot(this.getXRot() + (float) rotVelocityPitch);
            rotVelocityYaw *= 0.92;
            rotVelocityPitch *= 0.92;
            if (Math.abs(rotVelocityYaw) < 0.01) rotVelocityYaw = 0;
            if (Math.abs(rotVelocityPitch) < 0.01) rotVelocityPitch = 0;
        }

        // Гравітація + рух від імпульсу — Entity базовий клас гравітації не має.
        double curVX = this.getDeltaMovement().x;
        double curVY = this.getDeltaMovement().y;
        double curVZ = this.getDeltaMovement().z;
        boolean hasImpulse = Math.abs(curVX) > 0.01 || Math.abs(curVY) > 0.01 || Math.abs(curVZ) > 0.01;

        if (!this.onGround() || hasImpulse) {
            double vy = curVY;
            if (!this.isNoGravity()) {
                vy -= 0.04;
            }
            double friction = this.onGround() ? 0.55 : 0.98;
            this.setDeltaMovement(curVX * friction, vy, curVZ * friction);
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            this.setDeltaMovement(0.0, 0.0, 0.0);
        }

        if (!level().isClientSide()) {
            lifetimeTick++;
            if (lifetimeTick == lifetime.lifetimeTicks() - lifetime.fadeOutTicks()) {
                fadingOut = true;
                this.setInvisible(true);
                this.noPhysics = true;
            }
            if (lifetimeTick >= lifetime.lifetimeTicks()) {
                this.discard();
            }
        }
    }

    /**
     * {@code true} під час фінальних {@code fadeOutTicks} перед дискардом
     * — консюмерський interact/attack-хендлер (обробник
     * {@code ServerboundInteractPacket} чи forge-подія
     * {@code AttackEntityEvent}/{@code EntityInteractSpecific}) має
     * ігнорувати взаємодію, коли це {@code true}, замість дозволяти
     * ванільному коду дійти до вже фактично зникаючого {@link Entity}
     * (план, докстрінг класу вище — anti-kick race condition).
     */
    public final boolean isFadingOut() {
        return fadingOut;
    }

    // ── NBT: сутність навмисно нічого не зберігає (той самий вибір, що
    //    в оригіналі) — недовговічна, чисто клієнтсько-візуальна сутність,
    //    не має сенсу переживати чанк-релоад/сейв/лоад. ──────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public final Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
