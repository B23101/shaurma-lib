package dev.shaurmalib.common.lifecycle;

/**
 * Що робити, поки {@link MatchLifecycleState#IDLE} і немає активного матчу
 * (план, п. 3.19) — конфігурований поріг мінімальної кількості гравців для
 * старту, керується через {@code ShaurmaLib.Builder.withLifecycle(int)}.
 */
public final class IdleBehavior {

    private final int minPlayersToStart;

    public IdleBehavior(int minPlayersToStart) {
        if (minPlayersToStart < 1) {
            throw new IllegalArgumentException("minPlayersToStart має бути >= 1, отримано: " + minPlayersToStart);
        }
        this.minPlayersToStart = minPlayersToStart;
    }

    public int minPlayersToStart() {
        return minPlayersToStart;
    }

    public boolean canStart(int onlinePlayers) {
        return onlinePlayers >= minPlayersToStart;
    }
}
