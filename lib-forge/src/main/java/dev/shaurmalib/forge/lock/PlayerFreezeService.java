package dev.shaurmalib.forge.lock;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Узагальнення {@code MovementBlockHandler.freezePlayer(...)} зі
 * snipers_shaurma (план, п. 3.25, звужено до самої механіки заморозки).
 * <p>
 * Свідомо НЕ переносить сюди рішення "коли заморожувати" (у оригіналі —
 * прив'язка до конкретних {@code GamePhase}, SCN drift-корекція до
 * spawn-точки, SD round-intro прапорець) — це лишається продуктовою
 * логікою консюмера, який сам викликає {@link #freeze(ServerPlayer)}
 * щотік, поки триває власна умова (kit-select, round-intro тощо). Дивись
 * {@code InteractionLockRegistry} (план, п. 3.25) для ширшого, іменованого
 * механізму блокування руху/дій з кількома незалежними причинами
 * одночасно — цей клас лишається простим низькорівневим примітивом, на
 * якому такий реєстр будується.
 * <p>
 * <b>Задокументована в оригіналі проблема, яку тут виправлено:</b>
 * {@code setDeltaMovement(0,0,0)} зупиняє entity-фізику на сервері, але
 * {@code ServerboundMovePlayerPacket} обробляється незалежно — клієнт
 * надсилає позиційні пакети щотік (або частіше), і при швидкому
 * натисканні клавіш руху вони можуть "пролазити" між тіками
 * {@code setDeltaMovement}. Рішення (перенесене без змін): кожні
 * {@link #RESYNC_INTERVAL_TICKS} тіків надсилається
 * {@code ClientboundPlayerPositionPacket} (через {@code connection.teleport}),
 * що скидає client-side prediction і не дає накопиченому руху застосуватись.
 */
public final class PlayerFreezeService {

    private PlayerFreezeService() {}

    /**
     * Мінімальний інтервал між примусовими ресинками позиції (тіки).
     * Ресинк скидає client-side буфер руху, запобігаючи "накопиченню"
     * руху при частому кліканні клавіш під час заморозки.
     */
    private static final int RESYNC_INTERVAL_TICKS = 5;

    /** Лічильник тіків для ресинку (per-player). */
    private static final Map<UUID, Integer> resyncCounters = new ConcurrentHashMap<>();

    /**
     * Заморожує гравця на поточний тік — консюмер викликає це з власного
     * {@code LivingEvent.LivingTickEvent}-хендлера, поки триває власна
     * умова заморозки (kit-select, round-intro тощо). Не є idempotent
     * "увімкнути й забути" — треба викликати щотік, доки заморозка має
     * тривати; коли умова більше не виконується, консюмер сам викликає
     * {@link #clearResyncState(UUID)}, щоб лічильник не тримав застарілий
     * стан для гравця, який більше не заморожений.
     */
    public static void freeze(ServerPlayer player) {
        player.setDeltaMovement(0, 0, 0);
        player.setSprinting(false);
        player.setSwimming(false);

        int counter = resyncCounters.merge(player.getUUID(), 1, Integer::sum);
        if (counter >= RESYNC_INTERVAL_TICKS) {
            resyncCounters.put(player.getUUID(), 0);
            // teleport() надсилає ClientboundPlayerPositionPacket і скидає
            // client prediction — гравець "примагнічується" до поточної
            // серверної позиції.
            player.connection.teleport(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
        }
    }

    /**
     * Скидає лічильник ресинку гравця — консюмер викликає це, коли
     * гравець більше не заморожений (аналог гілки "else" в оригінальному
     * {@code MovementBlockHandler.onLivingUpdate}), щоб наступна заморозка
     * (можливо з іншої причини) почала відлік ресинку заново, а не
     * успадкувала застарілий лічильник.
     */
    public static void clearResyncState(UUID playerId) {
        resyncCounters.remove(playerId);
    }
}
