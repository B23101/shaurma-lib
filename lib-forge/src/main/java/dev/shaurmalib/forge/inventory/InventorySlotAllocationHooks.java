package dev.shaurmalib.forge.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
     * {@link InventorySlotAllocation#setHotbarSlotCount} звільняє гравця
     * від обмежень, якщо він у CREATIVE/SPECTATOR ({@code isExemptFromAllocation}
     * у консюмерів, напр. {@code InventoryAllocationModule.isExemptFromAllocation}
     * у maniacmod) — але сама бібліотека не знала про ЗМІНУ режиму:
     * збережений {@code Allocation} для гравця не зникав і не оновлювався
     * при {@code /gamemode}, тому клієнтський пакет, надісланий під час
     * попереднього режиму, лишався чинним аж до наступного логіну.
     * Наслідок: перемикання CREATIVE → ADVENTURE (чи навпаки) не
     * відновлювало/не знімало обмеження слотів без перезаходу.
     * <p>
     * Тут лише пересилаємо АКТУАЛЬНИЙ збережений {@code Allocation}
     * клієнту ще раз при кожній зміні режиму — той самий шлях, що
     * {@link #onLogin}. Що саме має бути дозволено ПІСЛЯ зміни режиму
     * (наприклад "адмін у creative — без обмежень взагалі") вирішує
     * консюмер: якщо в нього для цього гравця більше немає активного
     * {@code Allocation} (бо він, наприклад, ще не викликав
     * {@code setHotbarSlotCount} повторно), {@link InventorySlotAllocation#syncExisting}
     * коректно нічого не робить.
     */
    @SubscribeEvent
    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InventorySlotAllocation.syncExisting(player);
        }
    }
}
