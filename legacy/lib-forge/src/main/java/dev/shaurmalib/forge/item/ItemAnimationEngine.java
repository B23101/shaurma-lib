package dev.shaurmalib.forge.item;

import dev.shaurmalib.common.item.HandSlot;
import dev.shaurmalib.common.item.ItemDefinition;
import dev.shaurmalib.common.item.ItemDefinitionRegistry;
import dev.shaurmalib.common.item.PhantomSlotBridge;
import dev.shaurmalib.common.item.UseSessionEngine;
import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.ItemAnimPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Форг-двигун анімованих предметів бібліотеки — узагальнення
 * {@code AnimatedItemSystem.onServerTick}/{@code onItemToss}/{@code startUse}/
 * {@code cancelSession} з оригіналу. Вся anti-dupe СТЕЙТ-МАШИНА тепер живе
 * в {@code UseSessionEngine} (lib-common, чиста логіка); цей клас лише:
 * <ul>
 *   <li>тримає мапу ItemEntity для щойно викинутих (Q) активних предметів
 *       — той самий {@code DROPPED_ITEMS} з оригіналу, бо це живий
 *       Minecraft-об'єкт, який common-модуль не повинен імпортувати;</li>
 *   <li>підписується на реальний {@code TickEvent.ServerTickEvent} і
 *       {@code ItemTossEvent} та транслює їх у виклики движка;</li>
 *   <li>шле {@link ItemAnimPacket} клієнту (мережа — форг-специфічна).</li>
 * </ul>
 * Реєструється лише якщо мод-споживач запросив цей модуль через
 * {@code ShaurmaLib.Builder} (Registration Gateway) — нуль накладних
 * витрат для режимів без анімованих предметів.
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class ItemAnimationEngine {

    private static final Logger LOGGER = LogManager.getLogger();

    // ENGINE будується лінива — щоб опційний PhantomSlotBridge (якщо мод його
    // зареєструє через registerPhantomSlotBridge ДО першого startUse) потрапив
    // у конструктор до того, як рушій почне щось відстежувати. Registration
    // Gateway (ShaurmaLib.Builder) завжди викликає registerPhantomSlotBridge
    // (якщо запитано) раніше за будь-який реальний ігровий startUse-виклик.
    private static UseSessionEngine<ServerPlayer, InteractionHand, PlayerItemAccessImpl.Snapshot> ENGINE_INSTANCE;

    private static final Map<UUID, ItemEntity> DROPPED_ITEMS = new ConcurrentHashMap<>();

    private ItemAnimationEngine() {}

    /**
     * Мод-споживач опційно реєструє мост до своєї фантомної-слот фічі
     * (як параглайдер/зіплайн по F/G у snipers) — див. {@link PhantomSlotBridge}.
     * Якщо не викликано — рушій просто не робить цю додаткову anti-dupe перевірку.
     * Має бути викликано ДО першого {@link #startUse}.
     */
    public static synchronized void registerPhantomSlotBridge(PhantomSlotBridge<ServerPlayer> bridge) {
        ENGINE_INSTANCE = new UseSessionEngine<>(bridge);
    }

    private static synchronized UseSessionEngine<ServerPlayer, InteractionHand, PlayerItemAccessImpl.Snapshot> engine() {
        if (ENGINE_INSTANCE == null) {
            ENGINE_INSTANCE = new UseSessionEngine<>();
        }
        return ENGINE_INSTANCE;
    }

    public static boolean isPending(UUID playerUUID) {
        return engine().isPending(playerUUID);
    }

    /** 1:1 заміна {@code AnimatedItemSystem.startUse(player, hand, itemClass)}. */
    public static boolean startUse(ServerPlayer player, InteractionHand hand, Class<?> itemClass) {
        return startUse(player, hand, itemClass, null);
    }

    public static boolean startUse(ServerPlayer player, InteractionHand hand,
                                    Class<?> itemClass, String animOverride) {
        ItemDefinition<ServerPlayer, InteractionHand> def = ItemDefinitionRegistry
                .<ServerPlayer, InteractionHand>get(itemClass)
                .orElse(null);
        if (def == null) {
            LOGGER.warn("[ItemAnimationEngine] Немає ItemDefinition для {}", itemClass.getSimpleName());
            return false;
        }

        HandSlot handSlot = PlayerItemAccessImpl.toHandSlot(hand);
        PlayerItemAccessImpl access = new PlayerItemAccessImpl(player);

        boolean started = engine().startUse(player.getUUID(), handSlot, hand, itemClass, def, access);
        if (!started) return false;

        String anim = (animOverride != null && !animOverride.isEmpty()) ? animOverride : def.animationName;
        ShaurmaLibNetwork.sendToPlayer(player, new ItemAnimPacket(hand, anim));
        return true;
    }

    /** 1:1 заміна {@code AnimatedItemSystem.cancelSession} (баг 3 з оригіналу: смерть гравця під час анімації). */
    public static void cancelSession(UUID playerUUID) {
        var handSlotOpt = engine().activeHandSlot(playerUUID);
        engine().cancelSession(playerUUID);
        DROPPED_ITEMS.remove(playerUUID);

        if (handSlotOpt.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null) return;

        InteractionHand hand = PlayerItemAccessImpl.toMinecraftHand(handSlotOpt.get());
        ShaurmaLibNetwork.sendToPlayer(player, new ItemAnimPacket(hand, ""));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID uuid : engine().activePlayersSnapshot()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                engine().cancelSession(uuid);
                continue;
            }

            PlayerItemAccessImpl access = new PlayerItemAccessImpl(player);
            UseSessionEngine.StepResult result = engine().step(
                    uuid, access,
                    (p, h) -> ShaurmaLibNetwork.sendToPlayer(p, new ItemAnimPacket(h, "")),
                    player
            );

            RuntimeException activationError = engine().consumeLastActivationError();
            if (activationError != null) {
                LOGGER.error("[ItemAnimationEngine] onActivate error for {}: {}", uuid, activationError.getMessage());
            }

            if (result == UseSessionEngine.StepResult.CONSUMED_FROM_DROPPED_ENTITY) {
                DROPPED_ITEMS.remove(uuid);
            }
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        if (!engine().isPending(uuid)) return;

        ItemEntity ie = event.getEntity();
        ItemStack tossedItem = ie.getItem();
        if (tossedItem.isEmpty() || !ItemDefinitionRegistry.isRegistered(tossedItem.getItem().getClass())) return;

        DROPPED_ITEMS.put(uuid, ie);
        engine().onItemTossed(uuid, tossedItem.getItem().getClass(), () -> {
            ItemEntity dropped = DROPPED_ITEMS.remove(uuid);
            if (dropped != null && !dropped.isRemoved()) {
                dropped.discard();
            }
        });
        LOGGER.debug("[ItemAnimationEngine] {} tossed active item — marked for removal", uuid);
    }
}
