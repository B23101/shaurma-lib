package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.radio.RadioDialogOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * RadioDialogStopPacket — сервер → клієнт (план, п. 3.33), 1:1
 * перенесення {@code core.network.packets.RadioDialogStopPacket}.
 * Порожній пейлоад — лише сигнал "приховати поточну репліку негайно".
 */
public class RadioDialogStopPacket {

    public static void encode(RadioDialogStopPacket pkt, FriendlyByteBuf buf) {
        // Порожній пейлоад.
    }

    public static RadioDialogStopPacket decode(FriendlyByteBuf buf) {
        return new RadioDialogStopPacket();
    }

    public static void handle(RadioDialogStopPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ClientHandler::handleClientSide);
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void handleClientSide() {
            RadioDialogOverlay.getInstance().hide();
        }
    }

    public static void send(ServerPlayer player) {
        dev.shaurmalib.forge.network.ShaurmaLibNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player), new RadioDialogStopPacket());
    }
}
