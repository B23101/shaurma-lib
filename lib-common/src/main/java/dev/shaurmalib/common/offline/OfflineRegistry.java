package dev.shaurmalib.common.offline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Єдиний детермінований автомат станів офлайн-присутності (план, §4.5).
 * Правда живе тут, а сутність у світі — лише фізичне тіло.
 * <p>
 * <b>Потоки:</b> усе викликається лише з потоку сервера, синхронізації немає.
 * <p>
 * Не містить жодного Minecraft/Forge-коду, тому перевіряється юніт-тестами.
 */
public final class OfflineRegistry {

    private final Map<UUID, OfflineRecord> byOwner = new LinkedHashMap<>();
    private final Map<UUID, UUID> avatarToOwner = new HashMap<>();
    private OfflineScopeId activeScope;
    private OfflineCloseReason lastCloseReason;

    // ── scope ─────────────────────────────────────────────────────────────

    /** Відкриває scope. Кидає {@link IllegalStateException}, якщо інший scope уже відкрито. */
    public void openScope(OfflineScopeId scope) {
        Objects.requireNonNull(scope, "scope");
        if (activeScope != null) {
            throw new IllegalStateException("Scope уже відкрито: " + activeScope);
        }
        activeScope = scope;
    }

    public Optional<OfflineScopeId> activeScope() {
        return Optional.ofNullable(activeScope);
    }

    public boolean isScopeOpen() {
        return activeScope != null;
    }

    public Optional<OfflineCloseReason> lastCloseReason() {
        return Optional.ofNullable(lastCloseReason);
    }

    /**
     * Усі не завершені записи → {@link OfflineStatus#CLOSED}. Повертає їх, щоб
     * викликач зніс сутності. Після цього {@link #isScopeOpen()} = {@code false}.
     * Якщо scope не було відкрито, повертає порожній список.
     */
    public List<OfflineRecord> closeScope(OfflineCloseReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (activeScope == null) {
            return List.of();
        }
        List<OfflineRecord> closed = new ArrayList<>(byOwner.values());
        for (OfflineRecord record : closed) {
            record.setStatus(OfflineStatus.CLOSED);
        }
        byOwner.clear();
        avatarToOwner.clear();
        activeScope = null;
        lastCloseReason = reason;
        return closed;
    }

    // ── записи ────────────────────────────────────────────────────────────

    /**
     * Реєструє офлайн-гравця. Порожній результат, якщо scope закрито, токен
     * запису не збігається з активним scope, або запис для цього власника
     * вже існує.
     */
    public Optional<OfflineRecord> register(OfflineRecord record) {
        Objects.requireNonNull(record, "record");
        if (activeScope == null || !activeScope.equals(record.scope())) {
            return Optional.empty();
        }
        if (byOwner.containsKey(record.owner()) || avatarToOwner.containsKey(record.avatarEntity())) {
            return Optional.empty();
        }
        byOwner.put(record.owner(), record);
        avatarToOwner.put(record.avatarEntity(), record.owner());
        return Optional.of(record);
    }

    public Optional<OfflineRecord> get(UUID owner) {
        return Optional.ofNullable(byOwner.get(owner));
    }

    public Optional<OfflineRecord> byAvatar(UUID entity) {
        UUID owner = avatarToOwner.get(entity);
        return owner == null ? Optional.empty() : get(owner);
    }

    /** Знімок усіх активних записів (безпечно змінювати реєстр під час обходу). */
    public Collection<OfflineRecord> all() {
        return Collections.unmodifiableList(new ArrayList<>(byOwner.values()));
    }

    /** {@code true}, якщо для власника є запис (тіло стоїть або вбите, але власник ще не повернувся). */
    public boolean isOffline(UUID owner) {
        return byOwner.containsKey(owner);
    }

    /** {@code true}, лише якщо тіло власника стоїть живим. */
    public boolean isStanding(UUID owner) {
        OfflineRecord record = byOwner.get(owner);
        return record != null && record.status() == OfflineStatus.STANDING;
    }

    /** Скільки записів у стані {@link OfflineStatus#STANDING} задовольняють фільтр за UUID власника. */
    public int standingCount(Predicate<UUID> filter) {
        Objects.requireNonNull(filter, "filter");
        int count = 0;
        for (OfflineRecord record : byOwner.values()) {
            if (record.status() == OfflineStatus.STANDING && filter.test(record.owner())) {
                count++;
            }
        }
        return count;
    }

    // ── переходи ──────────────────────────────────────────────────────────

    /**
     * {@link OfflineStatus#STANDING} → {@link OfflineStatus#KILLED}.
     * Повертає {@code true} лише першому викликові: саме це гарантує «лут випадає один раз».
     */
    public boolean markKilled(UUID owner) {
        return transition(owner, OfflineStatus.KILLED);
    }

    /** {@link OfflineStatus#STANDING} → {@link OfflineStatus#EXPIRED}. Так само атомарно. */
    public boolean markExpired(UUID owner) {
        return transition(owner, OfflineStatus.EXPIRED);
    }

    private boolean transition(UUID owner, OfflineStatus next) {
        OfflineRecord record = byOwner.get(owner);
        if (record == null || record.status() != OfflineStatus.STANDING) {
            return false;
        }
        record.setStatus(next);
        return true;
    }

    /**
     * Видає запис при вході власника й переводить його в {@link OfflineStatus#RETURNED}
     * (повторно не видасть: запис одразу видаляється). Стан до переходу доступний
     * через {@link OfflineRecord#statusAtReturn()}.
     */
    public Optional<OfflineRecord> consumeReturn(UUID owner) {
        OfflineRecord record = byOwner.remove(owner);
        if (record == null) {
            return Optional.empty();
        }
        avatarToOwner.remove(record.avatarEntity());
        record.setStatusAtReturn(record.status());
        record.setStatus(OfflineStatus.RETURNED);
        return Optional.of(record);
    }

    /**
     * Прибирає запис одного власника без подій (→ {@link OfflineStatus#CLOSED}).
     * Для ручного {@code discardSilently} і заміни запису при повторному виході.
     */
    public Optional<OfflineRecord> discard(UUID owner) {
        OfflineRecord record = byOwner.remove(owner);
        if (record == null) {
            return Optional.empty();
        }
        avatarToOwner.remove(record.avatarEntity());
        record.setStatus(OfflineStatus.CLOSED);
        return Optional.of(record);
    }

    /** Підміняє сутність запису (респавн тіла з знімка). {@code false}, якщо запису немає або UUID зайнятий. */
    public boolean updateAvatar(UUID owner, UUID newEntity) {
        OfflineRecord record = byOwner.get(owner);
        if (record == null) {
            return false;
        }
        UUID taken = avatarToOwner.get(newEntity);
        if (taken != null && !taken.equals(owner)) {
            return false;
        }
        avatarToOwner.remove(record.avatarEntity());
        record.setAvatarEntity(newEntity);
        avatarToOwner.put(newEntity, owner);
        return true;
    }

    /** Оновлює позицію запису (тіло впало/відкинуте). {@code false}, якщо запису немає. */
    public boolean updatePosition(UUID owner, String dimensionKey,
                                  double x, double y, double z, float yaw, float pitch) {
        OfflineRecord record = byOwner.get(owner);
        if (record == null) {
            return false;
        }
        record.setPosition(dimensionKey, x, y, z, yaw, pitch);
        return true;
    }
}
