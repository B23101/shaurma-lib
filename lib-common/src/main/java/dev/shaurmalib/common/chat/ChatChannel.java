package dev.shaurmalib.common.chat;

import java.util.Objects;

/**
 * Один чат-канал бібліотеки — «окремий чат», у який пишуть і який
 * читають лише його учасники (лобі / виживші / маньяки / глядачі /
 * команда / глобальний...).
 *
 * <p>Канал — це <b>дані, а не логіка</b>. Бібліотека не знає ані назв
 * каналів конкретного режиму, ані правил, хто в який канал пише: це
 * описує консюмер через {@link ChatChannelProvider}, а сам канал лише
 * дає {@code id}, локалізаційну мітку і колір для UI. Саме тому канали
 * не захардкоджені в бібліотеці — маніяк реєструє свої (lobby/survivors/
 * maniacs/spectators), снайпери — свої (team), а рушій один.</p>
 *
 * @param id       стабільний рядковий ідентифікатор (напр. {@code "survivors"}).
 *                 Саме він летить у мережевих пакетах, тому змінювати його
 *                 між релізами не можна.
 * @param labelKey ключ локалізації для кнопки каналу в чаті.
 * @param colorArgb акцентний ARGB-колір каналу (рамка кнопки, підсвітка).
 */
public record ChatChannel(String id, String labelKey, int colorArgb) {

    /** Id глобального каналу — він є завжди і його не можна зняти. */
    public static final String GLOBAL_ID = "global";

    /** Id вбудованого каналу «команда» (легасі-шлях {@link TeamChatContext}). */
    public static final String TEAM_ID = "team";

    /** Глобальний канал — бачать і пишуть усі. Реєструється автоматично. */
    public static final ChatChannel GLOBAL =
        new ChatChannel(GLOBAL_ID, "gui.shaurma_lib.chat.channel.global", 0xFFE8EDF2);

    /** Канал «команда» — сумісність зі старим {@link TeamChatContext}-шляхом. */
    public static final ChatChannel TEAM =
        new ChatChannel(TEAM_ID, "gui.shaurma_lib.chat.channel.team", 0xFF39C5F0);

    public ChatChannel {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id каналу не може бути порожнім.");
        }
        if (labelKey == null || labelKey.isBlank()) {
            labelKey = "gui.shaurma_lib.chat.channel." + id;
        }
    }

    public boolean isGlobal() {
        return GLOBAL_ID.equals(id);
    }

    /** Той самий колір у вигляді HEX-рядка ({@code "#RRGGBB"}) — формат, який приймає {@link ChatFormatEngine}. */
    public String colorHex() {
        return String.format("#%06X", colorArgb() & 0xFFFFFF);
    }
}
