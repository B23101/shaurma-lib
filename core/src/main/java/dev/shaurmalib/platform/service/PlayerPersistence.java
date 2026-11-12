package dev.shaurmalib.platform.service;

import dev.shaurmalib.platform.ref.PlayerRef;
import java.util.OptionalDouble;

/**
 * Дані, що переживають релог. Це ВІСЬ ЛОАДЕРА, а не версії: Forge/NeoForge мають
 * {@code getPersistentData()}, Fabric — ні (Data Attachment API / власний сховок), тож реалізація — у {@code loader:*}.
 */
public interface PlayerPersistence {
    OptionalDouble getNumber(PlayerRef p, String key);
    void putNumber(PlayerRef p, String key, double value);
    void remove(PlayerRef p, String key);
}
