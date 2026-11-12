package dev.shaurmalib.common.chat;

/**
 * Тип запису в {@link ChatHistoryStore} (план, п. 3.9 + 3.29) —
 * перенесення {@code ChatHistoryStore.EntryType} snipers_shaurma 1:1.
 * <p>
 * {@code DEATH} і {@code SYSTEM} існують окремо від {@code CHAT_GLOBAL}/
 * {@code CHAT_TEAM}, бо в оригіналі один і той самий фід
 * ({@code AlertNotificationOverlay}) показує і смерті, і звичайний чат —
 * розрізнення типу лишається потрібним для фільтрів/іконок/кольору за
 * замовчуванням у майбутньому UI, навіть якщо рушій рендерить усі типи
 * однаково зараз.
 */
public enum ChatEntryType {
    DEATH,
    CHAT_GLOBAL,
    CHAT_TEAM,
    SYSTEM
}
