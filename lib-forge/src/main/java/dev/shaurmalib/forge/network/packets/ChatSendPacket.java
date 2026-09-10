package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.forge.chat.ChatModule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ChatSendPacket — клієнт → сервер (план, п. 3.9). Перенесено 1:1 з
 * {@code core.network.packets.ChatSendPacket} snipers_shaurma, на
 * канал бібліотеки ({@link dev.shaurmalib.forge.network.ShaurmaLibNetwork}),
 * а обробку делегує в {@link ChatModule}, який консюмер налаштовує
 * через {@code ShaurmaLib.Builder.withChat(...)} (не хардкод на єдиний
 * consumer modId, як у оригіналі).
 */
public class ChatSendPacket {

    public final String text;
    public final boolean team; // true = лише команда відправника, false = global

    public ChatSendPacket(String text, boolean team) {
        this.text = text;
        this.team = team;
    }

    public static void encode(ChatSendPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.text, ChatFormatEngine.MAX_LENGTH);
        buf.writeBoolean(pkt.team);
    }

    public static ChatSendPacket decode(FriendlyByteBuf buf) {
        return new ChatSendPacket(buf.readUtf(ChatFormatEngine.MAX_LENGTH), buf.readBoolean());
    }

    public static void handle(ChatSendPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ChatModule.handleIncoming(sender, pkt.text, pkt.team);
        });
        ctx.get().setPacketHandled(true);
    }
}
