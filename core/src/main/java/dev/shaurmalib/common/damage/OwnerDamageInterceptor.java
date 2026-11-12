package dev.shaurmalib.common.damage;

import dev.shaurmalib.platform.ref.DamageRef;

import java.util.UUID;

/**
 * Правило блокування урону за UUID жертви (план, §5.14). На відміну від
 * {@link DamageInterceptor} (приймає {@code PlayerRef}), воно однаково
 * застосовується і до онлайн-гравця, і до тіла офлайн-гравця: одне правило,
 * два види жертв. Реєструється через
 * {@link DamageInterceptorRegistry#registerOwnerRule}.
 * <p>
 * Наявні {@link DamageInterceptor}-и режиму лишаються як були й діють лише на
 * онлайн-гравців; ті, що мають діяти й на тіло, додатково реєструються в цій формі.
 */
@FunctionalInterface
public interface OwnerDamageInterceptor {

    /** @return {@code true}, якщо саме це правило вважає, що урон має бути заблокований. */
    boolean shouldBlockDamage(UUID victimOwner, DamageRef source, float amount, DamageContext ctx);
}
