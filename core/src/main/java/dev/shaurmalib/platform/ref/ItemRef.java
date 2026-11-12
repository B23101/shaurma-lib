package dev.shaurmalib.platform.ref;

/** Стек предметів (ItemStack). NBT у 1.20.1 vs DataComponents у 1.21+ ховається за адаптером. */
public interface ItemRef extends Handle {
    boolean isEmpty();
    ItemRef copy();
}
