package dev.shaurmalib.common.spectator;

/**
 * Політика доступних цілей для режиму спостереження (план, п. 3.34) —
 * узагальнення того, що в оригіналі snipers_shaurma було розкидано по
 * конкретних Phase-класах (наприклад "лише своя команда" для SCN,
 * "будь-хто живий" для SD-глядачів). Консюмер реалізує один метод, рушій
 * бібліотеки ({@code SpectatorTargetCycler}) фільтрує кандидатів через
 * нього щоразу, коли гравець перемикає ціль.
 *
 * @param <P> тип гравця (типово {@code ServerPlayer} на боці forge-шару,
 *            тут — узагальнений тип, щоб common-модуль лишався 0-Minecraft
 *            там, де це можливо; forge-шар підставляє конкретний тип).
 */
@FunctionalInterface
public interface SpectatorPolicy<P> {

    /** {@code true}, якщо {@code viewer} має право спостерігати за {@code candidate}. */
    boolean canSpectate(P viewer, P candidate);

    /** Готова політика "будь-хто живий, крім себе самого". */
    static <P> SpectatorPolicy<P> anyone() {
        return (viewer, candidate) -> !viewer.equals(candidate);
    }
}
