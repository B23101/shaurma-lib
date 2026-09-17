package dev.shaurmalib.forge.client.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.shaurmalib.common.chat.ChatChannel;
import dev.shaurmalib.common.chat.ChatChannelRegistry;
import dev.shaurmalib.common.chat.ChatEntry;
import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.forge.chat.ChatEntryRenderer;
import dev.shaurmalib.forge.chat.ChatEntryRendererRegistry;
import dev.shaurmalib.forge.chat.ChatModule;
import dev.shaurmalib.forge.chat.ChatScreenButton;
import dev.shaurmalib.forge.chat.ChatScreenButtonRegistry;
import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.ChatSendPacket;
import dev.shaurmalib.forge.overlay.OverlayPanelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * ChatHistoryScreen — кастомний чат бібліотеки.
 *
 * <p>Компонує в одному (не блокуючому гру) вікні:</p>
 * <ul>
 *   <li><b>канали</b> — кнопки «Загальний» + зареєстровані консюмером
 *       канали, у які місцевий гравець має право писати (список приходить
 *       з сервера {@code ChatChannelsSyncPacket}); вибраний канал
 *       визначає, куди піде повідомлення;</li>
 *   <li><b>верстку повідомлення</b> — делегується
 *       {@link ChatEntryRenderer}, зареєстрованому консюмером
 *       ({@link ChatEntryRendererRegistry}). Бібліотека має лише
 *       нейтральний текстовий рендер за замовчуванням: голова гравця,
 *       групова підсвітка ніку та будь-який інший вигляд — це вигляд
 *       КОНКРЕТНОГО режиму, тому його малює мод, а не бібліотека;</li>
 *   <li><b>кнопки консюмера</b> — {@link ChatScreenButtonRegistry}
 *       (напр. «Налаштування» лише для операторів, майбутня
 *       «Статистика»). Уся логіка кнопок — на боці консюмера;</li>
 *   <li>поле вводу з підказками команд і історією повідомлень.</li>
 * </ul>
 *
 * <p>Показ вхідних повідомлень у самій грі (спливаючий фід справа
 * зверху) — не тут, а через {@link ChatModule} +
 * {@code AlertNotificationSystem} (або власний {@code ChatDisplaySink}
 * консюмера).</p>
 */
@OnlyIn(Dist.CLIENT)
public class ChatHistoryScreen extends Screen {

    // ── Розміри/розкладка ────────────────────────────────────────────────
    private static final int FEED_W = 330;
    private static final int FEED_TOP_MARGIN = 8;
    private static final int FEED_BOTTOM_MARGIN = 8;
    private static final int FEED_ENTRY_PAD = 5;
    private static final int FEED_ENTRY_GAP = 4;

    private static final int INPUT_PANEL_W = 420;
    private static final int PANEL_MARGIN_BOTTOM = 40;
    private static final int INPUT_H = 20;
    private static final int CHANNEL_BTN_H = 16;
    private static final int CHANNEL_BTN_GAP = 3;
    private static final int EXTRA_BTN_H = 16;
    private static final int SUGGESTION_ROW_H = 12;
    private static final int MAX_VISIBLE_SUGGESTIONS = 8;
    private static final int MAX_HISTORY = 50;

    private static final int ACCENT_CYAN = 0xFF39C5F0;

    // ── Точки інтеграції з консюмером ────────────────────────────────────
    private final BooleanSupplier teamModeAvailable;
    private final Consumer<Boolean> tabOverlayForceVisible;

    private EditBox inputBox;
    private String selectedChannel = ChatChannel.GLOBAL_ID;
    private float feedScroll = 0f;
    private int inputPanelX, inputPanelY;
    private int feedX, feedY, feedH;
    private int channelRowY;
    private int extraButtonRowY;

    private final List<String> channelOrder = new ArrayList<>();
    private final List<ChatChannel> channels = new ArrayList<>();
    private final List<ChatScreenButton> extraButtons = new ArrayList<>();

    private static final List<String> messageHistory = new ArrayList<>();
    private int historyIndex = -1;

    private ParseResults<SharedSuggestionProvider> lastParse;
    private Suggestions currentSuggestions;
    private int highlightedSuggestion = -1;
    private int suggestionScrollTop = 0;

    public ChatHistoryScreen(BooleanSupplier teamModeAvailable, Consumer<Boolean> tabOverlayForceVisible) {
        super(Component.translatable("gui.shaurma_lib.chat.title"));
        this.teamModeAvailable = teamModeAvailable;
        this.tabOverlayForceVisible = tabOverlayForceVisible;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Ініціалізація / закриття
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        selectedChannel = ChannelSelection.resolveDefault();
        refreshChannels();

        channelRowY = this.height - PANEL_MARGIN_BOTTOM - INPUT_H - 4 - CHANNEL_BTN_H;
        extraButtonRowY = channelRowY - 4 - EXTRA_BTN_H;
        inputPanelX = (this.width - INPUT_PANEL_W) / 2;
        inputPanelY = this.height - PANEL_MARGIN_BOTTOM - INPUT_H;

        feedX = this.width - FEED_W - FEED_TOP_MARGIN;
        feedY = FEED_TOP_MARGIN;
        feedH = Math.max(40, channelRowY - 6 - FEED_TOP_MARGIN);

        inputBox = new EditBox(this.font, inputPanelX, inputPanelY, INPUT_PANEL_W, INPUT_H,
                Component.translatable("gui.shaurma_lib.chat.input_hint"));
        inputBox.setMaxLength(ChatFormatEngine.MAX_LENGTH);
        inputBox.setBordered(true);
        inputBox.setTextColor(0xFFE8EDF2);
        inputBox.setHint(Component.translatable("gui.shaurma_lib.chat.input_hint"));
        inputBox.setResponder(this::onInputChanged);
        addRenderableWidget(inputBox);
        this.setInitialFocus(inputBox);

        tabOverlayForceVisible.accept(true);
    }

    /** Перечитує канали з реєстру + клієнтського дзеркала доступу. */
    private void refreshChannels() {
        channels.clear();
        channelOrder.clear();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(ChatChannel.GLOBAL_ID);
        ids.addAll(ChatModule.clientWritableChannels());
        if (teamModeAvailable.getAsBoolean()) {
            ids.add(ChatChannel.TEAM_ID);
        }
        for (String id : ids) {
            channels.add(ChatChannelRegistry.byIdOrSynthetic(id));
            channelOrder.add(id);
        }
        if (!channelOrder.contains(selectedChannel)) {
            selectedChannel = ChannelSelection.resolveDefault();
        }

        extraButtons.clear();
        for (ChatScreenButton button : ChatScreenButtonRegistry.all()) {
            if (button.isVisible()) extraButtons.add(button);
        }
    }

    @Override
    public void removed() {
        super.removed();
        tabOverlayForceVisible.accept(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Рендер
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderFeed(g);
        super.render(g, mx, my, partialTick);
        renderChannelButtons(g, mx, my);
        renderExtraButtons(g, mx, my);
        renderCommandSuggestions(g, mx, my);

        ChatChannel channel = ChatChannelRegistry.byIdOrSynthetic(selectedChannel);
        String hint = Component.translatable(channel.labelKey()).getString();
        g.drawString(this.font, hint, inputPanelX, inputPanelY + INPUT_H + 4,
                OverlayPanelStyle.applyAlpha(channel.colorArgb(), 1f), true);
    }

    // ── Історія ──────────────────────────────────────────────────────────

    private void renderFeed(GuiGraphics g) {
        List<ChatEntry> entries = ChatModule.history().snapshot();
        if (entries.isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("gui.shaurma_lib.chat.empty_history").getString(),
                    feedX, feedY, 0xFF9AA0A6, true);
            return;
        }

        int maxLineW = FEED_W - FEED_ENTRY_PAD * 2;
        int[] entryHeights = new int[entries.size()];
        int totalH = 0;
        for (int i = 0; i < entries.size(); i++) {
            int h = entryHeight(entries.get(i), maxLineW);
            entryHeights[i] = h;
            totalH += h + FEED_ENTRY_GAP;
        }

        int maxScroll = Math.max(0, totalH - feedH);
        feedScroll = Mth.clamp(feedScroll, 0f, maxScroll);

        int bottomY = feedY + feedH;
        int y = bottomY + (int) feedScroll;
        for (int i = entries.size() - 1; i >= 0; i--) {
            int h = entryHeights[i];
            y -= h;
            if (y + h < feedY) break;
            if (y > bottomY) {
                y -= FEED_ENTRY_GAP;
                continue;
            }
            renderEntry(g, entries.get(i), y, h, maxLineW);
            y -= FEED_ENTRY_GAP;
        }
    }

    /**
     * Висота рядка = вміст від поточного {@link ChatEntryRenderer} плюс
     * внутрішні відступи панелі. Сам вигляд рядка бібліотека не знає.
     */
    private int entryHeight(ChatEntry entry, int maxLineW) {
        ChatEntryRenderer renderer = ChatEntryRendererRegistry.get();
        int contentH = Math.max(1, renderer.contentHeight(entry, this.font, maxLineW));
        return contentH + FEED_ENTRY_PAD * 2;
    }

    private void renderEntry(GuiGraphics g, ChatEntry entry, int y, int h, int maxLineW) {
        int accent = accentFor(entry);
        OverlayPanelStyle.drawPanel(g, feedX, y, FEED_W, h, 1f, accent);

        int contentH = Math.max(1, h - FEED_ENTRY_PAD * 2);
        ChatEntryRendererRegistry.get().render(g, entry, this.font,
                feedX + FEED_ENTRY_PAD, y + FEED_ENTRY_PAD, maxLineW, contentH, accent);
    }

    private int accentFor(ChatEntry entry) {
        return switch (entry.type()) {
            case DEATH -> OverlayPanelStyle.ACCENT_RED;
            case CHAT_GLOBAL, CHAT_TEAM -> ChatFormatEngine.parseColorOrDefault(entry.colorHex(), 0xFFAAAAAA);
            case SYSTEM -> OverlayPanelStyle.withAlpha(0xFFAAAAAA, 255);
        };
    }

    // ── Кнопки ───────────────────────────────────────────────────────────

    private void renderChannelButtons(GuiGraphics g, int mx, int my) {
        int x = inputPanelX;
        for (ChatChannel channel : channels) {
            int w = this.font.width(Component.translatable(channel.labelKey()).getString()) + 12;
            boolean active = channel.id().equals(selectedChannel);
            drawButton(g, x, channelRowY, w, CHANNEL_BTN_H,
                    Component.translatable(channel.labelKey()).getString(), active,
                    channel.colorArgb(), mx, my);
            x += w + CHANNEL_BTN_GAP;
        }
    }

    private void renderExtraButtons(GuiGraphics g, int mx, int my) {
        if (extraButtons.isEmpty()) return;
        int totalW = 0;
        for (ChatScreenButton button : extraButtons) {
            totalW += this.font.width(button.label()) + 12 + CHANNEL_BTN_GAP;
        }
        int x = inputPanelX + INPUT_PANEL_W - totalW + CHANNEL_BTN_GAP;
        for (ChatScreenButton button : extraButtons) {
            int w = this.font.width(button.label()) + 12;
            drawButton(g, x, extraButtonRowY, w, EXTRA_BTN_H, button.label().getString(),
                    false, ACCENT_CYAN, mx, my);
            x += w + CHANNEL_BTN_GAP;
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h,
                            String label, boolean active, int accent, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
        int fill = active ? OverlayPanelStyle.withAlpha(accent, 90)
                : (hovered ? 0x33FFFFFF : OverlayPanelStyle.withAlpha(0x000000, 140));
        int border = active ? accent : OverlayPanelStyle.BORDER;

        g.fill(x, y, x + w, y + h, fill);
        OverlayPanelStyle.border1px(g, x, y, w, h, border);

        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (w - tw) / 2, y + (h - 8) / 2,
                active ? 0xFFFFFFFF : 0xFF9AA0A6, false);
    }

    // ── Підказки команд ──────────────────────────────────────────────────

    private void renderCommandSuggestions(GuiGraphics g, int mx, int my) {
        if (currentSuggestions == null || currentSuggestions.getList().isEmpty()) return;

        List<Suggestion> list = currentSuggestions.getList();
        int total = list.size();
        int visibleCount = Math.min(total, MAX_VISIBLE_SUGGESTIONS);

        int x = inputBox.getX();
        int w = inputBox.getWidth();
        int h = visibleCount * SUGGESTION_ROW_H + 4;
        int y = inputBox.getY() - 2 - h;

        OverlayPanelStyle.drawPanel(g, x, y, w, h, 0.92f, ACCENT_CYAN);

        for (int row = 0; row < visibleCount; row++) {
            int idx = suggestionScrollTop + row;
            if (idx >= total) break;

            int rowY = y + 2 + row * SUGGESTION_ROW_H;
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + SUGGESTION_ROW_H;
            boolean keyboardSelected = idx == highlightedSuggestion;

            if (hovered || keyboardSelected) {
                g.fill(x + 1, rowY, x + w - 1, rowY + SUGGESTION_ROW_H, 0x33FFFFFF);
            }
            int color = keyboardSelected ? ACCENT_CYAN : (hovered ? 0xFFFFFFFF : 0xFF9AA0A6);
            g.drawString(this.font, list.get(idx).getText(), x + 4, rowY + 2, color, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Логіка підказок команд (Brigadier, публічний API)
    // ══════════════════════════════════════════════════════════════════

    private void onInputChanged(String text) {
        currentSuggestions = null;
        highlightedSuggestion = -1;
        suggestionScrollTop = 0;

        if (!text.startsWith("/")) {
            lastParse = null;
            return;
        }

        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;

        var dispatcher = connection.getCommands();
        var source = connection.getSuggestionsProvider();
        int slashCount = ChatFormatEngine.leadingSlashCount(text);
        String withoutSlash = text.substring(slashCount);
        int cursor = Math.max(0, inputBox.getCursorPosition() - slashCount);
        cursor = Math.min(cursor, withoutSlash.length());

        try {
            ParseResults<SharedSuggestionProvider> parsed = dispatcher.parse(withoutSlash, source);
            lastParse = parsed;
            final int cursorFinal = cursor;
            dispatcher.getCompletionSuggestions(parsed, cursorFinal).thenAccept(result -> {
                if (lastParse != parsed) return;
                currentSuggestions = (result == null || result.isEmpty()) ? null : result;
            });
        } catch (Exception ignored) {
            lastParse = null;
        }
    }

    private void applySuggestion(int index) {
        if (currentSuggestions == null) return;
        List<Suggestion> list = currentSuggestions.getList();
        if (index < 0 || index >= list.size()) return;

        Suggestion chosen = list.get(index);
        StringRange range = currentSuggestions.getRange();

        String current = inputBox.getValue();
        if (!current.startsWith("/")) return;
        int slashCount = ChatFormatEngine.leadingSlashCount(current);
        String withoutSlash = current.substring(slashCount);

        int start = Mth.clamp(range.getStart(), 0, withoutSlash.length());
        int end = Mth.clamp(range.getEnd(), start, withoutSlash.length());

        String newWithoutSlash = withoutSlash.substring(0, start) + chosen.getText() + withoutSlash.substring(end);
        String newValue = "/".repeat(slashCount) + newWithoutSlash;

        inputBox.setValue(newValue);
        int newCursor = slashCount + start + chosen.getText().length();
        inputBox.setCursorPosition(newCursor);
        inputBox.setHighlightPos(newCursor);
        onInputChanged(newValue);
    }

    private int suggestionCount() {
        return currentSuggestions == null ? 0 : currentSuggestions.getList().size();
    }

    private void moveSuggestionSelection(int delta) {
        int count = suggestionCount();
        if (count == 0) return;
        if (highlightedSuggestion < 0) {
            highlightedSuggestion = delta > 0 ? 0 : count - 1;
        } else {
            highlightedSuggestion = Mth.clamp(highlightedSuggestion + delta, 0, count - 1);
        }
        if (highlightedSuggestion < suggestionScrollTop) {
            suggestionScrollTop = highlightedSuggestion;
        } else if (highlightedSuggestion >= suggestionScrollTop + MAX_VISIBLE_SUGGESTIONS) {
            suggestionScrollTop = highlightedSuggestion - MAX_VISIBLE_SUGGESTIONS + 1;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Ввід
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        final int KEY_TAB = 258, KEY_ENTER = 257, KEY_KP_ENTER = 335, KEY_ESCAPE = 256;
        final int KEY_UP = 265, KEY_DOWN = 264;

        if (keyCode == KEY_UP) {
            if (suggestionCount() > 0) { moveSuggestionSelection(-1); return true; }
            else { recallOlderMessage(); return true; }
        }
        if (keyCode == KEY_DOWN) {
            if (suggestionCount() > 0) { moveSuggestionSelection(1); return true; }
            else { recallNewerMessage(); return true; }
        }
        if (suggestionCount() > 0 && keyCode == KEY_TAB) {
            applySuggestion(highlightedSuggestion >= 0 ? highlightedSuggestion : 0);
            return true;
        }
        if (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER) {
            if (highlightedSuggestion >= 0 && suggestionCount() > 0) {
                applySuggestion(highlightedSuggestion);
                return true;
            }
            trySendMessage();
            return true;
        }
        if (keyCode == KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (handleChannelClick(mx, my)) return true;
            if (handleExtraButtonClick(mx, my)) return true;

            if (currentSuggestions != null && !currentSuggestions.getList().isEmpty()) {
                int total = currentSuggestions.getList().size();
                int visibleCount = Math.min(total, MAX_VISIBLE_SUGGESTIONS);
                int x = inputBox.getX();
                int w = inputBox.getWidth();
                int h = visibleCount * SUGGESTION_ROW_H + 4;
                int y = inputBox.getY() - 2 - h;
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    int row = (int) ((my - (y + 2)) / SUGGESTION_ROW_H);
                    int idx = suggestionScrollTop + row;
                    if (idx >= 0 && idx < total) {
                        applySuggestion(idx);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean handleChannelClick(double mx, double my) {
        if (my < channelRowY || my >= channelRowY + CHANNEL_BTN_H) return false;
        int x = inputPanelX;
        for (ChatChannel channel : channels) {
            int w = this.font.width(Component.translatable(channel.labelKey()).getString()) + 12;
            if (mx >= x && mx < x + w) {
                selectedChannel = channel.id();
                return true;
            }
            x += w + CHANNEL_BTN_GAP;
        }
        return false;
    }

    private boolean handleExtraButtonClick(double mx, double my) {
        if (extraButtons.isEmpty()) return false;
        if (my < extraButtonRowY || my >= extraButtonRowY + EXTRA_BTN_H) return false;

        int totalW = 0;
        for (ChatScreenButton button : extraButtons) {
            totalW += this.font.width(button.label()) + 12 + CHANNEL_BTN_GAP;
        }
        int x = inputPanelX + INPUT_PANEL_W - totalW + CHANNEL_BTN_GAP;
        for (ChatScreenButton button : extraButtons) {
            int w = this.font.width(button.label()) + 12;
            if (mx >= x && mx < x + w) {
                button.press(this);
                return true;
            }
            x += w + CHANNEL_BTN_GAP;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (currentSuggestions != null && !currentSuggestions.getList().isEmpty()) {
            int total = currentSuggestions.getList().size();
            int visibleCount = Math.min(total, MAX_VISIBLE_SUGGESTIONS);
            int x = inputBox.getX();
            int w = inputBox.getWidth();
            int h = visibleCount * SUGGESTION_ROW_H + 4;
            int y = inputBox.getY() - 2 - h;
            if (mx >= x && mx < x + w && my >= y && my < y + h) {
                int maxTop = Math.max(0, total - visibleCount);
                suggestionScrollTop = Mth.clamp(suggestionScrollTop - (int) Math.signum(delta), 0, maxTop);
                return true;
            }
        }
        feedScroll = Math.max(0f, feedScroll + (float) delta * 12f);
        return true;
    }

    private void trySendMessage() {
        String text = inputBox.getValue();
        if (text == null || text.isBlank()) {
            this.onClose();
            return;
        }
        String stripped = text.strip();
        if (!stripped.isEmpty() && (messageHistory.isEmpty()
                || !messageHistory.get(messageHistory.size() - 1).equals(stripped))) {
            messageHistory.add(stripped);
            if (messageHistory.size() > MAX_HISTORY) messageHistory.remove(0);
        }
        historyIndex = -1;
        ShaurmaLibNetwork.sendToServer(new ChatSendPacket(stripped, selectedChannel));
        inputBox.setValue("");
        this.onClose();
    }

    private void recallOlderMessage() {
        if (messageHistory.isEmpty()) return;
        if (historyIndex < 0) {
            historyIndex = messageHistory.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        } else {
            return;
        }
        String msg = messageHistory.get(historyIndex);
        inputBox.setValue(msg);
        inputBox.setCursorPosition(msg.length());
    }

    private void recallNewerMessage() {
        if (historyIndex < 0) return;
        historyIndex++;
        if (historyIndex >= messageHistory.size()) {
            historyIndex = -1;
            inputBox.setValue("");
        } else {
            String msg = messageHistory.get(historyIndex);
            inputBox.setValue(msg);
            inputBox.setCursorPosition(msg.length());
        }
    }

    /**
     * Куди ставити курсор каналу при відкритті: серверний default (перший
     * доступний рольовий канал), або глобальний. Виноситься окремо, щоб
     * логіку вибору можна було змінити в одному місці.
     */
    private static final class ChannelSelection {
        static String resolveDefault() {
            String def = ChatModule.clientDefaultChannel();
            return def == null || def.isBlank() ? ChatChannel.GLOBAL_ID : def;
        }
    }
}
