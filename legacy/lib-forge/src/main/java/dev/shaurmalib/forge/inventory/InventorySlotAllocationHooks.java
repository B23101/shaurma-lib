package dev.shaurmalib.forge.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Серверні хуки {@link InventorySlotAllocation}.
 *
 * <p>Тут живе саме те, що консюмер фізично не може перехопити зі свого
 * коду: ванільне підбирання {@code ItemEntity} і зміна ігрового режиму.
 * Кастомні шляхи видачі предметів консюмер закриває сам через
 * {@link InventorySlotAllocation#addToAllowedSlots}.</p>
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class InventorySlotAllocationHooks {

    private InventorySlotAllocationHooks() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InventorySlotAllocation.syncExisting(player);
        }
    }

    /**
     * <b>Виправлення "0 слотів, а предмети все одно підбираються".</b>
     * <p>
     * {@code ItemEntity#playerTouch} спершу питає
     * {@code ForgeEventFactory.onItemPickup(...)} (це і є
     * {@link EntityItemPickupEvent}), і лише потім викликає
     * {@code Inventory#add(...)}. Ванільний {@code add} кладе стек у
     * БУДЬ-ЯКИЙ вільний слот, тому обмеження на кліки/слот у меню не
     * заважали підбиранню: предмет зникав у слоті 9..35, який гравець не
     * бачить. Тут підбирання скасовується ДО того, як ванільний {@code add}
     * його розкладе, якщо стек не вміщується в дозволені слоти (0 слотів →
     * нічого не влізе; 4 зайняті → теж нічого).
     */
    @SubscribeEvent
    public static void onEntityItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!InventorySlotAllocation.isRestricted(player)) return;

        ItemStack stack = event.getItem().getItem();
        if (!InventorySlotAllocation.canAccept(player, stack)) {
            event.setCanceled(true);
        }
    }

    /**
     * {@link InventorySlotAllocation#setHotbarSlotCount} звільняє гравця
     * від обмежень лише за політикою {@code CreativePolicy.EXEMPT} — але
     * сама бібліотека не знала про ЗМІНУ режиму: збережений
     * {@code Allocation} для гравця не зникав і не оновлювався при
     * {@code /gamemode}, тому клієнтський пакет, надісланий під час
     * попереднього режиму, лишався чинним аж до наступного логіну.
     * Наслідок: перемикання CREATIVE → ADVENTURE (чи навпаки) не
     * відновлювало/не знімало обмеження слотів без перезаходу.
     * <p>
     * {@code PlayerChangeGameModeEvent} — це PRE-подія: на момент її
     * виклику {@code player.getGameMode()}/{@code isCreative()} ще
     * повертають СТАРИЙ режим. Тому новий режим беремо з самого об'єкта
     * події ({@code event.getNewGameMode()}), а не з гравця.
     */
    @SubscribeEvent
    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (InventorySlotAllocation.applyGameModePolicy(player, event.getNewGameMode())) {
            return;
        }
        InventorySlotAllocation.syncExisting(player);
    }
}
