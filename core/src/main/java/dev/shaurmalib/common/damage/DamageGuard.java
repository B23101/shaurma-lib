package dev.shaurmalib.common.damage;

import dev.shaurmalib.platform.event.EventBus;
import dev.shaurmalib.platform.event.PlatformEvents.PlayerDamage;
import dev.shaurmalib.platform.event.Priority;

/**
 * Єдиний міст між подією шкоди лоадера та {@link DamageInterceptorRegistry}.
 * Раніше це дублювалось у Forge-хуках (DamageGuardHooks); тепер лоадер лише постить
 * {@link PlayerDamage}, а рішення приймає ця чиста логіка.
 */
public final class DamageGuard {
    private DamageGuard() {}

    public static EventBus.Subscription install(EventBus bus) {
        return bus.subscribe(PlayerDamage.class, Priority.HIGHEST, e -> {
            DamageContext ctx = new DamageContext(DamageContext.SourceKind.VANILLA,
                    e.source().typeId(), e.amount(), e.source().projectile());
            if (DamageInterceptorRegistry.isBlocked(e.victim(), e.source(), e.amount(), ctx)) e.cancel();
        });
    }
}
