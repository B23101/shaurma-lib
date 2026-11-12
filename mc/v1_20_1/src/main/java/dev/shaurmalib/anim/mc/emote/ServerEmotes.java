package dev.shaurmalib.anim.mc.emote;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.emote.Emote;
import dev.shaurmalib.anim.emote.EmoteMessage;
import dev.shaurmalib.anim.emote.EmoteSessions;
import dev.shaurmalib.anim.emote.ShaurmaEmotes;
import dev.shaurmalib.anim.mc.ServerAnimations;
import dev.shaurmalib.anim.net.AnimMessage;
import dev.shaurmalib.anim.runtime.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Серверна частина емоцій: прийняти запит клієнта, спитати хук мода, розіслати анімацію всім
 * ({@link ServerAnimations#play}); щотіка — погасити, якщо гравець пішов (для емоцій усього тіла)
 * або хук більше не дозволяє (впав, почав дію). Клей лоадера кличе {@link #handle}, {@link #tick}, {@link #onLogout}.
 *
 * Шар {@link Slot#ACTION}: серверна дія предмета (аптечка) природно витісняє емоцію, а пози станів
 * (лежить, на гаку — {@link Slot#POSE}) лежать вище й перекривають її, навіть якщо хук мода щось пропустив.
 */
public final class ServerEmotes {

    private ServerEmotes() {}

    public static final Slot SLOT = Slot.ACTION;

    public static void handle(ServerPlayer player, EmoteMessage m) {
        if (player == null || m == null) return;
        EmoteSessions sessions = ShaurmaEmotes.sessions();
        if (m.op() == EmoteMessage.Op.STOP) {
            stop(player);
            return;
        }
        Emote e = ShaurmaEmotes.get(m.emote());
        if (e == null) return;
        if (!ShaurmaEmotes.allowedOnServer(player, e)) {
            player.displayClientMessage(Component.translatableWithFallback("shaurma_lib.emote.denied", "Зараз не можна"), true);
            return;
        }
        EmoteSessions.Active prev = sessions.active(player.getUUID());
        if (prev != null && !prev.emote().clip().equals(e.clip())) ServerAnimations.stop(player, prev.emote().clip());
        AnimMessage.PlayRequestSpec spec = AnimMessage.PlayRequestSpec.of(e.clip()).slot(SLOT).mask(e.mask()).restart(true);
        if (e.loop() != null) spec.loop(e.loop() ? AnimationClip.LoopMode.LOOP : AnimationClip.LoopMode.ONCE);
        ServerAnimations.play(player, spec);
        sessions.start(player.getUUID(), e, player.getX(), player.getY(), player.getZ(), player.level().getGameTime());
    }

    /** Зупинити емоцію гравця (рух, повторне натискання, мод). */
    public static void stop(ServerPlayer player) {
        EmoteSessions.Active a = ShaurmaEmotes.sessions().stop(player.getUUID());
        if (a != null) ServerAnimations.stop(player, a.emote().clip());
    }

    public static void tick(MinecraftServer server) {
        EmoteSessions sessions = ShaurmaEmotes.sessions();
        for (UUID id : sessions.players()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            EmoteSessions.Active a = sessions.active(id);
            if (p == null || a == null) {
                sessions.stop(id);
                continue;
            }
            boolean allowed = p.isAlive() && ShaurmaEmotes.allowedOnServer(p, a.emote());
            if (sessions.check(id, p.getX(), p.getY(), p.getZ(), allowed, p.level().getGameTime()) != EmoteSessions.Decision.KEEP) stop(p);
        }
    }

    public static void onLogout(UUID player) { ShaurmaEmotes.sessions().stop(player); }

    public static void onServerStopped() { ShaurmaEmotes.sessions().clear(); }
}
