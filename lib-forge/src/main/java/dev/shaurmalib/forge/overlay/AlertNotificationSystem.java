package dev.shaurmalib.forge.overlay;

import dev.shaurmalib.common.overlay.AlertSpec;
import dev.shaurmalib.common.overlay.OverlayFeed;
import dev.shaurmalib.common.overlay.TimedOverlayEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Supplier;

/**
 * Узагальнена система стекованих сповіщень (план, п. 3.4 + 3.11) — заміна
 * {@code AlertNotificationOverlay} + {@code LootNotificationOverlay} +
 * {@code EventNotificationRouter} (усі три в оригіналі мали майже
 * ідентичну slide-in/hold/fade-out анімацію, що відрізнялась лише
 * тайминг-константами і кольором акценту). Один
 * {@code AlertNotificationSystem.push(feedId, AlertSpec)} замість трьох
 * окремих {@code addNotification(...)} методів у трьох різних класах.
 * <p>
 * Кожен окремий "фід" (напр. kill-feed у правому верхньому куті й окремий
 * loot-фід нижче нього) — власний {@link OverlayFeed}, зареєстрований під
 * своїм {@code feedId} — так само, як зараз kill-feed і loot-feed
 * рендерились двома різними {@code IGuiOverlay}-класами; тут це один
 * рушій із двома незалежними стеками стану.
 */
@OnlyIn(Dist.CLIENT)
public final class AlertNotificationSystem {

    private static final int H_PAD = 12;
    private static final int MIN_W = 120;
    private static final int MAX_W = 260;
    private static final int MARGIN = 8;
    private static final int GAP = 4;

    private static final java.util.Map<String, OverlayFeed> feeds = new java.util.HashMap<>();

    private AlertNotificationSystem() {}

    /**
     * Реєструє новий фід у правому верхньому куті (стандартне розташування
     * kill-feed з оригіналу). {@code topOffsetSupplier} — консюмер сам
     * вирішує, чи зсунути фід нижче для конкретного режиму (в оригіналі —
     * {@code SCN_TOP_OFFSET=20}, бо HUD SCN займає верх екрана), рушій не
     * хардкодить перевірку конкретного режиму.
     */
    public static void registerFeed(String feedId, Supplier<Integer> topOffsetSupplier) {
        feeds.putIfAbsent(feedId, new OverlayFeed());
        OverlayEngine.register("shaurma_alert_feed_" + feedId,
                (gui, graphics, partialTick, sw, sh) -> render(feedId, graphics, sw, sh, topOffsetSupplier.get()));
    }

    /** Варіант без динамічного зсуву — фід завжди прилипає до {@code MARGIN}. */
    public static void registerFeed(String feedId) {
        registerFeed(feedId, () -> 0);
    }

    public static void push(String feedId, AlertSpec spec) {
        OverlayFeed feed = feeds.computeIfAbsent(feedId, k -> new OverlayFeed());
        feed.push(new TimedOverlayEntry(spec.text(), spec.accentArgb(), spec.timings()));
    }

    public static List<TimedOverlayEntry> historySnapshot(String feedId) {
        OverlayFeed feed = feeds.get(feedId);
        return feed == null ? java.util.List.of() : feed.historySnapshot();
    }

    public static void resetHistory(String feedId) {
        OverlayFeed feed = feeds.get(feedId);
        if (feed != null) feed.reset();
    }

    private static void render(String feedId, GuiGraphics g, int sw, int sh, int topOffset) {
        OverlayFeed feed = feeds.get(feedId);
        if (feed == null) return;
        long now = System.currentTimeMillis();
        List<TimedOverlayEntry> active = feed.tick(now);
        if (active.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();

        for (int i = 0; i < active.size(); i++) {
            TimedOverlayEntry entry = active.get(i);
            float alpha = entry.alphaAt(now);
            if (alpha <= 0.004f) continue;

            Component message = Component.literal(entry.text());
            List<FormattedCharSequence> lines = mc.font.split(message, MAX_W - H_PAD * 2);
            int lineCount = Math.max(1, lines.size());
            int panelH = H_PAD / 2 + lineCount * mc.font.lineHeight + (lineCount - 1) * 2 + H_PAD / 2;

            int maxLineW = 0;
            for (FormattedCharSequence line : lines) maxLineW = Math.max(maxLineW, mc.font.width(line));
            int w = Math.min(MAX_W, Math.max(MIN_W, maxLineW + H_PAD * 2));

            float slideEased = entry.slideProgressAt(now);
            float fadeProgress = entry.fadeOutProgressAt(now);
            float slideX = w * (1f - slideEased) * (1f - fadeProgress);
            int x = sw - w - MARGIN + (int) slideX;

            final int feedIndex = i;
            float yOffset = feed.verticalOffset(feedIndex, now, GAP,
                    e -> H_PAD / 2 + Math.max(1, mc.font.split(Component.literal(e.text()), MAX_W - H_PAD * 2).size())
                            * mc.font.lineHeight + H_PAD / 2);
            int y = MARGIN + topOffset + i * (panelH + GAP) + (int) yOffset;

            OverlayPanelStyle.drawPanel(g, x, y, w, panelH, alpha, entry.accentArgb());

            float textAlpha = Math.min(1f, Math.max(0f, ((float) entry.ageMs(now) - 60f) / (entry.timings().slideMs() * 0.8f)));
            textAlpha *= alpha;
            if (textAlpha > 0.01f) {
                int baseTextY = y + H_PAD / 2;
                for (int li = 0; li < lines.size(); li++) {
                    g.drawString(mc.font, lines.get(li), x + H_PAD,
                            baseTextY + li * (mc.font.lineHeight + 2),
                            OverlayPanelStyle.applyAlpha(OverlayPanelStyle.TEXT_DEFAULT, textAlpha), true);
                }
            }
        }
    }
}
