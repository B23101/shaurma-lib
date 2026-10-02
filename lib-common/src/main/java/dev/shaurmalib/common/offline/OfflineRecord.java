package dev.shaurmalib.common.offline;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Дані одного офлайн-гравця (план, §4.4). Дублює позицію тіла, щоб
 * працювати, коли сутність не завантажена.
 * <p>
 * Змінювана частина закрита: стан і позицію змінює лише
 * {@link OfflineRegistry}. Назовні — тільки геттери.
 */
public final class OfflineRecord {

    private final UUID owner;
    private final String ownerName;
    private final OfflineScopeId scope;
    private final long loggedOutTick;
    private final OfflineSpec spec;

    private UUID avatarEntity;
    private String dimensionKey;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private OfflineStatus status = OfflineStatus.STANDING;
    private OfflineStatus statusAtReturn;
    private float health;

    public OfflineRecord(UUID owner, String ownerName, OfflineScopeId scope, UUID avatarEntity,
                         String dimensionKey, double x, double y, double z, float yaw, float pitch,
                         long loggedOutTick, OfflineSpec spec) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.ownerName = ownerName == null ? "" : ownerName;
        this.scope = Objects.requireNonNull(scope, "scope");
        this.avatarEntity = Objects.requireNonNull(avatarEntity, "avatarEntity");
        this.dimensionKey = Objects.requireNonNull(dimensionKey, "dimensionKey");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.loggedOutTick = loggedOutTick;
        this.spec = Objects.requireNonNull(spec, "spec");
        this.health = spec.initialHealth();
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public OfflineScopeId scope() {
        return scope;
    }

    public UUID avatarEntity() {
        return avatarEntity;
    }

    public String dimensionKey() {
        return dimensionKey;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public long loggedOutTick() {
        return loggedOutTick;
    }

    public OfflineStatus status() {
        return status;
    }

    public OfflineSpec spec() {
        return spec;
    }

    /** Поточне HP запису (використовує лише {@link OfflineDamagePolicy#vanillaLike()}). */
    public float health() {
        return health;
    }

    /** Стан на момент повернення власника. Порожній, доки {@link OfflineRegistry#consumeReturn} не викликано. */
    public Optional<OfflineStatus> statusAtReturn() {
        return Optional.ofNullable(statusAtReturn);
    }

    // ── лише для OfflineRegistry / OfflineDamagePolicy (той самий пакет) ──

    void setStatus(OfflineStatus next) {
        this.status = next;
    }

    void setStatusAtReturn(OfflineStatus value) {
        this.statusAtReturn = value;
    }

    void setAvatarEntity(UUID entity) {
        this.avatarEntity = Objects.requireNonNull(entity, "entity");
    }

    void setPosition(String dimensionKey, double x, double y, double z, float yaw, float pitch) {
        this.dimensionKey = Objects.requireNonNull(dimensionKey, "dimensionKey");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** @return HP після удару (не менше 0). */
    float applyDamage(float amount) {
        this.health = Math.max(0.0f, this.health - Math.max(0.0f, amount));
        return this.health;
    }
}
