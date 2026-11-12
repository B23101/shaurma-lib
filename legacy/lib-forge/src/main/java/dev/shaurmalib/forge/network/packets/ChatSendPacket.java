package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.common.chat.ChatChannel;
import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.forge.chat.ChatModule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ChatSendPacket — клієнт → сервер.
 *
 * <p>Несе текст і <b>id вибраного каналу</b> ({@code ChatChannel.GLOBAL_ID}
 * для загального чату). Раніше це був {@code boolean team}, тому окремих
 * чатів (лобі/виживші/маньяки/глядачі) фізично не існувало — був лише
 * «global vs team». Обробку делегує в {@link ChatModule}.</p>
 */
public class ChatSendPacket {

    public final String text;
    public final String channelId;

    public ChatSendPacket(String text, String channelId) {
        this.text = text;
        this.channelId = channelId == null || channelId.isBlank()
                ? ChatChannel.GLOBAL_ID : channelId;
    }

    public static void encode(ChatSendPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.text, ChatFormatEngine.MAX_LENGTH);
        buf.writeUtf(pkt.channelId, 64);
    }

    public static ChatSendPacket decode(FriendlyByteBuf buf) {
        return new ChatSendPacket(buf.readUtf(ChatFormatEngine.MAX_LENGTH), buf.readUtf(64));
    }

    public static void handle(ChatSendPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ChatModule.handleIncoming(sender, pkt.text, pkt.channelId);
        });
        ctx.get().setPacketHandled(true);
    }
}
