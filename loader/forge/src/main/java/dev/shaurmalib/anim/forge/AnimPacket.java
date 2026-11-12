package dev.shaurmalib.anim.forge;

import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.net.AnimMessage;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/** Forge-обгортка {@link AnimMessage}: формат дротів визначає ядро, лоадер лише несе байти. */
public record AnimPacket(AnimMessage message) {

    public static void encode(AnimPacket p, FriendlyByteBuf buf) {
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
            p.message.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static AnimPacket decode(FriendlyByteBuf buf) {
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            return new AnimPacket(AnimMessage.read(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void handle(AnimPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        // ядро не знає Minecraft — безпечно і на сервері; на клієнті застосовується в головному потоці
        if (c.getDirection().getReceptionSide().isClient()) c.enqueueWork(() -> ShaurmaAnim.handle(p.message));
        c.setPacketHandled(true);
    }
}
