package dev.shaurmalib.forge.chat;

import dev.shaurmalib.common.chat.ChatEntry;
import dev.shaurmalib.forge.overlay.OverlayPanelStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Дефолтна (нейтральна) верстка запису чату: звичайний перенос тексту без
 * іконок і без групової підсвітки ніку. Це той мінімум, який бібліотека
 * гарантує будь-якому моду, що не зареєстрував власний
 * {@link ChatEntryRenderer}.
 */
@OnlyIn(Dist.CLIENT)
public final class PlainChatEntryRenderer implements ChatEntryRenderer {

    private static final int LINE_SPACING = 1;

    @Override
    public int contentHeight(ChatEntry entry, Font font, int maxWidth) {
        List<FormattedCharSequence> lines = lines(entry, font, maxWidth);
        return Math.max(1, lines.size()) * (font.lineHeight + LINE_SPACING);
    }

    @Override
    public void render(GuiGraphics graphics, ChatEntry entry, Font font,
                       int x, int y, int width, int contentHeight, int accentArgb) {
        List<FormattedCharSequence> lines = lines(entry, font, width);
        int textY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, textY, OverlayPanelStyle.TEXT_DEFAULT, true);
            textY += font.lineHeight + LINE_SPACING;
        }
    }

    private List<FormattedCharSequence> lines(ChatEntry entry, Font font, int maxWidth) {
        Component message = entry.message() == null ? Component.empty() : entry.message();
        return font.split(message, maxWidth);
    }
}
