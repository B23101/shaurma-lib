package dev.shaurmalib.forge.tab;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.shaurmalib.common.tab.TabColumn;
import dev.shaurmalib.common.tab.TabTeamBarSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Золота тема snipers_shaurma — 1:1 те, що {@link TabListStyle} малював
 * до появи {@link TabTheme}. Це і тема за замовчуванням
 * ({@code new TabListStyle(skinResolver)}), і зручна БАЗА для власних:
 * успадкуйте й перевизначте лише потрібні примітиви — решта лишиться
 * золотою.
 * <p>
 * Клас навмисно НЕ {@code final}.
 */
public class DefaultTabTheme implements TabTheme {

    protected static int alpha(float p) {
        return (int) (p * 255);
    }

    @Override
    public void drawPanel(GuiGraphics g, int x, int y, int w, int h, float anim, long tick) {
        int a = (int) (anim * 255) & 0xFF;
        g.fill(x, y, x + w, y + h, ((int) (anim * 0x99) << 24) | 0x000000);
        g.fill(x, y, x + w, y + 1, (a << 24) | (TabListStyle.ACCENT_GOLD & 0xFFFFFF));
        g.fill(x, y + 1, x + w, y + 2, (Math.max(0, a - 180) << 24) | (TabListStyle.ACCENT_GOLD & 0xFFFFFF));
        int side = (((int) (anim * 0x55)) << 24) | (TabListStyle.ACCENT_GOLD_DM & 0xFFFFFF);
        g.fill(x, y, x + 1, y + h, side);
        g.fill(x + w - 1, y, x + w, y + h, side);
        g.fill(x, y + h - 1, x + w, y + h, side);
    }

    @Override
    public void drawHeader(GuiGraphics g, Font font, int cx, int sy, float anim,
                           String titleKey, String subtitleKey, long tick) {
        int a = (int) (anim * 255);
        g.drawCenteredString(font, Component.literal(Component.translatable(titleKey).getString().toUpperCase()),
                cx, sy + 4, (a << 24) | (TabListStyle.ACCENT_GOLD & 0xFFFFFF));
        if (subtitleKey != null) {
            int subA = (int) (anim * 110);
            g.drawCenteredString(font, Component.literal(Component.translatable(subtitleKey).getString()),
                    cx, sy + 15, (subA << 24) | 0xAAAAAA);
        }
    }

    @Override
    public void drawOnlineCount(GuiGraphics g, Font font, int cx, int y, float anim,
                                String countKey, int count, long tick) {
        int color = (int) (alpha(anim) * 0.85f);
        g.drawCenteredString(font, Component.literal("\u25cf " + Component.translatable(countKey, count).getString()),
                cx, y, (color << 24) | 0x55FF55);
    }

    @Override
    public void drawColumnHeaderBg(GuiGraphics g, int x, int y, int w, float anim, long tick) {
        g.fill(x, y, x + w, y + 13, ((int) (anim * 40) << 24) | 0x12182A);
        g.fill(x, y + 13, x + w, y + 14, ((int) (anim * 90) << 24) | TabListStyle.COL_HDR_LINE);
    }

    @Override
    public void drawColumnHeaders(GuiGraphics g, Font font, int ox, int y,
                                  List<TabColumn> columns, float anim, long tick) {
        int hc = alpha(anim) << 24 | TabListStyle.COL_GRAY;
        for (TabColumn col : columns) {
            g.drawString(font, Component.translatable(col.headerTextKey).getString(), ox + col.offsetX, y, hc, false);
        }
    }

    @Override
    public void drawRowBg(GuiGraphics g, int x, int y, int w, int h, int rank,
                          boolean isMe, int teamTint, float anim, long tick) {
        int stripe = (rank % 2 == 0) ? TabListStyle.STRIPE_A : TabListStyle.STRIPE_B;
        g.fill(x, y, x + w, y + h, ((int) (anim * 0x38) << 24) | stripe);
        if (teamTint != 0) g.fill(x, y, x + w, y + h, ((int) (anim * 0x18) << 24) | (teamTint & 0xFFFFFF));
        if (isMe) {
            g.fill(x, y, x + w, y + h, ((int) (anim * 0x28) << 24) | 0xFFD700);
            g.fill(x, y, x + 2, y + h, ((int) (anim * 255) << 24) | (TabListStyle.ACCENT_GOLD & 0xFFFFFF));
        }
    }

    @Override
    public void drawGoalPulse(GuiGraphics g, int x, int y, int w, int h, float anim, long tick) {
        float pulse = (float) (Math.sin(tick * 0.08) * 0.3 + 0.5);
        int pA = (int) (anim * pulse * 180);
        if (pA < 4) return;
        g.fill(x, y, x + w, y + h, (pA << 24) | 0xFFD700);
        g.fill(x, y, x + 2, y + h, ((int) (anim * 255) << 24) | (TabListStyle.ACCENT_GOLD & 0xFFFFFF));
    }

    @Override
    public void drawRespawning(GuiGraphics g, Font font, int x0, int rowW, int rowH,
                               int textX, int textY, String name, String respawnLabel,
                               float anim, long tick) {
        float pulse = (float) (Math.sin(tick * 0.10) * 0.35 + 0.65);
        int rowY = textY - (rowH - font.lineHeight) / 2;
        int aFill = (int) (anim * pulse * 60);
        if (aFill > 4) g.fill(x0, rowY, x0 + rowW, rowY + rowH, (aFill << 24) | (TabListStyle.RESPAWN_ORANGE & 0xFFFFFF));
        int aBar = (int) (anim * pulse * 255);
        if (aBar > 6) g.fill(x0, rowY, x0 + 2, rowY + rowH, (aBar << 24) | (TabListStyle.RESPAWN_ORANGE & 0xFFFFFF));
        int aName = (int) (anim * (0.7f + 0.3f * pulse) * 255);
        g.drawString(font, name, textX, textY, (aName << 24) | (TabListStyle.RESPAWN_ORANGE & 0xFFFFFF), false);

        String badge = "[" + respawnLabel + "]";
        int badgeX = textX + font.width(name) + 3;
        int availW = x0 + rowW - badgeX - 2;
        if (font.width(badge) > availW) badge = "[\u21ba]";
        if (availW > 6) {
            g.fill(badgeX - 1, textY - 1, badgeX + font.width(badge) + 2, textY + font.lineHeight,
                    ((int) (anim * 0x55) << 24) | 0x1A1F2E);
            g.drawString(font, badge, badgeX, textY, ((int) (anim * pulse * 255) << 24) | (TabListStyle.ACCENT_GOLD & 0xFFFFFF), false);
        }
    }

    @Override
    public void drawStrikeThrough(GuiGraphics g, Font font, String text, int x, int y,
                                  float anim, long tick) {
        int aText = (int) (anim * 140);
        g.drawString(font, text, x, y, (aText << 24) | TabListStyle.TEXT_DIM, false);
        int midY = y + font.lineHeight / 2;
        int aLine = (int) (anim * 180);
        g.fill(x, midY, x + font.width(text), midY + 1, (aLine << 24) | TabListStyle.STRIKE_LINE);
    }

    @Override
    public void drawTeamBar(GuiGraphics g, Font font, int x, int y, int w, int h,
                            TabTeamBarSpec spec, float anim, long tick) {
        int a = (int) (anim * 255);
        g.fill(x, y, x + w, y + h - 1, ((int) (anim * 0x55) << 24) | (spec.colorRGB & 0xFFFFFF));
        g.fill(x, y, x + w, y + h - 1, ((int) (anim * 0x77) << 24) | 0x05080F);
        g.fill(x, y + h - 1, x + w, y + h, (a << 24) | (spec.colorRGB & 0xFFFFFF));
        int textY = y + (h - font.lineHeight) / 2;
        g.drawString(font, "\u25cf", x + 6, textY, (a << 24) | (spec.colorRGB & 0xFFFFFF), false);
        g.drawString(font, spec.nameText, x + 18, textY, (a << 24) | 0xFFFFFF, false);
        if (spec.valueText != null && !spec.valueText.isEmpty()) {
            int vw = font.width(spec.valueText);
            g.drawString(font, spec.valueText, x + w - vw - 8, textY, (a << 24) | (TabListStyle.ACCENT_GOLD & 0xFFFFFF), false);
        }
    }

    @Override
    public void drawHead(GuiGraphics g, ResourceLocation skin, int x, int y, int size,
                         float anim, long tick) {
        if (skin == null) return;
        try {
            RenderSystem.setShaderColor(1f, 1f, 1f, anim);
            g.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
            g.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } catch (Exception ignored) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }
}
