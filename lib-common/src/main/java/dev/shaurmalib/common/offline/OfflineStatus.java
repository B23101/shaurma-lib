package dev.shaurmalib.common.offline;

/**
 * Стан запису офлайн-гравця в {@link OfflineRegistry} (план, §4.3).
 */
public enum OfflineStatus {
    /** Гравець офлайн, тіло стоїть у світі. */
    STANDING,
    /** Тіло вбито, лут випав, власник ще не повернувся. */
    KILLED,
    /** Вичерпано {@link OfflineSpec#maxAbsenceTicks()}. */
    EXPIRED,
    /** Власник повернувся (кінцевий стан; запис одразу видаляється з реєстру). */
    RETURNED,
    /** Scope закрито, тіло знято безшумно (кінцевий стан). */
    CLOSED;

    /** {@code true} для станів, з яких запис уже нікуди не переходить. */
    public boolean isTerminal() {
        return this == RETURNED || this == CLOSED;
    }
}
