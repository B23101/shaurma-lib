package dev.shaurmalib.forge.client.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.shaurmalib.common.chat.ChatEntry;
import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.forge.chat.ChatModule;
import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.ChatSendPacket;
import dev.shaurmalib.forge.overlay.OverlayPanelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * ChatHistoryScreen — узагальнена заміна ванільного {@code ChatScreen}
 * (план, п. 3.9 + 3.29). Перенесення {@code ChatHistoryScreen}
 * snipers_shaurma, з двома змінами відносно оригіналу:
 * <ol>
 *   <li><b>Виправлений баг подвійного слеша</b> — і в підказках команд
 *       ({@link #onInputChanged}), і при підстановці підказки
 *       ({@link #applySuggestion}) тепер знімаються ВСІ провідні
 *       {@code '/'} через {@link ChatFormatEngine#leadingSlashCount},
 *       а не рівно один. Раніше {@code "//tp @s ~ ~ ~"} парсився як
 *       {@code "/tp ..."} (недійсне ім'я команди для Brigadier),
 *       підказки зникали, і та сама помилка повторювалась на сервері
 *       в {@code ServerChatHandler} — команда мовчки не виконувалась.</li>
 *   <li><b>Узагальнені точки інтеграції</b> — замість жорсткого
 *       {@code ClientGameState.teamMode} і {@code CustomTabOverlay.forceVisible}
 *       консюмер передає {@link BooleanSupplier}/{@link Consumer} у
 *       конструкторі, тому бібліотека не знає нічого про конкретний
 *       tab-overlay клас чи джерело прапорця командного режиму.</li>
 * </ol>
 * Компонує в одному, не блокуючому огляд гри, вікні:
 *  - справа зверху: розгорнутий фід {@link ChatModule#history()} —
 *    та сама історія, що спливає в живому kill-feed, без окремого
 *    чат-логу, з прокруткою по всій історії поточного раунду;
 *  - знизу по центру: поле вводу з автофокусом, кнопками Global/Team
 *    (Team — дефолт у командних режимах; кнопок немає в соло-режимах);
 *  - повноцінні підказки аргументів команд (як у ванільному чаті).
 */
@OnlyIn(Dist.CLIENT)
public class ChatHistoryScreen extends Screen {

    // ── Розміри/розкладка ────────────────────────────────────────────────
    private static final int FEED_W = 280;
    private static final int FEED_TOP_MARGIN = 8;
    private static final int FEED_BOTTOM_MARGIN = 8;
    private static final int FEED_ENTRY_PAD_X = 12;
    private static final int FEED_ENTRY_GAP = 4;

    private static final int INPUT_PANEL_W = 360;
    private static final int PANEL_MARGIN_BOTTOM = 40;
    private static final int INPUT_H = 20;
    private static final int BTN_W = 64;
    private static final int BTN_GAP = 4;
    private static final int SUGGESTION_ROW_H = 12;
    private static final int MAX_VISIBLE_SUGGESTIONS = 8;

    // ── Точки інтеграції з консюмером (не хардкод snipers-класів) ──────
    private final BooleanSupplier teamModeAvailable;
    private final Consumer<Boolean> tabOverlayForceVisible; // може бути no-op, якщо консюмер не має tab-overlay

    private EditBox inputBox;
    private boolean teamMode;
    private float feedScroll = 0f;
    private int inputPanelX, inputPanelY;
    private int feedX, feedY, feedH;

    private static final int MAX_HISTORY = 50;
    private static final List<String> messageHistory = new ArrayList<>();
    private int historyIndex = -1;

    private ParseResults<SharedSuggestionProvider> lastParse;
    private Suggestions currentSuggestions;
    private int highlightedSuggestion = -1;
    private int suggestionScrollTop = 0;

    /**
     * @param teamModeAvailable       чи показувати кнопки Global/Team
     *                                (в оригіналі — {@code ClientGameState.teamMode}).
     * @param tabOverlayForceVisible  консюмер тримає свій tab-list
     *                                видимим, поки {@code true} —
     *                                передайте {@code b -> {}}, якщо
     *                                вашому мод немає такого оверлею.
     */
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
        feedX = this.width - FEED_W - FEED_TOP_MARGIN;
        feedY = FEED_TOP_MARGIN;
        feedH = this.height - FEED_TOP_MARGIN - FEED_BOTTOM_MARGIN - INPUT_H - PANEL_MARGIN_BOTTOM;

        inputPanelX = (this.width - INPUT_PANEL_W) / 2;
        inputPanelY = this.height - PANEL_MARGIN_BOTTOM - INPUT_H;

        boolean hasTeams = teamModeAvailable.getAsBoolean();
        teamMode = hasTeams;

        int inputW = hasTeams ? INPUT_PANEL_W - (BTN_W * 2 + BTN_GAP) - 6 : INPUT_PANEL_W;
        inputBox = new EditBox(this.font, inputPanelX, inputPanelY, inputW, INPUT_H,
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

    @Override
    public void removed() {
        super.removed();
        tabOverlayForceVisible.accept(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // гра лишається активною, ESC-меню тут не потрібне
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
        renderModeButtons(g, mx, my);
        super.render(g, mx, my, partialTick);
        renderCommandSuggestions(g, mx, my);

        String modeHint = Component.translatable(teamMode
                ? "gui.shaurma_lib.chat.mode_team"
                : "gui.shaurma_lib.chat.mode_global").getString();
        g.drawString(this.font, modeHint, inputPanelX, inputPanelY + INPUT_H + 4, OverlayPanelStyle.applyAlpha(0xFF9AA0A6, 1f), true);
    }

    /**
     * Малює розгорнуту {@link ChatModule#history()} — усю історію
     * записів поточного раунду, знизу вгору (найновіші внизу), з
     * прокруткою.
     */
    private void renderFeed(GuiGraphics g) {
        List<ChatEntry> entries = ChatModule.history().snapshot();
        if (entries.isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("gui.shaurma_lib.chat.empty_history").getString(),
                    feedX, feedY, 0xFF9AA0A6, true);
            return;
        }

        int maxLineW = FEED_W - FEED_ENTRY_PAD_X * 2;
        int[] entryHeights = new int[entries.size()];
        int totalH = 0;
        for (int i = 0; i < entries.size(); i++) {
            Component msg = entries.get(i).message();
            List<FormattedCharSequence> lines = this.font.split(msg, maxLineW);
            int lineCount = Math.max(1, lines.size());
            int h = FEED_ENTRY_PAD_X / 2 + lineCount * this.font.lineHeight + (lineCount - 1) * 2 + FEED_ENTRY_PAD_X / 2;
            entryHeights[i] = h;
            totalH += h + FEED_ENTRY_GAP;
        }

        int maxScroll = Math.max(0, totalH - feedH);
        feedScroll = Mth.clamp(feedScroll, 0f, maxScroll);

        int cursorYFromBottom = (int) feedScroll;
        int bottomY = feedY + feedH;

        int y = bottomY + cursorYFromBottom;
        for (int i = entries.size() - 1; i >= 0; i--) {
            int h = entryHeights[i];
            y -= h;
            if (y + h < feedY) break;
            if (y > bottomY) { y -= FEED_ENTRY_GAP; continue; }

            ChatEntry entry = entries.get(i);
            Component msg = entry.message();
            int accent = accentFor(entry);
            List<FormattedCharSequence> lines = this.font.split(msg, maxLineW);

            OverlayPanelStyle.drawPanel(g, feedX, y, FEED_W, h, 1f, accent);
            int textY = y + FEED_ENTRY_PAD_X / 2;
            for (FormattedCharSequence line : lines) {
                g.drawString(this.font, line, feedX + FEED_ENTRY_PAD_X, textY, OverlayPanelStyle.TEXT_DEFAULT, true);
                textY += this.font.lineHeight + 2;
            }

            y -= FEED_ENTRY_GAP;
        }
    }

    /**
     * Акцентна лінія панелі запису в T-екрані. Колір ніку відправника
     * вже "запечений" усередині {@code entry.message()} (застосований
     * один раз на сервері через {@code ChatFormatEngine.formatChatLine}),
     * тому тут лишається лише нейтральний акцент по замовчуванню —
     * панель сама не перефарбовує лінію під команду (те саме, що робив
     * живий kill-feed для CHAT-записів в оригіналі: акцентна лінія була
     * нейтрально-сірою, кольоровим був лише текст ніку всередині рядка).
     */
    private int accentFor(ChatEntry entry) {
        return switch (entry.type()) {
            case DEATH -> OverlayPanelStyle.ACCENT_RED;
            case CHAT_GLOBAL, CHAT_TEAM -> ChatFormatEngine.resolveAccent(null);
            case SYSTEM -> OverlayPanelStyle.withAlpha(0xFFAAAAAA, 255);
        };
    }

    private void renderModeButtons(GuiGraphics g, int mx, int my) {
        if (!teamModeAvailable.getAsBoolean()) return;

        int btnY = inputPanelY;
        int globalX = inputPanelX + INPUT_PANEL_W - (BTN_W * 2 + BTN_GAP);
        int teamX = inputPanelX + INPUT_PANEL_W - BTN_W;

        drawModeButton(g, globalX, btnY, "gui.shaurma_lib.chat.btn_global", !teamMode, mx, my);
        drawModeButton(g, teamX, btnY, "gui.shaurma_lib.chat.btn_team", teamMode, mx, my);
    }

    private void drawModeButton(GuiGraphics g, int x, int y, String labelKey, boolean active, int mx, int my) {
        boolean hovered = mx >= x && mx < x + BTN_W && my >= y && my < y + INPUT_H;
        int accentCyan = 0xFF39C5F0;
        int fill = active ? OverlayPanelStyle.withAlpha(accentCyan, 90)
                : (hovered ? 0x33FFFFFF : OverlayPanelStyle.withAlpha(0x000000, 140));
        int border = active ? accentCyan : OverlayPanelStyle.BORDER;

        g.fill(x, y, x + BTN_W, y + INPUT_H, fill);
        OverlayPanelStyle.border1px(g, x, y, BTN_W, INPUT_H, border);

        String label = Component.translatable(labelKey).getString();
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (BTN_W - tw) / 2, y + (INPUT_H - 8) / 2,
                active ? 0xFFFFFFFF : 0xFF9AA0A6, false);
    }

    private void renderCommandSuggestions(GuiGraphics g, int mx, int my) {
        if (currentSuggestions == null || currentSuggestions.getList().isEmpty()) return;

        List<Suggestion> list = currentSuggestions.getList();
        int total = list.size();
        int visibleCount = Math.min(total, MAX_VISIBLE_SUGGESTIONS);

        int x = inputBox.getX();
        int w = inputBox.getWidth();
        int h = visibleCount * SUGGESTION_ROW_H + 4;
        int y = inputBox.getY() - 2 - h;

        OverlayPanelStyle.drawPanel(g, x, y, w, h, 0.92f, 0xFF39C5F0);

        for (int row = 0; row < visibleCount; row++) {
            int idx = suggestionScrollTop + row;
            if (idx >= total) break;

            int rowY = y + 2 + row * SUGGESTION_ROW_H;
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + SUGGESTION_ROW_H;
            boolean keyboardSelected = idx == highlightedSuggestion;

            if (hovered || keyboardSelected) {
                g.fill(x + 1, rowY, x + w - 1, rowY + SUGGESTION_ROW_H, 0x33FFFFFF);
            }

            int color = keyboardSelected ? 0xFF39C5F0 : (hovered ? 0xFFFFFFFF : 0xFF9AA0A6);
            g.drawString(this.font, list.get(idx).getText(), x + 4, rowY + 2, color, false);
        }

        if (total > visibleCount) {
            String counter = (suggestionScrollTop + 1) + "-" + Math.min(total, suggestionScrollTop + visibleCount)
                    + "/" + total;
            int cw = this.font.width(counter);
            g.drawString(this.font, counter, x + w - cw - 4, y + h - SUGGESTION_ROW_H + 2, 0xFF9AA0A6, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Логіка підказок команд (Brigadier, публічний API)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Викликається при кожній зміні тексту в полі вводу. Парсить текст
     * через клієнтський {@code CommandDispatcher}, якщо він починається
     * з {@code '/'}.
     * <p>
     * <b>Виправлено:</b> раніше {@code text.substring(1)} знімав рівно
     * один провідний {@code '/'}, тому {@code "//tp"} парсився як
     * {@code "/tp"} — недійсне ім'я команди, підказки не з'являлись.
     * Тепер знімаються всі провідні слеші через
     * {@link ChatFormatEngine#leadingSlashCount}, і позиція курсора
     * перераховується на їхню кількість, а не на фіксовану 1.
     */
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

    /**
     * Підставляє обрану підказку в поле вводу, зберігаючи ВСІ провідні
     * слеші, введені гравцем (не форсуючи рівно один) — узгоджено з
     * фіксом у {@link #onInputChanged}, щоб текст, який реально піде на
     * сервер, точно відповідав тому, що бачив gравець при виборі підказки.
     */
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
    //  Ввід (клавіатура/миша)
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
            if (teamModeAvailable.getAsBoolean()) {
                int btnY = inputPanelY;
                int globalX = inputPanelX + INPUT_PANEL_W - (BTN_W * 2 + BTN_GAP);
                int teamX = inputPanelX + INPUT_PANEL_W - BTN_W;
                if (my >= btnY && my < btnY + INPUT_H) {
                    if (mx >= globalX && mx < globalX + BTN_W) { teamMode = false; return true; }
                    if (mx >= teamX && mx < teamX + BTN_W) { teamMode = true; return true; }
                }
            }

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
        if (!stripped.isEmpty() && (messageHistory.isEmpty() || !messageHistory.get(messageHistory.size() - 1).equals(stripped))) {
            messageHistory.add(stripped);
            if (messageHistory.size() > MAX_HISTORY) messageHistory.remove(0);
        }
        historyIndex = -1;
        ShaurmaLibNetwork.sendToServer(new ChatSendPacket(stripped, teamMode));
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
}
