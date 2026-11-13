package dev.shaurmalib.meta.mc.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.cosmetics.CosmeticDef;
import dev.shaurmalib.meta.cosmetics.CosmeticSlot;
import dev.shaurmalib.meta.cosmetics.Look;
import dev.shaurmalib.meta.cosmetics.Worn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Рендер косметики (перенесено з мафії й узагальнено під будь-який каталог):
 * <ul>
 *   <li>капелюх і предмет на спині — шар моделі гравця ({@link Layer});</li>
 *   <li>улюбленець і аура — окремо у світі: улюбленець має власну позицію, що плавно «доганяє» точку біля
 *       голови (експоненційне згладжування, незалежне від FPS), відстає при бігу й нахиляється за рухом;</li>
 *   <li>у прев'ю гардероба — усе на манекені ({@link #preview}).</li>
 * </ul>
 * Колір предмета передається через NBT {@code CosmeticColor} — його читає відтінок, який лоадер реєструє
 * для всіх моделей каталогу.
 */
public final class CosmeticRenderer {

    public static final String COLOR_TAG = "CosmeticColor";

    private static final Map<UUID, PetState> PETS = new HashMap<>();
    private static final Map<UUID, Float> AURAS = new HashMap<>();
    private static final Map<Long, ItemStack> STACKS = new HashMap<>();
    private static final Random RND = new Random();
    private static long auraNanos = System.nanoTime();

    /** Прев'ю гардероба: що малювати на манекені (null — звичайний рендер). */
    public static Look preview;
    public static boolean drawingPreview;
    public static float previewLift, previewLean, previewYaw, previewTilt;
    /** true — аура вже намальована ДО манекена (Preview.figure), шар її не дублює поверх моделі. */
    public static boolean previewAuraSeparate;

    private static final class PetState {
        double x, y, z, lastX, lastZ;
        float yaw, roll, pitch, appear;
        long lastNanos;
    }

    private CosmeticRenderer() {}

    public static void reset() {
        PETS.clear();
        AURAS.clear();
        STACKS.clear();
        preview = null;
        drawingPreview = false;
    }

    static Look lookOf(AbstractClientPlayer p) {
        if (drawingPreview) return preview;
        return MetaClient.look(p.getUUID());
    }

    private static boolean hidden(AbstractClientPlayer p) {
        return p.isSpectator() || p.isInvisible() || !p.isAlive();
    }

    /** Чи щось на спині (тоді плащ варто сховати — міксин режиму може спитати це). */
    public static boolean wearsBackItem(AbstractClientPlayer p) {
        Look l = lookOf(p);
        return l != null && l.has(CosmeticSlot.BACK);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Тік: частинки аур
    // ══════════════════════════════════════════════════════════════════════

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { PETS.clear(); AURAS.clear(); return; }
        PETS.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
        AURAS.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
        if (mc.player == null) return;
        for (AbstractClientPlayer p : level.players()) {
            Look look = MetaClient.look(p.getUUID());
            Worn aura = look == null ? null : look.get(CosmeticSlot.AURA);
            if (aura == null || hidden(p) || p.distanceToSqr(mc.player) > 1024) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            AuraRenderer.particles(level, p.getX(), p.getY(), p.getZ(), aura.color, aura.param(CosmeticSlot.AURA, "scale"), RND, auraType(aura));
        }
    }

    static int auraType(Worn w) {
        var mode = MetaClient.mode();
        CosmeticDef d = mode == null ? null : mode.cosmetics.get(w.item);
        return d == null ? 0 : d.auraType();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Світ: улюбленці й аури (подія «після сутностей»)
    // ══════════════════════════════════════════════════════════════════════

    public static void renderWorld(PoseStack m, MultiBufferSource buffers, Vec3 cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.nanoTime();
        for (AbstractClientPlayer p : mc.level.players()) {
            Look look = MetaClient.look(p.getUUID());
            boolean hide = hidden(p) || p.distanceToSqr(cam) > 4096;
            Worn aura = look == null ? null : look.get(CosmeticSlot.AURA);
            if (aura != null && !hide) renderAura(mc, buffers, m, cam, p, aura, partial, now);
            else AURAS.remove(p.getUUID());
            Worn pet = look == null ? null : look.get(CosmeticSlot.PET);
            if (pet == null || hide) { PETS.remove(p.getUUID()); continue; }
            renderPet(mc, buffers, m, cam, p, pet, partial, now);
        }
        auraNanos = now;
    }

    private static void renderAura(Minecraft mc, MultiBufferSource buffers, PoseStack m, Vec3 cam, AbstractClientPlayer p,
                                   Worn aura, float partial, long now) {
        float dt = Mth.clamp((now - auraNanos) / 1e9f, 0f, .1f);
        float appear = Math.min(1f, AURAS.getOrDefault(p.getUUID(), 0f) + dt * 1.1f);
        AURAS.put(p.getUUID(), appear);
        double px = Mth.lerp(partial, p.xo, p.getX()), py = Mth.lerp(partial, p.yo, p.getY()), pz = Mth.lerp(partial, p.zo, p.getZ());
        double dx = px - cam.x, dy = py - cam.y, dz = pz - cam.z;
        m.pushPose();
        m.translate(dx, dy, dz);
        AuraRenderer.draw(auraType(aura), m, buffers.getBuffer(MetaRenderTypes.GLOW), mc.level.getGameTime() + partial,
                aura.color, aura.param(CosmeticSlot.AURA, "scale"), appear, dx * dx + dy * dy + dz * dz < 576,
                p == mc.player && mc.options.getCameraType().isFirstPerson());
        m.popPose();
    }

    private static void renderPet(Minecraft mc, MultiBufferSource buffers, PoseStack m, Vec3 cam, AbstractClientPlayer p,
                                  Worn pet, float partial, long now) {
        Item item = item(pet.item);
        if (item == null) return;
        PetState s = PETS.get(p.getUUID());
        double px = Mth.lerp(partial, p.xo, p.getX()), py = Mth.lerp(partial, p.yo, p.getY()), pz = Mth.lerp(partial, p.zo, p.getZ());
        float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        boolean fresh = s == null;
        if (fresh) {
            s = new PetState();
            s.yaw = bodyYaw;
            s.lastNanos = now;
            PETS.put(p.getUUID(), s);
        }
        float dt = Mth.clamp((now - s.lastNanos) / 1e9f, 0f, .1f);
        s.lastNanos = now;
        float bx = pet.param(CosmeticSlot.PET, "x"), by = pet.param(CosmeticSlot.PET, "y"), bz = pet.param(CosmeticSlot.PET, "z");
        float bob = pet.param(CosmeticSlot.PET, "bob"), speed = pet.param(CosmeticSlot.PET, "speed"), scale = pet.param(CosmeticSlot.PET, "scale");

        s.yaw += Mth.wrapDegrees(bodyYaw - s.yaw) * (1f - (float) Math.exp(-dt * 5.5f));
        double rad = Math.toRadians(s.yaw), cos = Math.cos(rad), sin = Math.sin(rad);
        double phase = (mc.level.getGameTime() + partial) * .075 * speed;
        double b = Math.sin(phase) * bob, drift = Math.sin(phase * .55 + 1.3) * bob * .35;
        double tx = px + (bx + drift) * cos - bz * sin, ty = py + by + b, tz = pz + (bx + drift) * sin + bz * cos;
        if (fresh || Math.abs(tx - s.x) + Math.abs(tz - s.z) + Math.abs(ty - s.y) > 24) {
            s.x = tx; s.y = ty; s.z = tz; s.lastX = tx; s.lastZ = tz;
        }
        float kh = 1f - (float) Math.exp(-dt * 9f), kv = 1f - (float) Math.exp(-dt * 14f);
        s.x += (tx - s.x) * kh;
        s.z += (tz - s.z) * kh;
        s.y += (ty - s.y) * kv;
        double vx = dt > 1e-4 ? (s.x - s.lastX) / dt : 0, vz = dt > 1e-4 ? (s.z - s.lastZ) / dt : 0;
        s.lastX = s.x;
        s.lastZ = s.z;
        double forward = vx * -sin + vz * cos, lateral = vx * cos + vz * sin;
        float wantPitch = (float) Mth.clamp(forward * 4.0, -14, 14);
        float wantRoll = (float) Mth.clamp(lateral * 4.0, -14, 14) + (float) (Math.sin(phase * .7) * 3.0);
        float kt = 1f - (float) Math.exp(-dt * 7f);
        s.pitch += (wantPitch - s.pitch) * kt;
        s.roll += (wantRoll - s.roll) * kt;
        s.appear = Math.min(1f, s.appear + dt * 2.6f);

        double dx = s.x - cam.x, dy = s.y - cam.y, dz = s.z - cam.z;
        if (p == mc.player && dx * dx + dy * dy + dz * dz < .5) return;
        int light = mc.getEntityRenderDispatcher().getPackedLightCoords(p, partial);
        light = LightTexture.pack(Math.max(LightTexture.block(light), 11), LightTexture.sky(light));
        float sc = scale * easeOutBack(s.appear);
        m.pushPose();
        m.translate(dx, dy, dz);
        m.mulPose(Axis.YP.rotationDegrees(180f - s.yaw));
        m.mulPose(Axis.ZP.rotationDegrees(s.roll));
        m.mulPose(Axis.XP.rotationDegrees(-s.pitch));
        m.scale(sc, sc, sc);
        draw(m, buffers, light, p, item, pet.color, ItemDisplayContext.NONE);
        m.popPose();
    }

    /** Центр предмета на спині від центра тулуба (у блоках, +z — назад). */
    public static double backCenterZ(Worn back) {
        return .129 + back.param(CosmeticSlot.BACK, "z") + .375 * back.param(CosmeticSlot.BACK, "scale");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Шар моделі гравця
    // ══════════════════════════════════════════════════════════════════════

    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack m, MultiBufferSource v, int light, AbstractClientPlayer p, float limbSwing,
                           float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
            boolean ui = drawingPreview;
            if (!ui && hidden(p)) return;
            Look look = lookOf(p);
            if (look == null || look.isEmpty()) return;

            Worn hat = look.get(CosmeticSlot.HEAD);
            Item hatItem = hat == null ? null : item(hat.item);
            if (hatItem != null) {
                float sc = hat.param(CosmeticSlot.HEAD, "scale");
                m.pushPose();
                getParentModel().head.translateAndRotate(m);
                m.translate(0, -hat.param(CosmeticSlot.HEAD, "y"), 0);
                CustomHeadLayer.translateToHead(m, false);
                m.scale(sc, sc, sc);
                draw(m, v, light, p, hatItem, hat.color, ItemDisplayContext.HEAD);
                m.popPose();
            }
            Worn back = look.get(CosmeticSlot.BACK);
            Item backItem = back == null ? null : item(back.item);
            if (backItem != null) {
                // Простір тулуба перевернутий: +z — назад, y — вниз. (s,-s,-s) ставить предмет сторчма,
                // поворот на 180° кладе лямки до спини.
                float s = back.param(CosmeticSlot.BACK, "scale");
                m.pushPose();
                getParentModel().body.translateAndRotate(m);
                m.translate(0, .375 - back.param(CosmeticSlot.BACK, "y"), backCenterZ(back));
                m.scale(s, -s, -s);
                m.mulPose(Axis.YP.rotationDegrees(180f));
                draw(m, v, light, p, backItem, back.color, ItemDisplayContext.NONE);
                m.popPose();
            }
            for (CosmeticSlot extra : new CosmeticSlot[]{CosmeticSlot.BODY, CosmeticSlot.LEGS}) {
                Worn w = look.get(extra);
                Item it = w == null ? null : item(w.item);
                if (it == null) continue;
                float s = w.param(extra, "scale");
                m.pushPose();
                (extra == CosmeticSlot.BODY ? getParentModel().body : getParentModel().rightLeg).translateAndRotate(m);
                m.translate(0, extra == CosmeticSlot.BODY ? .375 : .375, 0);
                m.scale(s, -s, -s);
                draw(m, v, light, p, it, w.color, ItemDisplayContext.NONE);
                m.popPose();
            }
            if (ui) previewExtras(m, v, p, look, partial);
        }
    }

    /**
     * Аура прев'ю, намальована ДО манекена. Адитивне світіння без запису глибини, що йде шаром ПІСЛЯ
     * моделі, лягало поверх ніг (задня половина пентаграми й полум'я «просвічували» крізь тіло).
     * Якщо ж малювати його першим, непрозорий манекен просто перекриває те, що за ним.
     * {@code m} — вже у просторі манекена (як у renderEntityInInventory), початок координат — ноги.
     */
    public static void drawPreviewAuraBehind(PoseStack m, MultiBufferSource v, Worn aura, float yaw, float tilt, float time,
                                             float fitRadiusBlocks) {
        m.pushPose();
        m.mulPose(Axis.YP.rotationDegrees(180f)); // як LivingEntityRenderer.setupRotations при bodyYaw=0
        m.mulPose(Axis.YP.rotationDegrees(-(yaw + 180f)));
        if (tilt != 0f) m.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(tilt)));
        // Радіус кільця ≈ 1.22 · scale блока: стискаємо аури, що не влазять у вікно превʼю.
        float scale = Math.min(aura.param(CosmeticSlot.AURA, "scale"), fitRadiusBlocks / 1.22f);
        AuraRenderer.draw(auraType(aura), m, v.getBuffer(MetaRenderTypes.GLOW), time, aura.color,
                scale, 1f, true, false);
        m.popPose();
    }

    /** У прев'ю аура й улюбленець малюються прямо на манекені (у світі його нема). */
    private static void previewExtras(PoseStack m, MultiBufferSource v, AbstractClientPlayer p, Look look, float partial) {
        Worn aura = look.get(CosmeticSlot.AURA);
        if (aura != null && !previewAuraSeparate) {
            m.pushPose();
            m.translate(0, 1.501, 0);
            m.scale(-1f, -1f, 1f);
            m.mulPose(Axis.YP.rotationDegrees(-(previewYaw + 180f)));
            m.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(previewTilt)));
            AuraRenderer.draw(auraType(aura), m, v.getBuffer(MetaRenderTypes.GLOW), p.tickCount + partial, aura.color,
                    aura.param(CosmeticSlot.AURA, "scale"), 1f, true, false);
            m.popPose();
        }
        Worn pet = look.get(CosmeticSlot.PET);
        Item petItem = pet == null ? null : item(pet.item);
        if (petItem != null) {
            double phase = (p.tickCount + partial) * .075 * pet.param(CosmeticSlot.PET, "speed");
            double bob = Math.sin(phase) * pet.param(CosmeticSlot.PET, "bob");
            float s = pet.param(CosmeticSlot.PET, "scale");
            m.pushPose();
            m.translate(pet.param(CosmeticSlot.PET, "x"), 1.501 - pet.param(CosmeticSlot.PET, "y") - bob - previewLift,
                    -pet.param(CosmeticSlot.PET, "z"));
            m.mulPose(Axis.ZP.rotationDegrees(-(float) Math.sin(phase * .7) * 3f));
            m.mulPose(Axis.XP.rotationDegrees(previewLean));
            m.scale(-s, -s, s);
            draw(m, v, LightTexture.FULL_BRIGHT, p, petItem, pet.color, ItemDisplayContext.NONE);
            m.popPose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Предмети
    // ══════════════════════════════════════════════════════════════════════

    public static Item item(String id) {
        var mode = MetaClient.mode();
        CosmeticDef d = mode == null ? null : mode.cosmetics.get(id);
        if (d == null || d.model().isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(d.model());
        if (rl == null) return null;
        Item it = BuiltInRegistries.ITEM.get(rl);
        return it == Items.AIR ? null : it;
    }

    public static ItemStack stack(Item item, int color) {
        long key = ((long) System.identityHashCode(item) << 32) | (color & 0xFFFFFFL);
        return STACKS.computeIfAbsent(key, k -> {
            ItemStack st = new ItemStack(item);
            st.getOrCreateTag().putInt(COLOR_TAG, color & 0xFFFFFF);
            return st;
        });
    }

    /** Відтінок для реєстрації в лоадері: {@code (stack, tintIndex) -> колір}. */
    public static int tint(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(COLOR_TAG) ? stack.getTag().getInt(COLOR_TAG) : 0xFFFFFF;
    }

    static void draw(PoseStack m, MultiBufferSource v, int light, AbstractClientPlayer p, Item item, int color, ItemDisplayContext ctx) {
        Minecraft.getInstance().getItemRenderer().renderStatic(p, stack(item, color), ctx, false, m, v, p.level(), light,
                OverlayTexture.NO_OVERLAY, p.getId());
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
