package dev.shaurmalib.forge.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клієнт: негайно припинити Replay і повернути камеру гравцю. */
public class ReplayStopPacket {

    public ReplayStopPacket() {}

    public static void encode(ReplayStopPacket pkt, FriendlyByteBuf buf) {}

    public static ReplayStopPacket decode(FriendlyByteBuf buf) {
        return new ReplayStopPacket();
    }

    public static void handle(ReplayStopPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientHandler.stop()));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void stop() {
            dev.shaurmalib.forge.camera.replay.ReplayPlayer.stop();
        }
    }
}
