package dev.shaurmalib.forge.item;

import dev.shaurmalib.common.item.HandSlot;
import dev.shaurmalib.common.item.ItemDefinitionRegistry;
import dev.shaurmalib.common.item.PlayerItemAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Forge-реалізація {@link PlayerItemAccess} над {@code ServerPlayer}.
 * <p>
 * Логіка кожного методу перенесена 1:1 з приватних утиліт оригінального
 * {@code AnimatedItemSystem} ({@code resolveSlot}, {@code isHoldingInHand},
 * {@code findItemSlot}, {@code consumeSlot}) — лише розділена на окремі
 * методи інтерфейсу, щоб {@code UseSessionEngine} (lib-common) міг ними
 * керувати без прямого імпорту Minecraft-типів.
 */
public final class PlayerItemAccessImpl implements PlayerItemAccess<PlayerItemAccessImpl.Snapshot> {

    /** Знімок стеку: клас предмета + копія NBT — те саме, що (itemClass, itemTag) в оригіналі. */
    public record Snapshot(Class<?> itemClass, CompoundTag tag) {}

    private final ServerPlayer player;

    public PlayerItemAccessImpl(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public UUID uuid() {
        return player.getUUID();
    }

    @Override
    public long currentGameTime() {
        return player.serverLevel().getGameTime();
    }

    /** MAIN_HAND → вибраний хотбар-слот (0–8), OFF_HAND → 40 (vanilla offhand index) — як в оригіналі. */
    @Override
    public int resolveSlot(HandSlot hand) {
        return hand == HandSlot.MAIN_HAND ? player.getInventory().selected : 40;
    }

    @Override
    public Snapshot snapshotSlot(int slot) {
        ItemStack stack = getSlotStack(slot);
        if (stack.isEmpty()) return new Snapshot(Object.class, new CompoundTag());
        CompoundTag tag = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
        return new Snapshot(stack.getItem().getClass(), tag);
    }

    /**
     * 1:1 з оригінального {@code isHoldingInHand}: перевіряє лише що слот
     * непорожній і предмет у ньому ЗАРЕЄСТРОВАНИЙ рушієм — НЕ що це саме
     * той самий клас, що був у {@code snapshot}. Це свідома поведінка
     * оригіналу (не пропущена перевірка): якщо гравець встиг замінити
     * предмет на інший зареєстрований анімований предмет ще до
     * activateTick, "тримання в руці" все одно вважається істинним, а
     * подальший {@code findMatchingSlot} за class+NBT з {@code snapshot}
     * окремо гарантує, що на consumeTick витрачається саме оригінальний
     * предмет, а не той, що зараз у слоті.
     */
    @Override
    public boolean slotMatchesSnapshot(int slot, Snapshot snapshot) {
        ItemStack stack = getSlotStack(slot);
        if (stack.isEmpty()) return false;
        return ItemDefinitionRegistry.isRegistered(stack.getItem().getClass());
    }

    @Override
    public boolean isHandActive(HandSlot hand, int slot) {
        if (hand == HandSlot.MAIN_HAND) {
            return player.getInventory().selected == slot;
        }
        return true; // OFF_HAND (slot 40) — активний завжди, якщо непорожній (перевірено окремо)
    }

    /**
     * Спочатку перевіряє збережений слот (найшвидший шлях), потім скановує
     * весь інвентар включно з offhand (40) — F-swap може перемістити
     * предмет. Порівнює по класу і, якщо є NBT, по точному збігу тегу.
     */
    @Override
    public int findMatchingSlot(Snapshot snapshot, int preferredSlot) {
        ItemStack inSaved = getSlotStack(preferredSlot);
        if (!inSaved.isEmpty() && inSaved.getItem().getClass().equals(snapshot.itemClass())) {
            return preferredSlot;
        }

        for (int i = 0; i <= 40; i++) {
            ItemStack stack = getSlotStack(i);
            if (stack.isEmpty()) continue;
            if (!stack.getItem().getClass().equals(snapshot.itemClass())) continue;

            if (!snapshot.tag().isEmpty()) {
                if (stack.hasTag() && stack.getTag().equals(snapshot.tag())) return i;
            } else {
                return i;
            }
        }
        return -1;
    }

    /** Видаляє 1 екземпляр зі слоту, прибираючи GeckoLib-теги анімації — 1:1 з оригінального consumeSlot(). */
    @Override
    public void consumeOne(int slot) {
        ItemStack stack = getSlotStack(slot);
        if (stack.isEmpty()) return;

        if (stack.hasTag()) {
            stack.getTag().remove("geckoAnim");
            stack.getTag().remove("geckoAnimPrev");
        }

        stack.shrink(1);
        // ФІКС узгодження з оригіналом: жодного винятку для slot==40 (offhand).
        // Inventory.setItem(int, ItemStack) в net.minecraft.world.entity.player.Inventory
        // реалізований через ту саму compartments-структуру (items/armor/offhand
        // об'єднані одним індексованим фасадом), що й getItem(int) — симетрично
        // коректно приймає індекс 40. Оригінальний consumeSlot() викликав
        // setItem(slot, EMPTY) безумовно для БУДЬ-ЯКОГО слоту, включно з offhand;
        // попередня версія цього методу мала зайву умову `&& slot != 40`, яка
        // лишала б offhand-слот з count=0 стеком замість ItemStack.EMPTY після
        // витрачання останнього анімованого предмета з офхенду.
        if (stack.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private ItemStack getSlotStack(int slot) {
        if (slot == 40) {
            return player.getInventory().offhand.get(0);
        }
        if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
            return player.getInventory().getItem(slot);
        }
        return ItemStack.EMPTY;
    }

    public static HandSlot toHandSlot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? HandSlot.MAIN_HAND : HandSlot.OFF_HAND;
    }

    public static InteractionHand toMinecraftHand(HandSlot slot) {
        return slot == HandSlot.MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
}
