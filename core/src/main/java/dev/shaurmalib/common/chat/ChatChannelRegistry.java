package dev.shaurmalib.common.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реєстр чат-каналів (lib-common) — консюмер реєструє свої канали один
 * раз на старті, бібліотека читає їх для мережевої синхронізації кнопок
 * і рендеру чату.
 *
 * <p>Єдиний канал, який існує завжди і не може бути знятий, —
 * {@link ChatChannel#GLOBAL}. Порядок реєстрації визначає порядок кнопок
 * у чаті (глобальний завжди перший).</p>
 *
 * <p>Реєстр статичний, як {@code SoundCategoryRegistry}/{@code SoundCueRegistry}:
 * без стану ініціалізації, з явним {@link #clear()} на зупинці сервера —
 * інакше наступний світ у тій самій JVM успадкував би канали попереднього.</p>
 */
public final class ChatChannelRegistry {

    private static final Map<String, ChatChannel> CHANNELS =
        Collections.synchronizedMap(new LinkedHashMap<>());

    static {
        CHANNELS.put(ChatChannel.GLOBAL.id(), ChatChannel.GLOBAL);
    }

    private ChatChannelRegistry() {}

    /** Реєструє канал (або замінює однойменний). Глобальний залишається першим. */
    public static void register(ChatChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("Канал не може бути null.");
        }
        CHANNELS.put(channel.id(), channel);
    }

    /**
     * Знімає канал за id. Для {@link ChatChannel#GLOBAL_ID} нічого не
     * робить — глобальний чат існує завжди.
     */
    public static void unregister(String id) {
        if (id == null || ChatChannel.GLOBAL_ID.equals(id)) return;
        CHANNELS.remove(id);
    }

    /** Канал за id, або {@code null}, якщо не зареєстрований. */
    public static ChatChannel byId(String id) {
        return id == null ? null : CHANNELS.get(id);
    }

    /** Канал за id; невідомий id перетворюється на синтетичний канал (щоб UI не падав). */
    public static ChatChannel byIdOrSynthetic(String id) {
        ChatChannel channel = byId(id);
        if (channel != null) return channel;
        String safe = id == null || id.isBlank() ? ChatChannel.GLOBAL_ID : id;
        return new ChatChannel(safe, "gui.shaurma_lib.chat.channel." + safe, 0xFFAAAAAA);
    }

    /** Усі канали: глобальний перший, далі — у порядку реєстрації. */
    public static List<ChatChannel> all() {
        synchronized (CHANNELS) {
            List<ChatChannel> ordered = new ArrayList<>(CHANNELS.size());
            ChatChannel global = CHANNELS.get(ChatChannel.GLOBAL_ID);
            if (global != null) ordered.add(global);
            for (ChatChannel channel : CHANNELS.values()) {
                if (!channel.isGlobal()) ordered.add(channel);
            }
            return List.copyOf(ordered);
        }
    }

    /**
     * Прибирає канали консюмера, лишаючи {@link ChatChannel#GLOBAL}.
     * Викликати при зупинці сервера — реєстр статичний.
     */
    public static void clear() {
        CHANNELS.clear();
        CHANNELS.put(ChatChannel.GLOBAL.id(), ChatChannel.GLOBAL);
    }
}
