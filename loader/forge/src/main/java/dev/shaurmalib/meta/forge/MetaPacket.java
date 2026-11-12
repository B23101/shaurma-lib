package dev.shaurmalib.meta.forge;

import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.net.MetaMessage;
import dev.shaurmalib.meta.server.MetaServer;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/** Forge-обгортка {@link MetaMessage}: формат дротів — ядра, лоадер лише несе байти в обидва боки. */
public record MetaPacket(MetaMessage message) {

    public static void encode(MetaPacket p, FriendlyByteBuf buf) {
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
            p.message.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static MetaPacket decode(FriendlyByteBuf buf) {
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            return new MetaPacket(MetaMessage.read(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void handle(MetaPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isClient()) {
            if (!p.message.op().toServer()) c.enqueueWork(() -> MetaClient.handle(p.message));
        } else {
            ServerPlayer sender = c.getSender();
            if (sender != null && p.message.op().toServer()) c.enqueueWork(() -> {
                MetaServer s = MetaServer.get();
                if (s != null) s.handle(sender.getUUID(), p.message);
            });
        }
        c.setPacketHandled(true);
    }
}
