package dev.shaurmalib.mc;

import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.ref.PlayerRef;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/** Обгортка {@code Player} → {@link PlayerRef}. Єдине місце 1.20.1, що знає тип гравця. */
public record McPlayerRef(Player player) implements PlayerRef {
    @Override public Object raw() { return player; }
    @Override public UUID uuid() { return player.getUUID(); }
    @Override public String name() { return player.getGameProfile().getName(); }
    @Override public Vec3d position() { return new Vec3d(player.getX(), player.getY(), player.getZ()); }
    @Override public LevelRef level() { return McLevelRef.of(player.level()); }
}
