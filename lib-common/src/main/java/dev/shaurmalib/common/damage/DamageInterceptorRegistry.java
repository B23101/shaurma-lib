package dev.shaurmalib.common.damage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Центральний реєстр {@link DamageInterceptor}-ів (план, п. 3.26) —
 * статичний, без стану ініціалізації (як {@code TeleportService}/
 * {@code InteractionLockRegistry}), доступний одразу, консюмер лише
 * реєструє/знімає свої причини блокування за ключем.
 * <p>
 * Кожен interceptor реєструється під власним {@code id} (напр.
 * {@code "sapper_passive"}, {@code "scn_dome_shield"},
 * {@code "kit_select_invuln"}) — це дозволяє зняти рівно одну причину
 * ({@link #unregister}), не зачепивши решту, на відміну від голого
 * списку лямбд без імені. Порядок реєстрації не впливає на результат:
 * {@link #isBlocked} повертає {@code true}, щойно БОДАЙ ОДИН
 * зареєстрований interceptor підтвердить блокування — це навмисно "OR"
 * логіка, а не пріоритетний ланцюжок з можливістю однієї причини
 * "скасувати" висновок іншої (кожна причина незалежна, як і
 * {@code LockType}-причини в {@code InteractionLockRegistry}).
 */
public final class DamageInterceptorRegistry {

    private DamageInterceptorRegistry() {}

    private static final Map<String, DamageInterceptor> interceptors = new ConcurrentHashMap<>();

    /** Правила за UUID жертви: діють і на онлайн-гравців, і на тіла офлайн-гравців. */
    private static final Map<String, OwnerDamageInterceptor> ownerRules = new ConcurrentHashMap<>();

    /** Реєструє (або замінює за тим самим {@code id}) інтерцептор блокування урону. */
    public static void register(String id, DamageInterceptor interceptor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(interceptor, "interceptor");
        interceptors.put(id, interceptor);
    }

    /**
     * Реєструє правило за UUID жертви (план, §5.14). Воно перевіряється і в
     * {@link #isBlocked(ServerPlayer, DamageSource, float, DamageContext)} (для
     * онлайн-гравця за його UUID), і в
     * {@link #isBlockedForOwner(UUID, DamageSource, float, DamageContext)} (для
     * тіла офлайн-гравця). Той самий {@code id} замінює попереднє правило.
     */
    public static void registerOwnerRule(String id, OwnerDamageInterceptor rule) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rule, "rule");
        ownerRules.put(id, rule);
    }

    /** Знімає раніше зареєстрований інтерцептор або правило за {@code id}. Немає ефекту, якщо такого id не було. */
    public static void unregister(String id) {
        interceptors.remove(id);
        ownerRules.remove(id);
    }

    /**
     * @return {@code true}, якщо хоча б один зареєстрований interceptor
     * стверджує, що урон має бути заблокований за цих умов.
     */
    public static boolean isBlocked(ServerPlayer victim, DamageSource source, float amount, DamageContext ctx) {
        if (interceptors.isEmpty() && ownerRules.isEmpty()) return false;
        // Знімок у LinkedHashMap для стабільного порядку ітерації в межах одного виклику,
        // навіть якщо інший потік паралельно реєструє/знімає interceptor — без цього
        // ConcurrentHashMap.values() теж безпечний для ітерації, копія лише для детермінізму логів.
        for (Map.Entry<String, DamageInterceptor> entry : new LinkedHashMap<>(interceptors).entrySet()) {
            if (entry.getValue().shouldBlockDamage(victim, source, amount, ctx)) {
                return true;
            }
        }
        return isBlockedForOwner(victim.getUUID(), source, amount, ctx);
    }

    /**
     * Те саме для жертви, якої немає як {@code ServerPlayer} — тіла офлайн-гравця
     * (план, §5.14). Перевіряються лише правила, зареєстровані через
     * {@link #registerOwnerRule}: {@link DamageInterceptor}-и потребують
     * {@code ServerPlayer} і до тіла не застосовуються.
     */
    public static boolean isBlockedForOwner(UUID victimOwner, DamageSource source, float amount, DamageContext ctx) {
        if (ownerRules.isEmpty()) return false;
        for (Map.Entry<String, OwnerDamageInterceptor> entry : new LinkedHashMap<>(ownerRules).entrySet()) {
            if (entry.getValue().shouldBlockDamage(victimOwner, source, amount, ctx)) {
                return true;
            }
        }
        return false;
    }

    /** Прибирає всі зареєстровані interceptor-и — типово при вимкненні мода/юніт-тестах. */
    public static void clear() {
        interceptors.clear();
        ownerRules.clear();
    }
}
