package dev.shaurmalib.forge.radio;

import dev.shaurmalib.common.radio.RadioDialogEntry;
import dev.shaurmalib.common.radio.RadioDialogRegistry;
import dev.shaurmalib.common.radio.RadioLangEntry;
import dev.shaurmalib.common.radio.RadioTriggerContext;
import dev.shaurmalib.forge.network.packets.RadioDialogPacket;
import dev.shaurmalib.forge.network.packets.RadioDialogStopPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RadioDialogManager (план, п. 3.33) — серверний trigger-API, узагальнення
 * {@code core.config.RadioDialogManager} snipers_shaurma. Замінює 20+
 * розкиданих по фазах/менеджерах прямих викликів
 * {@code RadioDialogManager.broadcast(server, "RADIO_XXX")} на той самий
 * контракт, ПЛЮС дві речі, яких у оригіналі не було:
 * <ul>
 *   <li><b>Пріоритет</b> ({@link RadioTriggerContext#priority()}) —
 *       репліка з вищим пріоритетом перериває поточну активну репліку
 *       на гравці негайно; репліка з нижчим/рівним пріоритетом, поки
 *       грає інша, просто ігнорується (не ставиться в чергу — короткі
 *       командирські репліки не повинні "вистрілювати" із запізненням);</li>
 *   <li><b>Анти-спам cooldown-групи</b> ({@link RadioTriggerContext#cooldownGroup()}) —
 *       повторний тригер тієї самої групи протягом {@code cooldownTicks}
 *       після попереднього ігнорується. У оригіналі snipers це не було
 *       потрібне (кожна репліка викликалась рівно раз за подію), але
 *       стає важливим у режимі-другові (maniac), де подія-тригер може
 *       спрацювати частіше, ніж повинна лунати репліка.</li>
 * </ul>
 * <p>
 * Стан пріоритету/кулдауну тримається per-player (окремо для кожного
 * {@code ServerPlayer}, як і сам показ репліки — кожен гравець бачить
 * свою незалежну репліку диктора).
 */
public final class RadioDialogManager {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/radio");

    /** uuid -> активний пріоритет поточної репліки, що грає на клієнті (best-effort, серверна оцінка). */
    private static final Map<UUID, Integer> activePriority = new ConcurrentHashMap<>();
    /** uuid -> (cooldownGroup -> tick останнього тригера цієї групи на цьому гравці). */
    private static final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    private RadioDialogManager() {}

    /** Тригерить репліку для всіх онлайн гравців, зі звичайним пріоритетом, без cooldown-групи. */
    public static void broadcast(MinecraftServer server, String dialogKey) {
        broadcast(server, dialogKey, RadioTriggerContext.normal(), 0);
    }

    public static void broadcast(MinecraftServer server, String dialogKey, RadioTriggerContext ctx, int cooldownTicks) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            trigger(p, dialogKey, ctx, cooldownTicks);
        }
    }

    /** Тригерить репліку для одного гравця, зі звичайним пріоритетом, без cooldown-групи. */
    public static void trigger(ServerPlayer player, String dialogKey) {
        trigger(player, dialogKey, RadioTriggerContext.normal(), 0);
    }

    /**
     * Тригерить репліку для одного гравця з явним пріоритетом і
     * анти-спам групою.
     *
     * @param player        адресат
     * @param dialogKey     ключ репліки в {@code radio_dialogs.yml}
     * @param ctx           пріоритет + опційна cooldown-група
     * @param cooldownTicks довжина анти-спам вікна в тіках (ігнорується, якщо {@code ctx.cooldownGroup() == null})
     */
    public static void trigger(ServerPlayer player, String dialogKey, RadioTriggerContext ctx, int cooldownTicks) {
        RadioDialogEntry entry = RadioDialogRegistry.getEntry(dialogKey);
        if (entry == null) {
            LOGGER.warn("[RadioDialog] Невідомий ключ: {}", dialogKey);
            return;
        }

        UUID uuid = player.getUUID();

        if (ctx.cooldownGroup() != null && cooldownTicks > 0) {
            long now = player.getServer() != null ? player.getServer().getTickCount() : 0L;
            Map<String, Long> byGroup = cooldowns.computeIfAbsent(uuid, k -> new HashMap<>());
            Long last = byGroup.get(ctx.cooldownGroup());
            if (last != null && now - last < cooldownTicks) {
                return; // ще в межах анти-спам вікна цієї групи на цьому гравці
            }
            byGroup.put(ctx.cooldownGroup(), now);
        }

        Integer currentPriority = activePriority.get(uuid);
        if (currentPriority != null && ctx.priority() <= currentPriority) {
            // Активна репліка важливіша або рівна — нову ігноруємо, а не ставимо в чергу.
            return;
        }
        activePriority.put(uuid, ctx.priority());

        Map<String, RadioDialogPacket.LangEntry> langs = convertLangs(entry.langs());
        RadioDialogPacket.send(player, entry.modelItemRef(), RadioDialogRegistry.getDefaultLanguage(), langs);
    }

    /** Миттєво ховає репліку на конкретному гравці й звільняє пріоритетний слот. */
    public static void stop(ServerPlayer player) {
        activePriority.remove(player.getUUID());
        RadioDialogStopPacket.send(player);
    }

    /** Миттєво ховає репліку на всіх гравцях. */
    public static void stopAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            stop(p);
        }
    }

    /**
     * Звільняє пріоритетний слот гравця без надсилання stop-пакета —
     * викликати з клієнтського підтвердження "репліка сама дограла"
     * (типово недоступне серверу напряму, тому консюмер, що точно знає
     * приблизну тривалість {@code durationTicks + holdTicks} репліки,
     * може звільнити слот сам через невеликий delayed task; інакше слот
     * звільняється природно наступним {@link #trigger} з вищим чи рівним
     * пріоритетом, або явним {@link #stop}).
     */
    public static void releasePrioritySlot(ServerPlayer player) {
        activePriority.remove(player.getUUID());
    }

    private static Map<String, RadioDialogPacket.LangEntry> convertLangs(Map<String, RadioLangEntry> src) {
        Map<String, RadioDialogPacket.LangEntry> out = new java.util.LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k,
                new RadioDialogPacket.LangEntry(v.text(), v.soundRef(), v.durationTicks(), v.holdTicks())));
        return out;
    }
}
