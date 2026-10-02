package dev.shaurmalib.forge.offline;

import dev.shaurmalib.common.lifecycle.MatchLifecycleBus;
import dev.shaurmalib.common.lifecycle.MatchLifecycleState;
import dev.shaurmalib.common.offline.OfflineDamagePolicy;
import dev.shaurmalib.common.offline.OfflineListener;
import dev.shaurmalib.common.offline.OfflineLootPolicy;
import dev.shaurmalib.common.offline.OfflineReturn;
import dev.shaurmalib.common.offline.OfflineSnapshotContributor;
import dev.shaurmalib.common.offline.OfflineSpec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Налаштування офлайн-присутності, яке режим передає в
 * {@code ShaurmaLib.Builder#withOfflinePresence(...)} (план, §5.1).
 * Бібліотека не знає про ваш режим: усе доменне (кого тримати, як приймати
 * урон, що дропати, що робити при поверненні) приходить звідси.
 */
public final class OfflinePresenceConfig {

    private final Supplier<EntityType<? extends OfflineAvatarBase>> entityType;
    private final Function<ServerPlayer, OfflineSpec> spawnWhen;
    private final OfflineDamagePolicy damagePolicy;
    private final OfflineLootPolicy lootPolicy;
    private final OfflineSnapshotContributor snapshotContributor;
    private final OfflineListener listener;
    private final BiConsumer<ServerPlayer, OfflineReturn> onReturn;
    private final MatchLifecycleBus lifecycleBus;
    private final Set<MatchLifecycleState> openIn;
    private final Set<MatchLifecycleState> closeIn;
    private final boolean spawnInCreative;
    private final boolean spawnInSpectator;
    private final boolean allowSingleplayer;
    private final String nameTagHideTeamId;

    private OfflinePresenceConfig(Builder b) {
        this.entityType = b.entityType;
        this.spawnWhen = b.spawnWhen;
        this.damagePolicy = b.damagePolicy;
        this.lootPolicy = b.lootPolicy;
        this.snapshotContributor = b.snapshotContributor;
        this.listener = b.listener;
        this.onReturn = b.onReturn;
        this.lifecycleBus = b.lifecycleBus;
        this.openIn = Set.copyOf(b.openIn);
        this.closeIn = Set.copyOf(b.closeIn);
        this.spawnInCreative = b.spawnInCreative;
        this.spawnInSpectator = b.spawnInSpectator;
        this.allowSingleplayer = b.allowSingleplayer;
        this.nameTagHideTeamId = b.nameTagHideTeamId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Supplier<EntityType<? extends OfflineAvatarBase>> entityType() {
        return entityType;
    }

    public Function<ServerPlayer, OfflineSpec> spawnWhen() {
        return spawnWhen;
    }

    public OfflineDamagePolicy damagePolicy() {
        return damagePolicy;
    }

    public OfflineLootPolicy lootPolicy() {
        return lootPolicy;
    }

    public OfflineSnapshotContributor snapshotContributor() {
        return snapshotContributor;
    }

    public OfflineListener listener() {
        return listener;
    }

    public BiConsumer<ServerPlayer, OfflineReturn> onReturn() {
        return onReturn;
    }

    /** {@code null}, якщо scope не прив'язано до lifecycle (режим відкриває/закриває його сам). */
    public MatchLifecycleBus lifecycleBus() {
        return lifecycleBus;
    }

    public Set<MatchLifecycleState> openIn() {
        return openIn;
    }

    public Set<MatchLifecycleState> closeIn() {
        return closeIn;
    }

    public boolean spawnInCreative() {
        return spawnInCreative;
    }

    public boolean spawnInSpectator() {
        return spawnInSpectator;
    }

    public boolean allowSingleplayer() {
        return allowSingleplayer;
    }

    /** id scoreboard-команди приховування ніків для власників без команди; {@code null} — не використовувати. */
    public String nameTagHideTeamId() {
        return nameTagHideTeamId;
    }

    public static final class Builder {
        private Supplier<EntityType<? extends OfflineAvatarBase>> entityType;
        private Function<ServerPlayer, OfflineSpec> spawnWhen;
        private OfflineDamagePolicy damagePolicy = OfflineDamagePolicy.vanillaLike();
        private OfflineLootPolicy lootPolicy = OfflineLootPolicy.standard();
        private OfflineSnapshotContributor snapshotContributor = OfflineSnapshotContributor.none();
        private OfflineListener listener = OfflineListener.none();
        private BiConsumer<ServerPlayer, OfflineReturn> onReturn = (player, result) -> { };
        private MatchLifecycleBus lifecycleBus;
        private Set<MatchLifecycleState> openIn = EnumSet.noneOf(MatchLifecycleState.class);
        private Set<MatchLifecycleState> closeIn = EnumSet.noneOf(MatchLifecycleState.class);
        private boolean spawnInCreative = false;
        private boolean spawnInSpectator = false;
        private boolean allowSingleplayer = false;
        private String nameTagHideTeamId;

        private Builder() {}

        /** Обов'язково. Режим реєструє тип сам (як з {@code SkinnableEntityBase}). */
        public Builder entityType(Supplier<EntityType<? extends OfflineAvatarBase>> entityType) {
            this.entityType = Objects.requireNonNull(entityType, "entityType");
            return this;
        }

        /** Обов'язково. Викликається на logout; {@link OfflineSpec#none()} — тіла не треба. */
        public Builder spawnWhen(Function<ServerPlayer, OfflineSpec> spawnWhen) {
            this.spawnWhen = Objects.requireNonNull(spawnWhen, "spawnWhen");
            return this;
        }

        public Builder damagePolicy(OfflineDamagePolicy policy) {
            this.damagePolicy = Objects.requireNonNull(policy, "damagePolicy");
            return this;
        }

        public Builder lootPolicy(OfflineLootPolicy policy) {
            this.lootPolicy = Objects.requireNonNull(policy, "lootPolicy");
            return this;
        }

        public Builder snapshotContributor(OfflineSnapshotContributor contributor) {
            this.snapshotContributor = Objects.requireNonNull(contributor, "snapshotContributor");
            return this;
        }

        public Builder listener(OfflineListener listener) {
            this.listener = Objects.requireNonNull(listener, "listener");
            return this;
        }

        /** Режим відновлює свій стан (заморозку, пози, синхронізацію) після повернення власника. */
        public Builder onReturn(BiConsumer<ServerPlayer, OfflineReturn> onReturn) {
            this.onReturn = Objects.requireNonNull(onReturn, "onReturn");
            return this;
        }

        /**
         * Автоматично відкриває scope, коли lifecycle входить в один зі станів {@code openIn},
         * і закриває (причина {@code MATCH_ENDED}), коли входить в один зі станів {@code closeIn}.
         * Якщо не викликати, режим сам викликає {@code openScope()}/{@code closeScope(...)}.
         */
        public Builder bindScopeToLifecycle(MatchLifecycleBus bus,
                                            Set<MatchLifecycleState> openIn,
                                            Set<MatchLifecycleState> closeIn) {
            this.lifecycleBus = Objects.requireNonNull(bus, "bus");
            this.openIn = openIn.isEmpty() ? EnumSet.noneOf(MatchLifecycleState.class) : EnumSet.copyOf(openIn);
            this.closeIn = closeIn.isEmpty() ? EnumSet.noneOf(MatchLifecycleState.class) : EnumSet.copyOf(closeIn);
            return this;
        }

        /** За замовчуванням {@code false}. */
        public Builder spawnInCreative(boolean value) {
            this.spawnInCreative = value;
            return this;
        }

        /** За замовчуванням {@code false}. */
        public Builder spawnInSpectator(boolean value) {
            this.spawnInSpectator = value;
            return this;
        }

        /** За замовчуванням {@code false}; {@code true} лише для тестів на одиночному світі. */
        public Builder allowSingleplayer(boolean value) {
            this.allowSingleplayer = value;
            return this;
        }

        /**
         * Команда приховування ніків для власників, що не перебувають в ігровій команді
         * (те саме, що {@code LobbyModule.hideNameTag}). Власник у команді: тіло йде в неї.
         */
        public Builder nameTagHideTeamId(String teamId) {
            this.nameTagHideTeamId = teamId;
            return this;
        }

        public OfflinePresenceConfig build() {
            if (entityType == null) {
                throw new IllegalStateException(
                        "OfflinePresenceConfig вимагає entityType(...): режим має зареєструвати EntityType тіла.");
            }
            if (spawnWhen == null) {
                throw new IllegalStateException(
                        "OfflinePresenceConfig вимагає spawnWhen(...): бібліотека не знає, коли гравцю потрібне тіло.");
            }
            for (MatchLifecycleState state : openIn) {
                if (closeIn.contains(state)) {
                    throw new IllegalStateException(
                            "Стан " + state + " одночасно в openIn і closeIn у bindScopeToLifecycle(...).");
                }
            }
            return new OfflinePresenceConfig(this);
        }
    }
}
