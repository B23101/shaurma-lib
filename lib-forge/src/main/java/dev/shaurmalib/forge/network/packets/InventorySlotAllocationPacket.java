package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.inventory.InventorySlotAllocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клієнт: синхронізує дозволені фізичні слоти інвентаря локального
 * гравця для блокування vanilla inventory screen та рендера обмеженого hotbar.
 */
public final class InventorySlotAllocationPacket {

    private final boolean restricted;
    private final long allowedMask;
    private final boolean blockInventoryScreen;

    public InventorySlotAllocationPacket(boolean restricted, long allowedMask, boolean blockInventoryScreen) {
        this.restricted = restricted;
        this.allowedMask = allowedMask;
        this.blockInventoryScreen = blockInventoryScreen;
    }

    public static InventorySlotAllocationPacket from(InventorySlotAllocation.Allocation allocation) {
        return new InventorySlotAllocationPacket(
                true,
                InventorySlotAllocation.allowedMask(allocation),
                allocation.blockInventoryScreen());
    }

    public static InventorySlotAllocationPacket unrestricted() {
        return new InventorySlotAllocationPacket(false, 0L, false);
    }

    public static void encode(InventorySlotAllocationPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.restricted);
        buf.writeLong(packet.allowedMask);
        buf.writeBoolean(packet.blockInventoryScreen);
    }

    public static InventorySlotAllocationPacket decode(FriendlyByteBuf buf) {
        return new InventorySlotAllocationPacket(buf.readBoolean(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(InventorySlotAllocationPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientHandler.apply(packet));
        context.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    public static final class ClientHandler {
        private static boolean restricted;
        private static long allowedMask;
        private static boolean blockInventoryScreen;

        private ClientHandler() {}

        public static void apply(InventorySlotAllocationPacket packet) {
            restricted = packet.restricted;
            allowedMask = packet.allowedMask;
            blockInventoryScreen = packet.blockInventoryScreen;
        }

        public static boolean isRestricted() {
            return restricted;
        }

        public static boolean isAllowed(int slot) {
            return slot >= 0 && slot <= 40 && (allowedMask & (1L << slot)) != 0;
        }

        public static boolean isInventoryScreenBlocked() {
            return restricted && blockInventoryScreen;
        }
    }
}
