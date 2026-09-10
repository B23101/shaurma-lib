package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.graffiti.GraffitiClientCacheBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клієнт: "почав передачу {@code imageName}, буде
 * {@code totalChunks} шматків, загальний розмір {@code totalBytes}" — 1:1
 * перенесення {@code GraffitiImageStartPacket} snipers_shaurma (план, п.
 * 3.14). Отримавши це, клієнтський кеш (реєструється консюмером через
 * {@link GraffitiClientCacheBridge}) переходить у стан "приймається" і
 * припиняє малювати заглушку відсутньої текстури.
 */
public class GraffitiImageStartPacket {

    public final String imageName;
    public final long fingerprint;
    public final int totalBytes;
    public final int totalChunks;

    public GraffitiImageStartPacket(String imageName, long fingerprint, int totalBytes, int totalChunks) {
        this.imageName = imageName;
        this.fingerprint = fingerprint;
        this.totalBytes = totalBytes;
        this.totalChunks = totalChunks;
    }

    public static void encode(GraffitiImageStartPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.imageName, 128);
        buf.writeLong(pkt.fingerprint);
        buf.writeVarInt(pkt.totalBytes);
        buf.writeVarInt(pkt.totalChunks);
    }

    public static GraffitiImageStartPacket decode(FriendlyByteBuf buf) {
        return new GraffitiImageStartPacket(buf.readUtf(128), buf.readLong(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(GraffitiImageStartPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;
            GraffitiClientCacheBridge.dispatchTransferStart(pkt);
        });
        ctx.get().setPacketHandled(true);
    }
}
