package dev.shaurmalib.forge.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraftforge.common.MinecraftForge;

import java.util.Set;

/**
 * TeleportService — лібова версія {@code org.example.snipers_shaurma.core.util.TeleportUtil}
 * (70 рядків в оригіналі, перенесено 1:1 по сигнатурах — заміна імпорту в
 * споживачі не змінює жодного виклик-сайту), розширена п'ятьма пунктами з
 * плану (розділ 3.2).
 * <p>
 * <b>Оригінальний баг-фікс, що зберігається буквально:</b> якщо гравець
 * сидів у сидінні/техніці, прямий виклик {@code player.teleportTo(...)} НЕ
 * телепортував гравця туди, куди очікувалось — ванільна логіка
 * телепортує ентіті, але пасажир на наступному тіку знову притягується до
 * позиції свого vehicle (яке лишилось на старому місці). Рішення (з
 * оригіналу): примусово {@code stopRiding()} перед будь-якою
 * телепортацією, і якщо сидіння "не відпустило" (модові сидіння іноді
 * ігнорують запит) — примусово {@code vehicle.ejectPassengers()}.
 * <p>
 * Статичний, без стану — як і оригінал. {@code ShaurmaLib.Builder.withTeleport()}
 * — лише прапорець свідомого підключення (сервіс сам API не потребує
 * ін'єкції залежностей), подія {@link TeleportedEvent} завжди летить через
 * {@code MinecraftForge.EVENT_BUS} незалежно від того, хто підписаний.
 */
public final class TeleportService {

    /** Радіус force-load навколо цільового чанка (в чанках). 1 = сам чанк + сусіди по колу. */
    private static final int FORCE_LOAD_RADIUS = 1;

    private TeleportService() {}

    // ── Розширення п.1: dismount (з оригінального TeleportUtil, буквально) ──

    /**
     * Знімає гравця з будь-якого сидіння/техніки, якщо він на ньому їде.
     * Безпечно викликати, навіть якщо гравець нічого не осідлав.
     * <p>
     * Звичайного {@code player.stopRiding()} може бути НЕДОСТАТНЬО: деякі
     * модові сидіння/техніка (або тісне місце навколо сидіння, коли ваніль
     * не може знайти безпечну точку для "сходження") ігнорують запит і
     * лишають гравця верхи. Тому після {@code stopRiding()} додатково
     * примусово відв'язуємо пасажира з боку самого vehicle — тільки коли
     * гравець ДОСІ їде.
     */
    public static void dismount(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        player.stopRiding();

        if (player.getVehicle() != null) {
            vehicle.ejectPassengers();
        }
    }

    // ── Розширення п.4: passenger cascade ────────────────────────────────

    /**
     * Розширення оригінального {@link #dismount}: покриває і випадок, коли
     * гравець сам возить пасажирів (наприклад на своєму transport) —
     * {@code ejectPassengers()} гравця, а не лише знімання гравця з
     * чужого vehicle. Без цього пасажир лишається в старому світі/точці,
     * поки водій телепортується.
     */
    private static void ejectOwnPassengers(ServerPlayer player) {
        if (!player.getPassengers().isEmpty()) {
            player.ejectPassengers();
        }
    }

    // ── Розширення п.1: chunk-guarantee ──────────────────────────────────

    /**
     * Force-load цільового чанка ПЕРЕД телепортом — типовий баг "гравець
     * провалюється/зависає в повітрі" при телепорті на щойно завантажену
     * територію (лобі↔арена), коли ціль поза view-distance. Використовує
     * вбудований {@link TicketType#PORTAL} (той самий, яким ваніль сама
     * форсує чанки навколо порталу) — короткочасний тікет, автоматично
     * прибирається рушієм після {@code TicketType.PORTAL} timeout, тому не
     * потребує ручного {@code removeRegionTicket}.
     */
    private static void guaranteeChunkLoaded(ServerLevel level, double x, double z) {
        var chunkPos = new net.minecraft.world.level.ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        // 4-й аргумент — значення тікета типу TicketType<T>; PORTAL — це
        // TicketType<BlockPos>, тому передаємо BlockPos цілі, а не ChunkPos.
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, chunkPos, FORCE_LOAD_RADIUS,
                new BlockPos(Mth.floor(x), 0, Mth.floor(z)));
    }

    // ── Розширення п.2: fall-distance reset ──────────────────────────────

    /**
     * Скидає накопичену дистанцію падіння і вертикальну швидкість — інакше
     * після телепорту в кінці польоту (парашут/дроп/zipline) гравець
     * отримує фантомний fall-damage від старої висоти падіння, яка вже не
     * має стосунку до нової позиції.
     */
    private static void resetFallState(ServerPlayer player) {
        player.fallDistance = 0;
        player.setDeltaMovement(0, 0, 0);
    }

    // ── Публічний API (сигнатури — точна копія TeleportUtil) ─────────────

    public static void teleport(ServerPlayer player, ServerLevel level,
                                 double x, double y, double z, float yaw, float pitch) {
        teleport(player, level, x, y, z, yaw, pitch, TeleportReason.CUSTOM, null);
    }

    public static void teleport(ServerPlayer player, double x, double y, double z) {
        // Без явного ServerLevel — той самий вимір, що і зараз (без cross-dimension).
        teleport(player, player.serverLevel(), x, y, z,
                player.getYRot(), player.getXRot(), TeleportReason.CUSTOM, null);
    }

    public static void teleport(ServerPlayer player, ServerLevel level,
                                 double x, double y, double z,
                                 Set<RelativeMovement> relatives, float yaw, float pitch) {
        ServerLevel from = player.serverLevel();
        dismount(player);
        ejectOwnPassengers(player);
        guaranteeChunkLoaded(level, x, z);

        // ── Розширення п.3: cross-dimension safety ──────────────────────
        // relatives (RelativeMovement) — ванільний API, що не передбачає
        // зміну виміру (використовується типово для команд /tp з ~ ~ ~
        // у межах одного світу). Якщо ціль в іншому вимірі — relative-семантика
        // не має сенсу, тому явна помилка замість тихого некоректного результату.
        if (level != from && !relatives.isEmpty()) {
            throw new IllegalArgumentException(
                    "TeleportService: relative movement (Set<RelativeMovement>) непідтримуваний " +
                    "разом з cross-dimension телепортом — рівень призначення відрізняється від поточного.");
        }

        player.teleportTo(level, x, y, z, relatives, yaw, pitch);
        resetFallState(player);
        fireTeleportedEvent(player, from, level, TeleportReason.CUSTOM, null);
    }

    // ── Розширений API з reason-enum (план, п. 3.2, останній пункт) ──────

    public static void teleport(ServerPlayer player, ServerLevel level,
                                 double x, double y, double z, float yaw, float pitch,
                                 TeleportReason reason) {
        teleport(player, level, x, y, z, yaw, pitch, reason, null);
    }

    public static void teleport(ServerPlayer player, ServerLevel level,
                                 double x, double y, double z, float yaw, float pitch,
                                 TeleportReason reason, String customReasonDetail) {
        ServerLevel from = player.serverLevel();
        dismount(player);
        ejectOwnPassengers(player);
        guaranteeChunkLoaded(level, x, z);

        // ── Розширення п.3: cross-dimension safety ──────────────────────
        // Якщо цільовий рівень відрізняється від поточного — використовуємо
        // dimension-aware гілку ServerPlayer.teleportTo(ServerLevel, ...),
        // а не голий Entity.teleportTo (який НЕ міняє вимір). Явна
        // розгалуджена перевірка, а не покладання на те, що виклики завжди
        // в межах одного world.
        if (level != from) {
            player.teleportTo(level, x, y, z, yaw, pitch);
        } else {
            player.teleportTo(x, y, z);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }

        resetFallState(player);
        fireTeleportedEvent(player, from, level, reason, customReasonDetail);
    }

    private static void fireTeleportedEvent(ServerPlayer player, ServerLevel from, ServerLevel to,
                                             TeleportReason reason, String customReasonDetail) {
        MinecraftForge.EVENT_BUS.post(new TeleportedEvent(player, from, to, reason, customReasonDetail));
    }
}
