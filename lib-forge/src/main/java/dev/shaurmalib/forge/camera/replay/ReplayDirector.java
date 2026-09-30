package dev.shaurmalib.forge.camera.replay;

import dev.shaurmalib.common.camera.replay.ReplayPose;
import dev.shaurmalib.common.camera.replay.ReplayScript;
import dev.shaurmalib.forge.camera.FreeCameraChunkService;
import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.ReplayPlayPacket;
import dev.shaurmalib.forge.network.packets.ReplayStopPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * СЕРВЕРНИЙ режисер Replay: запускає сценарій на клієнті гравця і одночасно
 * веде за камерою чанки ({@link FreeCameraChunkService}) — саме це робить так,
 * що Replay на ВЕЛИКІЙ відстані від гравця показує повну сцену.
 *
 * <pre>{@code
 * ReplayScript script = ReplayScript.of(10_000, clipA, clipB, clipC); // стеля 10 с
 * ReplayDirector.play(player, script, 900);   // камера стартує через 900 мс
 * ...
 * ReplayDirector.stop(player, true);          // достроково, з поверненням гравця
 * }</pre>
 *
 * Клієнт і сервер відлічують час від одного й того ж моменту (пакет старту), а
 * якір чанків «біжить» на {@link #LEAD_MS} мс ВПЕРЕДУ камери: чанки встигають
 * дійти до клієнта раніше, ніж камера до них долетить.
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class ReplayDirector {

    /** На скільки мс якір чанків випереджає камеру. */
    public static final int LEAD_MS = 1200;
    /** Запас після кінця сценарію, після якого сесія закривається сама (захист від «забули stop»). */
    private static final int SAFETY_TAIL_MS = 2000;

    private static final class Run {
        final ReplayScript script;
        final int delayMs;
        long ticks;

        Run(ReplayScript script, int delayMs) {
            this.script = script;
            this.delayMs = Math.max(0, delayMs);
        }

        long elapsedMs() { return ticks * 50L; }
    }

    private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();

    private ReplayDirector() {}

    /**
     * Запускає сценарій гравцю. Порожній сценарій нічого не робить.
     *
     * @param delayMs через скільки мс від цього виклику камера реально стартує
     *                (щоб гра встигла затемнити екран, а чанки — дійти)
     */
    public static void play(ServerPlayer player, ReplayScript script, int delayMs) {
        if (script == null || script.isEmpty()) return;
        stop(player, false);
        RUNS.put(player.getUUID(), new Run(script, delayMs));
        ShaurmaLibNetwork.sendToPlayer(player, new ReplayPlayPacket(script, delayMs));
        // Чанки під перший кадр вантажаться вже зараз — під час затемнення.
        ReplayPose first = script.sampleAt(0);
        if (first != null) FreeCameraChunkService.follow(player, first.x(), first.z());
    }

    /**
     * Зупиняє сценарій.
     *
     * @param restore {@code true} — повернути гравця на його позицію (якщо далі
     *                його не телепортуватимуть); {@code false} — лишити де є
     */
    public static void stop(ServerPlayer player, boolean restore) {
        stop(player, restore, false);
    }

    /** Те саме, але з наказом клієнту припинити показ негайно. */
    public static void stop(ServerPlayer player, boolean restore, boolean notifyClient) {
        Run run = RUNS.remove(player.getUUID());
        FreeCameraChunkService.end(player, restore);
        if (run != null && notifyClient) ShaurmaLibNetwork.sendToPlayer(player, new ReplayStopPacket());
    }

    public static void stopAll(MinecraftServer server, boolean restore, boolean notifyClient) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) stop(p, restore, notifyClient);
        RUNS.clear();
    }

    public static boolean isRunning(UUID id) {
        return RUNS.containsKey(id);
    }

    // ── Тік ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNS.isEmpty()) return;
        MinecraftServer server = event.getServer();

        for (Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Run> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Run run = entry.getValue();
            if (player == null) { it.remove(); continue; }

            run.ticks++;
            long scriptMs = run.elapsedMs() - run.delayMs;
            long total = run.script.totalMs();

            if (scriptMs >= total + SAFETY_TAIL_MS) {
                it.remove();
                FreeCameraChunkService.end(player, true);
                continue;
            }
            // Якір біжить попереду камери; після кінця стоїть на останньому кадрі.
            long ahead = Math.min(Math.max(0L, scriptMs + LEAD_MS), Math.max(0L, total - 1L));
            ReplayPose pose = run.script.sampleAt(ahead);
            if (pose != null) FreeCameraChunkService.follow(player, pose.x(), pose.z());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RUNS.remove(event.getEntity().getUUID());
    }
}
