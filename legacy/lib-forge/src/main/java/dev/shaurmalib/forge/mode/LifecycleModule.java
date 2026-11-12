package dev.shaurmalib.forge.mode;

import dev.shaurmalib.common.lifecycle.IdleBehavior;
import dev.shaurmalib.common.lifecycle.MatchLifecycleBus;

/**
 * Робочий lifecycle-модуль (п. 3.19 плану) — {@link MatchLifecycleBus} +
 * {@link IdleBehavior}, доступні споживачу через
 * {@link dev.shaurmalib.forge.ShaurmaLib.Handle#lifecycleModule()}.
 */
public final class LifecycleModule {

    private final MatchLifecycleBus lifecycleBus = new MatchLifecycleBus();
    private final IdleBehavior idleBehavior;

    public LifecycleModule(int minPlayersToStart) {
        this.idleBehavior = new IdleBehavior(minPlayersToStart);
    }

    public MatchLifecycleBus lifecycleBus() {
        return lifecycleBus;
    }

    public IdleBehavior idleBehavior() {
        return idleBehavior;
    }
}