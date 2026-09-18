package dev.shaurmalib.forge.overlay;

import dev.shaurmalib.common.overlay.AlertSpec;
import dev.shaurmalib.common.overlay.OverlayFeed;
import dev.shaurmalib.common.overlay.OverlayTimings;
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
    /** Максимум видимих рядків у сплеші — довше повідомлення обрізається з «…» на останньому рядку. */
    private static final int MAX_LINES = 3;
    /** На скільки px запис «з'їжджає» вниз за повний collapse-прогрес (0..1). */
    private static final int COLLAPSE_DROP_PX = 10;

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

    /**
     * Стекає чат-повідомлення, намальоване тим самим {@link dev.shaurmalib.forge.chat.ChatEntryRenderer},
     * що й {@code ChatHistoryScreen} (голова гравця, нік кольором групи,
     * текст під ним) — замість голого текстового {@link AlertSpec}.
     * Без цього спливаюче сповіщення виглядало інакше, ніж той самий
     * запис у журналі чату.
     */
    public static void pushChatEntry(String feedId, dev.shaurmalib.common.chat.ChatEntry entry,
                                      int accentArgb, OverlayTimings timings) {
        OverlayFeed feed = feeds.computeIfAbsent(feedId, k -> new OverlayFeed());
        String plain = entry.rawText() != null ? entry.rawText()
                : (entry.message() != null ? entry.message().getString() : "");
        feed.push(new TimedOverlayEntry(plain, accentArgb, timings, System.currentTimeMillis(), entry));
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

            boolean isChatEntry = entry.payload() instanceof dev.shaurmalib.common.chat.ChatEntry;
            dev.shaurmalib.forge.chat.ChatEntryRenderer chatRenderer = isChatEntry
                    ? dev.shaurmalib.forge.chat.ChatEntryRendererRegistry.get() : null;
            dev.shaurmalib.common.chat.ChatEntry chatEntry = isChatEntry
                    ? (dev.shaurmalib.common.chat.ChatEntry) entry.payload() : null;

            List<FormattedCharSequence> lines = isChatEntry ? null : clampLines(mc, entry.text(), MAX_W - H_PAD * 2);
            int contentW = MAX_W - H_PAD * 2;
            int lineCount;
            int panelH;
            int w;
            if (isChatEntry) {
                // Ліміт рядків застосовуємо, обрізаючи "сирий" текст ще до передачі
                // в ChatEntryRenderer — сама верстка (голова+нік+текст) не знає
                // про MAX_LINES, тому підрізати мусимо тут, на рівні даних.
                dev.shaurmalib.common.chat.ChatEntry clamped = clampChatEntry(mc, chatEntry, contentW);
                int contentH = chatRenderer.contentHeight(clamped, mc.font, contentW);
                panelH = H_PAD / 2 + contentH + H_PAD / 2;
                w = MAX_W;
                lineCount = 1; // не використовується нижче для chat-шляху
            } else {
                lineCount = Math.max(1, lines.size());
                panelH = H_PAD / 2 + lineCount * mc.font.lineHeight + (lineCount - 1) * 2 + H_PAD / 2;
                int maxLineW = 0;
                for (FormattedCharSequence line : lines) maxLineW = Math.max(maxLineW, mc.font.width(line));
                w = Math.min(MAX_W, Math.max(MIN_W, maxLineW + H_PAD * 2));
            }

            float slideEased = entry.slideProgressAt(now);
            float fadeProgress = entry.fadeOutProgressAt(now);
            float slideX = w * (1f - slideEased) * (1f - fadeProgress);
            int x = sw - w - MARGIN + (int) slideX;

            final int feedIndex = i;
            float yOffset = feed.verticalOffset(feedIndex, now, GAP, e -> heightOf(mc, e, contentW));
            // Довге (обрізане до MAX_LINES) повідомлення через collapseAfterMs повільно
            // з'їжджає вниз і тане (collapseProgressAt), а не займає місце в стеку до
            // звичайного fade — тому не тіснить сповіщення, що прийшли після нього.
            float collapseDrop = COLLAPSE_DROP_PX * entry.collapseProgressAt(now);
            int y = MARGIN + topOffset + i * (panelH + GAP) + (int) yOffset + (int) collapseDrop;

            OverlayPanelStyle.drawPanel(g, x, y, w, panelH, alpha, entry.accentArgb());

            float textAlpha = Math.min(1f, Math.max(0f, ((float) entry.ageMs(now) - 60f) / (entry.timings().slideMs() * 0.8f)));
            textAlpha *= alpha;
            if (textAlpha <= 0.01f) continue;

            if (isChatEntry) {
                dev.shaurmalib.common.chat.ChatEntry clamped = clampChatEntry(mc, chatEntry, contentW);
                int contentH = chatRenderer.contentHeight(clamped, mc.font, contentW);
                g.pose().pushPose();
                applyTextAlpha(g, textAlpha);
                chatRenderer.render(g, clamped, mc.font, x + H_PAD, y + H_PAD / 2, contentW, contentH, entry.accentArgb());
                g.pose().popPose();
            } else {
                int baseTextY = y + H_PAD / 2;
                for (int li = 0; li < lines.size(); li++) {
                    g.drawString(mc.font, lines.get(li), x + H_PAD,
                            baseTextY + li * (mc.font.lineHeight + 2),
                            OverlayPanelStyle.applyAlpha(OverlayPanelStyle.TEXT_DEFAULT, textAlpha), true);
                }
            }
        }
    }

    /** Висота запису для стискання стеку — гілка chat делегує в {@code ChatEntryRenderer}. */
    private static int heightOf(Minecraft mc, TimedOverlayEntry e, int contentW) {
        if (e.payload() instanceof dev.shaurmalib.common.chat.ChatEntry chatEntry) {
            dev.shaurmalib.forge.chat.ChatEntryRenderer renderer = dev.shaurmalib.forge.chat.ChatEntryRendererRegistry.get();
            dev.shaurmalib.common.chat.ChatEntry clamped = clampChatEntry(mc, chatEntry, contentW);
            return H_PAD / 2 + renderer.contentHeight(clamped, mc.font, contentW) + H_PAD / 2;
        }
        return H_PAD / 2 + Math.max(1, clampLines(mc, e.text(), contentW).size())
                * mc.font.lineHeight + H_PAD / 2;
    }

    /**
     * {@code drawString}/{@code ChatEntryRenderer.render} самі не приймають
     * альфу — панель і дефолтний текстовий шлях кодують її прямо в колір
     * ARGB (див. {@code OverlayPanelStyle.applyAlpha}). {@code ChatEntryRenderer}
     * — SPI консюмера, тож без зміни його контракту тут ми лише лишаємо
     * місце для майбутнього fade кастомної верстки; наразі малюємо на
     * повній альфі тексту (панель під ним усе одно вже затухає, бо
     * {@code drawPanel} застосовує {@code alpha} до фону/рамки).
     */
    private static void applyTextAlpha(GuiGraphics g, float textAlpha) {
        // Навмисно порожньо: див. javadoc. Лишено як явна точка розширення,
        // якщо ChatEntryRenderer отримає параметр альфи в майбутньому.
    }

    /**
     * Обрізає {@code rawText} чат-запису до {@link #MAX_LINES} видимих
     * рядків з «…» на останньому — {@code senderName}/{@code colorHex}/
     * {@code senderUuid} (голова, нік) лишаються без змін, обрізається
     * лише текст повідомлення.
     */
    private static dev.shaurmalib.common.chat.ChatEntry clampChatEntry(
            Minecraft mc, dev.shaurmalib.common.chat.ChatEntry entry, int contentW) {
        String raw = entry.rawText();
        if (raw == null) return entry;
        int textW = entry.isPlayerMessage() ? Math.max(1, contentW) : contentW;
        List<FormattedCharSequence> lines = mc.font.split(Component.literal(raw), textW);
        if (lines.size() <= MAX_LINES) return entry;
        String clampedText = plainTailForWidth(mc, raw, textW, MAX_LINES) + "…";
        return new dev.shaurmalib.common.chat.ChatEntry(entry.type(), entry.message(), entry.senderUuid(),
                entry.channelId(), entry.senderName(), clampedText, entry.colorHex(), entry.timestampMs());
    }

    /**
     * Розбиває текст на рядки під ширину панелі й обрізає до {@link #MAX_LINES}:
     * якщо рядків більше, останній видимий рядок ущільнюється символами, доки
     * не звільниться місце для «…» в кінці. Без цього довге чат-повідомлення
     * розтягувало панель на весь екран замість фіксованих 1-3 рядків.
     */
    private static List<FormattedCharSequence> clampLines(Minecraft mc, String text, int maxWidth) {
        Component message = Component.literal(text == null ? "" : text);
        List<FormattedCharSequence> lines = mc.font.split(message, maxWidth);
        if (lines.size() <= MAX_LINES) return lines;

        List<FormattedCharSequence> visible = new java.util.ArrayList<>(lines.subList(0, MAX_LINES));
        // Останній видимий рядок відновлюємо з сирого тексту (а не форматованого
        // FormattedCharSequence, який важко вкорочувати напряму) і доточуємо "…".
        String plain = message.getString();
        String lastLinePlain = plainTailForWidth(mc, plain, maxWidth, MAX_LINES);
        visible.set(MAX_LINES - 1, Component.literal(lastLinePlain + "…").getVisualOrderText());
        return visible;
    }

    /**
     * Знаходить текст ОСТАННЬОГО видимого рядка (після {@code Font.split}
     * на {@code targetLineIndex} рядків) і вкорочує його під ширину з
     * запасом на «…». {@code Font.split} ріже по межах слів/символів
     * незалежно для кожного рядка, тому просто взяти весь {@code plain} і
     * підрізати під ширину ОДНОГО рядка (як робив попередній наївний
     * варіант) для багаторядкового тексту повертало б хвіст ПЕРШОГО рядка
     * замість останнього видимого.
     */
    private static String plainTailForWidth(Minecraft mc, String plain, int maxWidth, int targetLineIndex) {
        List<FormattedCharSequence> wrapped = mc.font.split(Component.literal(plain), maxWidth);
        int lastIdx = Math.min(targetLineIndex, wrapped.size()) - 1;
        // Довжина (у символах plain) усіх рядків ДО останнього видимого —
        // FormattedCharSequence не дає рядок напряму, тому йдемо по
        // зростаючих префіксах plain і рахуємо, скільки рядків вони дають,
        // щоб знайти межу зростаючим пошуком (текст без \n і § — довжина
        // рядків монотонно росте з довжиною префіксу).
        int consumedChars = 0;
        for (int li = 0; li < lastIdx; li++) {
            consumedChars = advancePastLine(mc, plain, consumedChars, maxWidth);
        }
        String tail = plain.substring(Math.min(consumedChars, plain.length()));

        int ellipsisW = mc.font.width("…");
        int budget = Math.max(0, maxWidth - ellipsisW);
        String candidate = tail;
        while (mc.font.width(candidate) > budget && !candidate.isEmpty()) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate;
    }

    /** Зсуває {@code from} за кінець рядка, який {@code Font.split} вмістив би в {@code maxWidth}, починаючи з {@code from}. */
    private static int advancePastLine(Minecraft mc, String plain, int from, int maxWidth) {
        String rest = plain.substring(Math.min(from, plain.length()));
        List<FormattedCharSequence> split = mc.font.split(Component.literal(rest), maxWidth);
        if (split.isEmpty()) return plain.length();
        // Довжину першого рядка з `split` наближено відновлюємо бінарним
        // пошуком по довжині префіксу `rest`, що дає ту саму кількість
        // рядків при повторному split — найдовший префікс, який ще
        // вміщається в один рядок під maxWidth.
        int lo = 0, hi = rest.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (mc.font.width(rest.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return from + Math.max(1, lo);
    }
}
