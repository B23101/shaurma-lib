package dev.shaurmalib.forge.graffiti;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.GraffitiRemovePacket;
import dev.shaurmalib.forge.network.packets.GraffitiSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Розсилає стан блоків графіті гравцям, що спостерігають відповідний
 * чанк (план, п. 3.14) — 1:1 перенесення {@code GraffitiSyncManager}
 * snipers_shaurma, підключене до {@link ShaurmaLibNetwork} замість
 * мережевого каналу консюмера (окремий канал лібу, план розділ 6 п. 2).
 * <p>
 * Ми НЕ покладаємось лише на {@code ClientboundBlockEntityDataPacket}
 * (хоча він і реалізований у {@link GraffitiBlockEntityBase} про всяк
 * випадок для ванільних механізмів типу {@code /reload}), а явно шлемо
 * власний легкий {@link GraffitiSyncPacket} — так клієнтський кеш
 * рендера консюмера наповнюється детерміновано і одразу містить
 * fingerprint файлу для порівняння з кешем.
 * <p>
 * {@link #fileStore(MinecraftServer)} потребує namespace, тому цей клас
 * очікує, що консюмер один раз викличе {@link #bind(GraffitiFileStore)}
 * при {@code withGraffiti(...)} на {@link dev.shaurmalib.forge.ShaurmaLib.Builder}
 * — той самий "один активний namespace на JVM" принцип, що решта
 * статичних сервісів лібу (сервер один процес = один консюмер).
 */
public final class GraffitiSyncManager {

    private static volatile GraffitiFileStore fileStore;

    private GraffitiSyncManager() {}

    /** Викликається один раз із {@code withGraffiti(...)} на Builder. */
    public static void bind(GraffitiFileStore store) {
        fileStore = store;
    }

    public static GraffitiFileStore fileStore() {
        GraffitiFileStore store = fileStore;
        if (store == null) {
            throw new IllegalStateException(
                    "Graffiti-модуль не підключено — викличте withGraffiti(...) на Builder.");
        }
        return store;
    }

    /** Надсилає повний стан одного блоку всім гравцям, що трекають його чанк. */
    public static void syncBlock(Level level, GraffitiBlockEntityBase be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        MinecraftServer server = serverLevel.getServer();
        long fingerprint = be.spec().needsImageTransfer()
                ? fileStore().fingerprint(server, be.spec().imageName())
                : 0L;

        GraffitiSyncPacket pkt = GraffitiSyncPacket.of(be.getBlockPos(), be.getSide(), be.spec(), fingerprint);
        sendToChunkWatchers(serverLevel, new ChunkPos(be.getBlockPos()), pkt);
    }

    /** Надсилає повний стан одному конкретному гравцю (напр. при появі в трекінгу чанка). */
    public static void syncBlockTo(ServerPlayer player, GraffitiBlockEntityBase be) {
        MinecraftServer server = player.getServer();
        long fingerprint = be.spec().needsImageTransfer()
                ? fileStore().fingerprint(server, be.spec().imageName())
                : 0L;

        GraffitiSyncPacket pkt = GraffitiSyncPacket.of(be.getBlockPos(), be.getSide(), be.spec(), fingerprint);
        ShaurmaLibNetwork.sendToPlayer(player, pkt);
    }

    public static void onBlockRemoved(Level level, GraffitiBlockEntityBase be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        GraffitiRemovePacket pkt = new GraffitiRemovePacket(be.getBlockPos());
        sendToChunkWatchers(serverLevel, new ChunkPos(be.getBlockPos()), pkt);
    }

    private static void sendToChunkWatchers(ServerLevel level, ChunkPos chunkPos, Object packet) {
        // Не покладаємось на внутрішній ChunkMap API (нестабільний між версіями) —
        // натомість шлемо всім гравцям цього виміру, чий сервер-сайд view distance
        // покриває чанк. Дешево (гравців мало, це не викликається щотіку), і дає
        // той самий практичний результат: пакет долітає рівно тим, хто бачить чанк.
        int viewDist = level.getServer().getPlayerList().getViewDistance();
        for (ServerPlayer player : level.players()) {
            ChunkPos playerChunk = new ChunkPos(player.blockPosition());
            int dx = Math.abs(playerChunk.x - chunkPos.x);
            int dz = Math.abs(playerChunk.z - chunkPos.z);
            if (dx <= viewDist && dz <= viewDist) {
                ShaurmaLibNetwork.sendToPlayer(player, packet);
            }
        }
    }
}
