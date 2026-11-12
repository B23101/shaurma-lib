package dev.shaurmalib.mc;

import dev.shaurmalib.platform.ref.ItemRef;
import net.minecraft.world.item.ItemStack;

/** {@link ItemRef} над {@link ItemStack} 1.20.1 (NBT). У 1.21+ — DataComponents, лише цей клас інший. */
public record McItemRef(ItemStack stack) implements ItemRef {
    @Override public Object raw() { return stack; }
    @Override public boolean isEmpty() { return stack.isEmpty(); }
    @Override public ItemRef copy() { return new McItemRef(stack.copy()); }
}
