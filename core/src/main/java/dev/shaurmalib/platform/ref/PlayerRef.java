package dev.shaurmalib.platform.ref;

/** Гравець: {@code ServerPlayer} на сервері або {@code LocalPlayer} на клієнті (ядру різниця не потрібна). */
public interface PlayerRef extends EntityRef {
    String name();
}
