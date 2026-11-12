package dev.shaurmalib.common.lifecycle;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Центральна шина lifecycle-подій (план, п. 3.19). Формалізує те, що зараз
 * розмазано по {@code GameStateManager} + {@code PlayerJoinEventHandler} +
 * окремих {@code Phase}-класах snipers_shaurma — будь-який лібовий сервіс
 * (наприклад {@code ModeSettingsScreen} із п. 3.32, або майбутній
 * {@code MatchLifecycleRecorder} з п. 3.20) підписується один раз тут
 * замість того, щоб знати про кожен конкретний Phase-клас режиму.
 * <p>
 * Instance-based — один {@code MatchLifecycleBus} на споживача (через
 * {@code ShaurmaLib.Builder.withLifecycle(...)}).
 */
public final class MatchLifecycleBus {

    @FunctionalInterface
    public interface Listener {
        void onLifecycleChanged(MatchLifecycleState previous, MatchLifecycleState current);
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile MatchLifecycleState current = MatchLifecycleState.IDLE;

    public MatchLifecycleState current() {
        return current;
    }

    public boolean isIdle() {
        return current == MatchLifecycleState.IDLE;
    }

    public void subscribe(Listener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Викликається режимом-споживачем (типово з мапінгу власного
     * {@code GamePhase}/{@code IPhase.lifecycleState()}) при кожній зміні
     * фази. Якщо новий стан збігається з поточним — нічого не робить
     * (немає сенсу сповіщати підписників про "зміну", якої не було).
     */
    public void transitionTo(MatchLifecycleState next) {
        MatchLifecycleState prev = this.current;
        if (prev == next) return;
        this.current = next;
        for (Listener l : listeners) {
            l.onLifecycleChanged(prev, next);
        }
    }
}
