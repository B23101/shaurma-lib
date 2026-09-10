package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.graffiti.GraffitiClientCacheBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клієнт: один шматок байтів PNG — 1:1 перенесення
 * {@code GraffitiImageChunkPacket} snipers_shaurma (план, п. 3.14).
 * <p>
 * Розбиваємо PNG на шматки по ~24 KB ({@link #CHUNK_SIZE}), щоб надійно
 * вкладатись у ліміт розміру одного пакета Forge SimpleChannel (типово
 * ~1 MB, але дрібні шматки дають плавніший прогрес і менше шансів на
 * затори каналу при кількох одночасних передачах різним гравцям).
 */
public class GraffitiImageChunkPacket {

    public static final int CHUNK_SIZE = 24 * 1024;

    public final String imageName;
    public final long fingerprint;
    public final int chunkIndex;
    public final byte[] data;

    public GraffitiImageChunkPacket(String imageName, long fingerprint, int chunkIndex, byte[] data) {
        this.imageName = imageName;
        this.fingerprint = fingerprint;
        this.chunkIndex = chunkIndex;
        this.data = data;
    }

    public static void encode(GraffitiImageChunkPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.imageName, 128);
        buf.writeLong(pkt.fingerprint);
        buf.writeVarInt(pkt.chunkIndex);
        buf.writeVarInt(pkt.data.length);
        buf.writeBytes(pkt.data);
    }

    public static GraffitiImageChunkPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(128);
        long fp = buf.readLong();
        int idx = buf.readVarInt();
        int len = buf.readVarInt();
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new GraffitiImageChunkPacket(name, fp, idx, data);
    }

    public static void handle(GraffitiImageChunkPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;
            GraffitiClientCacheBridge.dispatchChunk(pkt);
        });
        ctx.get().setPacketHandled(true);
    }
}
