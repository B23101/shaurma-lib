package dev.shaurmalib.platform.service;

import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.ref.ServerRef;

/**
 * Обгортає рідні об'єкти у {@code *Ref}. Реалізує {@code mc:*}; {@code loader:*} лише викликає її,
 * тож клей лоадера не імпортує версійних обгорток.
 */
public interface RefFactory {
    PlayerRef player(Object nativePlayer);
    LevelRef level(Object nativeLevel);
    ServerRef server(Object nativeServer);
}
