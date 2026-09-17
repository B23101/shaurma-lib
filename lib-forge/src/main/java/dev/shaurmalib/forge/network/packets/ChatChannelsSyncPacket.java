package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.chat.ChatModule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ChatChannelsSyncPacket — сервер → конкретний гравець.
 *
 * <p>Повідомляє клієнту, у які канали ВІН має право писати зараз. Без
 * цього T-екран не знав би, які кнопки каналів показувати (напр. у лобі
 * рольових каналів ще немає, а під час гри виживому доступні лише
 * «виживші» + глобальний). Сервер кличе {@link ChatModule#syncChannels}
 * при вході гравця і на кожній зміні ролей/фаз.</p>
 */
public class ChatChannelsSyncPacket {

    public final List<String> writableChannels;
    public final String defaultChannel;

    public ChatChannelsSyncPacket(List<String> writableChannels, String defaultChannel) {
        this.writableChannels = writableChannels == null ? List.of() : List.copyOf(writableChannels);
        this.defaultChannel = defaultChannel == null ? "" : defaultChannel;
    }

    public static void encode(ChatChannelsSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.writableChannels.size());
        for (String channel : pkt.writableChannels) {
            buf.writeUtf(channel, 64);
        }
        buf.writeUtf(pkt.defaultChannel, 64);
    }

    public static ChatChannelsSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> channels = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            channels.add(buf.readUtf(64));
        }
        return new ChatChannelsSyncPacket(channels, buf.readUtf(64));
    }

    public static void handle(ChatChannelsSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientHandler.handleClientSide(pkt));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void handleClientSide(ChatChannelsSyncPacket pkt) {
            ChatModule.onChannelsSync(pkt.writableChannels, pkt.defaultChannel);
        }
    }
}
