package dev.shaurmalib.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Заміна розкиданих ручних викликів {@code ConfigLoader.reloadX()} по
 * всьому коду snipers_shaurma (кожен новий конфіг-клас там мав власний
 * статичний {@code reloadX()}, і хтось мусив пам'ятати викликати його з
 * потрібного місця — наприклад {@code GameModeRegistry.setActive(...)}
 * явно викликає {@code ConfigLoader.reloadAirdropConfig()} окремим рядком)
 * на єдину подію {@code ConfigReloadEvent(modeId)}.
 * <p>
 * Кожен модуль (loot/kits/items/…, і в майбутньому будь-який модуль
 * maniac-mode) сам підписується через {@link #subscribe(Listener)} один
 * раз при ініціалізації — знижує ризик класу багів "забули викликати
 * reload для нового модуля", задокументований у коментарі оригінального
 * {@code GameModeRegistry.setActive}.
 * <p>
 * Instance-based (не статичний реєстр) — один {@code ConfigReloadBus} на
 * один {@link ShaurmaConfigTree}/споживача, так само як решта модулів
 * бібліотеки.
 */
public final class ConfigReloadBus {

    @FunctionalInterface
    public interface Listener {
        /** @param modeId id режиму, конфіг якого щойно перезавантажено. */
        void onConfigReload(String modeId);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Listener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Listener listener) {
        listeners.remove(listener);
    }

    /** Викликається після того, як конфіг режиму фактично перечитано з диска. */
    public void fireReload(String modeId) {
        List<RuntimeException> errors = new ArrayList<>();
        for (Listener l : listeners) {
            try {
                l.onConfigReload(modeId);
            } catch (RuntimeException e) {
                // Одна помилка в одному модулі не повинна зривати reload
                // решти підписників — збираємо і кидаємо після проходу всіх.
                errors.add(e);
            }
        }
        if (!errors.isEmpty()) {
            RuntimeException combined = new RuntimeException(
                    "ConfigReloadBus: " + errors.size() + " listener(s) failed during reload of '" + modeId + "'");
            for (RuntimeException e : errors) combined.addSuppressed(e);
            throw combined;
        }
    }
}
