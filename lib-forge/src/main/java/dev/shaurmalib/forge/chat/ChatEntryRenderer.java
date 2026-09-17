package dev.shaurmalib.forge.chat;

import dev.shaurmalib.common.chat.ChatEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * SPI верстки одного запису чату — консюмер сам вирішує, як виглядає
 * повідомлення, а бібліотека лише дає дані й місце на екрані.
 *
 * <p>Це той самий принцип, що й у решти бібліотеки: «дані завжди є,
 * вигляд обирає консюмер». Дефолтний рендер ({@link PlainChatEntryRenderer})
 * малює звичайний текстовий рядок. Мод, якому треба власний вигляд —
 * 2D-голова гравця, нік кольором групи, текст під ніком — реєструє свою
 * реалізацію через {@link ChatEntryRendererRegistry#set} (або
 * {@code ShaurmaLib.Builder.withChatEntryRenderer(...)}) і верстка
 * переїжджає у мод, а не в бібліотеку.</p>
 *
 * <p>Рендер викликається на кожному кадрі, тому реалізація мусить бути
 * дешевою (без виділення важких об'єктів).</p>
 */
@OnlyIn(Dist.CLIENT)
public interface ChatEntryRenderer {

    /**
     * Висота ВМІСТУ запису (без внутрішніх відступів панелі, які додає
     * екран) для заданої ширини тексту.
     */
    int contentHeight(ChatEntry entry, Font font, int maxWidth);

    /**
     * Малює вміст запису у прямокутнику {@code (x, y, width, contentHeight)}.
     * Фон панелі й акцентну рамку вже намалював екран.
     *
     * @param accentArgb акцентний колір запису (колір групи/каналу або
     *                   нейтральний сірий) — якщо потрібен для тексту.
     */
    void render(GuiGraphics graphics, ChatEntry entry, Font font,
                int x, int y, int width, int contentHeight, int accentArgb);
}
