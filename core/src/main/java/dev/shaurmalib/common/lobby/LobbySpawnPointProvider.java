package dev.shaurmalib.common.lobby;

/**
 * Постачає поточну точку спавну лобі — реалізується споживачем, бо вибір
 * джерела координат режимо-специфічний (див. {@link LobbySpawnPoint}
 * джавадок). Бібліотека викликає це щоразу при відправці гравця в лобі,
 * а не кешує значення — так само, як оригінал щоразу заново читає
 * {@code ConfigLoader.game().lobbySpawn} чи {@code scnCfg.lobbyX/Y/Z} у
 * момент виклику {@code sendToLobby}, а не одного разу при старті.
 */
@FunctionalInterface
public interface LobbySpawnPointProvider {
    LobbySpawnPoint get();
}
