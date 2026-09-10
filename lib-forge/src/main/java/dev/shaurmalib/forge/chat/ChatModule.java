package dev.shaurmalib.forge.chat;

import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.common.chat.ChatHistoryStore;
import dev.shaurmalib.common.chat.TeamChatContext;
import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.ChatBroadcastPacket;
import dev.shaurmalib.forge.overlay.AlertNotificationSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.UUID;

/**
 * ChatModule — центральна точка чат-системи бібліотеки (план, п. 3.9 +
 * 3.29). Заміна {@code ServerChatHandler} snipers_shaurma, з трьома
 * принциповими змінами відносно оригіналу:
 * <ol>
 *   <li><b>Виправлений баг подвійного слеша</b> — див.
 *       {@link ChatFormatEngine#stripLeadingSlashes(String)}: раніше
 *       {@code "//команда"} мовчки не виконувалась, бо знімався лише
 *       один провідний {@code '/'};</li>
 *   <li><b>{@link TeamChatContext}</b> замінює жорстку залежність на
 *       {@code MatchSession}/{@code TeamManager} snipers — консюмер сам
 *       підключає свою командну логіку через
 *       {@code ShaurmaLib.Builder.withChat(TeamChatContext, feedId)}.</li>
 *   <li><b>{@link ChatDisplaySink}</b> — опційний хук показу вхідного
 *       повідомлення на клієнті. Бібліотека НЕ нав'язує свій готовий
 *       {@link AlertNotificationSystem} рендер: консюмер, що вже має
 *       власний kill-feed/notification overlay (як
 *       {@code AlertNotificationOverlay} у snipers), реєструє sink через
 *       {@code ShaurmaLib.Builder.withChatDisplaySink(...)} і отримує
 *       готовий, відформатований {@link Component} + accent-колір для
 *       показу у СВОЄМУ рендері — без переходу на бібліотечний
 *       {@code withOverlays()}. Якщо sink не зареєстровано — модуль
 *       падає назад на дефолтний {@link AlertNotificationSystem} фід
 *       (стара поведінка, потребує {@code withOverlays()}).
 * </ol>
 * <p>
 * Дані (форматування, історія, команди) завжди йдуть через
 * {@link ChatFormatEngine}/{@link ChatHistoryStore} незалежно від того,
 * який рендер обрано — бібліотека відповідає за "базу", консюмер вільний
 * побудувати свій власний вигляд поверх неї.
 */
public final class ChatModule {

    /**
     * SPI-хук показу одного вхідного чат/системного повідомлення на
     * клієнті. Реалізується консюмером, що хоче рендерити чат власним
     * overlay-ем замість бібліотечного {@link AlertNotificationSystem}
     * (наприклад — уже наявний {@code AlertNotificationOverlay} у
     * snipers, щоб UX і фід лишились рівно тими самими, що й раніше).
     */
    @OnlyIn(Dist.CLIENT)
    public interface ChatDisplaySink {
        void display(Component message, int accentArgb);
    }

    private static volatile TeamChatContext teamContext;
    private static volatile String feedId = "chat";
    private static volatile ChatDisplaySink displaySink;
    private static final ChatHistoryStore HISTORY = new ChatHistoryStore();

    private ChatModule() {}

    /**
     * Підключає команду/консюмерську логіку — викликається з
     * {@code ShaurmaLib.Builder.build()} при {@code withChat(...)}.
     *
     * @param context команди/кольори гравців; {@code null} — модуль
     *                працює в чисто-global режимі (усі команди-параметри
     *                {@code team=true} автоматично зводяться до global,
     *                як для соло-режимів без команд взагалі).
     * @param feedId  ідентифікатор {@link AlertNotificationSystem} фіду,
     *                у який пушити вхідні повідомлення, якщо
     *                {@link ChatDisplaySink} НЕ зареєстровано (типово
     *                той самий kill-feed, що вже зареєстрований
     *                консюмером). Ігнорується, якщо sink підключено.
     */
    public static void attach(TeamChatContext context, String feedId) {
        teamContext = context;
        ChatModule.feedId = feedId;
    }

    /**
     * Підключає власний рендер показу повідомлень замість дефолтного
     * {@link AlertNotificationSystem} — викликається з
     * {@code ShaurmaLib.Builder.build()} при
     * {@code withChatDisplaySink(...)}. {@code null} скидає на
     * дефолтну поведінку (бібліотечний фід).
     */
    @OnlyIn(Dist.CLIENT)
    public static void attachDisplaySink(ChatDisplaySink sink) {
        displaySink = sink;
    }

    public static ChatHistoryStore history() {
        return HISTORY;
    }

    // ══════════════════════════════════════════════════════════════
    //  Сервер: вхідне повідомлення від гравця
    // ══════════════════════════════════════════════════════════════

    /**
     * Обробляє вхідний текст від {@link dev.shaurmalib.forge.network.packets.ChatSendPacket}.
     * Команди виконуються з правами гравця (не консолі); звичайний
     * текст форматується й розсилається як {@link ChatBroadcastPacket}.
     */
    public static void handleIncoming(ServerPlayer sender, String rawText, boolean wantsTeam) {
        String text = ChatFormatEngine.sanitize(rawText);
        if (text == null) return;

        if (ChatFormatEngine.isCommand(text)) {
            String cmd = ChatFormatEngine.stripLeadingSlashes(text);
            if (cmd.isBlank()) return;
            if (sender.getServer() != null) {
                sender.getServer().getCommands().performPrefixedCommand(
                        sender.createCommandSourceStack(), cmd);
            }
            return;
        }

        TeamChatContext ctx = teamContext;
        String teamId = ctx != null ? ctx.teamIdOf(sender.getUUID()) : null;
        boolean teamModeActive = ctx != null && ctx.teamModeActive() && teamId != null && !teamId.isEmpty();

        // Соло-режими (без команд) не мають кнопки Team на клієнті, але
        // про всяк випадок (застарілий/підроблений пакет) — fallback на global.
        boolean actuallyTeam = wantsTeam && teamModeActive;

        String senderColorHex = ctx != null ? ctx.teamColorHex(teamId) : null;
        MutableComponent formatted = ChatFormatEngine.formatChatLine(
                sender.getGameProfile().getName(), text, senderColorHex);

        if (actuallyTeam) {
            List<UUID> teammates = ctx.teammatesOf(teamId);
            for (UUID uuid : teammates) {
                ServerPlayer p = sender.getServer() != null
                        ? sender.getServer().getPlayerList().getPlayer(uuid) : null;
                if (p != null) {
                    ShaurmaLibNetwork.sendToPlayer(p,
                            new ChatBroadcastPacket(formatted, sender.getUUID(), teamId, true));
                }
            }
            // Відправник теж має побачити власне повідомлення (teammatesOf
            // може не включати самого відправника — переконуємось явно).
            if (!teammates.contains(sender.getUUID())) {
                ShaurmaLibNetwork.sendToPlayer(sender,
                        new ChatBroadcastPacket(formatted, sender.getUUID(), teamId, true));
            }
        } else if (sender.getServer() != null) {
            for (ServerPlayer p : sender.getServer().getPlayerList().getPlayers()) {
                ShaurmaLibNetwork.sendToPlayer(p,
                        new ChatBroadcastPacket(formatted, sender.getUUID(), teamId, false));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Клієнт: вхідне повідомлення з мережі / перехоплений системний чат
    // ══════════════════════════════════════════════════════════════

    @OnlyIn(Dist.CLIENT)
    public static void onBroadcastReceived(Component message, UUID senderUuid, String senderTeamId, boolean team) {
        boolean hasTeam = senderTeamId != null && !senderTeamId.isEmpty();
        String teamColorHex = hasTeam ? resolveTeamColorForAccent(senderTeamId) : null;
        int accent = ChatFormatEngine.resolveAccent(teamColorHex);
        displayMessage(message, accent);
        HISTORY.addChat(message, senderUuid, senderTeamId, team);
    }

    /**
     * Системні/ванільні повідомлення (вивід команд тощо), перехоплені
     * {@link dev.shaurmalib.forge.mixin.MixinChatComponent} — той самий
     * фід, той самий нейтральний сірий акцент, що й в оригіналі.
     */
    @OnlyIn(Dist.CLIENT)
    public static void onSystemMessage(Component message) {
        displayMessage(message, 0xFFAAAAAA);
        HISTORY.addSystem(message);
    }

    /**
     * Показує одне повідомлення через {@link ChatDisplaySink}, якщо
     * консюмер його зареєстрував (власний overlay), інакше — дефолтний
     * бібліотечний {@link AlertNotificationSystem} фід (стара поведінка).
     */
    @OnlyIn(Dist.CLIENT)
    private static void displayMessage(Component message, int accentArgb) {
        ChatDisplaySink sink = displaySink;
        if (sink != null) {
            sink.display(message, accentArgb);
            return;
        }
        AlertNotificationSystem.push(feedId, dev.shaurmalib.common.overlay.AlertSpec.of(message.getString(), accentArgb));
    }

    @OnlyIn(Dist.CLIENT)
    private static String resolveTeamColorForAccent(String teamId) {
        TeamChatContext ctx = teamContext;
        return ctx != null ? ctx.teamColorHex(teamId) : null;
    }
}
