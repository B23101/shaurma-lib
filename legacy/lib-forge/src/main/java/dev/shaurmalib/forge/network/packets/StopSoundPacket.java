package dev.shaurmalib.forge.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * StopSoundPacket (lib) — сервер → клієнт, 1:1 перенесення
 * {@code core.network.packets.StopSoundPacket} snipers_shaurma (план,
 * п. 3.5). Логіка не змінена: id і/або категорія, будь-яка комбінація
 * (обидва / лише id / лише категорія / жодного — "зупинити все").
 * <p>
 * <b>Важливо (задокументований в оригіналі баг-контекст):</b> категорія,
 * якою був відтворений звук через {@link PlaySoundPacket}, ОБОВ'ЯЗКОВО
 * має збігатись з категорією, переданою сюди — інакше
 * {@code SoundManager#stop(ResourceLocation, SoundSource)} не знайде
 * канал і звук не зупиниться (ванільний {@code SoundManager} матчить
 * по точній категорії). {@link PlaySoundPacket} завжди відтворює з тим
 * {@code SoundSource}, що передав відправник — жодної підміни на
 * MASTER всередині {@link dev.shaurmalib.forge.sound.SoundCenter}.
 */
public class StopSoundPacket {

    private final String soundId;
    private final String sourceName;

    public StopSoundPacket(String soundId, SoundSource source) {
        this.soundId = soundId;
        this.sourceName = source != null ? source.getName() : null;
    }

    public StopSoundPacket(SoundSource source) {
        this.soundId = null;
        this.sourceName = source != null ? source.getName() : null;
    }

    public StopSoundPacket(String soundId) {
        this.soundId = soundId;
        this.sourceName = null;
    }

    public StopSoundPacket() {
        this.soundId = null;
        this.sourceName = null;
    }

    public static void encode(StopSoundPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.soundId != null);
        if (pkt.soundId != null) buf.writeUtf(pkt.soundId);
        buf.writeBoolean(pkt.sourceName != null);
        if (pkt.sourceName != null) buf.writeUtf(pkt.sourceName);
    }

    public static StopSoundPacket decode(FriendlyByteBuf buf) {
        String soundId = buf.readBoolean() ? buf.readUtf() : null;
        String sourceName = buf.readBoolean() ? buf.readUtf() : null;

        if (soundId != null && sourceName != null) {
            return new StopSoundPacket(soundId, resolveSource(sourceName));
        } else if (soundId != null) {
            return new StopSoundPacket(soundId);
        } else if (sourceName != null) {
            return new StopSoundPacket(resolveSource(sourceName));
        } else {
            return new StopSoundPacket();
        }
    }

    private static SoundSource resolveSource(String name) {
        for (SoundSource s : SoundSource.values()) {
            if (s.getName().equals(name)) return s;
        }
        return SoundSource.MASTER;
    }

    public static void handle(StopSoundPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientHandler.stopOnClient(pkt)));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void stopOnClient(StopSoundPacket pkt) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() == null) return;

            ResourceLocation soundLocation = pkt.soundId != null ? new ResourceLocation(pkt.soundId) : null;
            SoundSource source = pkt.sourceName != null ? resolveSource(pkt.sourceName) : null;

            if (soundLocation != null && source != null) {
                mc.getSoundManager().stop(soundLocation, source);
            } else if (soundLocation != null) {
                for (SoundSource s : SoundSource.values()) {
                    mc.getSoundManager().stop(soundLocation, s);
                }
            } else if (source != null) {
                mc.getSoundManager().stop(null, source);
            } else {
                mc.getSoundManager().stop();
            }
        }
    }

    // ── Convenience senders ──────────────────────────────────────────────

    public static void send(net.minecraft.server.level.ServerPlayer player, StopSoundPacket pkt) {
        dev.shaurmalib.forge.network.ShaurmaLibNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    public static void sendToAll(net.minecraft.server.MinecraftServer server, StopSoundPacket pkt) {
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            dev.shaurmalib.forge.network.ShaurmaLibNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }
}
