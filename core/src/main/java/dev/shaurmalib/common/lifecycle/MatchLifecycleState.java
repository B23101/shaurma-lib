package dev.shaurmalib.common.lifecycle;

/**
 * Груба lifecycle-класифікація матчу (план, п. 3.19) — не заміна для
 * детального {@code GamePhase} snipers_shaurma
 * ({@code IDLE, COUNTDOWN_1, KIT_SELECT, COUNTDOWN_2, DROPPING, ACTIVE,
 * FINAL, ENDED}), а грубший бакет НАД ним, потрібний лібовим сервісам, які
 * лише хочуть знати "чи зараз можна редагувати налаштування режиму" або
 * "чи почати показ лобі-табло" — без прив'язки до snipers-специфічних
 * назв фаз.
 * <p>
 * Кожен режим (у будь-якому моді-споживачі) мапить свою власну, детальнішу
 * фазову модель на цей enum одним методом (наприклад
 * {@code IPhase.lifecycleState()} — п. 3.19 плану). Приклад мапінгу для
 * snipers_shaurma:
 * <pre>
 *   IDLE                       → IDLE
 *   COUNTDOWN_1 / COUNTDOWN_2  → COUNTDOWN
 *   KIT_SELECT / DROPPING      → COUNTDOWN  (все ще "матч готується")
 *   ACTIVE / FINAL             → ACTIVE
 *   ENDED                      → ENDING
 * </pre>
 * Лишень {@code ModeSettingsScreen} (план 3.32) і подібні лібові сервіси
 * питають "чи зараз IDLE" саме через цей грубий стан — вони НЕ повинні
 * знати про існування {@code KIT_SELECT}/{@code DROPPING} snipers.
 */
public enum MatchLifecycleState {
    IDLE,
    WAITING_FOR_PLAYERS,
    COUNTDOWN,
    ACTIVE,
    ENDING
}
