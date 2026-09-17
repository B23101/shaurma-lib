package dev.shaurmalib.forge.chat;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Реєстр верстки записів чату. Консюмер один раз реєструє свій
 * {@link ChatEntryRenderer}; якщо не реєстрував — використовується
 * нейтральний {@link PlainChatEntryRenderer}.
 *
 * <p>Один рендер на клієнт, як і {@code ChatScreenButtonRegistry}: вигляд
 * чату в режимі — рішення мода, а не бібліотеки.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ChatEntryRendererRegistry {

    private static volatile ChatEntryRenderer renderer = new PlainChatEntryRenderer();

    private ChatEntryRendererRegistry() {}

    public static void set(ChatEntryRenderer custom) {
        renderer = custom == null ? new PlainChatEntryRenderer() : custom;
    }

    public static ChatEntryRenderer get() {
        return renderer;
    }

    /** Повертає дефолтну верстку (напр. при виході зі світу). */
    public static void reset() {
        renderer = new PlainChatEntryRenderer();
    }
}
