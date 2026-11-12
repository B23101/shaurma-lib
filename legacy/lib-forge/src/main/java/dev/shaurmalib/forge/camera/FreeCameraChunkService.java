package dev.shaurmalib.forge.camera;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * СЕРВЕРНЕ виправлення «FreeCamera не завантажує чанки, коли камера далеко
 * від гравця».
 *
 * <h3>У чому був баг</h3>
 * {@link FreeCameraEntity} існує лише на клієнті. Ванільний сервер вирішує,
 * які чанки слати клієнту, ВИКЛЮЧНО за позицією ГРАВЦЯ (радіус огляду
 * навколо нього). Тож коли камера відлітала від гравця далі за радіус огляду,
 * клієнт не мав жодного чанка навколо неї: замість сцени — порожнеча/небо.
 * Клієнтський код це виправити не може: даних про чанки в нього просто немає.
 *
 * <h3>Як це виправлено</h3>
 * На час сесії сервер «прив'язує» стеження чанків гравця до камери: гравця
 * (його ТІЛО, не камеру) ставлять над потрібним чанком, і ванільна система
 * стеження сама шле й тримає чанки навколо. Щоб це було безпечно й невидимо:
 * <ul>
 *   <li>тіло стоїть над світом ({@code maxBuildHeight + 16}) — чанки залежать
 *       лише від X/Z, тож висота не має значення, а ніхто не побачить тіло
 *       в небі й не зіткнеться з ним;</li>
 *   <li>на час сесії гравець невразливий і без гравітації (без падіння,
 *       без «player moved wrongly»); попередні значення відновлюються;</li>
 *   <li>позицію перераховують лише коли камера перейшла в ІНШИЙ чанк
 *       (а не щотіку) — мінімум пакетів;</li>
 *   <li>після сесії гравця повертають туди, де він був
 *       ({@link #end(ServerPlayer, boolean)}). Вихід із гри посеред сесії
 *       теж відновлює позицію ДО збереження гравця.</li>
 * </ul>
 *
 * <h3>Безпека</h3>
 * Перенесення тіла — це фактично телепорт. Тому клієнт САМ увімкнути сесію не
 * може: запит від клієнта ({@link #handleClientRequest}) ігнорується, поки
 * сервер не викликав {@link #authorize(ServerPlayer)}. Серверні сценарії
 * ({@code ReplayDirector}) працюють без клієнта взагалі.
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class FreeCameraChunkService {

    /** Скільки блоків над межею світу стоїть «якір». */
    private static final int ANCHOR_ABOVE_BUILD_LIMIT = 16;
    /** Мінімальний інтервал між запитами від клієнта (тіки). */
    private static final int MIN_CLIENT_REQUEST_INTERVAL_TICKS = 5;

    private static final class Session {
        final ResourceKey<Level> dimension;
        final double x, y, z;
        final float yaw, pitch;
        final boolean wasInvulnerable;
        final boolean wasNoGravity;
        int anchorChunkX = Integer.MIN_VALUE;
        int anchorChunkZ = Integer.MIN_VALUE;
        long lastClientRequestTick = Long.MIN_VALUE;

        Session(ServerPlayer p) {
            this.dimension = p.level().dimension();
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
            this.yaw = p.getYRot();
            this.pitch = p.getXRot();
            this.wasInvulnerable = p.isInvulnerable();
            this.wasNoGravity = p.isNoGravity();
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<UUID> AUTHORIZED = ConcurrentHashMap.newKeySet();

    private FreeCameraChunkService() {}

    // ── Авторизація для запитів ВІД КЛІЄНТА ──────────────────────────────

    /** Дозволяє цьому клієнту просити чанки для власної камери. Викликає серверний мод. */
    public static void authorize(ServerPlayer player) {
        AUTHORIZED.add(player.getUUID());
    }

    /** Знімає дозвіл і завершує активну сесію (з поверненням гравця на місце). */
    public static void revoke(ServerPlayer player) {
        AUTHORIZED.remove(player.getUUID());
        end(player, true);
    }

    public static boolean isAuthorized(UUID id) {
        return AUTHORIZED.contains(id);
    }

    // ── Сесія ────────────────────────────────────────────────────────────

    /** Чи стоїть чанк-якір цього гравця на камері зараз. */
    public static boolean isActive(UUID id) {
        return SESSIONS.containsKey(id);
    }

    /**
     * Веде стеження чанків за камерою у (x, z). Перший виклик відкриває сесію
     * (запам'ятовує позицію й ставить невразливість/без гравітації), наступні
     * лише пересувають якір, коли камера змінила чанк.
     */
    public static void follow(ServerPlayer player, double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            s = new Session(player);
            SESSIONS.put(player.getUUID(), s);
            player.setInvulnerable(true);
            player.setNoGravity(true);
        } else if (player.level().dimension() != s.dimension) {
            // Гравця перенесли в інший вимір поза нашим контролем — сесія втратила сенс.
            forget(player);
            return;
        }

        int cx = Mth.floor(x) >> 4;
        int cz = Mth.floor(z) >> 4;
        if (cx == s.anchorChunkX && cz == s.anchorChunkZ) return;
        s.anchorChunkX = cx;
        s.anchorChunkZ = cz;

        ServerLevel level = player.serverLevel();
        double ay = level.getMaxBuildHeight() + ANCHOR_ABOVE_BUILD_LIMIT;
        player.fallDistance = 0f;
        player.teleportTo(level, cx * 16 + 8.0, ay, cz * 16 + 8.0, s.yaw, s.pitch);
    }

    /**
     * Завершує сесію.
     *
     * @param restore {@code true} — повернути гравця на позицію до сесії
     *                (звичайний випадок); {@code false} — лишити де є (коли
     *                наступний крок сам його телепортує, а зайвий пакет не потрібен).
     */
    public static void end(ServerPlayer player, boolean restore) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        player.setInvulnerable(s.wasInvulnerable);
        player.setNoGravity(s.wasNoGravity);
        player.fallDistance = 0f;
        if (restore && player.level().dimension() == s.dimension) {
            player.teleportTo(player.serverLevel(), s.x, s.y, s.z, s.yaw, s.pitch);
        }
    }

    /** Завершує ВСІ сесії (кінець матчу, зупинка сервера). */
    public static void endAll(net.minecraft.server.MinecraftServer server, boolean restore) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) end(p, restore);
        SESSIONS.clear();
    }

    /**
     * Прибирає сесію БЕЗ пакетів: гравець виходить або міняє вимір. Прапори
     * повертаються, позиція виставляється напряму — щоб на збереження
     * гравця не потрапила висота якоря.
     */
    private static void forget(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        player.setInvulnerable(s.wasInvulnerable);
        player.setNoGravity(s.wasNoGravity);
        player.fallDistance = 0f;
        if (player.level().dimension() == s.dimension) {
            player.absMoveTo(s.x, s.y, s.z, s.yaw, s.pitch);
        }
    }

    // ── Запит від клієнта (C2S) ──────────────────────────────────────────

    /**
     * Обробка {@code FreeCameraChunkRequestPacket}. Працює лише для гравців,
     * яких сервер дозволив ({@link #authorize}), не частіше за один раз на
     * кілька тіків і лише в межах світового бордюра.
     */
    public static void handleClientRequest(ServerPlayer player, double x, double z, boolean release) {
        if (!AUTHORIZED.contains(player.getUUID())) return;
        if (release) {
            end(player, true);
            return;
        }
        long now = player.serverLevel().getGameTime();
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && now - s.lastClientRequestTick < MIN_CLIENT_REQUEST_INTERVAL_TICKS) return;
        if (!Double.isFinite(x) || !Double.isFinite(z)) return;
        if (!player.serverLevel().getWorldBorder().isWithinBounds(BlockPos.containing(x, 0, z))) return;
        follow(player, x, z);
        s = SESSIONS.get(player.getUUID());
        if (s != null) s.lastClientRequestTick = now;
    }

    // ── Хуки ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            forget(sp);
            AUTHORIZED.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) forget(sp);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) forget(p);
        SESSIONS.clear();
        AUTHORIZED.clear();
    }
}
