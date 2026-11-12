package dev.shaurmalib.meta.mc.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.shaurmalib.meta.hud.Waypoint;
import dev.shaurmalib.meta.hud.WaypointCompass;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * Компас цілі вгорі по центру: темна плашка, у колі — стрілка, що показує, куди йти (повертається разом із
 * камерою), праворуч назва цілі й «211 м · вище». Під плашкою — тонка шкала з маркерами інших цілей.
 * Стрілка крутиться плавно; нова ціль «вистрибує» і кілька секунд пульсує.
 */
public final class CompassHud {

    private static float arrow = Float.NaN;
    private static float shown;
    private static long lastNanos = System.nanoTime();
    private static String lastId = "";
    private static long appearedAt;
    /** Режим може вимкнути компас (наприклад, на екранах катсцен). */
    public static volatile boolean enabled = true;

    private CompassHud() {}

    public static void render(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = Math.min(.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Player pl = mc.player;
        Waypoint wp = WaypointCompass.primary();
        boolean want = enabled && wp != null && pl != null && !mc.options.hideGui && mc.screen == null;
        shown = MetaUi.approach(shown, want ? 1f : 0f, 8f, dt);
        if (shown < .02f || wp == null || pl == null) { if (wp == null) arrow = Float.NaN; return; }
        if (!wp.id().equals(lastId)) { lastId = wp.id(); appearedAt = System.currentTimeMillis(); }

        double px = pl.getX(), py = pl.getEyeY() - 1.0, pz = pl.getZ();
        float yaw = pl.getViewYRot(partial);
        WaypointCompass.Reading r = WaypointCompass.read(wp, px, py, pz, yaw);
        float target = (float) r.relYaw();
        if (Float.isNaN(arrow)) arrow = target;
        arrow += (float) WaypointCompass.wrap(target - arrow) * Math.min(1f, dt * 10f);

        Font f = mc.font;
        String label = wp.label();
        String sub = r.distanceText() + (r.verticalText().isEmpty() ? "" : " · " + r.verticalText());
        int w = Math.max(150, Math.max(f.width(label), f.width(sub)) + 58), h = 30;
        int x = (g.guiWidth() - w) / 2, y = 6 + Math.round((1f - MetaUi.outCubic(shown)) * -40);
        long ms = System.currentTimeMillis();
        float since = (ms - appearedAt) / 1000f;
        float pop = since < .5f ? MetaUi.outBack(since / .5f) : 1f;
        float pulse = since < 4f || wp.pulse() ? .5f + .5f * (float) Math.sin(ms * .008) : 0f;
        int accent = wp.color();

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2f, y + h / 2f, 0);
        pose.scale(.85f + .15f * pop, .85f + .15f * pop, 1f);
        pose.translate(-(x + w / 2f), -(y + h / 2f), 0);

        float al = shown;
        MetaUi.shadow(g, x, y, w, h, 6, al);
        if (pulse > 0) MetaUi.glow(g, x, y, w, h, 6, accent, pulse * .6f * al);
        MetaUi.rgrad(g, x, y, w, h, 6, MetaUi.a(0x1C2230, .88f * al), MetaUi.a(0x0E121A, .92f * al));
        MetaUi.rborder(g, x, y, w, h, 6, MetaUi.a(MetaUi.mix(0xFF3A4458, MetaUi.solid(accent), .25f + .5f * pulse), al));
        g.fill(x + 6, y + 1, x + w - 6, y + 2, MetaUi.a(0xFFFFFF, .08f * al));

        // коло зі стрілкою
        int cx = x + 16, cy = y + h / 2;
        MetaUi.disc(g, cx, cy, 11, MetaUi.a(0x000000, .35f * al));
        MetaUi.arc(g, cx, cy, 11, 0, Math.PI * 2, 1, MetaUi.a(0xC8CEDA, .55f * al));
        boolean behind = Math.abs(arrow) > 110;
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(arrow));
        int ac = MetaUi.a(behind ? MetaUi.mix(0xFFFFFFFF, MetaUi.solid(accent), .6f) : 0xFFFFFF, al);
        // стрілка-«навігатор»: трикутник з вирізом
        for (int i = 0; i < 9; i++) {
            int half = i * 6 / 9;
            g.fill(-half, -6 + i, half + 1, -5 + i, ac);
        }
        g.fill(-1, 2, 2, 3, MetaUi.a(0x000000, .5f * al));
        pose.popPose();

        MetaUi.text(g, f, label, x + 34, y + 6, MetaUi.a(0xF2F2F7, al), true);
        MetaUi.text(g, f, sub, x + 34, y + 17, MetaUi.a(0x9AA3B5, al), false);
        if (!wp.icon().equals("arrow") && MetaIcons.has(wp.icon())) MetaIcons.draw(g, wp.icon(), x + w - 14, y + 11, 1, MetaUi.a(accent, .85f * al));

        // шкала: маркери всіх цілей у полі ±90°
        int sy = y + h + 3, sw = w - 24, sx = x + 12;
        g.fill(sx, sy, sx + sw, sy + 1, MetaUi.a(0xFFFFFF, .12f * al));
        for (Waypoint o : WaypointCompass.all()) {
            double rel = WaypointCompass.read(o, px, py, pz, yaw).relYaw();
            if (Math.abs(rel) > 90) continue;
            int mx = sx + sw / 2 + (int) (rel / 90.0 * sw / 2);
            boolean main = o == wp;
            g.fill(mx - (main ? 2 : 1), sy - (main ? 2 : 1), mx + (main ? 2 : 1) + 1, sy + (main ? 3 : 2), MetaUi.a(o.color(), al));
        }
        pose.popPose();
    }
}
