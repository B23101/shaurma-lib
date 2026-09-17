package dev.shaurmalib.forge.inventory;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.InventorySlotAllocationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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

    /**
     * Чи поширюються обмеження слотів на гравців у CREATIVE/SPECTATOR.
     *
     * <p>{@link #APPLY} (дефолт) — правила діють для всіх режимів: адмін
     * у креативі посеред матчу бачить ті самі 0/2/4 слоти, що й гравець.
     * Саме це потрібно режимам, де креатив — лише інструмент адміна, а не
     * ігрова механіка.</p>
     *
     * <p>{@link #EXEMPT} — гравець у CREATIVE/SPECTATOR не обмежується
     * взагалі (обмеження знімається, не застосовується заново, доки
     * гравець не повернеться у звичайний режим). Це потрібно картобудівникам
     * і режимам, де креатив використовується у самій грі.</p>
     */
    public enum CreativePolicy {
        APPLY,
        EXEMPT
    }

    private static final Map<UUID, Allocation> ALLOCATIONS = new ConcurrentHashMap<>();
    private static volatile boolean enabled;
    private static volatile CreativePolicy creativePolicy = CreativePolicy.APPLY;

    private InventorySlotAllocation() {}

    public static void enable() {
        enabled = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Вмикає/вимикає звільнення гравців у CREATIVE/SPECTATOR. Дефолт —
     * {@link CreativePolicy#APPLY} (обмеження чинні і в креативі).
     * Викликати на старті сервера (або під час reload конфігу) — значення
     * читається при кожному {@link #setAllowedSlots} і при кожній зміні
     * ігрового режиму.
     */
    public static void setCreativePolicy(CreativePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Політика для креативу не може бути null.");
        }
        creativePolicy = policy;
    }

    public static CreativePolicy creativePolicy() {
        return creativePolicy;
    }

    /**
     * Чи цього гравця звільнено від обмежень лише через ігровий режим
     * (CREATIVE/SPECTATOR) згідно з поточною {@link #creativePolicy()}.
     */
    public static boolean isGameModeExempt(ServerPlayer player) {
        if (player == null || creativePolicy == CreativePolicy.APPLY) return false;
        return player.isCreative() || player.isSpectator();
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

        // Політика креативу вирішується ТУТ, а не в консюмері: якщо гравець
        // звільнений — не зберігаємо обмеження взагалі і надсилаємо клієнту
        // "без обмежень" (інакше на клієнті лишився б попередній пакет).
        if (isGameModeExempt(player)) {
            clear(player);
            return;
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

    // ══════════════════════════════════════════════════════════════
    //  Підбирання предметів — дозволені слоти, а не "весь інвентар"
    // ══════════════════════════════════════════════════════════════

    /**
     * Чи є куди покласти стек, зважаючи ЛИШЕ на дозволені слоти.
     * <p>
     * <b>Це той самий пропущений шлях, через який "0 слотів" все одно
     * підбирав предмети:</b> ванільний {@code Inventory#add} кладе стек у
     * будь-який вільний слот (включно з 9..35, 36..40), тому навіть з
     * нулем дозволених слотів підбирання "вдавалось" — предмет просто
     * зникав у недоступному слоті. Тут перевіряються рівно ті слоти, які
     * гравець реально бачить і може використати.</p>
     *
     * @return {@code true}, якщо весь стек влізе (дозволені слоти
     *         можуть доповнити наявні стеки або мають вільне місце).
     */
    public static boolean canAccept(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null) return false;
        Allocation allocation = ALLOCATIONS.get(player.getUUID());
        if (allocation == null) return true; // гравець без обмежень
        return simulateAdd(player.getInventory(), allocation, stack.copy(), true).isEmpty();
    }

    /**
     * Кладе стек ЛИШЕ у дозволені слоти. Повертає залишок
     * ({@link ItemStack#EMPTY}, якщо все влізло).
     * <p>
     * Консюмер використовує це замість {@code player.getInventory().add(...)}
     * для власних шляхів видачі/підбирання предметів (напр. кастомна
     * сутність лежачого луту). Для ванільного {@code ItemEntity} той самий
     * інваріант тримає {@code InventorySlotAllocationHooks} через
     * {@code EntityItemPickupEvent}.
     */
    public static ItemStack addToAllowedSlots(ServerPlayer player, ItemStack stack) {
        requireEnabled();
        if (player == null || stack == null) {
            throw new IllegalArgumentException("Гравець і стек не можуть бути null.");
        }
        Allocation allocation = ALLOCATIONS.get(player.getUUID());
        if (allocation == null) {
            // Немає обмежень — звичайна ванільна поведінка, щоб консюмер
            // міг викликати цей метод безумовно.
            boolean added = player.getInventory().add(stack);
            return added ? ItemStack.EMPTY : stack;
        }
        ItemStack remaining = simulateAdd(player.getInventory(), allocation, stack.copy(), false);
        if (remaining.getCount() != stack.getCount()) {
            player.getInventory().setChanged();
        }
        return remaining;
    }

    /**
     * Спільна логіка перевірки/розкладки по дозволених слотах.
     *
     * @param dryRun {@code true} — нічого не змінювати (лише порахувати
     *               залишок), {@code false} — реально покласти.
     * @return залишок стека, який не вліз.
     */
    private static ItemStack simulateAdd(Inventory inventory,
                                         Allocation allocation,
                                         ItemStack remaining,
                                         boolean dryRun) {
        // 1. Доповнюємо наявні стеки тим самим предметом (як ванільний add).
        for (Integer slot : allocation.allowedSlots) {
            if (remaining.isEmpty()) break;
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remaining)) continue;
            int space = existing.getMaxStackSize() - existing.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, remaining.getCount());
            if (!dryRun) {
                existing.grow(move);
            }
            remaining.shrink(move);
        }
        // 2. Порожні дозволені слоти.
        for (Integer slot : allocation.allowedSlots) {
            if (remaining.isEmpty()) break;
            ItemStack existing = inventory.getItem(slot);
            if (!existing.isEmpty()) continue;
            int move = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            if (!dryRun) {
                ItemStack placed = remaining.copy();
                placed.setCount(move);
                inventory.setItem(slot, placed);
            }
            remaining.shrink(move);
        }
        return remaining;
    }

    /**
     * Переоцінка обмежень при зміні ігрового режиму. Повертає {@code true},
     * якщо гравця було звільнено політикою (обмеження знято), і викликати
     * {@link #syncExisting} після цього не потрібно.
     * <p>
     * Це потрібно, бо {@code setAllowedSlots} не зберігає обмеження для
     * звільненого гравця: перехід назад у ADVENTURE/SURVIVAL має знову
     * застосувати правила — це робить консюмер (повторний виклик
     * {@code setHotbarSlotCount} на наступному тіку), а на зміні режиму
     * ми лише прибираємо старі обмеження.
     */
    public static boolean applyGameModePolicy(ServerPlayer player, net.minecraft.world.level.GameType newGameMode) {
        if (player == null) return false;
        boolean exempt = creativePolicy == CreativePolicy.EXEMPT
                && (newGameMode == net.minecraft.world.level.GameType.CREATIVE
                    || newGameMode == net.minecraft.world.level.GameType.SPECTATOR);
        if (exempt) {
            if (ALLOCATIONS.containsKey(player.getUUID())) {
                clear(player);
            }
            return true;
        }
        return false;
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
