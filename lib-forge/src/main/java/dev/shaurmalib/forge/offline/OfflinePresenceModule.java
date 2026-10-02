package dev.shaurmalib.forge.offline;

import dev.shaurmalib.common.damage.DamageContext;
import dev.shaurmalib.common.lifecycle.MatchLifecycleBus;
import dev.shaurmalib.common.lifecycle.MatchLifecycleState;
import dev.shaurmalib.common.offline.OfflineCloseReason;
import dev.shaurmalib.common.offline.OfflineHitResult;
import dev.shaurmalib.common.offline.OfflineRecord;
import dev.shaurmalib.common.offline.OfflineRegistry;
import dev.shaurmalib.common.offline.OfflineReturn;
import dev.shaurmalib.common.offline.OfflineScopeId;
import dev.shaurmalib.common.offline.OfflineSpec;
import dev.shaurmalib.common.offline.OfflineStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Публічний фасад системи «офлайн-присутності» (план, §5.2): залишає фізичне
 * тіло гравця у світі, коли той виходить із сервера під час матчу, і повертає
 * гравця в це тіло при вході. Доступний через
 * {@code ShaurmaLib.Handle#offlinePresenceModule()}.
 * <p>
 * <b>Принципи.</b> Правда живе в {@link OfflineRegistry}, сутність — лише тіло.
 * Поки scope не відкрито, тіла не створюються; коли закривається (кінець матчу,
 * деактивація режиму, зупинка сервера) — усі тіла зникають безшумно: без дропу,
 * без спавну, без подій «вбито». Бібліотека не знає про ваш режим: усе доменне
 * приходить через {@link OfflinePresenceConfig}.
 * <p>
 * <b>Потоки:</b> лише потік сервера. Кожен колбек режиму обгорнуто в {@code try/catch}
 * з логом, щоб виняток режиму не залишав тіла й тікети в напівстані.
 */
public final class OfflinePresenceModule {

    private static final Logger LOGGER = LogManager.getLogger("ShaurmaLib/OfflinePresence");

    /** Як часто (тіки) модуль перевіряє ліміт відсутності та розсинхрон «запис є, сутності немає». */
    private static final int CHECK_INTERVAL = 20;

    private static final List<OfflinePresenceModule> LIVE = new CopyOnWriteArrayList<>();

    private final String consumerModId;
    private final OfflinePresenceConfig config;
    private final OfflineRegistry registry = new OfflineRegistry();
    private final OfflineChunkAnchor anchors = new OfflineChunkAnchor();
    /** Знімки стану власників (джерело лута й респавну тіла). Лише пам'ять, як і реєстр. */
    private final Map<UUID, OfflineAvatarSnapshot> snapshots = new HashMap<>();
    /** Повернення, які режим ще не забрав через {@link #takeReturn}. */
    private final Map<UUID, OfflineReturn> pendingReturns = new HashMap<>();
    /** Власники, чиє тіло не знайшли в попередню перевірку: респавн лише після другого промаху. */
    private final Set<UUID> missingOnce = new HashSet<>();

    private EntityType<? extends OfflineAvatarBase> resolvedType;
    private OfflinePresenceHooks hooks;
    private MatchLifecycleBus.Listener lifecycleListener;
    private boolean shuttingDown;

    public OfflinePresenceModule(String consumerModId, OfflinePresenceConfig config) {
        this.consumerModId = consumerModId;
        this.config = config;
    }

    // ── Підключення до Forge (викликає ShaurmaLib.Builder#build) ──────────

    /**
     * Підписує хуки на {@code MinecraftForge.EVENT_BUS} і, за потреби, scope на lifecycle.
     * Ідемпотентний. Знімається в {@link #detach()} на {@code ServerStoppedEvent}.
     */
    public void attach() {
        if (hooks != null) {
            return;
        }
        LIVE.add(this);
        hooks = new OfflinePresenceHooks(this);
        MinecraftForge.EVENT_BUS.register(hooks);

        MatchLifecycleBus bus = config.lifecycleBus();
        if (bus != null) {
            lifecycleListener = (previous, current) -> onLifecycleChanged(current);
            bus.subscribe(lifecycleListener);
            onLifecycleChanged(bus.current());
        }
        LOGGER.info("[{}] Офлайн-присутність підключено", consumerModId);
    }

    void detach() {
        if (hooks == null) {
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(hooks);
        hooks = null;
        MatchLifecycleBus bus = config.lifecycleBus();
        if (bus != null && lifecycleListener != null) {
            bus.unsubscribe(lifecycleListener);
        }
        lifecycleListener = null;
        LIVE.remove(this);
    }

    private void onLifecycleChanged(MatchLifecycleState state) {
        if (config.closeIn().contains(state)) {
            closeScope(OfflineCloseReason.MATCH_ENDED);
        } else if (config.openIn().contains(state)) {
            openScope();
        }
    }

    // ── Статичний пошук модуля ─────────────────────────────────────────────

    /** Модуль, якому належить тип цього тіла, або {@code null} (тоді тіло — осиротіле). */
    @Nullable
    static OfflinePresenceModule forAvatar(Entity avatar) {
        for (OfflinePresenceModule module : LIVE) {
            EntityType<? extends OfflineAvatarBase> type = module.avatarType();
            if (type != null && type == avatar.getType()) {
                return module;
            }
        }
        return null;
    }

    /** Усі підключені модулі (зазвичай один). */
    static List<OfflinePresenceModule> live() {
        return LIVE;
    }

    /** Перший підключений модуль, або {@code null}. Для налагоджувальних команд. */
    @Nullable
    public static OfflinePresenceModule current() {
        return LIVE.isEmpty() ? null : LIVE.get(0);
    }

    @Nullable
    private EntityType<? extends OfflineAvatarBase> avatarType() {
        if (resolvedType == null) {
            try {
                resolvedType = config.entityType().get();
            } catch (RuntimeException e) {
                return null;
            }
        }
        return resolvedType;
    }

    // ── Scope ──────────────────────────────────────────────────────────────

    /** Відкриває scope (ідемпотентно: якщо відкрито, повертає поточний токен). */
    public OfflineScopeId openScope() {
        Optional<OfflineScopeId> active = registry.activeScope();
        if (active.isPresent()) {
            return active.get();
        }
        OfflineScopeId id = OfflineScopeId.newId();
        registry.openScope(id);
        pendingReturns.clear();
        return id;
    }

    /**
     * Закриває scope: усі тіла зникають безшумно (без дропу й без {@code onAvatarKilled}),
     * чанки відпускаються.
     *
     * @return скільки записів закрито (0, якщо scope не було відкрито).
     */
    public int closeScope(OfflineCloseReason reason) {
        if (!registry.isScopeOpen()) {
            return 0;
        }
        MinecraftServer server = server();
        List<OfflineRecord> closed = registry.closeScope(reason);
        for (OfflineRecord record : closed) {
            safe("closeScope/removeBody", () -> removeBodySilently(server, record));
        }
        snapshots.clear();
        pendingReturns.clear();
        missingOnce.clear();
        if (server != null) {
            anchors.releaseAll(server);
            OfflineAvatarSweeper.sweepLoaded(server);
        }
        int removed = closed.size();
        safe("listener.onScopeClosed", () -> config.listener().onScopeClosed(reason, removed));
        return removed;
    }

    public boolean isScopeOpen() {
        return registry.isScopeOpen();
    }

    // ── Запити ─────────────────────────────────────────────────────────────

    /** Власник офлайн: тіло стоїть або вбите, але власник ще не повернувся. */
    public boolean isOffline(UUID owner) {
        return registry.isOffline(owner);
    }

    /** Тіло власника стоїть живим (вбите тіло — {@code false}: з матчу гравця вже виключено). */
    public boolean hasAvatar(UUID owner) {
        return registry.isStanding(owner);
    }

    public Optional<OfflineRecord> record(UUID owner) {
        return registry.get(owner);
    }

    public Collection<OfflineRecord> records() {
        return registry.all();
    }

    /** Власники, чиї тіла стоять живими. */
    public Set<UUID> standingOwners() {
        Set<UUID> out = new LinkedHashSet<>();
        for (OfflineRecord record : registry.all()) {
            if (record.status() == OfflineStatus.STANDING) {
                out.add(record.owner());
            }
        }
        return out;
    }

    public int standingCount(Predicate<UUID> filter) {
        return registry.standingCount(filter);
    }

    /** Тіло власника (порожньо, якщо чанк не завантажено; чанк тримається, тож майже завжди є). */
    public Optional<OfflineAvatarBase> avatar(UUID owner) {
        MinecraftServer server = server();
        if (server == null) {
            return Optional.empty();
        }
        return registry.get(owner).map(r -> findAvatar(server, r));
    }

    /**
     * Дозволяє режимові в його власному login-обробнику (NORMAL, після нашого HIGHEST)
     * дізнатися, чи це повернення, а не новий гравець. Віддає результат один раз.
     */
    public Optional<OfflineReturn> takeReturn(UUID owner) {
        return Optional.ofNullable(pendingReturns.remove(owner));
    }

    // ── Ручні дії ──────────────────────────────────────────────────────────

    /**
     * Вбиває тіло власника як «вбите»: лут випадає за політикою, режим отримує
     * {@code onAvatarKilled}. Рівно один раз на власника.
     *
     * @return {@code false}, якщо тіла немає або воно вже вбите.
     */
    public boolean kill(UUID owner, @Nullable Entity killer) {
        Optional<OfflineRecord> record = registry.get(owner);
        return record.isPresent() && retire(record.get(), true, killer);
    }

    /** Прибирає тіло без дропу й без подій. */
    public boolean discardSilently(UUID owner) {
        Optional<OfflineRecord> discarded = registry.discard(owner);
        if (discarded.isEmpty()) {
            return false;
        }
        snapshots.remove(owner);
        missingOnce.remove(owner);
        MinecraftServer server = server();
        safe("discardSilently", () -> removeBodySilently(server, discarded.get()));
        if (server != null) {
            anchors.release(server, owner);
        }
        return true;
    }

    /**
     * Налагодження ({@code /<root> offline spawn}): створює тіло з поточного гравця,
     * не виходячи. Тіло нічого не дропає (інакше вбивство дублювало б предмети онлайн-гравця).
     */
    public Optional<OfflineRecord> spawnDebug(ServerPlayer player) {
        if (!registry.isScopeOpen()) {
            return Optional.empty();
        }
        OfflineSpec spec = null;
        try {
            spec = config.spawnWhen().apply(player);
        } catch (RuntimeException e) {
            LOGGER.error("spawnWhen кинув виняток під час debug-спавну", e);
        }
        if (spec == null || !spec.spawnAvatar()) {
            spec = OfflineSpec.avatar();
        }
        discardSilently(player.getUUID());
        OfflineAvatarSnapshot snapshot =
                OfflineAvatarSnapshot.capture(player, config.snapshotContributor(), false);
        return Optional.ofNullable(spawnAvatar(player.server, player.serverLevel(), snapshot, spec));
    }

    // ── Logout / login ─────────────────────────────────────────────────────

    /** Гравець виходить (PlayerLoggedOutEvent, HIGHEST): за потреби створює тіло (план, §6.1). */
    void onOwnerLogout(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (shuttingDown || server == null || !server.isRunning()) {
            return;
        }
        if (player.connection == null) {
            return;
        }
        if (server.isSingleplayer() && !config.allowSingleplayer()) {
            return;
        }
        if (player.isCreative() && !config.spawnInCreative()) {
            return;
        }
        if (player.isSpectator() && !config.spawnInSpectator()) {
            return;
        }
        if (!registry.isScopeOpen()) {
            return;
        }

        pendingReturns.remove(player.getUUID());

        OfflineSpec spec;
        try {
            spec = config.spawnWhen().apply(player);
        } catch (RuntimeException e) {
            LOGGER.error("spawnWhen кинув виняток для {}: тіло не створюється", player.getGameProfile().getName(), e);
            return;
        }
        if (spec == null || !spec.spawnAvatar()) {
            return;
        }

        // Повторний вихід при наявному записі (напр. debug-тіло): спершу безшумно прибираємо старе.
        discardSilently(player.getUUID());

        player.stopRiding();
        OfflineAvatarSnapshot snapshot =
                OfflineAvatarSnapshot.capture(player, config.snapshotContributor(), true);
        spawnAvatar(server, player.serverLevel(), snapshot, spec);
    }

    /** Гравець заходить (PlayerLoggedInEvent, HIGHEST). */
    void onOwnerLogin(ServerPlayer player) {
        OfflineReturnFlow.run(this, player);
    }

    /**
     * Створює тіло й реєструє запис. Порядок важливий: запис з UUID сутності
     * з'являється ДО {@code addFreshEntity}, інакше {@code EntityJoinLevelEvent}
     * побачить тіло без запису й скасує його.
     */
    @Nullable
    private OfflineRecord spawnAvatar(MinecraftServer server, ServerLevel level,
                                      OfflineAvatarSnapshot snapshot, OfflineSpec spec) {
        Optional<OfflineScopeId> scope = registry.activeScope();
        if (scope.isEmpty()) {
            return null;
        }
        OfflineAvatarBase avatar = createAvatar(level);
        if (avatar == null) {
            return null;
        }
        avatar.applySnapshot(snapshot, scope.get());

        OfflineRecord record = new OfflineRecord(
                snapshot.ownerUuid(), snapshot.ownerName(), scope.get(), avatar.getUUID(),
                snapshot.dimensionKey(), snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.yaw(), snapshot.pitch(), server.getTickCount(), spec);
        if (registry.register(record).isEmpty()) {
            return null;
        }
        snapshots.put(snapshot.ownerUuid(), snapshot);
        OfflineNameTags.join(server, avatar, snapshot.ownerName(), config.nameTagHideTeamId());

        if (!level.addFreshEntity(avatar)) {
            LOGGER.warn("Не вдалося додати тіло {} у світ", snapshot.ownerName());
            registry.discard(snapshot.ownerUuid());
            snapshots.remove(snapshot.ownerUuid());
            OfflineNameTags.leave(server, avatar.getUUID());
            return null;
        }
        if (spec.holdsChunk()) {
            anchors.anchor(server, level, snapshot.ownerUuid(), avatar.chunkPosition().x, avatar.chunkPosition().z);
        }
        safe("listener.onAvatarSpawned", () -> config.listener().onAvatarSpawned(record));
        return record;
    }

    @Nullable
    private OfflineAvatarBase createAvatar(ServerLevel level) {
        EntityType<? extends OfflineAvatarBase> type = avatarType();
        if (type == null) {
            LOGGER.error("EntityType тіла ще не зареєстровано: тіло не створюється");
            return null;
        }
        return type.create(level);
    }

    // ── Урон і смерть ──────────────────────────────────────────────────────

    /**
     * Удар по тілу (викликається з {@link OfflineAvatarBase#hurt}). Повертає, чи
     * зараховано удар. Для {@code BYPASSES_INVULNERABILITY} (порожнеча, {@code /kill})
     * тіло вбивається напряму; решта йде в політику режиму.
     */
    boolean handleHurt(OfflineAvatarBase avatar, DamageSource source, float amount) {
        Optional<OfflineRecord> found = registry.byAvatar(avatar.getUUID());
        if (found.isEmpty() || found.get().status() != OfflineStatus.STANDING) {
            return false;
        }
        OfflineRecord record = found.get();

        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            retire(record, true, source.getEntity());
            return true;
        }
        if (record.spec().invulnerable()) {
            return false;
        }

        boolean projectile = source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity();
        DamageContext ctx = new DamageContext(DamageContext.SourceKind.VANILLA,
                source.type().msgId(), amount, projectile, record.owner(), true);

        OfflineHitResult result = OfflineHitResult.IGNORED;
        try {
            OfflineHitResult returned = config.damagePolicy().onHit(record, source, amount, ctx);
            if (returned != null) {
                result = returned;
            }
        } catch (RuntimeException e) {
            LOGGER.error("damagePolicy кинула виняток для {}: удар проігноровано", record.ownerName(), e);
        }

        if (result == OfflineHitResult.HIT) {
            avatar.playHitVisual(source, amount);
            return true;
        }
        if (result == OfflineHitResult.KILLED) {
            retire(record, true, source.getEntity());
            return true;
        }
        return false;
    }

    /** Смерть, що прийшла повз {@code hurt} (чужий мод викликав {@code die}). */
    void killByAvatar(OfflineAvatarBase avatar, @Nullable Entity killer) {
        registry.byAvatar(avatar.getUUID()).ifPresent(record -> retire(record, true, killer));
    }

    /**
     * Спільний шлях «тіло закінчилось, але власник ще не повернувся» для смерті й
     * вичерпання ліміту відсутності: атомарний перехід (лут один раз), лут, зняття тіла,
     * чанка й ніктега, подія.
     *
     * @param killed {@code true} — {@link OfflineStatus#KILLED} з анімацією смерті;
     *               {@code false} — {@link OfflineStatus#EXPIRED}, тіло просто зникає.
     */
    private boolean retire(OfflineRecord record, boolean killed, @Nullable Entity killer) {
        UUID owner = record.owner();
        if (!(killed ? registry.markKilled(owner) : registry.markExpired(owner))) {
            return false;
        }
        MinecraftServer server = server();
        OfflineAvatarBase avatar = server == null ? null : findAvatar(server, record);
        if (avatar != null) {
            registry.updatePosition(owner, avatar.level().dimension().location().toString(),
                    avatar.getX(), avatar.getY(), avatar.getZ(), avatar.getYRot(), avatar.getXRot());
        }
        Vec3 pos = new Vec3(record.x(), record.y(), record.z());
        ServerLevel level = server == null ? null : levelOf(server, record.dimensionKey());

        OfflineAvatarSnapshot snapshot = snapshots.remove(owner);
        missingOnce.remove(owner);
        if (level != null && snapshot != null) {
            dropLoot(level, record, pos, snapshot);
        }

        if (avatar != null) {
            if (killed) {
                safe("avatar.playDeath", () -> avatar.playDeath());
            } else {
                avatar.discard();
            }
        }
        if (server != null) {
            anchors.release(server, owner);
            OfflineNameTags.leave(server, record.avatarEntity());
        }
        if (killed) {
            safe("listener.onAvatarKilled", () -> config.listener().onAvatarKilled(record, killer));
        } else {
            safe("listener.onAvatarExpired", () -> config.listener().onAvatarExpired(record));
        }
        return true;
    }

    private void dropLoot(ServerLevel level, OfflineRecord record, Vec3 pos, OfflineAvatarSnapshot snapshot) {
        try {
            List<ItemStack> items = config.lootPolicy().collect(record, snapshot.dropItems());
            config.lootPolicy().drop(level, pos, items);
        } catch (RuntimeException e) {
            LOGGER.error("lootPolicy кинула виняток для {}", record.ownerName(), e);
        }
    }

    // ── Перевірки тіла (викликає сама сутність і sweeper) ──────────────────

    /** Чи має це тіло право існувати: scope збігається з активним і є запис (для мертвого — будь-який стан). */
    boolean isRegistered(OfflineAvatarBase avatar) {
        Optional<OfflineScopeId> active = registry.activeScope();
        if (active.isEmpty() || !active.get().equals(avatar.getScope())) {
            return false;
        }
        Optional<OfflineRecord> record = registry.byAvatar(avatar.getUUID());
        if (record.isEmpty()) {
            return false;
        }
        return record.get().status() == OfflineStatus.STANDING || avatar.isDeadOrDying();
    }

    /** Раз на {@link OfflineAvatarBase}-перевірку: синхронізує позицію запису й чанк-якір, якщо тіло змістилось. */
    void onAvatarTick(OfflineAvatarBase avatar) {
        MinecraftServer server = avatar.getServer();
        if (server == null) {
            return;
        }
        registry.byAvatar(avatar.getUUID()).ifPresent(record -> {
            if (record.status() != OfflineStatus.STANDING) {
                return;
            }
            UUID owner = record.owner();
            registry.updatePosition(owner, avatar.level().dimension().location().toString(),
                    avatar.getX(), avatar.getY(), avatar.getZ(), avatar.getYRot(), avatar.getXRot());
            if (record.spec().holdsChunk() && avatar.level() instanceof ServerLevel level) {
                anchors.reanchorIfMoved(server, level, owner, avatar.chunkPosition().x, avatar.chunkPosition().z);
            }
            missingOnce.remove(owner);
        });
    }

    // ── Тік сервера ────────────────────────────────────────────────────────

    /** Ліміт відсутності й розсинхрон «запис є, сутності немає» (план, §6.2). */
    void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % CHECK_INTERVAL != 0 || !registry.isScopeOpen()) {
            return;
        }
        for (OfflineRecord record : registry.all()) {
            if (record.status() != OfflineStatus.STANDING) {
                continue;
            }
            int max = record.spec().maxAbsenceTicks();
            if (max > 0 && now - record.loggedOutTick() >= max) {
                retire(record, false, null);
                continue;
            }
            safe("ensureAvatarPresent", () -> ensureAvatarPresent(server, record));
        }
    }

    private void ensureAvatarPresent(MinecraftServer server, OfflineRecord record) {
        ServerLevel level = levelOf(server, record.dimensionKey());
        if (level == null) {
            return;
        }
        Entity existing = level.getEntity(record.avatarEntity());
        if (existing instanceof OfflineAvatarBase body && !body.isRemoved()) {
            missingOnce.remove(record.owner());
            return;
        }
        if (!level.hasChunkAt(BlockPos.containing(record.x(), record.y(), record.z()))) {
            return;
        }
        // Сутності чанка можуть підвантажуватись із запізненням: респавнимо лише після другого промаху.
        if (missingOnce.add(record.owner())) {
            return;
        }
        OfflineAvatarSnapshot snapshot = snapshots.get(record.owner());
        if (snapshot == null) {
            return;
        }
        respawn(server, level, record, snapshot);
    }

    /** Відновлює тіло зі знімка там, де воно стояло востаннє. Дублікат, що підвантажиться пізніше, sweeper скасує. */
    private void respawn(MinecraftServer server, ServerLevel level, OfflineRecord record, OfflineAvatarSnapshot snapshot) {
        OfflineAvatarBase avatar = createAvatar(level);
        if (avatar == null) {
            return;
        }
        OfflineAvatarSnapshot at = snapshot.atPosition(record.dimensionKey(),
                record.x(), record.y(), record.z(), record.yaw(), record.pitch());
        avatar.applySnapshot(at, record.scope());

        UUID previous = record.avatarEntity();
        if (!registry.updateAvatar(record.owner(), avatar.getUUID())) {
            return;
        }
        if (!level.addFreshEntity(avatar)) {
            registry.updateAvatar(record.owner(), previous);
            return;
        }
        OfflineNameTags.leave(server, previous);
        OfflineNameTags.join(server, avatar, record.ownerName(), config.nameTagHideTeamId());
        snapshots.put(record.owner(), at);
        missingOnce.remove(record.owner());
        if (record.spec().holdsChunk()) {
            anchors.anchor(server, level, record.owner(), avatar.chunkPosition().x, avatar.chunkPosition().z);
        }
        LOGGER.warn("Тіло {} відновлено зі знімка (сутність зникла без запису)", record.ownerName());
    }

    // ── Життєвий цикл сервера ──────────────────────────────────────────────

    void onServerStarting(MinecraftServer server) {
        shuttingDown = false;
        OfflineAvatarSweeper.sweepLoaded(server);
    }

    /**
     * ДО того, як Forge прожене гравців із сервера: інакше всі отримали б logout і
     * залишили тіла. Виставляємо прапорець і закриваємо scope.
     */
    void onServerStopping(MinecraftServer server) {
        shuttingDown = true;
        closeScope(OfflineCloseReason.SERVER_STOPPING);
        anchors.releaseAll(server);
    }

    // ── Доступ для OfflineReturnFlow / OfflineAvatarSweeper ────────────────

    OfflineRegistry registry() {
        return registry;
    }

    OfflineChunkAnchor anchors() {
        return anchors;
    }

    OfflinePresenceConfig config() {
        return config;
    }

    Map<UUID, OfflineAvatarSnapshot> snapshots() {
        return snapshots;
    }

    Map<UUID, OfflineReturn> pendingReturns() {
        return pendingReturns;
    }

    Set<UUID> missingOnce() {
        return missingOnce;
    }

    @Nullable
    static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    @Nullable
    static ServerLevel levelOf(MinecraftServer server, String dimensionKey) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimensionKey)));
    }

    /** Завантажене тіло запису або {@code null}. */
    @Nullable
    OfflineAvatarBase findAvatar(MinecraftServer server, OfflineRecord record) {
        ServerLevel level = levelOf(server, record.dimensionKey());
        if (level == null) {
            return null;
        }
        Entity entity = level.getEntity(record.avatarEntity());
        return entity instanceof OfflineAvatarBase body && !body.isRemoved() ? body : null;
    }

    /** Знімає тіло без дропу й подій; ніктег знімається й тоді, коли сутність не завантажена. */
    void removeBodySilently(@Nullable MinecraftServer server, OfflineRecord record) {
        if (server == null) {
            return;
        }
        OfflineAvatarBase avatar = findAvatar(server, record);
        if (avatar != null) {
            avatar.discard();
        }
        OfflineNameTags.leave(server, record.avatarEntity());
    }

    void safe(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.error("[{}] Виняток у {}", consumerModId, what, e);
        }
    }
}
