package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.common.camera.replay.ReplayScript;
import dev.shaurmalib.forge.camera.replay.ReplayScriptCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клієнт: показати Replay-сценарій, камера стартує через {@code delayMs}. */
public class ReplayPlayPacket {

    private final ReplayScript script;
    private final int delayMs;

    public ReplayPlayPacket(ReplayScript script, int delayMs) {
        this.script = script;
        this.delayMs = delayMs;
    }

    public static void encode(ReplayPlayPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.delayMs);
        ReplayScriptCodec.write(buf, pkt.script);
    }

    public static ReplayPlayPacket decode(FriendlyByteBuf buf) {
        int delay = buf.readVarInt();
        return new ReplayPlayPacket(ReplayScriptCodec.read(buf), delay);
    }

    public static void handle(ReplayPlayPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientHandler.play(pkt)));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void play(ReplayPlayPacket pkt) {
            dev.shaurmalib.forge.camera.replay.ReplayPlayer.play(pkt.script, pkt.delayMs);
        }
    }
}
