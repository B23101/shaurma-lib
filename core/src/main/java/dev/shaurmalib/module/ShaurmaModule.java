package dev.shaurmalib.module;

import java.util.Set;

/**
 * Підключаєма можливість бібліотеки (stamina, чат, оверлеї…). Один клас — одна можливість, без {@code withXxx} у
 * 2000-рядковому Builder. Модуль сам вирішує, що вішати на шину для даної сторони.
 */
public interface ShaurmaModule {
    String id();

    /** Ідентифікатори модулів, що мають бути підключені РАНІШЕ. */
    default Set<String> dependsOn() { return Set.of(); }

    void attach(ModuleContext ctx);

    /** Зворотній порядок до attach; за замовчуванням нічого. */
    default void detach() {}
}
