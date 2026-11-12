package dev.shaurmalib.common.chat;

import java.util.List;
import java.util.UUID;

/**
 * Міст до командної логіки консюмера (план, п. 3.9) — узагальнення
 * {@code MatchSession}/{@code TeamManager} snipers_shaurma, на які
 * оригінальний {@code ServerChatHandler} посилався напряму (жорстка
 * залежність бібліотечного за задумом коду на конкретні класи гри).
 * <p>
 * Бібліотека не знає, що таке "матч" чи "команда" — вона лише питає
 * консюмера через цей інтерфейс: "чи є в цього гравця команда зараз?",
 * "хто його союзники?", "який у команди колір?". Соло-режими (без
 * команд) повертають {@code null}/порожній список — рушій сам
 * робить fallback на global-розсилку, як і в оригіналі.
 */
public interface TeamChatContext {

    /**
     * Ідентифікатор команди гравця в поточному матчі, або {@code null}/
     * порожній рядок, якщо гравець без команди (соло-режим чи матч
     * неактивний) — тоді {@code team=true}-повідомлення автоматично
     * розсилаються як global.
     */
    String teamIdOf(UUID playerUuid);

    /** Чи активний зараз матч у режимі команд (a la {@code session.teamMode}). */
    boolean teamModeActive();

    /** UUID членів команди {@code teamId}, включно з самим гравцем. */
    List<UUID> teammatesOf(String teamId);

    /** HEX-колір команди (напр. {@code "#3399FF"}), або {@code null}, якщо не задано. */
    String teamColorHex(String teamId);
}
