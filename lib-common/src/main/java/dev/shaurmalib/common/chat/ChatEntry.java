package dev.shaurmalib.common.chat;

import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Один запис в історії чату.
 *
 * <p>Окрім уже відформатованого {@link #message()} (колір ніку + текст —
 * те, що бачить {@code ChatDisplaySink} і старий фід) запис тепер несе і
 * <b>структуровані</b> поля ({@link #senderName()}, {@link #channelId()},
 * {@link #rawText()}), щоб {@code ChatHistoryScreen} міг намалювати
 * повідомлення власною версткою: 2D-голова гравця, нік кольором групи і
 * текст під ним. Це той самий принцип, що й у решти бібліотеки —
 * «дані завжди є, рендер обирає консюмер».</p>
 *
 * <p>{@code net.minecraft.network.chat.*} — офіційний Mojang-пакет, не
 * Forge-специфічний, тому присутність тут не порушує ізоляцію
 * {@code lib-common} (Architecture Sniffer перевіряє лише
 * {@code net.minecraftforge.*}).</p>
 */
public final class ChatEntry {

    private final ChatEntryType type;
    private final Component message;
    private final UUID senderUuid;      // null для DEATH/SYSTEM
    private final String channelId;     // null якщо без каналу
    private final String senderName;    // null для DEATH/SYSTEM
    private final String rawText;       // null для DEATH/SYSTEM
    private final String colorHex;      // колір групи відправника, може бути null
    private final long timestampMs;

    public ChatEntry(ChatEntryType type, Component message, UUID senderUuid, String channelId) {
        this(type, message, senderUuid, channelId, null, null, null, System.currentTimeMillis());
    }

    /** Конструктор з явним {@code timestampMs} — для юніт-тестів і реплею. */
    public ChatEntry(ChatEntryType type, Component message, UUID senderUuid, String channelId,
                     long timestampMs) {
        this(type, message, senderUuid, channelId, null, null, null, timestampMs);
    }

    public ChatEntry(ChatEntryType type,
                     Component message,
                     UUID senderUuid,
                     String channelId,
                     String senderName,
                     String rawText,
                     String colorHex) {
        this(type, message, senderUuid, channelId, senderName, rawText, colorHex,
                System.currentTimeMillis());
    }

    public ChatEntry(ChatEntryType type,
                     Component message,
                     UUID senderUuid,
                     String channelId,
                     String senderName,
                     String rawText,
                     String colorHex,
                     long timestampMs) {
        this.type = type;
        this.message = message;
        this.senderUuid = senderUuid;
        this.channelId = channelId;
        this.senderName = senderName;
        this.rawText = rawText;
        this.colorHex = colorHex;
        this.timestampMs = timestampMs;
    }

    public ChatEntryType type() {
        return type;
    }

    public Component message() {
        return message;
    }

    public UUID senderUuid() {
        return senderUuid;
    }

    /** Id чат-каналу, у якому надіслано повідомлення. {@code null} для системних. */
    public String channelId() {
        return channelId;
    }

    /** @deprecated назва з часів «командного» чату; використовуйте {@link #channelId()}. */
    @Deprecated
    public String senderTeamId() {
        return channelId;
    }

    /** Нік відправника без форматування — для окремого рядка над текстом. */
    public String senderName() {
        return senderName;
    }

    /** «Сирий» текст повідомлення (без ніку й роздільника). */
    public String rawText() {
        return rawText;
    }

    /** HEX-колір групи відправника (для ніку), якщо відомий. */
    public String colorHex() {
        return colorHex;
    }

    public long timestampMs() {
        return timestampMs;
    }

    /** Чи це звичайне чат-повідомлення гравця (можна малювати головою + ніком). */
    public boolean isPlayerMessage() {
        return (type == ChatEntryType.CHAT_GLOBAL || type == ChatEntryType.CHAT_TEAM)
            && senderUuid != null && senderName != null;
    }
}
