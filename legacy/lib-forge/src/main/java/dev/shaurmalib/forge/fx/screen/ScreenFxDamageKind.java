package dev.shaurmalib.forge.fx.screen;

/**
 * Категорія ефекту пошкодження (вибирає набір текстур). Рахується на сервері
 * ({@link ScreenFxDamageClassifier}), клієнт лише відтворює.
 */
public enum ScreenFxDamageKind {
    /** Гостра зброя або кігті — порізи. */
    SLASH,
    /** Тупий удар, падіння — удар. */
    IMPACT,
    /** Стріла, шипи, укус — проколи. */
    PIERCE,
    /** Загальні бризки крові. */
    SPLATTER,
    FIRE,
    FREEZE,
    /** Без ефекту. */
    NONE
}
