package dev.shaurmalib.mc;

import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.ref.ServerRef;
import dev.shaurmalib.platform.service.RefFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class RefFactoryImpl implements RefFactory {
    @Override public PlayerRef player(Object p) { return new McPlayerRef((Player) p); }
    @Override public LevelRef level(Object l) { return McLevelRef.of((Level) l); }
    @Override public ServerRef server(Object s) { MinecraftServer m = (MinecraftServer) s; return () -> m; }
}
