package dev.shaurmalib.mc;

import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.service.PlayerOps;
import net.minecraft.world.entity.player.Player;

public final class PlayerOpsImpl implements PlayerOps {
    private static Player p(PlayerRef r) { return r.unwrap(Player.class); }

    @Override public boolean isCreativeOrSpectator(PlayerRef r) { Player p = p(r); return p.isCreative() || p.isSpectator(); }
    @Override public boolean isSprinting(PlayerRef r) { return p(r).isSprinting(); }
    @Override public void setSprinting(PlayerRef r, boolean s) { p(r).setSprinting(s); }
    @Override public boolean isRiding(PlayerRef r) { return p(r).getVehicle() != null; }
    @Override public int tickCount(PlayerRef r) { return p(r).tickCount; }
    @Override public void setFood(PlayerRef r, int food, float sat) {
        p(r).getFoodData().setFoodLevel(food);
        p(r).getFoodData().setSaturation(sat);
    }
}
