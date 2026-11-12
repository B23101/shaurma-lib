package dev.shaurmalib.anim.mc;

import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.clip.ClipEvent;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.Rig;
import dev.shaurmalib.anim.runtime.AnimInputs;
import dev.shaurmalib.anim.runtime.AnimSubject;
import dev.shaurmalib.anim.runtime.Animator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Клієнтський клей рушія для Minecraft 1.20.1 (ваніль, без лоадера). Лоадер кличе:
 * {@link #clientTick()} щотіка, {@link #onDisconnect()} при виході, і реєструє {@link AnimReloader}.
 *
 * Тут вирішується, ЯКИЙ риг у сутності ({@link #setRigResolver}): звичайний гравець — {@link Rig#PLAYER},
 * маньяк — риг його geo-моделі. Рушію байдуже: шари, маски, правила працюють однаково.
 */
public final class McAnim {

    private McAnim() {}

    private static final Function<Entity, Rig> DEFAULT_RESOLVER = e -> e instanceof Player ? Rig.PLAYER : null;
    private static Function<Entity, Rig> rigResolver = DEFAULT_RESOLVER;
    private static final Map<UUID, Subject> SUBJECTS = new HashMap<>();
    private static final Map<UUID, Pose> FRAME = new HashMap<>();

    /** Сутність, яку зараз малює LivingEntityRenderer (лише тоді setupAnim-міксин чіпає модель). */
    static LivingEntity renderingEntity;

    static {
        ShaurmaAnim.onEvent(McAnim::defaultEvents);
    }

    /**
     * Який риг у сутності. null — не анімувати рушієм. За замовчуванням гравці — {@link Rig#PLAYER},
     * решта — ні. Мод може повернути geo-риг (маньяк) або увімкнути рушій для мобів.
     */
    public static void setRigResolver(Function<Entity, Rig> resolver) {
        rigResolver = resolver == null ? DEFAULT_RESOLVER : resolver;
    }

    public static Rig rigOf(Entity e) {
        try {
            return rigResolver.apply(e);
        } catch (RuntimeException ex) {
            return DEFAULT_RESOLVER.apply(e);
        }
    }

    // ── Превʼю в GUI: та сама сутність, але окремий аніматор ─────────────

    private static Entity previewEntity;
    private static Animator previewAnimator;
    private static float previewPartial;

    /**
     * Намалювати сутність (у GUI) з ІНШИМ аніматором: колесо емоцій показує гравця, що грає наведену
     * емоцію, а справжній гравець у світі в цей час стоїть як стояв. Весь рендер-ланцюг (міксини моделі,
     * предмети, пропси) питає {@link #animator(Entity)} — тому підміни тут досить.
     */
    public static void withPreview(Entity e, Animator animator, float partial, Runnable render) {
        Entity pe = previewEntity;
        Animator pa = previewAnimator;
        float pp = previewPartial;
        previewEntity = e;
        previewAnimator = animator;
        previewPartial = partial;
        try {
            render.run();
        } finally {
            previewEntity = pe;
            previewAnimator = pa;
            previewPartial = pp;
            FRAME.remove(e.getUUID()); // поза превʼю не має потрапити в шари світу цього кадру
        }
    }

    static boolean previewing(Entity e) { return previewAnimator != null && e == previewEntity; }

    /** Риг, яким сутність малюється зараз (у превʼю — риг аніматора превʼю). */
    public static Rig renderRigOf(Entity e) { return previewing(e) ? previewAnimator.rig() : rigOf(e); }

    /** Аніматор сутності з правильним ригом (створює за потреби). */
    public static Animator animator(Entity e) {
        if (previewAnimator != null && e == previewEntity) return previewAnimator;
        Rig rig = rigOf(e);
        return rig == null ? null : ShaurmaAnim.animator(e.getUUID(), rig);
    }

    // ── Тік ──────────────────────────────────────────────────────────────

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            onDisconnect();
            return;
        }
        if (mc.isPaused()) return;
        Set<UUID> alive = new HashSet<>();
        for (AbstractClientPlayer p : level.players()) {
            Rig rig = rigOf(p);
            if (rig == null) continue;
            alive.add(p.getUUID());
            Animator a = ShaurmaAnim.animator(p.getUUID(), rig);
            Subject s = SUBJECTS.get(p.getUUID());
            if (s == null || s.entity != p || s.animator != a) {
                s = new Subject(p, a);
                SUBJECTS.put(p.getUUID(), s);
            }
            fillTickInputs(p, a.inputs(), p == mc.player);
            try {
                ShaurmaAnim.tick(s);
            } catch (RuntimeException ex) {
                // одна сутність з битим кліпом не зупиняє анімації решти
            }
        }
        SUBJECTS.keySet().retainAll(alive);
        FRAME.keySet().retainAll(alive);
        ShaurmaAnim.retainOnly(alive);
    }

    public static void onDisconnect() {
        SUBJECTS.clear();
        FRAME.clear();
        ShaurmaAnim.clearAll();
    }

    static void fillTickInputs(LivingEntity e, AnimInputs in, boolean local) {
        in.onGround = e.onGround();
        in.sneaking = e.isCrouching();
        in.sprinting = e.isSprinting();
        in.swimming = e.isSwimming();
        in.crawling = e.isVisuallyCrawling();
        in.riding = e.isPassenger();
        in.climbing = e.onClimbable();
        in.flying = e.isFallFlying() || (e instanceof Player p && p.getAbilities().flying);
        in.inWater = e.isInWater();
        in.usingItem = e.isUsingItem();
        in.swinging = e.swinging;
        in.sleeping = e.isSleeping();
        in.dead = !e.isAlive();
        in.localPlayer = local;
        in.verticalSpeed = (float) (e.getY() - e.yo);
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        in.horizontalSpeed = (float) Math.sqrt(dx * dx + dz * dz);
        in.fallDistance = e.fallDistance;
        in.hurtTime = e.hurtTime;
        in.ageTicks = e.tickCount;
        in.mainHandItem = itemId(e.getMainHandItem());
        in.offHandItem = itemId(e.getOffhandItem());
        in.limbSwingAmount = e.walkAnimation.speed();
        in.limbSwing = e.walkAnimation.position();
    }

    static void fillFrameInputs(LivingEntity e, AnimInputs in, float partial) {
        in.limbSwing = e.walkAnimation.position(partial);
        in.limbSwingAmount = Math.min(1f, e.walkAnimation.speed(partial));
        float body = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        float head = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot);
        in.netHeadYaw = Mth.wrapDegrees(head - body);
        in.headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
    }

    public static String itemId(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key == null ? "" : key.toString();
    }

    // ── Кадр ─────────────────────────────────────────────────────────────

    /**
     * Обчислює позу кадру для сутності (база — вхідна поза моделі або null).
     * Результат кешується до наступного виклику — шари рендера (предмет, пропси) читають {@link #framePose}.
     */
    public static Pose evaluate(LivingEntity e, float partial, Pose base, boolean baseHasLook) {
        Animator a = animator(e);
        if (a == null) return null;
        if (previewing(e)) partial = previewPartial; // GUI малює з partial=1 — превʼю плавне власним
        fillFrameInputs(e, a.inputs(), partial);
        return evaluateWith(e, a, partial, base, baseHasLook);
    }

    static Pose evaluateWith(LivingEntity e, Animator a, float partial, Pose base, boolean baseHasLook) {
        Pose out = FRAME.get(e.getUUID());
        if (out == null || out.rig() != a.rig()) {
            out = new Pose(a.rig());
            FRAME.put(e.getUUID(), out);
        }
        a.evaluate(partial, base, baseHasLook, out);
        return out;
    }

    /** Остання обчислена поза сутності (цей кадр) або null. */
    public static Pose framePose(Entity e) {
        Pose p = FRAME.get(e.getUUID());
        Rig rig = previewing(e) ? previewAnimator.rig() : rigOf(e);
        return p != null && p.rig() == rig ? p : null;
    }

    // ── Події за замовчуванням: звук і частинки ──────────────────────────

    private static void defaultEvents(AnimSubject subject, AnimationClip clip, ClipEvent event) {
        if (!(subject.entity() instanceof Entity e) || e.level() == null || !e.level().isClientSide) return;
        String type = event.type();
        ResourceLocation id = ResourceLocation.tryParse(event.value());
        if (id == null) return;
        if ("sound".equals(type)) {
            SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
            e.level().playLocalSound(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), sound, SoundSource.PLAYERS,
                (float) event.param("volume", 1), (float) event.param("pitch", 1), false);
        } else if ("particle".equals(type)) {
            ParticleType<?> pt = BuiltInRegistries.PARTICLE_TYPE.get(id);
            if (pt instanceof ParticleOptions opts) {
                int n = (int) Math.max(1, event.param("count", 1));
                for (int i = 0; i < n; i++) {
                    e.level().addParticle(opts, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 0, 0.02, 0);
                }
            }
        }
    }

    /** Суб'єкт для правил: сира сутність + її аніматор. */
    static final class Subject implements AnimSubject {
        final LivingEntity entity;
        final Animator animator;

        Subject(LivingEntity entity, Animator animator) {
            this.entity = entity;
            this.animator = animator;
        }

        @Override public UUID uuid() { return entity.getUUID(); }
        @Override public Object entity() { return entity; }
        @Override public AnimInputs inputs() { return animator.inputs(); }
        @Override public Animator animator() { return animator; }
    }
}
