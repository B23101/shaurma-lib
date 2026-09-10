package dev.shaurmalib.common.lobby;

/**
 * Координати точки спавну лобі — чисті дані, без прив'язки до
 * {@code ServerLevel}/виміру (той визначається окремо, типово
 * {@code server.overworld()} споживача, як і в оригіналі
 * {@code PlayerJoinEventHandler.sendToLobby}).
 * <p>
 * Перенесення того, що в оригіналі — два паралельних джерела координат
 * лобі в одному методі {@code sendToLobby(ServerPlayer)}:
 * <pre>
 *   SCN  → SCNGameConfig (scnCfg.lobbyX/Y/Z, свій per-mode лобі)
 *   решта → GameConfig.SpawnPoint (ConfigLoader.game().lobbySpawn, спільний)
 * </pre>
 * Бібліотека не знає про SCN чи "решту" — вона лише отримує вже готовий
 * {@link LobbySpawnPoint} через {@link LobbySpawnPointProvider}, яку
 * консюмер реалізує сам (у snipers це буде та сама гілка
 * "якщо активний режим SCN — читай SCNGameConfig, інакше —
 * GameConfig.lobbySpawn", перенесена як є, а не переізобрітена).
 */
public record LobbySpawnPoint(double x, double y, double z, float yaw) {

    public LobbySpawnPoint(double x, double y, double z) {
        this(x, y, z, 0f);
    }
}
