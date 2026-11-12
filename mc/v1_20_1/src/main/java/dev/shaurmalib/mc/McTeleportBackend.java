package dev.shaurmalib.mc;

import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.service.TeleportBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Телепорт 1.20.1: {@code ServerPlayer.teleportTo(level, x, y, z, yaw, pitch)} + тікет чанка. */
public final class McTeleportBackend implements TeleportBackend {
    private static ServerPlayer p(PlayerRef r) { return r.unwrap(ServerPlayer.class); }

    @Override
    public void teleport(PlayerRef player, LevelRef target, double x, double y, double z, float yaw, float pitch) {
        ServerPlayer sp = p(player);
        ServerLevel level = target != null ? target.unwrap(ServerLevel.class) : sp.serverLevel();
        sp.teleportTo(level, x, y, z, yaw, pitch);
    }

    @Override
    public void ensureChunkLoaded(LevelRef level, double x, double z) {
        ServerLevel l = level.unwrap(ServerLevel.class);
        BlockPos pos = BlockPos.containing(x, 0, z);
        ChunkPos cp = new ChunkPos(pos);
        l.getChunkSource().addRegionTicket(TicketType.PORTAL, cp, 1, pos);
        l.getChunk(cp.x, cp.z);
    }

    @Override
    public void resetMotionAndFall(PlayerRef player) {
        ServerPlayer sp = p(player);
        sp.fallDistance = 0f;
        sp.setDeltaMovement(Vec3.ZERO);
    }

    @Override public boolean hasPassengers(PlayerRef player) { return p(player).isVehicle(); }
    @Override public boolean isPassenger(PlayerRef player) { return p(player).isPassenger(); }
    @Override public void ejectPassengers(PlayerRef player) { p(player).ejectPassengers(); }
}
