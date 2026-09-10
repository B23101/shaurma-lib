package dev.shaurmalib.common.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Форматування й розбір вхідного чат-тексту (план, п. 3.9) —
 * перенесення чистої (без Forge/сервер-командного виклику) частини
 * {@code ServerChatHandler} snipers_shaurma: обрізка довжини,
 * визначення "це команда чи звичайне повідомлення", побудова
 * форматованого {@code "Ім'я: текст"} з кольором ніку за командою.
 * <p>
 * Виконання самої команди (виклик {@code CommandDispatcher} з правами
 * гравця) лишається на боці Forge-модуля ({@code lib-forge}), бо це
 * вимагає {@code ServerPlayer}/{@code MinecraftServer} — тут лише
 * чиста текстова логіка, придатна для юніт-тестів без піднятого сервера.
 */
public final class ChatFormatEngine {

    public static final int MAX_LENGTH = 256;
    private static final int NAME_COLOR_DEFAULT = 0xFFFFFFFF;
    private static final int SEPARATOR_COLOR = 0xFFAAAAAA;
    private static final int MESSAGE_COLOR = 0xFFE8EDF2;

    private ChatFormatEngine() {}

    /** Обрізає й тримує вхідний текст у межах {@link #MAX_LENGTH}, повертає {@code null} для порожнього/бланк вводу. */
    public static String sanitize(String rawText) {
        if (rawText == null) return null;
        String text = rawText.strip();
        if (text.isEmpty()) return null;
        if (text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH);
        return text;
    }

    public static boolean isCommand(String sanitizedText) {
        return sanitizedText.startsWith("/");
    }

    /**
     * Знімає ВСІ провідні {@code '/'} з тексту команди, а не лише один.
     * <p>
     * <b>Виправлений баг оригіналу:</b> {@code ServerChatHandler.handleIncoming}
     * і клієнтський {@code ChatHistoryScreen} обидва робили
     * {@code text.substring(1)} — рівно один символ. При вводі
     * {@code "//tp @s ~ ~ ~"} (подвійний слеш — випадкове подвійне
     * натискання, або звичка з інших ігор/плагінів, де {@code //} щось
     * означає) лишався рядок {@code "/tp @s ~ ~ ~"} — Brigadier не має
     * команди з іменем, що починається на {@code '/'}, тому
     * {@code performPrefixedCommand} мовчки не знаходив збіг: гравець
     * бачив, що повідомлення "зникло", команда не виконувалась, і на
     * перший погляд це виглядало так, ніби другий {@code '/'} "з'їдає"
     * перший, а не так, що обидва разом ламають розбір команди. Тут
     * знімаються всі провідні слеші одразу, тому {@code "//tp"},
     * {@code "///tp"} тощо виконуються як звичайна команда {@code "tp"}.
     *
     * @return текст команди без жодного провідного {@code '/'}, можливо порожній рядок для {@code "//"} чи {@code "/"}.
     */
    public static String stripLeadingSlashes(String text) {
        int i = 0;
        while (i < text.length() && text.charAt(i) == '/') i++;
        return text.substring(i);
    }

    /** Кількість провідних символів {@code '/'} на початку рядка — потрібно клієнту для перерахунку позиції курсора. */
    public static int leadingSlashCount(String text) {
        int i = 0;
        while (i < text.length() && text.charAt(i) == '/') i++;
        return i;
    }

    /**
     * Будує форматований {@code "Ім'я: текст"} компонент — 1:1 формула
     * з оригінального {@code ServerChatHandler}: колір ніку з
     * {@code teamColorHex} (або білий, якщо немає команди/невалідний
     * hex), сірий роздільник, світлий текст повідомлення.
     */
    public static MutableComponent formatChatLine(String playerName, String text, String teamColorHex) {
        int nameColor = parseColorOrDefault(teamColorHex, NAME_COLOR_DEFAULT);
        return Component.empty()
                .append(Component.literal(playerName).withStyle(s -> s.withColor(nameColor)))
                .append(Component.literal(": ").withStyle(s -> s.withColor(SEPARATOR_COLOR)))
                .append(Component.literal(text).withStyle(s -> s.withColor(MESSAGE_COLOR)));
    }

    public static int parseColorOrDefault(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            long v = Long.parseLong(h, 16);
            return (int) (0xFF000000L | v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Акцентний колір фіду для вхідного повідомлення: командний колір, або нейтральний сірий для global/соло. */
    public static int resolveAccent(String teamColorHex) {
        return parseColorOrDefault(teamColorHex, 0xFFAAAAAA);
    }
}
