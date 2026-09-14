package dev.shaurmalib.forge.inventory;

import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.inventory.Slot;

@Mixin(Slot.class)
public interface SlotContainerAccess {
    @Accessor("container")
    Container shaurma$getContainer();
}
