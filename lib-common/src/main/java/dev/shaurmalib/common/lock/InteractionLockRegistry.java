package dev.shaurmalib.common.lock;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Центральний реєстр незалежних "замків" на дії гравця (план, п. 3.25).
 * <p>
 * Ключова відмінність від оригінального коду snipers_shaurma: там кожен
 * {@code Phase}-клас блокував рух/взаємодію власною, окремою перевіркою
 * (наприклад {@code CountdownPhase} і {@code KitSelectPhase} кожен
 * по-своєму вирішував "заморозити гравця чи ні"), тому дві системи, що
 * заблокували одну й ту саму дію одночасно з різних причин, могли
 * випадково розлочити одна одну передчасно, якщо обидві написані без
 * координації.
 * <p>
 * Тут кожна причина блокування — рядок-ключ ({@code lockReason}, як і
 * описано в плані — "ключ причини"): {@link #lock(UUID, LockType, String)}
 * додає причину до множини активних причин для цього типу локу, а
 * {@link #unlock(UUID, LockType, String)} прибирає ЛИШЕ цю причину.
 * {@link #isLocked(UUID, LockType)} повертає {@code true}, доки лишається
 * хоч одна активна причина — тип розлоковується лише коли множина причин
 * порожня. Це дозволяє, наприклад, одночасно тримати {@code MOVEMENT}
 * заблокованим і через {@code "kit_select_phase"}, і через
 * {@code "round_intro_camera"} — зняття однієї не вплине на іншу.
 * <p>
 * Статичний реєстр без бізнес-логіки "коли" лочити — консюмер (Forge-хуки
 * бібліотеки або сам мод) сам викликає {@code lock}/{@code unlock} у
 * потрібні моменти; сам реєстр лише зберігає стан і відповідає на
 * запити {@code isLocked}.
 */
public final class InteractionLockRegistry {

    private InteractionLockRegistry() {}

    // playerId -> (LockType -> set активних причин)
    private static final Map<UUID, Map<LockType, Set<String>>> locks = new ConcurrentHashMap<>();

    /**
     * Додає причину блокування для типу дії. Ідемпотентно — повторний
     * виклик з тим самим {@code reason} нічого не змінює.
     */
    public static void lock(UUID playerId, LockType type, String reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        locks.computeIfAbsent(playerId, id -> new EnumMap<>(LockType.class))
                .computeIfAbsent(type, t -> new CopyOnWriteArraySet<>())
                .add(reason);
    }

    /**
     * Прибирає конкретну причину блокування. Тип лишається заблокованим,
     * якщо є інші активні причини — розлочується лише коли множина причин
     * для цього типу стає порожньою.
     */
    public static void unlock(UUID playerId, LockType type, String reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        Map<LockType, Set<String>> byType = locks.get(playerId);
        if (byType == null) return;
        Set<String> reasons = byType.get(type);
        if (reasons == null) return;
        reasons.remove(reason);
        if (reasons.isEmpty()) {
            byType.remove(type);
        }
        if (byType.isEmpty()) {
            locks.remove(playerId);
        }
    }

    /** Чи заблокований цей тип дії для гравця (хоч би однією причиною). */
    public static boolean isLocked(UUID playerId, LockType type) {
        Map<LockType, Set<String>> byType = locks.get(playerId);
        if (byType == null) return false;
        Set<String> reasons = byType.get(type);
        return reasons != null && !reasons.isEmpty();
    }

    /**
     * Прибирає ВСІ причини блокування для гравця і для всіх типів —
     * викликати при виході гравця з сервера (щоб замки не лишились
     * "висіти" в реєстрі для UUID, який вже не в грі) або при примусовому
     * скиданні стану (round reset).
     */
    public static void clearAll(UUID playerId) {
        locks.remove(playerId);
    }

    /**
     * Прибирає конкретну причину для гравця для ВСІХ типів одразу —
     * зручно, коли одна фаза блокувала кілька типів дій під одним
     * {@code reason} (типовий випадок: kit-select блокує і рух, і атаку,
     * і use-item одним ключем причини).
     */
    public static void unlockAll(UUID playerId, String reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reason, "reason");
        Map<LockType, Set<String>> byType = locks.get(playerId);
        if (byType == null) return;
        for (LockType type : LockType.values()) {
            Set<String> reasons = byType.get(type);
            if (reasons != null) {
                reasons.remove(reason);
                if (reasons.isEmpty()) {
                    byType.remove(type);
                }
            }
        }
        if (byType.isEmpty()) {
            locks.remove(playerId);
        }
    }
}
