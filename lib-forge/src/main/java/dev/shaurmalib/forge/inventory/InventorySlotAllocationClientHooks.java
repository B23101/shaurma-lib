package dev.shaurmalib.forge.inventory;

import dev.shaurmalib.forge.network.packets.InventorySlotAllocationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT)
public final class InventorySlotAllocationClientHooks {

    @FunctionalInterface
    public interface HotbarRenderer {
        void render(GuiGraphics graphics, Minecraft minecraft, List<Integer> allowedSlots,
                    float partialTick, int screenWidth, int screenHeight);
    }

    private static volatile Supplier<? extends Screen> customInventoryScreen;
    private static volatile HotbarRenderer customHotbarRenderer;

    private InventorySlotAllocationClientHooks() {}

    public static void setCustomInventoryScreen(Supplier<? extends Screen> screenFactory) {
        customInventoryScreen = screenFactory;
    }

    public static void setCustomHotbarRenderer(HotbarRenderer renderer) {
        customHotbarRenderer = renderer;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof InventoryScreen
                && InventorySlotAllocationPacket.ClientHandler.isInventoryScreenBlocked()) {
            Supplier<? extends Screen> factory = customInventoryScreen;
            if (factory == null) {
                event.setCanceled(true);
            } else {
                Screen replacement = factory.get();
                if (replacement == null) {
                    throw new IllegalStateException("Кастомний inventory screen factory повернув null.");
                }
                event.setNewScreen(replacement);
            }
        }
    }

    /**
     * Колесо миші перехоплюється ДО ванільної обробки (onMouseScroll,
     * {@link #onMouseScroll}) — тому в нормальному випадку selected уже
     * стоїть на дозволеному слоті ще до кінця цього ж кадру, і цей
     * тіковий обробник ніколи не бачить заборонений слот.
     * <p>
     * Лишається як страховка на випадок, коли selected став забороненим
     * НЕ через скрол (наприклад сервер щойно звузив allowedMask, або
     * гравець перемкнувся клавішею 1-9 — той шлях перехоплювач скролу
     * не бачить). У цьому випадку немає "напрямку", з якого прийшов
     * гравець, тому найближчий дозволений слот шукається в обидва боки
     * одночасно (по колу з 9 слотів) — а не завжди перший зліва, як
     * було раніше: той старий варіант і був причиною бага "скрол вліво
     * не працює", бо він переписував БУДЬ-яку заборонену позицію на
     * слот 0, включно з тією, що скрол щойно правильно виставив сам.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !InventorySlotAllocationPacket.ClientHandler.isRestricted()) {
            return;
        }

        int selected = minecraft.player.getInventory().selected;
        if (InventorySlotAllocationPacket.ClientHandler.isAllowed(selected)) {
            return;
        }
        int nearest = nearestAllowedSlot(selected);
        if (nearest >= 0) {
            minecraft.player.getInventory().selected = nearest;
        }
    }

    /**
     * Перехоплює скрол колеса ДО того, як ванільний
     * {@code MouseHandler.onScroll}/{@code Inventory.swapPaint} застосує
     * його — інакше вибір слота на один кадр "мигтів" би на забороненій
     * позиції, поки {@link #onClientTick} не встиг би її виправити.
     * <p>
     * ── Баг, який це виправляє ─────────────────────────────────────────
     * Ванільний скрол циклічно змінює selected на ±1 з обгортанням по
     * ВСІХ 9 слотах (0↔8). У обмеженому інвентарі дозволені слоти —
     * довільна підмножина (наприклад лише 0,1,2) — тому крок ±1 по
     * ванільній шкалі майже завжди потрапляє на заборонений слот, і
     * старий {@code onClientTick} виправляв це, ставлячи ЗАВЖДИ перший
     * дозволений слот зліва (0) — незалежно від того, в який бік гравець
     * скролив. Тому скрол "вліво" (з першого дозволеного слота на
     * обгортання назад) завжди повертав на початок списку замість
     * переходу на ОСТАННІЙ дозволений слот, як очікує гравець.
     * <p>
     * Тут замість цього напрямок скролу рахується явно і застосовується
     * до ЛОГІЧНОГО списку дозволених слотів (не до фізичних індексів
     * 0-8), з обгортанням по цьому списку — тобто скрол вліво з першого
     * дозволеного слота коректно переходить на останній дозволений, а
     * не на нульовий.
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event.getScrollDeltaY() == 0) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || !InventorySlotAllocationPacket.ClientHandler.isRestricted()) {
            return;
        }

        List<Integer> allowed = allowedHotbarSlots();
        if (allowed.isEmpty()) return;

        // Ванільна конвенція: додатний scrollDeltaY (колесо "від себе")
        // рухає вибір ВЛІВО по хотбару (selected зменшується); від'ємний
        // — вправо. direction тут виражає той самий знак у термінах
        // кроку по allowed-списку.
        int direction = event.getScrollDeltaY() > 0 ? -1 : 1;

        int selected = minecraft.player.getInventory().selected;
        int currentIndex = allowed.indexOf(selected);
        int nextIndex;
        if (currentIndex < 0) {
            // Обраний слот уже поза дозволеними (наприклад тільки-но
            // звужена allocation) — найближчий у напрямку скролу.
            nextIndex = direction > 0 ? 0 : allowed.size() - 1;
        } else {
            nextIndex = Math.floorMod(currentIndex + direction, allowed.size());
        }

        minecraft.player.getInventory().selected = allowed.get(nextIndex);
        event.setCanceled(true);
    }

    /**
     * Найближчий дозволений слот до {@code from} у ФІЗИЧНИХ індексах
     * 0-8, шукаючи в обидва боки одночасно (по колу) — використовується
     * лише як страховка в {@link #onClientTick}, коли немає відомого
     * напрямку скролу. -1, якщо жодного дозволеного слота немає.
     */
    private static int nearestAllowedSlot(int from) {
        for (int distance = 1; distance <= 9; distance++) {
            int up = Math.floorMod(from + distance, 9);
            if (InventorySlotAllocationPacket.ClientHandler.isAllowed(up)) return up;
            int down = Math.floorMod(from - distance, 9);
            if (InventorySlotAllocationPacket.ClientHandler.isAllowed(down)) return down;
        }
        return -1;
    }

    @SubscribeEvent
    public static void onHotbarPre(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())
                || !InventorySlotAllocationPacket.ClientHandler.isRestricted()) {
            return;
        }

        event.setCanceled(true);
        List<Integer> allowedSlots = allowedHotbarSlots();
        HotbarRenderer renderer = customHotbarRenderer;
        if (renderer != null) {
            renderer.render(event.getGuiGraphics(), Minecraft.getInstance(), allowedSlots,
                    event.getPartialTick(), event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight());
        } else {
            renderAllocatedHotbar(event.getGuiGraphics(), allowedSlots);
        }
    }

    private static List<Integer> allowedHotbarSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            if (InventorySlotAllocationPacket.ClientHandler.isAllowed(slot)) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    private static void renderAllocatedHotbar(GuiGraphics graphics, List<Integer> slots) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()) return;

        if (slots.isEmpty()) return;

        int slotSize = 20;
        int left = (minecraft.getWindow().getGuiScaledWidth() - slots.size() * slotSize) / 2;
        int top = minecraft.getWindow().getGuiScaledHeight() - 22;

        for (int index = 0; index < slots.size(); index++) {
            int inventorySlot = slots.get(index);
            int x = left + index * slotSize;
            boolean selected = minecraft.player.getInventory().selected == inventorySlot;
            int background = selected ? 0xFFB0B0B0 : 0xAA202020;

            graphics.fill(x, top, x + slotSize, top + slotSize, background);
            graphics.fill(x + 1, top + 1, x + slotSize - 1, top + slotSize - 1, 0xAA000000);

            ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 2, top + 2);
                graphics.renderItemDecorations(minecraft.font, stack, x + 2, top + 2);
            }
        }
    }
}
