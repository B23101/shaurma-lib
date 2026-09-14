package dev.shaurmalib.forge.inventory;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.InventorySlotAllocationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверне розподілення слотів інвентаря між гравцями.
 *
 * <p>Індекси інвентаря Minecraft: 0..8 — hotbar, 9..35 — основний
 * інвентар, 36..39 — броня, 40 — offhand. Обмеження застосовується до
 * конкретного гравця, а не глобально до всіх гравців.</p>
 *
 * <p>Для типового режиму використовуйте {@link #setHotbarSlotCount}:
 * {@code 0} означає, що жоден слот hotbar не доступний, {@code 4} — доступні
 * слоти 0..3, {@code 9} — увесь hotbar. Вміст недоступних слотів не
 * видаляється автоматично.</p>
 */
public final class InventorySlotAllocation {

    private static final Map<UUID, Allocation> ALLOCATIONS = new ConcurrentHashMap<>();
    private static volatile boolean enabled;

    private InventorySlotAllocation() {}

    public static void enable() {
        enabled = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setHotbarSlotCount(ServerPlayer player, int count) {
        requireEnabled();
        if (count < 0 || count > 9) {
            throw new IllegalArgumentException("Кількість hotbar-слотів має бути від 0 до 9.");
        }

        Set<Integer> slots = new LinkedHashSet<>();
        for (int slot = 0; slot < count; slot++) {
            slots.add(slot);
        }
        setAllowedSlots(player, slots);
    }

    public static void setAllowedSlots(ServerPlayer player, Collection<Integer> slots) {
        setAllowedSlots(player, slots, true);
    }

    public static void setAllowedSlots(ServerPlayer player,
                                       Collection<Integer> slots,
                                       boolean blockInventoryScreen) {
        requireEnabled();
        if (player == null || slots == null) {
            throw new IllegalArgumentException("Гравець і slots не можуть бути null.");
        }

        Set<Integer> copy = new LinkedHashSet<>();
        for (Integer slot : slots) {
            if (slot == null || slot < 0 || slot > 40) {
                throw new IllegalArgumentException("Недійсний слот інвентаря: " + slot);
            }
            copy.add(slot);
        }

        Allocation allocation = new Allocation(copy, blockInventoryScreen, true);
        ALLOCATIONS.put(player.getUUID(), allocation);
        normalizeSelectedSlot(player, allocation);
        syncToClient(player, allocation);
    }

    public static void setInventoryScreenBlocked(ServerPlayer player, boolean blocked) {
        requireEnabled();
        Allocation current = ALLOCATIONS.get(player.getUUID());
        if (current == null) {
            return;
        }
        Allocation updated = new Allocation(current.allowedSlots, blocked, current.blockItemDrop);
        ALLOCATIONS.put(player.getUUID(), updated);
        syncToClient(player, updated);
    }

    public static void setItemDropBlocked(ServerPlayer player, boolean blocked) {
        requireEnabled();
        Allocation current = ALLOCATIONS.get(player.getUUID());
        if (current == null) {
            return;
        }
        Allocation updated = new Allocation(current.allowedSlots, current.blockInventoryScreen, blocked);
        ALLOCATIONS.put(player.getUUID(), updated);
        syncToClient(player, updated);
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        ALLOCATIONS.remove(player.getUUID());
        ShurmaLibClientSync.clear(player);
    }

    public static boolean isRestricted(ServerPlayer player) {
        return player != null && ALLOCATIONS.containsKey(player.getUUID());
    }

    public static boolean isSlotAllowed(ServerPlayer player, int inventorySlot) {
        Allocation allocation = player == null ? null : ALLOCATIONS.get(player.getUUID());
        return allocation == null || allocation.allowedSlots.contains(inventorySlot);
    }

    public static boolean isInventoryScreenBlocked(ServerPlayer player) {
        Allocation allocation = player == null ? null : ALLOCATIONS.get(player.getUUID());
        return allocation != null && allocation.blockInventoryScreen;
    }

    public static boolean isItemDropBlocked(ServerPlayer player) {
        Allocation allocation = player == null ? null : ALLOCATIONS.get(player.getUUID());
        return allocation != null && allocation.blockItemDrop;
    }

    public static boolean shouldCancelClick(AbstractContainerMenu menu,
                                             int slotId,
                                             int button,
                                             ClickType clickType,
                                             ServerPlayer player) {
        Allocation allocation = ALLOCATIONS.get(player.getUUID());
        if (allocation == null) return false;

        // These operations can distribute one click over several slots and
        // therefore cannot be safely allowed without a custom transfer path.
        if (clickType == ClickType.QUICK_MOVE
                || clickType == ClickType.QUICK_CRAFT
                || clickType == ClickType.PICKUP_ALL) {
            return true;
        }

        if (clickType == ClickType.SWAP && !allocation.allowedSlots.contains(button)) {
            return true;
        }

        if (slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }

        Slot slot = menu.getSlot(slotId);
        if (!(slot instanceof SlotContainerAccess access)) {
            return false;
        }

        Container container = access.shaurma$getContainer();
        return container == player.getInventory()
                && !allocation.allowedSlots.contains(slot.getContainerSlot());
    }

    public static void syncExisting(ServerPlayer player) {
        Allocation allocation = ALLOCATIONS.get(player.getUUID());
        if (allocation != null) {
            syncToClient(player, allocation);
        }
    }

    public static long allowedMask(Allocation allocation) {
        long mask = 0L;
        for (Integer slot : allocation.allowedSlots) {
            mask |= 1L << slot;
        }
        return mask;
    }

    static void syncToClient(ServerPlayer player, Allocation allocation) {
        ShurmaLibClientSync.sync(player, allocation);
    }

    private static void normalizeSelectedSlot(ServerPlayer player, Allocation allocation) {
        if (allocation.allowedSlots.contains(player.getInventory().selected)) {
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (allocation.allowedSlots.contains(slot)) {
                player.getInventory().selected = slot;
                player.inventoryMenu.sendAllDataToRemote();
                return;
            }
        }

        // Minecraft still requires an internal selected-slot value even when
        // the game mode allocates zero hotbar slots.
        player.getInventory().selected = 0;
        player.inventoryMenu.sendAllDataToRemote();
    }

    public record Allocation(Set<Integer> allowedSlots,
                             boolean blockInventoryScreen,
                             boolean blockItemDrop) {
        public Allocation {
            allowedSlots = Collections.unmodifiableSet(new LinkedHashSet<>(allowedSlots));
        }
    }

    private static void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException(
                    "Розподіл слотів не підключено — викличте withInventorySlotAllocation() на Builder.");
        }
    }

    private static final class ShurmaLibClientSync {
        private static void sync(ServerPlayer player, Allocation allocation) {
            ShaurmaLibNetwork.sendToPlayer(player, InventorySlotAllocationPacket.from(allocation));
        }

        private static void clear(ServerPlayer player) {
            ShaurmaLibNetwork.sendToPlayer(player, InventorySlotAllocationPacket.unrestricted());
        }
    }
}
