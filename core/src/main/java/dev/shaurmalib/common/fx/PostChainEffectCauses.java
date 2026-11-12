package dev.shaurmalib.common.fx;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Реєстр незалежних причин активації повноекранного post-chain ефекту
 * (план, п. 3.23) — узагальнення {@code causeCombatZone}/{@code causeDeathRise}
 * з {@code BlackAndWhiteScreenEffect} (два окремих boolean-прапорці, active
 * = один АБО інший) у довільну множину рядкових причин на кожен зареєстрований
 * {@code effectId} — той самий "reason key" підхід, що
 * {@link dev.shaurmalib.common.lock.InteractionLockRegistry} (план, п. 3.25),
 * узагальнений для клієнтського (не per-player, лише локальний клієнт)
 * випадку.
 * <p>
 * Ефект активний, доки лишається хоч одна причина; знімається лише коли
 * множина причин для цього {@code effectId} стає порожньою — дві незалежні
 * системи (напр. "вихід з бойової зони" і "фаза RISE смерті"), що активували
 * той самий ефект одночасно, не знімають одна одну передчасно.
 */
public final class PostChainEffectCauses {

    private PostChainEffectCauses() {}

    private static final Map<String, Set<String>> causesByEffect = new ConcurrentHashMap<>();

    /** Додає причину активації. Ідемпотентно — повторний виклик з тим самим {@code reason} нічого не змінює. */
    public static void activate(String effectId, String reason) {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(reason, "reason");
        causesByEffect.computeIfAbsent(effectId, id -> new CopyOnWriteArraySet<>()).add(reason);
    }

    /** Прибирає конкретну причину. Ефект лишається активним, якщо є інші активні причини. */
    public static void deactivate(String effectId, String reason) {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(reason, "reason");
        Set<String> reasons = causesByEffect.get(effectId);
        if (reasons == null) return;
        reasons.remove(reason);
        if (reasons.isEmpty()) {
            causesByEffect.remove(effectId);
        }
    }

    /** {@code true}, якщо ефект зараз має бути активний (хоч би одна причина активна). */
    public static boolean isActive(String effectId) {
        Set<String> reasons = causesByEffect.get(effectId);
        return reasons != null && !reasons.isEmpty();
    }

    /**
     * Аварійне повне скидання всіх причин для конкретного ефекту —
     * консюмер викликає це при виході з гри/респавні/дисконнекті (той
     * самий момент, коли оригінал викликав {@code resetAllCauses()}).
     */
    public static void clearAll(String effectId) {
        causesByEffect.remove(effectId);
    }
}
