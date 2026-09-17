package dev.shaurmalib.forge.chat;

import dev.shaurmalib.common.chat.ChatChannel;
import dev.shaurmalib.common.chat.ChatChannelProvider;
import dev.shaurmalib.common.chat.ChatChannelRegistry;
import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.common.chat.ChatHistoryStore;
import dev.shaurmalib.common.chat.TeamChatContext;
import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.ChatBroadcastPacket;
import dev.shaurmalib.forge.network.packets.ChatChannelsSyncPacket;
import dev.shaurmalib.forge.overlay.AlertNotificationSystem;
import dev.shaurmalib.forge.sound.SoundCenter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ChatModule — центральна точка чат-системи бібліотеки. Заміна
 * {@code ServerChatHandler} snipers_shaurma, узагальнена до
 * <b>багатоканального</b> чату:
 *
 * <ol>
 *   <li><b>Канали</b> — консюмер реєструє свої канали в
 *       {@link ChatChannelRegistry} і описує правила доступу через
 *       {@link ChatChannelProvider}. Бібліотека не знає ані назв каналів,
 *       ані ролей: «лобі / виживші / маньяки / глядачі» — це просто
 *       чотири зареєстровані канали маніяка. Повідомлення каналу йде
 *       ЛИШЕ тим, кого поверне {@link ChatChannelProvider#recipientsOf};
 *       глобальний канал бачать усі.</li>
 *   <li><b>Виправлений баг подвійного слеша</b> —
 *       {@link ChatFormatEngine#stripLeadingSlashes(String)}.</li>
 *   <li><b>Структуровані повідомлення</b> — у пакеті летять нік,
 *       id каналу, колір групи і «сирий» текст, тому клієнтський
 *       {@link dev.shaurmalib.forge.client.chat.ChatHistoryScreen} малює
 *       голову гравця, нік кольором групи і текст під ним, а не лише
 *       готовий однорядковий {@code Component}.</li>
 *   <li><b>{@link ChatDisplaySink}</b> — опційний хук показу вхідного
 *       повідомлення (напр. власний kill-feed снайперів). Якщо не
 *       зареєстровано — модуль пушить у бібліотечний
 *       {@link AlertNotificationSystem} фід, який {@link #attachFeed}
 *       реєструє автоматично (раніше цього не робив ніхто — саме тому
 *       спливаюче повідомлення справа зверху було невидиме).</li>
 *   <li><b>Звук повідомлення</b> — {@link #SOUND_ID}, вмикається
 *       {@code withChatSound()} консюмера.</li>
 * </ol>
 */
public final class ChatModule {

    /** Звук вхідного повідомлення (файл додає консюмер у свою звукову теку). */
    public static final String SOUND_ID = "shaurma_lib:chat_message";

    /**
     * SPI-хук показу одного вхідного чат/системного повідомлення на
     * клієнті. Реалізується консюмером, що хоче рендерити чат власним
     * overlay-ем замість бібліотечного {@link AlertNotificationSystem}.
     */
    // БЕЗ @OnlyIn! Хоча реалізації цього інтерфейсу — клієнтські, САМ тип
    // стоїть у сигнатурі спільного методу
    // ShaurmaLib.Builder.withChatDisplaySink(ChatDisplaySink), який викликає
    // КОЖЕН консюмер (у т.ч. на виділеному сервері — звичайно всередині
    // DistExecutor). Якщо позначити інтерфейс @OnlyIn(CLIENT), Forge's
    // RuntimeDistCleaner виріже його з серверного рантайму, і створення
    // мод-класу консюмера падає ще до старту.
    public interface ChatDisplaySink {
        void display(Component message, int accentArgb);
    }

    private static volatile TeamChatContext teamContext;
    private static volatile ChatChannelProvider channelProvider;
    private static volatile String feedId = "chat";
    private static volatile ChatDisplaySink displaySink;
    private static volatile boolean soundEnabled;
    private static volatile Supplier<Integer> feedTopOffset = () -> 0;
    private static volatile String registeredFeedId;

    private static final ChatHistoryStore HISTORY = new ChatHistoryStore();

    /** Клієнтське дзеркало: у які канали МІСЦЕВИЙ гравець має право писати. */
    private static volatile List<String> clientWritableChannels = List.of();
    private static volatile String clientDefaultChannel = ChatChannel.GLOBAL_ID;

    private ChatModule() {}

    // ══════════════════════════════════════════════════════════════
    //  Підключення (викликається з ShaurmaLib.Builder.build())
    // ══════════════════════════════════════════════════════════════

    /**
     * Легасі-шлях: один «командний» канал поверх {@link TeamChatContext}.
     * Повідомлення {@code team=true} ідуть у канал {@link ChatChannel#TEAM_ID}.
     */
    public static void attach(TeamChatContext context, String feedId) {
        teamContext = context;
        channelProvider = context == null ? null : legacyProvider(context);
        ChatModule.feedId = feedId;
    }

    /**
     * Багатоканальний шлях: консюмер сам описує канали і доступ
     * ({@link ChatChannelProvider}), а канали для UI реєструє в
     * {@link ChatChannelRegistry}.
     */
    public static void attachChannels(ChatChannelProvider provider,
                                      String feedId,
                                      boolean soundEnabled,
                                      Supplier<Integer> feedTopOffset) {
        channelProvider = provider;
        teamContext = null;
        ChatModule.feedId = feedId;
        ChatModule.soundEnabled = soundEnabled;
        ChatModule.feedTopOffset = feedTopOffset == null ? () -> 0 : feedTopOffset;
    }

    /**
     * Підключає власний рендер показу повідомлень замість дефолтного
     * {@link AlertNotificationSystem}. {@code null} скидає на дефолт.
     */
    @OnlyIn(Dist.CLIENT)
    public static void attachDisplaySink(ChatDisplaySink sink) {
        displaySink = sink;
    }

    /**
     * Реєструє живий фід {@link AlertNotificationSystem}, у який модуль
     * пушить вхідні повідомлення. Без цього виклику повідомлення
     * накопичувались у невидимому фіді — саме тому «спливаюче
     * повідомлення справа зверху» не з'являлось.
     * <p>
     * Викликається з {@code ShaurmaLib.Builder.build()} на клієнті, якщо
     * чат увімкнено і {@code feedId} не {@code null}. Безпечно до повторного
     * виклику.
     */
    @OnlyIn(Dist.CLIENT)
    public static void attachFeed() {
        attachClientFeed(feedId, feedTopOffset, soundEnabled);
    }

    /**
     * Явне підключення клієнтської частини чату.
     *
     * <p>Потрібне модам, які піднімають бібліотеку на <b>серверній</b> події
     * ({@code ServerAboutToStartEvent}) — тоді на виділеному сервері
     * {@code build()} виконується лише сервером, а клієнт, підключений до
     * нього, не отримує ні фіду, ні звуку. Консюмер викликає це зі свого
     * клієнтського сетапу ({@code FMLClientSetupEvent}) — тоді поведінка
     * однакова і в singleplayer, і на дедіку.</p>
     *
     * <p>Безпечно викликати повторно: фід реєструється один раз на id.</p>
     *
     * @param feedId     id фіду {@link AlertNotificationSystem};
     * @param topOffset  вертикальний зсув фіду (як у {@code registerFeed});
     * @param sound      вмикати звук вхідного повідомлення.
     */
    @OnlyIn(Dist.CLIENT)
    public static void attachClientFeed(String feedId, Supplier<Integer> topOffset, boolean sound) {
        if (feedId != null && !feedId.isBlank()) {
            ChatModule.feedId = feedId;
        }
        ChatModule.feedTopOffset = topOffset == null ? () -> 0 : topOffset;
        if (sound) soundEnabled = true;

        String id = ChatModule.feedId;
        if (id == null || id.isBlank() || id.equals(registeredFeedId)) return;
        AlertNotificationSystem.registerFeed(id, ChatModule.feedTopOffset);
        registeredFeedId = id;
    }

    /** Вмикає звук вхідних повідомлень ({@link #SOUND_ID}). */
    public static void enableSound() {
        soundEnabled = true;
    }

    /** Знімає стан чату при зупинці сервера (інакше наступний світ успадкує канали). */
    public static void shutdown() {
        teamContext = null;
        channelProvider = null;
        displaySink = null;
        feedId = "chat";
        soundEnabled = false;
        registeredFeedId = null;
        clientWritableChannels = List.of();
        clientDefaultChannel = ChatChannel.GLOBAL_ID;
        HISTORY.reset();
    }

    public static ChatHistoryStore history() {
        return HISTORY;
    }

    /** Канали, у які місцевий гравець має право писати (клієнт). */
    @OnlyIn(Dist.CLIENT)
    public static List<String> clientWritableChannels() {
        return clientWritableChannels;
    }

    /** Канал, вибраний за замовчуванням для місцевого гравця (клієнт). */
    @OnlyIn(Dist.CLIENT)
    public static String clientDefaultChannel() {
        return clientDefaultChannel;
    }

    // ══════════════════════════════════════════════════════════════
    //  Сервер: синхронізація доступних каналів
    // ══════════════════════════════════════════════════════════════

    /**
     * Надсилає гравцю список каналів, у які він має право писати.
     * Консюмер кличе це при вході та коли роль/фаза змінились
     * ({@code /maniac} ротація ролей, ROLE_REVEAL, RESET...).
     */
    public static void syncChannels(ServerPlayer player) {
        if (player == null) return;
        ChatChannelProvider provider = channelProvider;
        List<String> writable = provider == null
                ? writableFromLegacy(player)
                : new ArrayList<>(provider.writableChannels(player.getUUID()));
        String def = writable.isEmpty() ? ChatChannel.GLOBAL_ID : writable.get(0);
        ShaurmaLibNetwork.sendToPlayer(player, new ChatChannelsSyncPacket(writable, def));
    }

    public static void syncChannels(List<ServerPlayer> players) {
        if (players == null) return;
        for (ServerPlayer player : players) syncChannels(player);
    }

    private static List<String> writableFromLegacy(ServerPlayer player) {
        TeamChatContext ctx = teamContext;
        if (ctx == null) return List.of();
        String teamId = ctx.teamIdOf(player.getUUID());
        if (teamId == null || teamId.isEmpty() || !ctx.teamModeActive()) return List.of();
        return List.of(ChatChannel.TEAM_ID);
    }

    // ══════════════════════════════════════════════════════════════
    //  Сервер: вхідне повідомлення від гравця
    // ══════════════════════════════════════════════════════════════

    /** Легасі-сигнатура (boolean team) — зберігається для сумісності. */
    public static void handleIncoming(ServerPlayer sender, String rawText, boolean wantsTeam) {
        handleIncoming(sender, rawText, wantsTeam ? ChatChannel.TEAM_ID : ChatChannel.GLOBAL_ID);
    }

    /**
     * Обробляє вхідний текст від
     * {@link dev.shaurmalib.forge.network.packets.ChatSendPacket}. Команди
     * виконуються з правами гравця (не консолі); звичайний текст
     * форматується й розсилається як {@link ChatBroadcastPacket} лише
     * учасникам вибраного каналу.
     */
    public static void handleIncoming(ServerPlayer sender, String rawText, String channelId) {
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

        if (sender.getServer() == null) return;

        String channel = channelId == null || channelId.isBlank()
                ? ChatChannel.GLOBAL_ID : channelId;
        Set<UUID> recipientIds = resolveRecipients(sender, channel);
        if (recipientIds == null) return; // канал недоступний цьому гравцю

        ChatChannelProvider provider = channelProvider;
        String colorHex = provider != null
                ? provider.colorHexOf(channel, sender.getUUID())
                : legacyColorHex(sender, channel);
        if ((colorHex == null || colorHex.isEmpty()) && !ChatChannel.GLOBAL_ID.equals(channel)) {
            colorHex = ChatChannelRegistry.byIdOrSynthetic(channel).colorHex();
        }

        MutableComponent formatted = ChatFormatEngine.formatChatLine(
                sender.getGameProfile().getName(), text, colorHex);

        ChatBroadcastPacket packet = new ChatBroadcastPacket(
                sender.getUUID(), sender.getGameProfile().getName(), channel, colorHex, text, formatted);

        for (UUID uuid : recipientIds) {
            ServerPlayer target = sender.getServer().getPlayerList().getPlayer(uuid);
            if (target != null) {
                ShaurmaLibNetwork.sendToPlayer(target, packet);
            }
        }
    }

    /**
     * @return UUID отримувачів каналу (з самим відправником), або
     *         {@code null}, якщо гравець не має права писати в цей канал.
     */
    private static Set<UUID> resolveRecipients(ServerPlayer sender, String channel) {
        if (ChatChannel.GLOBAL_ID.equals(channel)) {
            Set<UUID> all = new LinkedHashSet<>();
            for (ServerPlayer p : sender.getServer().getPlayerList().getPlayers()) {
                all.add(p.getUUID());
            }
            return all;
        }

        ChatChannelProvider provider = channelProvider;
        List<String> writable = provider == null
                ? writableFromLegacy(sender)
                : provider.writableChannels(sender.getUUID());
        if (writable == null || !writable.contains(channel)) {
            return null;
        }

        List<UUID> members = provider == null
                ? legacyTeamMembers(sender)
                : provider.recipientsOf(channel, sender.getUUID());
        Set<UUID> recipients = new LinkedHashSet<>();
        if (members != null) recipients.addAll(members);
        recipients.add(sender.getUUID()); // автор завжди бачить власне повідомлення
        return recipients;
    }

    private static List<UUID> legacyTeamMembers(ServerPlayer sender) {
        TeamChatContext ctx = teamContext;
        if (ctx == null) return List.of();
        String teamId = ctx.teamIdOf(sender.getUUID());
        return teamId == null ? List.of() : ctx.teammatesOf(teamId);
    }

    private static String legacyColorHex(ServerPlayer sender, String channel) {
        TeamChatContext ctx = teamContext;
        if (ctx == null) return null;
        String teamId = ctx.teamIdOf(sender.getUUID());
        return ctx.teamColorHex(teamId);
    }

    private static ChatChannelProvider legacyProvider(TeamChatContext ctx) {
        return new ChatChannelProvider() {
            @Override
            public List<String> writableChannels(UUID sender) {
                String teamId = ctx.teamIdOf(sender);
                if (teamId == null || teamId.isEmpty() || !ctx.teamModeActive()) return List.of();
                return List.of(ChatChannel.TEAM_ID);
            }

            @Override
            public List<UUID> recipientsOf(String channelId, UUID sender) {
                String teamId = ctx.teamIdOf(sender);
                return teamId == null ? List.of() : ctx.teammatesOf(teamId);
            }

            @Override
            public String colorHexOf(String channelId, UUID sender) {
                return ctx.teamColorHex(ctx.teamIdOf(sender));
            }
        };
    }

    // ══════════════════════════════════════════════════════════════
    //  Клієнт: вхідне повідомлення з мережі / перехоплений системний чат
    // ══════════════════════════════════════════════════════════════

    /** Легасі-сигнатура — сумісність зі старим пакетом. */
    @OnlyIn(Dist.CLIENT)
    public static void onBroadcastReceived(Component message, UUID senderUuid, String senderTeamId, boolean team) {
        String channel = senderTeamId == null || senderTeamId.isEmpty()
                ? (team ? ChatChannel.TEAM_ID : ChatChannel.GLOBAL_ID)
                : senderTeamId;
        int accent = ChatFormatEngine.resolveAccent(resolveColorForAccent(channel, message));
        displayMessage(message, accent);
        HISTORY.addChat(message, senderUuid, channel, null, null, null, team);
    }

    /**
     * Структуроване вхідне повідомлення: нік, канал, колір групи і
     * «сирий» текст (окремо від уже відформатованого {@code formatted}).
     */
    @OnlyIn(Dist.CLIENT)
    public static void onMessageReceived(UUID senderUuid,
                                         String senderName,
                                         String channelId,
                                         String colorHex,
                                         String rawText,
                                         Component formatted) {
        String channel = channelId == null || channelId.isEmpty()
                ? ChatChannel.GLOBAL_ID : channelId;
        int accent = ChatFormatEngine.resolveAccent(colorHex != null ? colorHex
                : ChatChannelRegistry.byIdOrSynthetic(channel).colorHex());
        displayMessage(formatted, accent);
        playMessageSound();
        HISTORY.addChat(formatted, senderUuid, channel, senderName, rawText, colorHex,
                !ChatChannel.GLOBAL_ID.equals(channel));
    }

    /** Клієнтське дзеркало доступних каналів (з {@code ChatChannelsSyncPacket}). */
    @OnlyIn(Dist.CLIENT)
    public static void onChannelsSync(List<String> writable, String defaultChannel) {
        clientWritableChannels = writable == null ? List.of() : List.copyOf(writable);
        clientDefaultChannel = defaultChannel == null || defaultChannel.isEmpty()
                ? ChatChannel.GLOBAL_ID : defaultChannel;
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
     * бібліотечний {@link AlertNotificationSystem} фід.
     */
    @OnlyIn(Dist.CLIENT)
    private static void displayMessage(Component message, int accentArgb) {
        ChatDisplaySink sink = displaySink;
        if (sink != null) {
            sink.display(message, accentArgb);
            return;
        }
        String id = feedId;
        if (id != null && !id.isBlank()) {
            AlertNotificationSystem.push(id,
                    dev.shaurmalib.common.overlay.AlertSpec.of(message.getString(), accentArgb));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void playMessageSound() {
        if (!soundEnabled) return;
        SoundCenter.playUi(SOUND_ID, 1.0f, 1.0f);
    }

    @OnlyIn(Dist.CLIENT)
    private static String resolveColorForAccent(String channelId, Component message) {
        ChatChannelProvider provider = channelProvider;
        Player local = net.minecraft.client.Minecraft.getInstance().player;
        if (provider != null && local != null) {
            String hex = provider.colorHexOf(channelId, local.getUUID());
            if (hex != null && !hex.isEmpty()) return hex;
        }
        ChatChannel channel = ChatChannelRegistry.byId(channelId);
        if (channel != null && !channel.isGlobal()) {
            return String.format("#%06X", channel.colorArgb() & 0xFFFFFF);
        }
        return null;
    }
}
