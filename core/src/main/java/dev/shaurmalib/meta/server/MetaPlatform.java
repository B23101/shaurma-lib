package dev.shaurmalib.meta.server;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

/** Що мета-серверу треба від гри. Реалізує шар версії (ServerPlayer, шлях світу, права). */
public interface MetaPlatform {

    /** Корінь даних: {@code <світ>/data/shaurma_meta}. */
    Path dataDir();

    Collection<UUID> online();

    boolean isAdmin(UUID player);

    /** Повідомлення в чат/action-bar від імені сервера (помилки покупок тощо). */
    void tell(UUID player, String text, boolean actionBar);
}
