package dev.shaurmalib.anim.forge;

import dev.shaurmalib.anim.emote.EmoteMessage;
import dev.shaurmalib.anim.mc.emote.ServerEmotes;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/** Клієнт → сервер: запит емоції. Формат — {@link EmoteMessage} ядра. */
public record EmotePacket(EmoteMessage message) {

    public static void encode(EmotePacket p, FriendlyByteBuf buf) {
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
            p.message.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static EmotePacket decode(FriendlyByteBuf buf) {
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            return new EmotePacket(EmoteMessage.read(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void handle(EmotePacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer sender = c.getSender();
        if (sender != null && c.getDirection().getReceptionSide().isServer()) {
            c.enqueueWork(() -> ServerEmotes.handle(sender, p.message));
        }
        c.setPacketHandled(true);
    }
}
