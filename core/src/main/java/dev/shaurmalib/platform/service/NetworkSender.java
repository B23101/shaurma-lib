package dev.shaurmalib.platform.service;

import dev.shaurmalib.platform.ref.PlayerRef;

/** Відправка пакетів. Рідна реалізація — в адаптері лоадера (різні API на Fabric/Forge/NeoForge). */
public interface NetworkSender {
    void sendToPlayer(PlayerRef player, Object packet);
    void sendToAllPlayers(Object packet);
    void sendToServer(Object packet);
}
