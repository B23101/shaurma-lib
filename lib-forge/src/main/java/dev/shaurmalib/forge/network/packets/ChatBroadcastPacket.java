package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.chat.ChatModule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * ChatBroadcastPacket — сервер → клієнт(и) (план, п. 3.9 + 3.29).
 * Перенесено 1:1 з {@code core.network.packets.ChatBroadcastPacket}
 * snipers_shaurma: доставляє готове, вже відформатоване на сервері
 * повідомлення ({@code senderTeamId} лише для клієнтського вибору
 * акцентного кольору фіду, форматування ніку вже "запечене" в
 * {@code message}).
 * <p>
 * На клієнті пушиться і в {@code AlertNotificationSystem} (видимий
 * фід — той самий потік, що й death-нотифікації), і в
 * {@link dev.shaurmalib.common.chat.ChatHistoryStore} (для
 * {@code ChatHistoryScreen}) — {@link ChatModule} відповідає за обидва
 * виклики, пакет сам нічого не знає про рушій рендеру.
 */
public class ChatBroadcastPacket {

    public final Component message;
    public final UUID senderUuid;
    public final String senderTeamId; // може бути порожній рядок, якщо без команди
    public final boolean team;

    public ChatBroadcastPacket(Component message, UUID senderUuid, String senderTeamId, boolean team) {
        this.message = message;
        this.senderUuid = senderUuid;
        this.senderTeamId = senderTeamId == null ? "" : senderTeamId;
        this.team = team;
    }

    public static void encode(ChatBroadcastPacket pkt, FriendlyByteBuf buf) {
        buf.writeComponent(pkt.message);
        buf.writeUUID(pkt.senderUuid);
        buf.writeUtf(pkt.senderTeamId, 64);
        buf.writeBoolean(pkt.team);
    }

    public static ChatBroadcastPacket decode(FriendlyByteBuf buf) {
        Component msg = buf.readComponent();
        UUID sender = buf.readUUID();
        String teamId = buf.readUtf(64);
        boolean team = buf.readBoolean();
        return new ChatBroadcastPacket(msg, sender, teamId, team);
    }

    public static void handle(ChatBroadcastPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientHandler.handleClientSide(pkt));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void handleClientSide(ChatBroadcastPacket pkt) {
            ChatModule.onBroadcastReceived(pkt.message, pkt.senderUuid, pkt.senderTeamId, pkt.team);
        }
    }
}
