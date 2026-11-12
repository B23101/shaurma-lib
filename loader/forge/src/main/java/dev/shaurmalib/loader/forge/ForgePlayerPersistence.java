package dev.shaurmalib.loader.forge;

import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.service.PlayerPersistence;
import java.util.OptionalDouble;
import net.minecraft.world.entity.player.Player;

// Forge/NeoForge: getPersistentData(). Fabric-версія — окремий клас (Data Attachment / власне сховище).
public final class ForgePlayerPersistence implements PlayerPersistence {
    private static net.minecraft.nbt.CompoundTag tag(PlayerRef r) { return r.unwrap(Player.class).getPersistentData(); }
    @Override public OptionalDouble getNumber(PlayerRef p, String k) {
        return tag(p).contains(k) ? OptionalDouble.of(tag(p).getFloat(k)) : OptionalDouble.empty();
    }
    @Override public void putNumber(PlayerRef p, String k, double v) { tag(p).putFloat(k, (float) v); }
    @Override public void remove(PlayerRef p, String k) { tag(p).remove(k); }
}
