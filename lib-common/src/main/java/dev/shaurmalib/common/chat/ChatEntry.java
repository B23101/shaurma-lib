package dev.shaurmalib.common.chat;

import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Один запис в історії чату (план, п. 3.9 + 3.29) — перенесення
 * {@code ChatHistoryStore.Entry} snipers_shaurma 1:1, разом з полем
 * {@code timestamp}.
 * <p>
 * На відміну від {@code AlertSpec}/{@code TimedOverlayEntry} (де текст —
 * навмисно {@code String}, план п. 3.4), тут зберігається вже готовий
 * {@link Component} — форматування (колір ніку за командою, роздільник,
 * колір тексту) застосовується один раз на сервері
 * ({@code ChatFormatEngine}, план п. 3.9) і має лишитись незмінним при
 * повторному рендері в T-екрані. {@code net.minecraft.network.chat.*} —
 * офіційний Mojang-пакет, не Forge-специфічний, тому присутність тут не
 * порушує ізоляцію {@code lib-common} (Architecture Sniffer, п. 2.1,
 * перевіряє лише {@code net.minecraftforge.*}).
 */
public final class ChatEntry {

    private final ChatEntryType type;
    private final Component message;
    private final UUID senderUuid;      // null для DEATH/SYSTEM
    private final String senderTeamId;  // null якщо без команди
    private final long timestampMs;

    public ChatEntry(ChatEntryType type, Component message, UUID senderUuid, String senderTeamId) {
        this(type, message, senderUuid, senderTeamId, System.currentTimeMillis());
    }

    /** Конструктор з явним {@code timestampMs} — для юніт-тестів і реплею. */
    public ChatEntry(ChatEntryType type, Component message, UUID senderUuid, String senderTeamId, long timestampMs) {
        this.type = type;
        this.message = message;
        this.senderUuid = senderUuid;
        this.senderTeamId = senderTeamId;
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

    public String senderTeamId() {
        return senderTeamId;
    }

    public long timestampMs() {
        return timestampMs;
    }
}
