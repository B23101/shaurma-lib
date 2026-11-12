package dev.shaurmalib.common.markers;

import java.util.UUID;

/**
 * Правила "кому і коли показувати мітку" (план, п. 3.13) — та частина,
 * яка ЗАВЖДИ лишається на боці головного мода, як прямо сформульовано в
 * плані: "мод буде на базі відображати за правилами рендеру їх". Рушій
 * бібліотеки ({@code WorldBillboardEngine}, lib-forge) щокадру запитує
 * реалізацію цього інтерфейсу й малює лише те, що вона повернула —
 * бібліотека сама не приймає жодних рішень про видимість.
 * <p>
 * Приклад із {@code C4MarkerRenderer} snipers_shaurma (перенесений
 * інваріант "своя C4 — завжди видно, чужа — ніколи, союзна — лише в
 * командному режимі"):
 * <pre>{@code
 * MarkerVisibilityPolicy<C4MarkerData> c4Policy = (marker, viewerId) -> {
 *     if (marker.ownerId().equals(viewerId)) return true;              // своя — завжди
 *     if (!teamModeActive) return false;                               // соло — тільки своя
 *     String viewerTeam = playerTeams.get(viewerId);
 *     String ownerTeam  = playerTeams.get(marker.ownerId());
 *     return viewerTeam != null && viewerTeam.equals(ownerTeam);       // союзна — та сама команда
 * };
 * }</pre>
 *
 * @param <T> тип даних конкретної мітки (визначається консюмером —
 *            для C4 це координати+власник, для лідерборду — набір рядків).
 */
@FunctionalInterface
public interface MarkerVisibilityPolicy<T> {

    /**
     * @param marker         дані мітки, що розглядається для показу.
     * @param viewerPlayerId UUID гравця, якому потенційно показується мітка
     *                       (типово {@code Minecraft.getInstance().player.getUUID()}
     *                       на боці консюмера).
     * @return {@code true}, якщо мітку треба намалювати цьому гравцю.
     */
    boolean isVisible(T marker, UUID viewerPlayerId);

    /** Політика "видно всім завжди" — типовий дефолт для публічних міток (лідерборди, орієнтири). */
    static <T> MarkerVisibilityPolicy<T> alwaysVisible() {
        return (marker, viewerId) -> true;
    }
}
