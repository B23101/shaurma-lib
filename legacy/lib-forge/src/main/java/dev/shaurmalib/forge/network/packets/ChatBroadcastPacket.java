package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.forge.chat.ChatModule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * ChatBroadcastPacket — сервер → клієнт(и).
 *
 * <p>Окрім готового {@code message} (нік + текст одним рядком — те, що
 * показує живий фід) пакет несе <b>структуровані</b> поля: id каналу,
 * нік відправника, HEX-колір його групи і «сирий» текст. Завдяки цьому
 * T-екран малює повідомлення власною версткою (2D-голова гравця, нік
 * кольором групи, текст під ним), а не лише однорядковий {@code Component}.
 * Форматування як і раніше робиться один раз на сервері — щоб текст у
 * фіді й в історії не міг розійтися.</p>
 */
public class ChatBroadcastPacket {

    public final Component message;
    public final UUID senderUuid;
    public final String senderName;
    public final String channelId;   // ChatChannel.GLOBAL_ID якщо без каналу
    public final String colorHex;    // колір групи відправника, може бути порожній
    public final String rawText;     // текст без ніку
    public final boolean team;       // true = не глобальний канал

    public ChatBroadcastPacket(Component message, UUID senderUuid, String senderTeamId, boolean team) {
        this(message, senderUuid, null, senderTeamId, null, null, team);
    }

    public ChatBroadcastPacket(UUID senderUuid,
                               String senderName,
                               String channelId,
                               String colorHex,
                               String rawText,
                               Component formatted) {
        this(formatted, senderUuid, senderName, channelId, colorHex, rawText,
                channelId != null && !"global".equals(channelId));
    }

    public ChatBroadcastPacket(Component message,
                               UUID senderUuid,
                               String senderName,
                               String channelId,
                               String colorHex,
                               String rawText,
                               boolean team) {
        this.message = message;
        this.senderUuid = senderUuid;
        this.senderName = senderName == null ? "" : senderName;
        this.channelId = channelId == null ? "" : channelId;
        this.colorHex = colorHex == null ? "" : colorHex;
        this.rawText = rawText == null ? "" : rawText;
        this.team = team;
    }

    public static void encode(ChatBroadcastPacket pkt, FriendlyByteBuf buf) {
        buf.writeComponent(pkt.message);
        buf.writeUUID(pkt.senderUuid);
        buf.writeUtf(pkt.senderName, 64);
        buf.writeUtf(pkt.channelId, 64);
        buf.writeUtf(pkt.colorHex, 16);
        buf.writeUtf(pkt.rawText, ChatFormatEngine.MAX_LENGTH);
        buf.writeBoolean(pkt.team);
    }

    public static ChatBroadcastPacket decode(FriendlyByteBuf buf) {
        Component msg = buf.readComponent();
        UUID sender = buf.readUUID();
        String senderName = buf.readUtf(64);
        String channelId = buf.readUtf(64);
        String colorHex = buf.readUtf(16);
        String rawText = buf.readUtf(ChatFormatEngine.MAX_LENGTH);
        boolean team = buf.readBoolean();
        return new ChatBroadcastPacket(msg, sender, senderName, channelId, colorHex, rawText, team);
    }

    public static void handle(ChatBroadcastPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientHandler.handleClientSide(pkt));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void handleClientSide(ChatBroadcastPacket pkt) {
            if (pkt.senderName != null && !pkt.senderName.isEmpty()) {
                ChatModule.onMessageReceived(pkt.senderUuid, pkt.senderName,
                        pkt.channelId, pkt.colorHex, pkt.rawText, pkt.message);
            } else {
                // Старий сервер / пакет без структурованих полів.
                ChatModule.onBroadcastReceived(pkt.message, pkt.senderUuid, pkt.channelId, pkt.team);
            }
        }
    }
}
