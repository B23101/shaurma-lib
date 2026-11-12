package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.graffiti.GraffitiTransferManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клієнт → сервер: "у мене немає картинки {@code imageName}, надішли" —
 * 1:1 перенесення {@code GraffitiRequestImagePacket} snipers_shaurma
 * (план, п. 3.14).
 * <p>
 * Клієнт може слати цей пакет повторно (з розумним інтервалом — логіка
 * ретраю лишається на боці клієнтського кешу консюмера) поки не отримає
 * {@link GraffitiImageStartPacket} чи сам PNG. Сервер ігнорує повторні
 * запити, якщо передача вже йде ({@link GraffitiTransferManager} —
 * дедуплікація за player+imageName).
 */
public class GraffitiRequestImagePacket {

    public final String imageName;

    public GraffitiRequestImagePacket(String imageName) {
        this.imageName = imageName;
    }

    public static void encode(GraffitiRequestImagePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.imageName, 128);
    }

    public static GraffitiRequestImagePacket decode(FriendlyByteBuf buf) {
        return new GraffitiRequestImagePacket(buf.readUtf(128));
    }

    public static void handle(GraffitiRequestImagePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null) return;
            GraffitiTransferManager.onImageRequested(context.getSender(), pkt.imageName);
        });
        context.setPacketHandled(true);
    }
}
