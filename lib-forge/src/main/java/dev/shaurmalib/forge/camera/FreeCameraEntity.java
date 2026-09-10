package dev.shaurmalib.forge.camera;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Бібліотечна фіктивна сутність-камера (п. 3.15 плану, "вільна камера").
 * Перенесення {@code core.client.camera.FreeCamera} snipers_shaurma
 * 1:1 — цей клас уже був добре ізольований і задокументовано виправляв
 * власний баг, тому переноситься без переробки логіки, лише пакет.
 * <p>
 * Стоїть нерухомо там, де її поставили; рух надають окремі "motion"-
 * класи ({@code PathMotion}/{@code LoopPathMotion}/{@code BezierApproachMotion}),
 * що щотік викликають {@link #moveSmooth} або {@link #teleport}.
 * <p>
 * <b>Задокументований фікс оригіналу (ID race condition):</b> раніше в
 * snipers_shaurma всі камери (DeathCam/KitSelect/Drop/EndGame/SD) мали
 * ОДИН спільний хардкоджений ID — якщо дві системи камери виявлялись
 * активними одночасно, друга {@link #spawn()} перезаписувала запис у
 * {@code ClientLevel} під тим самим ID, і подальший despawn будь-якої з
 * них міг видалити чужий запис — ванільний Minecraft, побачивши camera
 * entity видаленою, сам автоматично повертав камеру на гравця. Тепер
 * кожен екземпляр отримує власний унікальний ID через лічильник — саме
 * тому в бібліотеці це особливо важливо: кілька незалежних споживачів
 * (snipers, maniac) можуть створювати {@link FreeCameraEntity} одночасно
 * без координації одне з одним.
 */
@OnlyIn(Dist.CLIENT)
public class FreeCameraEntity extends net.minecraft.client.player.AbstractClientPlayer {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(-42069);
    private final int freeCameraEntityId = ID_COUNTER.decrementAndGet();

    public FreeCameraEntity(ClientLevel level, float x, float y, float z, float yaw, float pitch) {
        super(level, new GameProfile(UUID.randomUUID(), "ShaurmaLibFreeCamera"));
        setId(freeCameraEntityId);
        setPose(Pose.SWIMMING);
        getAbilities().flying = true;
        noPhysics = true;
        moveTo(x, y, z, yaw, pitch);
    }

    @Override
    public void tick() {
        // Нічого — moveSmooth() сам зберігає old-значення перед оновленням.
        // Не викликаємо super.tick(), щоб уникнути побічних ефектів
        // LivingEntity/Player (aiStep, ефекти, анімації тощо).
    }

    /**
     * Плавне переміщення для камери, що РУХАЄТЬСЯ безперервно (motion-
     * класи викликають це щотік).
     * <p>
     * Зберігає поточну позицію/ротацію як "стару" ДО виклику
     * {@code moveTo()}. {@link #tick()} порожній (не викликає
     * {@code super.tick()}), тому {@code xOld/yOld/zOld/yRotO/xRotO}
     * ніколи не оновлюються автоматично — без цього кожен render-кадр
     * lerp(partialTick) починав би інтерполяцію зі СТАРТОВОЇ позиції/
     * ротації (з моменту створення камери), звідси помітне "сіпання".
     * <p>
     * {@link #getViewXRot}/{@link #getViewYRot} нижче перевизначені й
     * повертають ПОТОЧНИЙ кут напряму (без лерпу по partialTick) — тому
     * не має значення, що {@code moveTo()} тут же перезаписує
     * {@code yRotO/xRotO} новим значенням.
     */
    public void moveSmooth(double x, double y, double z, float yaw, float pitch) {
        this.xOld = getX();
        this.yOld = getY();
        this.zOld = getZ();
        this.yRotO = getYRot();
        this.xRotO = getXRot();
        moveTo(x, y, z, yaw, pitch);
    }

    /**
     * Миттєво переміщує камеру на нову позицію/ротацію БЕЗ lerp-
     * артефактів. Використовувати ЛИШЕ для одноразових стрибків (спавн
     * камери, "snap" на фінальну позицію, миттєва зміна цілі) — НЕ для
     * безперервного руху (для нього — {@link #moveSmooth}).
     */
    public void teleport(double x, double y, double z, float yaw, float pitch) {
        moveTo(x, y, z, yaw, pitch);
        this.xOld  = x;
        this.yOld  = y;
        this.zOld  = z;
        this.yRotO = yaw;
        this.xRotO = pitch;
        setXRot(pitch);
        setYRot(yaw);
    }

    /** Додає сутність у клієнтський світ (1.20.1: {@code ClientLevel.putNonPlayerEntity}). */
    public void spawn() {
        ClientLevel level = (ClientLevel) level();
        if (level != null) {
            level.putNonPlayerEntity(freeCameraEntityId, this);
        }
    }

    /** Видаляє сутність з клієнтського світу через {@code discard()}. */
    public void despawn() {
        discard();
    }

    public int entityId() {
        return freeCameraEntityId;
    }

    // ─── Вимикаємо всі взаємодії ───────────────────────────────────────────

    @Override
    protected void checkFallDamage(double h, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean onClimbable() { return false; }

    @Override
    public boolean isInWater() { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean canCollideWith(Entity other) { return false; }

    @Override
    public void setPose(Pose pose) { super.setPose(Pose.SWIMMING); }

    @Override
    protected boolean updateIsUnderwater() {
        this.wasUnderwater = false;
        return false;
    }

    @Override
    protected void doWaterSplashEffect() {}

    @Override
    public boolean isEffectiveAi() { return true; }

    // Повертаємо ПОТОЧНИЙ кут напряму, без лерпу по partialTick. Дефолтна
    // поведінка Entity лерпить (xRotO → xRot) для згладжування "чужих"
    // сутностей, чия ротація приходить дискретними мережевими пакетами.
    // FreeCameraEntity — локальна клієнтська сутність, яку рухає сам
    // motion-клас щотіку з уже готовим, плавно порахованим кутом —
    // додатковий лерп поверх цього не згладжує, а спотворює: кутова
    // "обгортка" 359°→0° або будь-який навмисний різкий стрибок кута
    // (наприклад possess) під дефолтним Mth.lerp дає видиме тремтіння.
    @Override
    public float getViewXRot(float partialTick) { return this.getXRot(); }

    @Override
    public float getViewYRot(float partialTick) { return this.getYRot(); }
}
