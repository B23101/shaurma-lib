package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.graffiti.GraffitiClientCacheBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клієнт: блок графіті на {@code pos} видалено — 1:1 перенесення
 * {@code GraffitiRemovePacket} snipers_shaurma (план, п. 3.14).
 */
public class GraffitiRemovePacket {

    public final BlockPos pos;

    public GraffitiRemovePacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(GraffitiRemovePacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
    }

    public static GraffitiRemovePacket decode(FriendlyByteBuf buf) {
        return new GraffitiRemovePacket(buf.readBlockPos());
    }

    public static void handle(GraffitiRemovePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;
            GraffitiClientCacheBridge.dispatchRemove(pkt.pos);
        });
        ctx.get().setPacketHandled(true);
    }
}
