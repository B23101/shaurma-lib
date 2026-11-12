package dev.shaurmalib.platform.ref;

/**
 * Непрозорий дескриптор ігрового об'єкта. Ядро ніколи не викликає {@link #raw()}:
 * його використовує лише адаптер версії/лоадера, який знає конкретний тип.
 */
public interface Handle {
    Object raw();

    /** Безпечне приведення для адаптерів. */
    default <T> T unwrap(Class<T> type) { return type.cast(raw()); }
}
