package dev.shaurmalib.common.damage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Власна умова блокування урону гравцю (план, п. 3.26 — "блокування
 * урону не через ванільний метод а власний"). Узагальнення того, що в
 * оригінальному коді snipers_shaurma вже реалізовано так само (не
 * єдиний {@code Entity.isInvulnerableTo}, а {@code LivingHurtEvent} з
 * {@code EventPriority.HIGH} і кастомним {@code setCanceled(true)}
 * залежно від контексту — {@code PlayerDamageEventHandler},
 * {@code AttackPreventionHandler}, {@code PigProtectionHandler},
 * {@code SapperPassiveHandler}, {@code SCNDomeShieldGuard},
 * {@code BodyDamageEventHandler}), тепер як один переносний контракт
 * замість окремого {@code @Mod.EventBusSubscriber}-класу на кожну нову
 * причину недоторканості.
 * <p>
 * Кілька незалежних {@link DamageInterceptor} можуть співіснувати
 * одночасно (щит-механіка, kit-select недоторканість, respawn-invuln —
 * кожна причина свій окремий interceptor, а не один величезний
 * if-else) — дивись {@link DamageInterceptorRegistry}. Реєстр звертає
 * увагу лише на те, чи БОДАЙ ОДИН зареєстрований interceptor повернув
 * {@code true} — інтерцептори не "скасовують" один одного, кожен лише
 * стверджує чи заперечує блокування для свого власного випадку.
 */
@FunctionalInterface
public interface DamageInterceptor {

    /**
     * @return {@code true}, якщо саме ЦЕЙ interceptor вважає, що урон
     * має бути заблокований за цих умов. {@code false} не означає
     * "дозволити урон" — лише "ця конкретна причина не застосовується
     * зараз"; фінальне рішення приймає {@link DamageInterceptorRegistry}
     * на основі ВСІХ зареєстрованих interceptor-ів.
     */
    boolean shouldBlockDamage(ServerPlayer victim, DamageSource source, float amount, DamageContext ctx);
}
