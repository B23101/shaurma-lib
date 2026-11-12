package dev.shaurmalib.meta.mc.screen;

import dev.shaurmalib.meta.cosmetics.CosmeticDef;
import dev.shaurmalib.meta.cosmetics.CosmeticSlot;
import dev.shaurmalib.meta.cosmetics.Look;
import dev.shaurmalib.meta.cosmetics.Worn;
import dev.shaurmalib.meta.mc.render.CosmeticRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Quaternionf;

/**
 * 3D-прев'ю косметики на манекені зі скіном гравця (картки магазину, пропуску, гардероба) і фон-«промені»
 * за рідкістю, як у карток вітрини.
 */
final class Preview {

    private static RemotePlayer mannequin;

    private Preview() {}

    static RemotePlayer mannequin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        if (mannequin == null || mannequin.level() != mc.level) {
            mannequin = new RemotePlayer(mc.level, mc.player.getGameProfile()) {
                @Override public boolean isModelPartShown(PlayerModelPart part) { return true; }
            };
        }
        return mannequin;
    }

    /** Манекен з одним предметом у прямокутнику (дизайнові координати екрана {@code s}). */
    static void item(GuiGraphics g, MetaScreen s, CosmeticDef d, int x, int y, int w, int h, float yaw) {
        Worn worn = new Worn(d.id(), d.defaultColor());
        worn.validate(d.slot());
        Look look = new Look().only(d.slot(), worn);
        boolean aura = d.slot() == CosmeticSlot.AURA, pet = d.slot() == CosmeticSlot.PET;
        // для капелюха — крупніше й вище (видно голову), для улюбленця/аури — весь манекен
        float size = d.slot() == CosmeticSlot.HEAD ? h * .62f : aura ? h * .34f : h * .40f;
        int foot = d.slot() == CosmeticSlot.HEAD ? y + h + (int) (h * .55f) : y + h - (aura ? h / 3 : 8);
        figure(g, s, x + w / 2, foot, size, yaw, look, x, y, w, h, aura, pet ? -.1f : 0);
    }

    static void figure(GuiGraphics g, MetaScreen s, int cx, int foot, float size, float yaw, Look look,
                       int clx, int cly, int clw, int clh, boolean tilt, float lift) {
        RemotePlayer m = mannequin();
        if (m == null) return;
        m.yBodyRot = m.yBodyRotO = m.yHeadRot = m.yHeadRotO = 0;
        m.setYRot(0);
        m.setXRot(0);
        g.enableScissor((int) (s.ox + clx * s.ui), (int) (s.oy + cly * s.ui), (int) (s.ox + (clx + clw) * s.ui), (int) (s.oy + (cly + clh) * s.ui));
        CosmeticRenderer.preview = look;
        CosmeticRenderer.drawingPreview = true;
        CosmeticRenderer.previewYaw = yaw;
        CosmeticRenderer.previewTilt = tilt ? .42f : 0f;
        CosmeticRenderer.previewLift = lift;
        try {
            InventoryScreen.renderEntityInInventory(g, cx, foot, (int) size,
                    new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.toRadians(yaw)), null, m);
        } finally {
            CosmeticRenderer.drawingPreview = false;
            CosmeticRenderer.preview = null;
            CosmeticRenderer.previewTilt = CosmeticRenderer.previewLift = 0;
            g.disableScissor();
        }
    }

    /** Предмет гри як 3D-іконка (скрині, подарунок): {@code itemId} з реєстру, центр (cx, cy), масштаб від 16 px. */
    static void itemIcon(GuiGraphics g, String itemId, float cx, float cy, float scale) {
        var rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        var it = rl == null ? net.minecraft.world.item.Items.CHEST : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        if (it == net.minecraft.world.item.Items.AIR) it = net.minecraft.world.item.Items.CHEST;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-8, -8, 0);
        g.renderItem(new net.minecraft.world.item.ItemStack(it), 0, 0);
        g.pose().popPose();
    }

    static String timer(long msLeft) {
        if (msLeft <= 0) return "0хв";
        long m = msLeft / 60_000L, h = m / 60, d = h / 24;
        if (d > 0) return d + "д " + (h % 24) + "год";
        if (h > 0) return h + "год " + (m % 60) + "хв";
        return m + "хв";
    }
}
