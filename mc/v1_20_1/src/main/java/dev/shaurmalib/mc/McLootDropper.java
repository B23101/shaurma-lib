package dev.shaurmalib.mc;

import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.ref.ItemRef;
import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.service.LootDropper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** Випадання предметів у світ (1.20.1). */
public final class McLootDropper implements LootDropper {
    @Override
    public void dropItems(LevelRef level, Vec3d pos, List<ItemRef> items) {
        Level l = level.unwrap(Level.class);
        for (ItemRef i : items) {
            ItemStack s = i.unwrap(ItemStack.class);
            if (!s.isEmpty()) Containers.dropItemStack(l, pos.x(), pos.y(), pos.z(), s.copy());
        }
    }
}
