package dev.shaurmalib.forge.fx.screen;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.function.Predicate;

/**
 * Серверна частина екранних ефектів.
 * <ul>
 *   <li>{@link #play} — показати гравцю пресет (свій ефект режиму: удар маньяка, смерть від пастки…);</li>
 *   <li>{@link #enableVanillaDamage} — автоматична кров/вогонь/іній від ВАНІЛЬНОГО урону і пил від вибухів
 *       (снайпери). Режимам зі своєю системою HP це не потрібно.</li>
 * </ul>
 */
public final class ScreenFxServer {

    private static Predicate<ServerPlayer> damageGate;
    private static boolean listening;

    private ScreenFxServer() {}

    public static void play(ServerPlayer player, String presetId, float intensity) {
        if (player != null) ShaurmaLibNetwork.sendToPlayer(player, ScreenFxPacket.preset(presetId, intensity));
    }

    public static void damage(ServerPlayer player, ScreenFxDamageKind kind, float damage, float healthAfter) {
        if (player != null && kind != ScreenFxDamageKind.NONE) {
            ShaurmaLibNetwork.sendToPlayer(player, ScreenFxPacket.damage(kind, damage, healthAfter));
        }
    }

    /**
     * Увімкнути ефекти від ванільного урону й вибухів (у радіусі 16 блоків, з урахуванням видимості).
     * {@code active} — коли саме (наприклад, лише в активній фазі матчу); null — вимкнути.
     */
    public static synchronized void enableVanillaDamage(Predicate<ServerPlayer> active) {
        damageGate = active;
        if (active != null && !listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ScreenFxServer::onDamage);
            MinecraftForge.EVENT_BUS.addListener(ScreenFxServer::onExplosion);
        }
    }

    private static void onDamage(LivingDamageEvent e) {
        Predicate<ServerPlayer> gate = damageGate;
        if (gate == null || e.isCanceled() || e.getAmount() <= 0f || !(e.getEntity() instanceof ServerPlayer victim)) return;
        if (!gate.test(victim)) return;
        damage(victim, ScreenFxDamageClassifier.classify(e.getSource()), e.getAmount(),
            Math.max(0f, victim.getHealth() - e.getAmount()));
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        Predicate<ServerPlayer> gate = damageGate;
        if (gate == null || e.getLevel().isClientSide || e.getLevel().getServer() == null) return;
        Vec3 pos = e.getExplosion().getPosition();
        final float range = 16f;
        for (ServerPlayer p : e.getLevel().getServer().getPlayerList().getPlayers()) {
            if (p.level() != e.getLevel() || !gate.test(p)) continue;
            double d = Math.sqrt(p.distanceToSqr(pos));
            if (d >= range) continue;
            float intensity = (float) Math.max(0, Math.min(1, (1 - d / range) * Explosion.getSeenPercent(pos, p)));
            if (intensity > 0.01f) play(p, "shaurma_lib:explosion", intensity);
        }
    }
}
