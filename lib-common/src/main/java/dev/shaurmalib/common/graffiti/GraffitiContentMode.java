package dev.shaurmalib.common.graffiti;

/**
 * Режим контенту одного графіті-блоку (план, п. 3.14) — перенесення
 * {@code GraffitiBlockEntity.ContentMode} snipers_shaurma. Обидва режими
 * ділять spatial-налаштування (scale/offset/rotation) — контент
 * відрізняється лише тим, ЩО малюється в рамці на стіні.
 */
public enum GraffitiContentMode {
    /** Картинка з PNG, завантажена з {@code GraffitiFileStore}. */
    IMAGE,
    /** Список {@link GraffitiTextLine} — до {@link GraffitiSpec#MAX_TEXT_LINES}. */
    TEXT
}
