package dev.shaurmalib.forge.radio;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.shaurmalib.forge.overlay.OverlayEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Radio commander dialog overlay (план, п. 3.33) — узагальнення
 * {@code core.client.gui.overlay.hud.RadioDialogOverlay} snipers_shaurma
 * (390 рядків), 1:1 з тим самим візуальним дизайном:
 * <ul>
 *   <li>жодного фонового прямокутника/панелі — лише текст і 3D-іконка
 *       предмету "пливуть" над хотбаром;</li>
 *   <li>один рядок тексту, буквений reveal з punch-in масштабом,
 *       color-fade золотий → білий на щойно з'явлених символах,
 *       marquee-скрол якщо текст ширший за зону;</li>
 *   <li>typewriter-курсор, поки триває друк.</li>
 * </ul>
 * <p>
 * <b>Відмінність від оригіналу</b>: рендер підключається як звичайний
 * жилець {@link OverlayEngine} (реєструється в {@code attach(...)}), а
 * не власний {@code IGuiOverlay}/{@code registerAboveAll(...)} — не
 * потребує окремого запису в {@code RegisterGuiOverlaysEvent} консюмера.
 * Умова видимості (в оригіналі — {@code ClientModOptions...radioDialogOverlayEnabled}
 * і {@code DeathCameraHandler.isActive()}) винесена в {@code BooleanSupplier},
 * переданий у {@link #attach(java.util.function.BooleanSupplier)} —
 * бібліотека не хардкодить назву консюмерської опції чи наявність
 * системи death-камери.
 */
@OnlyIn(Dist.CLIENT)
public class RadioDialogOverlay {

    private static RadioDialogOverlay INSTANCE;

    public static RadioDialogOverlay getInstance() {
        if (INSTANCE == null) INSTANCE = new RadioDialogOverlay();
        return INSTANCE;
    }

    private static final int ABOVE_HOTBAR = 16;

    private static final int ANIM_TICKS = 10;
    private static final int RIGHT_MARGIN = 10;
    private static final int SCROLL_SPEED = 2;

    private static final long SLIDE_MS = 300L;

    private static final int ACCENT_GOLD = 0xFFD86A;
    private static final int COLOR_TEXT = 0xFFFFFF;

    private boolean active = false;
    private String rawText = "", displayText = "", modelItemId = "", soundId = "";
    private int durationTicks = 80, holdTicks = 40;
    private int charIndex = 0, typeTick = 0, holdTimer = 0;
    private float charsPerTick = 1f;
    private boolean typing = true, typingComplete = false;
    private final float[] charAnim = new float[128];
    private boolean fadingIn = false, fadingOut = false;
    private float fadeProgress = 0f, iconScale = 0f;

    private int scrollOffset = 0;
    private boolean scrolling = false;
    private int lastKnownAvailableW = 0;

    private long showStartMs = 0L;
    private long hideStartMs = 0L;

    private RadioDialogOverlay() {}

    /**
     * Підключає рушій до {@link OverlayEngine} — викликати один раз з
     * {@code RadioModuleHook.onAttach(...)}.
     *
     * @param visible консюмерська умова показу (опції, death-камера тощо);
     *                {@code () -> true}, якщо таких умов немає.
     */
    public static void attach(java.util.function.BooleanSupplier visible) {
        OverlayEngine.register("shaurma_radio_dialog", (gui, gfx, partialTick, sw, sh) ->
                getInstance().render(gfx, sw, sh), visible);
    }

    // ── API ──────────────────────────────────────────────────────────────
    public void show(String text, String modelItemId, String soundId, int durationTicks, int holdTicks) {
        RadioAudioDucking.stop();
        this.rawText = text == null ? "" : text;
        this.displayText = this.rawText;
        this.modelItemId = modelItemId == null ? "" : modelItemId;
        this.soundId = soundId == null ? "" : soundId;
        this.durationTicks = Math.max(10, durationTicks);
        this.holdTicks = Math.max(0, holdTicks);
        this.charIndex = 0;
        this.typeTick = 0;
        this.typing = true;
        this.typingComplete = false;
        this.holdTimer = 0;
        this.scrollOffset = 0;
        this.scrolling = false;
        for (int i = 0; i < charAnim.length; i++) charAnim[i] = 0f;
        int total = displayText.length();
        this.charsPerTick = total > 0 ? (float) total / this.durationTicks : 999f;
        this.fadingIn = true;
        this.fadingOut = false;
        this.fadeProgress = 0f;
        this.iconScale = 0f;
        this.active = true;
        this.showStartMs = System.currentTimeMillis();
        this.hideStartMs = 0L;
        RadioAudioDucking.play(soundId);
    }

    public void hide() {
        if (active) startFadeOut();
    }

    public boolean isActive() {
        return active;
    }

    // ── Tick ─────────────────────────────────────────────────────────────
    @Mod.EventBusSubscriber(modid = "shaurma_lib", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    private static final class TickListener {
        @SubscribeEvent
        static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            getInstance().tick();
        }
    }

    public void tick() {
        if (!active) return;
        RadioAudioDucking.tick();
        if (fadingIn) {
            fadeProgress += 1f / ANIM_TICKS;
            iconScale = easeOutBack(fadeProgress);
            if (fadeProgress >= 1f) {
                fadeProgress = 1f;
                iconScale = 1f;
                fadingIn = false;
            }
        }
        if (fadingOut) {
            fadeProgress -= 1f / ANIM_TICKS;
            iconScale = Math.max(0f, fadeProgress);
            if (fadeProgress <= 0f) {
                fadeProgress = 0f;
                fadingOut = false;
                active = false;
                RadioAudioDucking.stop();
                return;
            }
        }
        if (typing && !fadingIn && !fadingOut) {
            typeTick++;
            int target = Math.min((int) (typeTick * charsPerTick), displayText.length());
            if (target > charIndex) charIndex = target;
            if (charIndex >= displayText.length()) {
                typing = false;
                typingComplete = true;
                holdTimer = 0;
            }
        }
        for (int i = 0; i < charIndex && i < charAnim.length; i++) {
            if (charAnim[i] < 1f) charAnim[i] = Math.min(1f, charAnim[i] + 0.22f);
        }

        // Скрол рахується тут (не в render!), синхронно з lastKnownAvailableW,
        // яке оновлюється в render() кожен кадр — уникає розбіжності
        // "scrollOffset ніколи не досягає maxScroll" при різних guiScaledWidth.
        boolean textOverflows = lastKnownAvailableW > 0 && fontWidth(displayText) > lastKnownAvailableW;
        if (textOverflows) {
            scrolling = true;
            int maxScroll = Math.max(0, fontWidth(displayText) - lastKnownAvailableW);
            scrollOffset = Math.min(scrollOffset + SCROLL_SPEED, maxScroll);
            boolean scrollDone = scrollOffset >= maxScroll;
            if (typingComplete && scrollDone && !fadingOut) {
                holdTimer++;
                if (holdTimer >= holdTicks) startFadeOut();
            }
        } else {
            scrollOffset = 0;
            scrolling = false;
            if (typingComplete && !fadingOut) {
                holdTimer++;
                if (holdTimer >= holdTicks) startFadeOut();
            }
        }
    }

    private static int fontWidth(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || s == null) return 0;
        return mc.font.width(s);
    }

    // ── Render ───────────────────────────────────────────────────────────
    private void render(GuiGraphics gfx, int sw, int sh) {
        if (!active || fadeProgress <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;

        int lineH = font.lineHeight;
        int iconSize = lineH + 4;
        int gap = 6;

        long now = System.currentTimeMillis();
        long sinceShow = now - showStartMs;
        long sinceHide = hideStartMs > 0 ? (now - hideStartMs) : 0;

        int alpha = (int) (fadeProgress * 255);

        float slideT = fadingOut ? clamp01(1f - (float) sinceHide / SLIDE_MS)
                : clamp01(1f - (float) sinceShow / SLIDE_MS);
        int slideY = (int) (easeOutCubic(slideT) * 10f);

        int zoneW = sw / 2;
        int startX = (sw - zoneW) / 2;
        int availableTextW = zoneW - iconSize - gap;
        lastKnownAvailableW = availableTextW;

        int hotbarTop = sh - 22;
        int rowY = hotbarTop - ABOVE_HOTBAR - Math.max(lineH, iconSize);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int iconX = startX;
        int iconY = rowY + (Math.max(lineH, iconSize) - iconSize) / 2 + slideY;
        int textX = startX + iconSize + gap;
        int textY = rowY + (Math.max(lineH, iconSize) - lineH) / 2 + slideY;

        // Punch-in іконки (без pose().scale() навколо renderItem — див.
        // коментар у renderModelIcon щодо чому саме так) виражений лише
        // через альфу появи, синхронізовану з тим самим fadeProgress, що
        // й решта картки.
        int iconAlpha = (int) (Math.min(1f, iconScale) * alpha);
        renderModelIcon(gfx, mc, iconX, iconY, iconSize, iconAlpha);

        int xOff = 0;
        int textEndX = textX + availableTextW;

        gfx.enableScissor(textX, rowY - 4, textEndX, rowY + Math.max(lineH, iconSize) + 4);

        int firstAnimatingIdx = charIndex;
        for (int ci = 0; ci < charIndex && ci < charAnim.length; ci++) {
            if (charAnim[ci] < 1f) {
                firstAnimatingIdx = ci;
                break;
            }
        }

        if (firstAnimatingIdx > 0) {
            String settledPart = displayText.substring(0, Math.min(firstAnimatingIdx, displayText.length()));
            int settledW = font.width(settledPart);
            int visualX = textX - scrollOffset;
            if (visualX + settledW > textX && visualX < textEndX) {
                gfx.drawString(font, settledPart, visualX, textY, (alpha << 24) | COLOR_TEXT, true);
            }
            xOff = settledW;
        }

        for (int ci = firstAnimatingIdx; ci < charIndex && ci < displayText.length(); ci++) {
            String ch = String.valueOf(displayText.charAt(ci));
            int charW = font.width(ch);
            int visualX = textX + xOff - scrollOffset;

            if (visualX + charW > textX && visualX < textEndX) {
                float ca = ci < charAnim.length ? charAnim[ci] : 1f;
                float chAlpha = easeOutCubic(ca);
                float chScale = 0.72f + ca * 0.28f;
                float chRise = (1f - ca) * 4f;

                int aMain = (int) (chAlpha * alpha);
                int chColor = lerpColor(
                        (aMain << 24) | (ACCENT_GOLD & 0xFFFFFF),
                        (aMain << 24) | COLOR_TEXT,
                        ca);

                gfx.pose().pushPose();
                gfx.pose().translate(visualX, textY + chRise, 0);
                gfx.pose().scale(chScale, chScale, 1f);
                gfx.drawString(font, ch, 0, 0, chColor, true);
                gfx.pose().popPose();
            }
            xOff += charW;
        }

        if (typing && !typingComplete) {
            long blinkPhase = now % 500L;
            if (blinkPhase < 350L) {
                int caretA = (int) (alpha * 0.85f);
                int caretX = textX + xOff - scrollOffset;
                if (caretX + 2 > textX && caretX < textEndX) {
                    gfx.fill(caretX + 1, textY, caretX + 2, textY + lineH,
                            (caretA << 24) | (ACCENT_GOLD & 0xFFFFFF));
                }
            }
        }

        gfx.disableScissor();
        RenderSystem.disableBlend();
    }

    private void renderModelIcon(GuiGraphics gfx, Minecraft mc, int x, int y, int size, int alpha) {
        if (modelItemId.isEmpty()) return;
        try {
            String[] parts = modelItemId.contains(":") ? modelItemId.split(":", 2)
                    : new String[]{"shaurma_lib", modelItemId};
            ResourceLocation rl = new ResourceLocation(parts[0], parts[1]);
            var item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null) return;
            ItemStack stack = new ItemStack(item);
            // renderItem завжди малює в межах 16x16 (як і решта оверлеїв
            // бібліотеки, напр. PhantomSlotHudOverlay) — тут лише
            // центруємо цей фіксований розмір усередині виділеної
            // iconSize-зони, без матричного pose().scale(...) навколо
            // renderItem: той внутрішньо сам керує PoseStack і 3D-глибиною
            // предмета, тож зовнішній scale легко ламає z-порядок/підсвітку
            // на різних GL-драйверах — тому не переносимо цей ризик з
            // нуля, а лишаємось на перевіреному в оригіналі підході.
            int itemX = x + (size - 16) / 2;
            int itemY = y + (size - 16) / 2;
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
            gfx.renderItem(stack, itemX, itemY);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } catch (Exception ignored) {
            // Некоректний ідентифікатор — без іконки, без фонової заглушки (мінімалізм).
        }
    }

    private void startFadeOut() {
        fadingOut = true;
        typingComplete = false;
        if (hideStartMs == 0L) hideStartMs = System.currentTimeMillis();
    }

    // ── Easing/utils ─────────────────────────────────────────────────────
    private float easeOutCubic(float t) {
        t = clamp01(t);
        return 1 - (float) Math.pow(1 - t, 3);
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ba + (bb - ba) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}
