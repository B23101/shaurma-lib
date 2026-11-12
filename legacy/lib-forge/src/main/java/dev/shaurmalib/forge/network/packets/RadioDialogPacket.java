package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.radio.RadioDialogOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * RadioDialogPacket — сервер → клієнт (план, п. 3.33), 1:1 перенесення
 * {@code core.network.packets.RadioDialogPacket} snipers_shaurma.
 * <p>
 * Сервер пакує всі доступні мовні варіанти в один пакет; клієнт сам
 * дивиться свою локаль і обирає відповідний варіант, з фолбеком на
 * {@code defaultLang}, переданий сервером ({@code RadioDialogRegistry}
 * лишається джерелом правди тільки на сервері — клієнт лише відтворює
 * те, що прийшло).
 * <p>
 * Layout (FriendlyByteBuf):
 * <pre>
 * String  modelItemId
 * String  defaultLang
 * int     langCount
 * repeat:
 *   String langCode
 *   String text
 *   String soundId
 *   int    durationTicks
 *   int    holdTicks
 * </pre>
 */
public class RadioDialogPacket {

    public final String modelItemId;
    public final String defaultLang;
    public final Map<String, LangEntry> langs;

    public static final class LangEntry {
        public final String text;
        public final String soundId;
        public final int durationTicks;
        public final int holdTicks;

        public LangEntry(String text, String soundId, int durationTicks, int holdTicks) {
            this.text = text;
            this.soundId = soundId;
            this.durationTicks = durationTicks;
            this.holdTicks = holdTicks;
        }
    }

    public RadioDialogPacket(String modelItemId, String defaultLang, Map<String, LangEntry> langs) {
        this.modelItemId = modelItemId == null ? "" : modelItemId;
        this.defaultLang = defaultLang == null ? "en_us" : defaultLang;
        this.langs = langs;
    }

    public static void encode(RadioDialogPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.modelItemId, 512);
        buf.writeUtf(pkt.defaultLang, 16);
        buf.writeInt(pkt.langs.size());
        for (Map.Entry<String, LangEntry> e : pkt.langs.entrySet()) {
            buf.writeUtf(e.getKey(), 16);
            LangEntry le = e.getValue();
            buf.writeUtf(le.text, 2048);
            buf.writeUtf(le.soundId, 256);
            buf.writeInt(le.durationTicks);
            buf.writeInt(le.holdTicks);
        }
    }

    public static RadioDialogPacket decode(FriendlyByteBuf buf) {
        String modelItemId = buf.readUtf(512);
        String defaultLang = buf.readUtf(16);
        int count = buf.readInt();
        Map<String, LangEntry> langs = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String langCode = buf.readUtf(16);
            String text = buf.readUtf(2048);
            String soundId = buf.readUtf(256);
            int durationTicks = buf.readInt();
            int holdTicks = buf.readInt();
            langs.put(langCode, new LangEntry(text, soundId, durationTicks, holdTicks));
        }
        return new RadioDialogPacket(modelItemId, defaultLang, langs);
    }

    public static void handle(RadioDialogPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientHandler.handleClientSide(pkt));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void handleClientSide(RadioDialogPacket pkt) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            String locale = "en_us";
            try {
                locale = mc.getLanguageManager().getSelected().toLowerCase();
            } catch (Exception ignored) {}

            LangEntry chosen = pkt.langs.get(locale);
            if (chosen == null) chosen = pkt.langs.get(pkt.defaultLang);
            if (chosen == null && !pkt.langs.isEmpty()) chosen = pkt.langs.values().iterator().next();
            if (chosen == null) return;

            RadioDialogOverlay.getInstance().show(
                    chosen.text, pkt.modelItemId, chosen.soundId,
                    chosen.durationTicks, chosen.holdTicks);
        }
    }

    // ── Convenience senders ──────────────────────────────────────────────

    public static void send(ServerPlayer player, String modelItemId, String defaultLang,
                             Map<String, LangEntry> langs) {
        dev.shaurmalib.forge.network.ShaurmaLibNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RadioDialogPacket(modelItemId, defaultLang, langs));
    }

    public static void sendToAll(net.minecraft.server.MinecraftServer server, String modelItemId,
                                  String defaultLang, Map<String, LangEntry> langs) {
        RadioDialogPacket pkt = new RadioDialogPacket(modelItemId, defaultLang, langs);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            dev.shaurmalib.forge.network.ShaurmaLibNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }
}
