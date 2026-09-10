package dev.shaurmalib.common.damage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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

    /** Реєструє (або замінює за тим самим {@code id}) інтерцептор блокування урону. */
    public static void register(String id, DamageInterceptor interceptor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(interceptor, "interceptor");
        interceptors.put(id, interceptor);
    }

    /** Знімає раніше зареєстрований інтерцептор за {@code id}. Немає ефекту, якщо такого id не було. */
    public static void unregister(String id) {
        interceptors.remove(id);
    }

    /**
     * @return {@code true}, якщо хоча б один зареєстрований interceptor
     * стверджує, що урон має бути заблокований за цих умов.
     */
    public static boolean isBlocked(ServerPlayer victim, DamageSource source, float amount, DamageContext ctx) {
        if (interceptors.isEmpty()) return false;
        // Знімок у LinkedHashMap для стабільного порядку ітерації в межах одного виклику,
        // навіть якщо інший потік паралельно реєструє/знімає interceptor — без цього
        // ConcurrentHashMap.values() теж безпечний для ітерації, копія лише для детермінізму логів.
        for (Map.Entry<String, DamageInterceptor> entry : new LinkedHashMap<>(interceptors).entrySet()) {
            if (entry.getValue().shouldBlockDamage(victim, source, amount, ctx)) {
                return true;
            }
        }
        return false;
    }

    /** Прибирає всі зареєстровані interceptor-и — типово при вимкненні мода/юніт-тестах. */
    public static void clear() {
        interceptors.clear();
    }
}
