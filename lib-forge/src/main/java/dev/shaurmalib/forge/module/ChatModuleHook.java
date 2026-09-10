package dev.shaurmalib.forge.module;

import dev.shaurmalib.common.chat.TeamChatContext;
import dev.shaurmalib.forge.chat.ChatModule;

/**
 * Точка вбудовування чат-модуля (план, п. 3.9 + 3.29) — заміна
 * {@code ServerChatHandler} + {@code ChatHistoryScreen} +
 * {@code ChatScreenInterceptHandler} + {@code MixinChatComponent} +
 * {@code ChatHistoryStore} snipers_shaurma одним {@code withChat(...)}
 * на {@code ShaurmaLib.Builder}.
 * <p>
 * {@link #onAttach} лише реєструє {@link TeamChatContext} і
 * {@code feedId} у статичному {@link ChatModule} (сам рушій —
 * статичний, як {@code TeleportService}/{@code ItemAnimationEngine}) —
 * миксин ({@code MixinChatComponent}) і мережеві пакети завжди присутні
 * в jar-і lib-forge й підключаються незалежно від виклику цього хука;
 * без {@code withChat(...)} вони просто нічого корисного не роблять
 * (немає зареєстрованого фіду для push).
 */
public interface ChatModuleHook {
    void onAttach(FMLModuleContext ctx);

    static ChatModuleHook of(TeamChatContext teamContext, String feedId) {
        return ctx -> ChatModule.attach(teamContext, feedId);
    }
}
