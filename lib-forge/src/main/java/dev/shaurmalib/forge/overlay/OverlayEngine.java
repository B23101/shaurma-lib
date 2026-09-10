package dev.shaurmalib.forge.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Центральний рушій оверлеїв (план, п. 3.4) — заміна 40+ окремих
 * {@code event.registerAboveAll("name", new XxxOverlay())} викликів у
 * {@code ClientRegistration.registerOverlays(...)} snipers_shaurma одним
 * реальним {@link IGuiOverlay}, зареєстрованим під іменем
 * {@code "shaurma_overlay_stack"}. Кожен зареєстрований у бібліотеці
 * "жилець" (kill-feed, kit-actionbar, countdown, world-tint, ...) більше
 * НЕ отримує власного {@code registerAboveAll(...)} запису й окремого
 * Java-класу, що імплементує {@code IGuiOverlay}; він реєструється тут
 * через {@link #register(String, OverlayRenderable)}, а сам движок один
 * раз підключається до Forge в {@link #attach(RegisterGuiOverlaysEvent)}.
 * <p>
 * Порядок рендеру всередині стеку — порядок реєстрації (як список, не
 * Forge-шар пріоритетів) — це свідомо простіше за {@code registerAboveAll}
 * матрицю з оригіналу, бо всередині ОДНОГО {@code IGuiOverlay}-хука немає
 * сенсу в Forge-рівні {@code above/below}-відносинах: якщо порядок двох
 * підсистем лібу важливий один відносно одного (напр. world-tint має
 * малюватись під kill-feed), консюмер реєструє їх у потрібному порядку.
 * <p>
 * {@code visible} — необов'язковий {@link BooleanSupplier} на кожен запис
 * (аналог перевірок типу {@code ClientModOptions...alertPanelEnabled} чи
 * {@code DeathCameraHandler.isActive()}, розкиданих по кожному
 * оригінальному класу-оверлею) — рушій сам ховає підсистему без того, щоб
 * кожна підсистема окремо перевіряла це у власному {@code render(...)}.
 */
public final class OverlayEngine {

    /** Функціональний контракт одного "жильця" стеку — той самий сигнатурний набір, що {@link IGuiOverlay#render}. */
    @FunctionalInterface
    public interface OverlayRenderable {
        void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight);
    }

    private static final class Entry {
        final String id;
        final OverlayRenderable renderable;
        final BooleanSupplier visible;

        Entry(String id, OverlayRenderable renderable, BooleanSupplier visible) {
            this.id = id;
            this.renderable = renderable;
            this.visible = visible;
        }
    }

    private static final List<Entry> entries = new ArrayList<>();

    private OverlayEngine() {}

    /** Реєструє жильця стеку, завжди видимого (немає умови приховування). */
    public static void register(String id, OverlayRenderable renderable) {
        register(id, renderable, () -> true);
    }

    /**
     * Реєструє жильця стеку з умовою видимості — виконується щокадру ПЕРЕД
     * {@code renderable.render(...)}, щоб приховані підсистеми (наприклад
     * вимкнена в опціях панель, або активна {@code DeathCameraHandler}, що
     * має ховати kill-feed) не витрачали час навіть на власний early-return.
     */
    public static void register(String id, OverlayRenderable renderable, BooleanSupplier visible) {
        entries.removeIf(e -> e.id.equals(id));
        entries.add(new Entry(id, renderable, visible));
    }

    public static void unregister(String id) {
        entries.removeIf(e -> e.id.equals(id));
    }

    /**
     * Одноразовий виклик у конструкторі мода (клієнтський сетап), реєструє
     * єдиний реальний {@link IGuiOverlay} у Forge під іменем
     * {@code "shaurma_overlay_stack"}. Consumer більше нічого не реєструє
     * сам через {@code RegisterGuiOverlaysEvent} для будь-якого модуля лібу.
     */
    @OnlyIn(Dist.CLIENT)
    public static void attach(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("shaurma_overlay_stack", STACK_OVERLAY);
    }

    @OnlyIn(Dist.CLIENT)
    private static final IGuiOverlay STACK_OVERLAY = (gui, graphics, partialTick, sw, sh) -> {
        // Копія списку не потрібна — рендер завжди на клієнтському тіку
        // (єдиний потік), а register/unregister відбуваються здебільшого
        // при вході/виході з режиму, не посеред кадру рендеру.
        for (Entry entry : entries) {
            if (!entry.visible.getAsBoolean()) continue;
            entry.renderable.render((ForgeGui) gui, graphics, partialTick, sw, sh);
        }
    };
}
