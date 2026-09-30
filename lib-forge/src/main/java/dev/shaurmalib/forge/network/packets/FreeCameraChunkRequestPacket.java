package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.camera.FreeCameraChunkService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клієнт → сервер: «моя камера зараз над (x, z), дошли туди чанки» або
 * «камеру вимкнено, поверни мене». Сервер виконує це ЛИШЕ для гравців, яких він
 * сам дозволив ({@link FreeCameraChunkService#authorize}) — див. чому в докстрінгу сервісу.
 */
public class FreeCameraChunkRequestPacket {

    private final double x;
    private final double z;
    private final boolean release;

    public FreeCameraChunkRequestPacket(double x, double z, boolean release) {
        this.x = x;
        this.z = z;
        this.release = release;
    }

    public static void encode(FreeCameraChunkRequestPacket pkt, FriendlyByteBuf buf) {
        buf.writeDouble(pkt.x);
        buf.writeDouble(pkt.z);
        buf.writeBoolean(pkt.release);
    }

    public static FreeCameraChunkRequestPacket decode(FriendlyByteBuf buf) {
        return new FreeCameraChunkRequestPacket(buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public static void handle(FreeCameraChunkRequestPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = ctx.get().getSender();
            if (sender != null) FreeCameraChunkService.handleClientRequest(sender, pkt.x, pkt.z, pkt.release);
        });
        ctx.get().setPacketHandled(true);
    }
}
