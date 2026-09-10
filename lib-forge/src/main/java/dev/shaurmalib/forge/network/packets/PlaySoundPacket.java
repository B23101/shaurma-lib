package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.forge.sound.SoundCenter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * PlaySoundPacket (lib) — сервер → клієнт, узагальнення
 * {@code core.network.packets.PlaySoundPacket} snipers_shaurma (план,
 * п. 3.5, "звуковий центр").
 * <p>
 * <b>Ключова відмінність від оригіналу:</b> оригінал вирішував, до якого
 * category-слайдера належить звук, через membership-перевірку у трьох
 * захардкоджених {@code Set.of(...)} з конкретними snipers-ідентифікаторами
 * ({@code ANNOUNCEMENT_OVERLAY_SOUNDS}/{@code MOD_MUSIC_SOUNDS}/
 * {@code KILL_SOUNDS}) прямо в тілі пакета. Лібова версія натомість
 * шукає {@link dev.shaurmalib.common.sound.SoundCue} по {@code soundId}
 * у {@link dev.shaurmalib.common.sound.SoundCueRegistry} — консюмер
 * реєструє визначення один раз при старті, пакет самої бібліотеки не
 * містить жодного імені конкретного звуку жодного консюмера.
 * <p>
 * Дублювання клієнтського overlay-звуку (в оригіналі: countdown-звуки,
 * які {@code GameStartAnnouncementOverlay}/{@code CinematicCountdownOverlay}
 * відтворюють самі, тому серверний пакет для них треба ігнорувати) —
 * лишається продуктовою відповідальністю консюмера: {@link SoundCenter}
 * приймає опційний {@code suppressIf} предикат при відтворенні (див.
 * {@link SoundCenter#play(String, SoundSource, float, float)}), консюмер
 * підключає власну перевірку активності свого countdown-оверлея, а не
 * бібліотека знає про існування конкретних overlay-класів консюмера.
 */
public class PlaySoundPacket {

    public final String soundId;
    public final SoundSource source;
    public final float volume;
    public final float pitch;

    public PlaySoundPacket(String soundId, SoundSource source, float volume, float pitch) {
        this.soundId = soundId;
        this.source = source;
        this.volume = volume;
        this.pitch = pitch;
    }

    public PlaySoundPacket(String soundId) {
        this(soundId, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static void encode(PlaySoundPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.soundId);
        buf.writeUtf(pkt.source.getName());
        buf.writeFloat(pkt.volume);
        buf.writeFloat(pkt.pitch);
    }

    public static PlaySoundPacket decode(FriendlyByteBuf buf) {
        String soundId = buf.readUtf();
        String sourceName = buf.readUtf();
        float volume = buf.readFloat();
        float pitch = buf.readFloat();
        SoundSource source = SoundSource.MASTER;
        for (SoundSource s : SoundSource.values()) {
            if (s.getName().equals(sourceName)) { source = s; break; }
        }
        return new PlaySoundPacket(soundId, source, volume, pitch);
    }

    public static void handle(PlaySoundPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        SoundCenter.play(pkt.soundId, pkt.source, pkt.volume, pkt.pitch)));
        ctx.get().setPacketHandled(true);
    }

    // ── Convenience senders ──────────────────────────────────────────────

    public static void send(net.minecraft.server.level.ServerPlayer player,
                             String soundId, SoundSource source, float volume, float pitch) {
        dev.shaurmalib.forge.network.ShaurmaLibNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PlaySoundPacket(soundId, source, volume, pitch));
    }

    public static void sendToAll(net.minecraft.server.MinecraftServer server,
                                  String soundId, SoundSource source, float volume, float pitch) {
        PlaySoundPacket pkt = new PlaySoundPacket(soundId, source, volume, pitch);
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            dev.shaurmalib.forge.network.ShaurmaLibNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }
}
