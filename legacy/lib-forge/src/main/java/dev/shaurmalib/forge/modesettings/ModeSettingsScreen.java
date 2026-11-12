package dev.shaurmalib.forge.modesettings;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.shaurmalib.common.lifecycle.MatchLifecycleBus;
import dev.shaurmalib.common.lifecycle.MatchLifecycleState;
import dev.shaurmalib.common.modesettings.PlayerModeRow;
import dev.shaurmalib.common.modesettings.SettingDefinition;
import dev.shaurmalib.common.modesettings.SettingOption;
import dev.shaurmalib.common.modesettings.SettingsSyncBridge;
import dev.shaurmalib.common.overlay.ScreenOpenAnimator;
import dev.shaurmalib.forge.menu.AnimatedMenuButton;
import dev.shaurmalib.forge.menu.AnimatedMenuTab;
import dev.shaurmalib.forge.mode.GameModeContract;
import dev.shaurmalib.forge.mode.GameModeRegistry;
import dev.shaurmalib.forge.overlay.OverlayPanelStyle;
import dev.shaurmalib.forge.style.StyleTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Лібова версія {@code GameSettingsScreen} (план, п. 3.32) — перенесення
 * 1059-рядкового екрана snipers_shaurma, **повністю узагальнене**:
 * жодного хардкоду {@code ICON_SC}/{@code ICON_BR}/{@code ICON_SCN}/
 * {@code ICON_SD} чи {@code if ("sc".equals(id))}-розгалужень — ліва
 * панель режимів рендериться циклом по {@link GameModeRegistry#all()},
 * кожен пункт малюється іконкою/кольором із розширеного
 * {@link GameModeContract} ({@code menuIcon()}/{@code accentColor()},
 * додано на кроці 3.18, п. Етап 1 плану, саме заради цього екрана).
 * <p>
 * Структура (1:1 з оригіналом):
 * <pre>
 *  [Ліва панель]  Список режимів (menuIcon/accentColor) — тільки в IDLE
 *  [Права панель] Таби: Налаштування | Гравці (вкладка Гравці — опційна)
 *  [Рядок]        Іконка | Назва | Кнопки вибору
 *  [Tooltip]      Завжди над курсором, в межах панелі
 * </pre>
 * Дані (список {@link SettingDefinition} з поточними значеннями, список
 * {@link PlayerModeRow}) і збереження/зміна режиму йдуть через
 * {@link SettingsSyncBridge} — консюмер підключає власний мережевий шар,
 * бібліотека не постачає жодного пакета для цього модуля (див. докстрінг
 * {@link SettingsSyncBridge}).
 * <p>
 * Блокування редагування під час активної гри питає {@link MatchLifecycleBus}
 * (чи зараз {@link MatchLifecycleState#IDLE}) — екран НЕ дублює власну
 * перевірку конкретної фази режиму (план, п. 3.32: "ModeSettingsScreen лише
 * питає в лібового lifecycle-модуля 'чи зараз IDLE', не дублює власну
 * перевірку фази").
 * <p>
 * Вкладка "Гравці" — вимикна: якщо консюмеру вона не потрібна (передав
 * {@code showPlayersTab = false}), таби взагалі не рендеряться, і екран
 * займає всю праву панель одним видом налаштувань.
 */
public class ModeSettingsScreen extends Screen {

    // ─── Геометрія (1:1 з оригіналом) ────────────────────────────────
    private static final int HEADER_H = 26;
    private static final int TAB_H = 20;
    private static final int FOOTER_H = 34;
    private static final int PAD = 8;
    private static final int ROW_H = 24;
    private static final int OPT_H = 16;
    private static final int OPT_GAP = 3;
    private static final int PLAYER_RH = 22;
    private static final int MODE_W = 58;
    private static final int MODE_GAP = 6;
    private static final int MODE_BTN_H = 50;
    private static final int MODE_BTN_GAP = 4;
    private static final int ICON_SZ = 16;
    private static final int ICON_PAD = 4;
    private static final int NAME_W = 130;
    private static final int BG_MAX_A = 160;

    private final StyleTheme style;
    private final GameModeRegistry modeRegistry;
    private final MatchLifecycleBus lifecycleBus;
    private final SettingsSyncBridge bridge;
    private final boolean showPlayersTab;
    private final ScreenOpenAnimator animator = ScreenOpenAnimator.defaultSpeed();

    private final List<SettingDefinition> settings = new ArrayList<>();
    private final Map<String, Integer> selectedValues = new HashMap<>();
    private final Map<String, Integer> previousValues = new HashMap<>();
    private final List<String> saveLog = new ArrayList<>();
    private long saveLogTimer = 0;

    private int activeTab = 0;
    private int settingsScroll = 0;
    private int playersScroll = 0;
    private boolean isDragging = false;

    private String tipHeader = null;
    private String tipDesc = null;
    private int tipMx, tipMy;
    private int lastHoveredSlot = -1;
    private long lastHoverSoundMs = 0;
    private Runnable hoverSound;
    private Runnable clickSound;

    private int bgX, bgY, bgW, bgH;
    private int modeX, modeY, modeW, modeH;

    public ModeSettingsScreen(Component title, StyleTheme style, GameModeRegistry modeRegistry,
                               MatchLifecycleBus lifecycleBus, SettingsSyncBridge bridge, boolean showPlayersTab) {
        super(title);
        this.style = style;
        this.modeRegistry = modeRegistry;
        this.lifecycleBus = lifecycleBus;
        this.bridge = bridge;
        this.showPlayersTab = showPlayersTab;
    }

    /** Опційні hover/click звуки — консюмер підключає свій {@code MenuSoundHelper}-еквівалент, або лишає {@code null} для тиші. */
    public void setSounds(Runnable hoverSound, Runnable clickSound) {
        this.hoverSound = hoverSound;
        this.clickSound = clickSound;
    }

    /**
     * Заповнити список налаштувань (в оригіналі — {@code onSettingsReloaded}).
     * Викликається консюмером після прийому мережевої відповіді від
     * сервера з метаданими налаштувань поточного режиму.
     */
    public void setSettings(List<SettingDefinition> defs, Map<String, Integer> currentValues) {
        settings.clear();
        selectedValues.clear();
        previousValues.clear();
        for (SettingDefinition d : defs) {
            settings.add(d);
            int cur = currentValues.getOrDefault(d.id, d.defaultValue);
            selectedValues.put(d.id, cur);
            previousValues.put(d.id, cur);
        }
        rebuildWidgets();
    }

    /** Викликається консюмером, коли активний режим змінився ззовні (сервер підтвердив зміну) — форсує видиму анімацію "оновлення". */
    public void onModeChanged() {
        animator.ensureMinimum(0.1f);
        rebuildWidgets();
    }

    // ─── Ініціалізація ────────────────────────────────────────────────

    @Override
    protected void init() {
        calcLayout();
        rebuildWidgets();
        if (showPlayersTab) bridge.requestPlayerList();
    }

    private void calcLayout() {
        bgW = Mth.clamp(this.width - 20 - MODE_W - MODE_GAP, 340, 520);
        bgH = Mth.clamp(this.height - 24, 300, 420);

        int totalW = MODE_W + MODE_GAP + bgW;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - bgH) / 2;

        modeX = startX;
        modeY = startY;
        modeW = MODE_W;
        modeH = bgH;

        bgX = startX + MODE_W + MODE_GAP;
        bgY = startY;
    }

    @Override
    public void rebuildWidgets() {
        clearWidgets();

        int tabY = bgY + HEADER_H;
        if (showPlayersTab) {
            int tabW = (bgW - 2) / 2;
            List<PlayerModeRow> players = bridge.playersList();
            int playerCount = players != null ? players.size() : 0;
            addRenderableWidget(new AnimatedMenuTab(bgX + 1, tabY, tabW, TAB_H,
                    Component.translatable("gui.shaurmalib.settings.tab_settings"),
                    b -> { activeTab = 0; rebuildWidgets(); }, activeTab == 0, animator));
            addRenderableWidget(new AnimatedMenuTab(bgX + 1 + tabW, tabY, bgW - 2 - tabW, TAB_H,
                    Component.translatable("gui.shaurmalib.settings.tab_players", playerCount),
                    b -> { activeTab = 1; rebuildWidgets(); }, activeTab == 1, animator));
        }

        int footY = bgY + bgH - FOOTER_H + (FOOTER_H - 18) / 2;
        int bw = 76;
        boolean idle = isIdle();

        if (activeTab == 0 && idle) {
            addRenderableWidget(new AnimatedMenuButton(bgX + PAD, footY, bw, 18,
                    Component.translatable("gui.shaurmalib.settings.save"),
                    b -> saveSettings(), OverlayPanelStyle.ACCENT_YELLOW, animator));
            addRenderableWidget(new AnimatedMenuButton(bgX + PAD + bw + 5, footY, bw, 18,
                    Component.translatable("gui.shaurmalib.settings.reset"),
                    b -> resetToDefaults(), 0xFF7A6000, animator));
        }
        addRenderableWidget(new AnimatedMenuButton(bgX + bgW - bw - PAD, footY, bw, 18,
                Component.translatable("gui.shaurmalib.settings.close"),
                b -> onClose(), OverlayPanelStyle.ACCENT_RED, animator));

        if (showPlayersTab && activeTab == 1) buildPlayerButtons();
    }

    private void buildPlayerButtons() {
        List<PlayerModeRow> list = bridge.playersList();
        if (list == null) return;
        int vTop = bgY + HEADER_H + TAB_H + PAD;
        int vBot = bgY + bgH - FOOTER_H;
        int tW = 82;
        int listW = bgW - PAD * 2 - 10;

        for (int i = 0; i < list.size(); i++) {
            PlayerModeRow p = list.get(i);
            int rowY = vTop + i * PLAYER_RH - playersScroll;
            if (rowY + PLAYER_RH <= vTop || rowY >= vBot) continue;
            final String pName = p.name;
            final boolean makeSpec = !p.isSpectator;
            int accent = p.isSpectator ? 0xFF44E07A : OverlayPanelStyle.ACCENT_RED;
            addRenderableWidget(new AnimatedMenuButton(
                    bgX + PAD + listW - tW, rowY + (PLAYER_RH - 14) / 2, tW, 14,
                    Component.translatable(p.isSpectator
                            ? "gui.shaurmalib.settings.to_player"
                            : "gui.shaurmalib.settings.to_spectator"),
                    b -> bridge.togglePlayerSpectator(pName, makeSpec), accent, animator));
        }
    }

    private boolean isIdle() {
        return lifecycleBus.current() == MatchLifecycleState.IDLE;
    }

    // ─── Render ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        animator.tick(delta);
        tipHeader = null;
        tipDesc = null;

        float ea = animator.easedProgress();
        int a = animator.panelAlpha255();

        style.drawScreenDim(g, this.width, this.height, ea);

        renderModePanel(g, mx, my, a, ea);
        renderMainPanel(g, a, ea);
        renderHeader(g, a);
        if (showPlayersTab) renderTabs(g, a);

        int cTop = bgY + HEADER_H + (showPlayersTab ? TAB_H : 0);
        int cBot = bgY + bgH - FOOTER_H;
        g.enableScissor(bgX + 1, cTop, bgX + bgW - 1, cBot);
        if (activeTab == 0) renderSettings(g, mx, my, a, ea, cTop, cBot);
        else renderPlayers(g, mx, my, a, cTop, cBot);
        g.disableScissor();

        renderScrollbar(g, a, cTop, cBot);
        style.hline(g, bgX + PAD, bgY + bgH - FOOTER_H, bgW - PAD * 2,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.BORDER, a));
        if (showPlayersTab && activeTab == 1) renderPlayerStats(g, a);
        renderSaveLog(g, ea);

        super.render(g, mx, my, delta);

        if (activeTab == 0 && !isIdle()) {
            renderLockedOverlay(g, a, cTop, cBot);
        }

        if (tipHeader != null) {
            style.drawTooltip(g, this.font, tipHeader,
                    tipDesc != null ? new String[]{tipDesc} : null,
                    tipMx, tipMy, this.width, this.height);
        }

        updateWidgetHover(mx, my);
    }

    // ─── Ліва панель режимів ────────────────────────────────────────

    private void renderModePanel(GuiGraphics g, int mx, int my, int a, float ea) {
        g.fill(modeX, modeY, modeX + modeW, modeY + modeH,
                OverlayPanelStyle.withAlpha(0x090A0F, (int) (ea * BG_MAX_A)));
        style.border1px(g, modeX, modeY, modeW, modeH,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.BORDER, a));
        g.fill(modeX, modeY, modeX + modeW, modeY + 2,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.ACCENT_YELLOW, a));

        g.drawCenteredString(this.font,
                Component.translatable("gui.shaurmalib.settings.mode_switch"),
                modeX + modeW / 2, modeY + 7,
                OverlayPanelStyle.withAlpha(0xFF667788, a));
        style.hline(g, modeX + 4, modeY + HEADER_H - 2, modeW - 8,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.BORDER, a));

        GameModeContract current = modeRegistry.current();
        if (!isIdle()) {
            g.drawCenteredString(this.font, Component.literal("§c\u2298"),
                    modeX + modeW / 2, modeY + modeH - 20, OverlayPanelStyle.withAlpha(OverlayPanelStyle.ACCENT_RED, a));
        }
        String shortId = current.id().length() <= 4 ? current.id().toUpperCase()
                : current.id().substring(0, 4).toUpperCase();
        g.drawCenteredString(this.font, Component.literal(shortId),
                modeX + modeW / 2, modeY + modeH - 10,
                OverlayPanelStyle.withAlpha(current.accentColor(), a));

        renderModeButtons(g, mx, my, a, ea);
    }

    private void renderModeButtons(GuiGraphics g, int mx, int my, int a, float ea) {
        boolean idle = isIdle();
        int btnW = modeW - 4;
        int btnX = modeX + 2;
        int btnY = modeY + HEADER_H + 8;

        int i = 0;
        for (GameModeContract mode : modeRegistry.all()) {
            int y = btnY + i * (MODE_BTN_H + MODE_BTN_GAP);
            boolean active = mode == modeRegistry.current();
            boolean hov = mx >= btnX && mx <= btnX + btnW && my >= y && my <= y + MODE_BTN_H;

            renderModeButton(g, a, ea, btnX, y, btnW, MODE_BTN_H, mode, active, idle, hov);

            if (hov) {
                tipHeader = Component.translatable(mode.id()).getString();
                String hint = idle ? "gui.shaurmalib.settings.mode_switch_hint" : "gui.shaurmalib.settings.mode_locked_desc";
                tipDesc = Component.translatable(hint).getString();
                tipMx = mx;
                tipMy = my;
            }
            i++;
        }
    }

    private void renderModeButton(GuiGraphics g, int a, float ea, int x, int y, int w, int h,
                                   GameModeContract mode, boolean active, boolean enabled, boolean hov) {
        int accent = mode.accentColor();
        int activeBg = OverlayPanelStyle.withAlpha(accent, (int) (a * 0.18f));
        int bg = active ? activeBg
                : hov ? OverlayPanelStyle.withAlpha(0x1A1A2A, (int) (a * 0.4f))
                : OverlayPanelStyle.withAlpha(0x07080C, (int) (a * 0.25f));
        g.fill(x, y, x + w, y + h, bg);

        style.border1px(g, x, y, w, h,
                OverlayPanelStyle.withAlpha(active ? accent : (hov ? accent : OverlayPanelStyle.BORDER), a));
        if (active) {
            g.fill(x, y, x + w, y + 2, OverlayPanelStyle.withAlpha(accent, a));
        }

        int iconSz = 22;
        int iconX = x + (w - iconSz) / 2;
        int iconY = y + 4;
        ResourceLocation icon = mode.menuIcon();
        if (icon != null) {
            try {
                RenderSystem.enableBlend();
                if (!enabled) RenderSystem.setShaderColor(0.35f, 0.35f, 0.35f, ea);
                g.blit(icon, iconX, iconY, 0, 0, iconSz, iconSz, iconSz, iconSz);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            } catch (Exception ignored) {
                RenderSystem.disableBlend();
            }
        }

        int tc = active ? OverlayPanelStyle.withAlpha(accent, a)
                : OverlayPanelStyle.withAlpha(enabled ? 0xFF667788 : 0xFF333344, a);
        String shortId = mode.id().length() <= 4 ? mode.id().toUpperCase() : mode.id().substring(0, 4).toUpperCase();
        g.drawCenteredString(this.font, Component.literal(shortId), x + w / 2, y + h - 11, tc);

        if (!enabled) {
            g.drawCenteredString(this.font, Component.literal("§c\u2298"), x + w / 2, y + h / 2 - 4,
                    OverlayPanelStyle.withAlpha(OverlayPanelStyle.ACCENT_RED, (int) (a * 0.5f)));
        }
    }

    // ─── Головна панель ─────────────────────────────────────────────

    private void renderMainPanel(GuiGraphics g, int a, float ea) {
        g.fill(bgX, bgY, bgX + bgW, bgY + bgH,
                OverlayPanelStyle.withAlpha(0x0C0D12, (int) (ea * BG_MAX_A)));
        style.border1px(g, bgX, bgY, bgW, bgH,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.BORDER, a));
        g.fill(bgX, bgY, bgX + bgW, bgY + 2,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.ACCENT_YELLOW, a));
    }

    private void renderHeader(GuiGraphics g, int a) {
        g.drawCenteredString(this.font, this.title,
                bgX + bgW / 2, bgY + 7,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.ACCENT_YELLOW, a));
        style.hline(g, bgX + PAD, bgY + HEADER_H - 2, bgW - PAD * 2,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.BORDER, a));
    }

    private void renderTabs(GuiGraphics g, int a) {
        int tabW = (bgW - 2) / 2;
        int tabY = bgY + HEADER_H;
        int x0 = activeTab == 0 ? bgX + 1 : bgX + 1 + tabW;
        int x1 = activeTab == 0 ? bgX + 1 + tabW : bgX + bgW - 1;
        g.fill(x0, tabY + TAB_H - 2, x1, tabY + TAB_H,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.ACCENT_YELLOW, a));
    }

    // ─── Налаштування ───────────────────────────────────────────────

    private void renderSettings(GuiGraphics g, int mx, int my, int a, float ea, int cTop, int cBot) {
        if (settings.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.shaurmalib.settings.empty"),
                    bgX + bgW / 2, cTop + 20,
                    OverlayPanelStyle.withAlpha(0xFF667788, a));
            return;
        }

        int rowX = bgX + PAD;
        int rowW = bgW - PAD * 2 - 12;
        int startY = cTop + 4 - settingsScroll;
        boolean locked = !isIdle();

        for (int i = 0; i < settings.size(); i++) {
            SettingDefinition s = settings.get(i);
            int rowY = startY + i * (ROW_H + 2);
            if (rowY + ROW_H < cTop || rowY > cBot) continue;

            int curVal = selectedValues.getOrDefault(s.id, s.defaultValue);
            boolean rHov = !locked && mx >= rowX && mx <= rowX + rowW && my >= rowY && my <= rowY + ROW_H;

            int bgA = (int) (ea * (rHov ? 0.55f : 0.35f) * 255);
            g.fill(rowX, rowY, rowX + rowW, rowY + ROW_H,
                    OverlayPanelStyle.withAlpha(rHov ? 0x1A1A2A : 0x07080C, bgA));
            g.fill(rowX, rowY + ROW_H - 1, rowX + rowW, rowY + ROW_H,
                    OverlayPanelStyle.withAlpha(OverlayPanelStyle.BORDER, (int) (ea * 0.3f * 255)));

            int iconX = rowX + ICON_PAD;
            int iconY = rowY + (ROW_H - ICON_SZ) / 2;
            boolean hasIcon = renderSettingIcon(g, s.iconPath, iconX, iconY);

            if (hasIcon && mx >= iconX && mx <= iconX + ICON_SZ && my >= rowY && my <= rowY + ROW_H) {
                tipHeader = Component.translatable(s.displayNameKey).getString();
                if (s.descriptionKey != null && !s.descriptionKey.isEmpty()) {
                    tipDesc = Component.translatable(s.descriptionKey).getString();
                }
                tipMx = mx;
                tipMy = my;
            }

            int nameX = iconX + ICON_SZ + ICON_PAD;
            String name = trimText(Component.translatable(s.displayNameKey).getString(), NAME_W);
            int textColor = locked ? OverlayPanelStyle.withAlpha(0xFF333344, a)
                    : OverlayPanelStyle.withAlpha(rHov ? OverlayPanelStyle.TEXT_DEFAULT : 0xFFAACCDD, a);
            g.drawString(this.font, name, nameX, rowY + (ROW_H - 8) / 2, textColor, false);

            int optCount = s.options.size();
            if (optCount == 0) continue;

            int optW = Math.min(64, (rowW - (nameX - rowX) - ICON_PAD) / optCount - OPT_GAP);
            int optTotalW = optCount * (optW + OPT_GAP) - OPT_GAP;
            int optStartX = rowX + rowW - optTotalW - ICON_PAD;

            for (int j = 0; j < optCount; j++) {
                SettingOption opt = s.options.get(j);
                int ox = optStartX + j * (optW + OPT_GAP);
                int oy = rowY + (ROW_H - OPT_H) / 2;
                boolean sel = opt.value == curVal;
                boolean oHov = !locked && mx >= ox && mx <= ox + optW && my >= oy && my <= oy + OPT_H;

                int optBg = sel ? 0x0A1525 : (oHov ? 0x1A1A2A : 0x07080C);
                style.drawCutPanel(g, ox, oy, optW, OPT_H, ea,
                        OverlayPanelStyle.withAlpha(optBg, (int) (ea * 200)),
                        OverlayPanelStyle.withAlpha(sel ? 0xFF44E07A : (oHov ? 0xFF7A6000 : OverlayPanelStyle.BORDER), a));

                String optTxt = trimText(Component.translatable(opt.displayTextKey).getString(), optW - 4);
                int optColor = locked ? OverlayPanelStyle.withAlpha(0xFF333344, a)
                        : OverlayPanelStyle.withAlpha(sel ? 0xFF44E07A : (oHov ? OverlayPanelStyle.ACCENT_YELLOW : 0xFF667788), a);
                g.drawCenteredString(this.font, Component.literal(optTxt),
                        ox + optW / 2, oy + (OPT_H - 8) / 2, optColor);
            }

            if (rHov && s.descriptionKey != null && !s.descriptionKey.isEmpty() && tipHeader == null) {
                tipHeader = Component.translatable(s.displayNameKey).getString();
                tipDesc = Component.translatable(s.descriptionKey).getString();
                tipMx = mx;
                tipMy = my;
            }
        }
    }

    private boolean renderSettingIcon(GuiGraphics g, String iconPath, int x, int y) {
        if (iconPath == null || iconPath.isEmpty()) return false;
        try {
            RenderSystem.enableBlend();
            if (iconPath.startsWith("item:") || iconPath.contains(":textures/item/")) {
                ItemStack stack = resolveIconItem(iconPath);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, x, y);
                    RenderSystem.disableBlend();
                    return true;
                }
            } else {
                g.blit(parseIconLoc(iconPath), x, y, 0, 0, 16, 16, 16, 16);
                RenderSystem.disableBlend();
                return true;
            }
            RenderSystem.disableBlend();
        } catch (Exception ignored) {
            RenderSystem.disableBlend();
        }
        return false;
    }

    private ItemStack resolveIconItem(String path) {
        String id = path;
        if (id.startsWith("item:")) {
            id = id.substring(5);
        } else if (id.contains(":textures/item/")) {
            String[] parts = id.split(":textures/item/", 2);
            String fileName = parts[1];
            if (fileName.endsWith(".png")) fileName = fileName.substring(0, fileName.length() - 4);
            id = parts[0] + ":" + fileName;
        }
        try {
            String[] p = id.split(":", 2);
            var item = ForgeRegistries.ITEMS.getValue(p.length == 2
                    ? new ResourceLocation(p[0], p[1]) : new ResourceLocation(id));
            if (item != null && item != Items.AIR) return new ItemStack(item);
        } catch (Exception ignored) {
            // Пропускаємо — рядок повертає ItemStack.EMPTY нижче.
        }
        return ItemStack.EMPTY;
    }

    private ResourceLocation parseIconLoc(String path) {
        if (path != null && path.contains(":")) {
            String[] p = path.split(":", 3);
            if (p.length >= 2) return new ResourceLocation(p[0], p[1]);
        }
        return new ResourceLocation("minecraft", path != null ? path : "textures/gui/icons.png");
    }

    // ─── Гравці ────────────────────────────────────────────────────

    private void renderPlayers(GuiGraphics g, int mx, int my, int a, int cTop, int cBot) {
        List<PlayerModeRow> list = bridge.playersList();
        int listW = bgW - PAD * 2 - 12;

        if (list == null || list.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.shaurmalib.settings.no_players"),
                    bgX + bgW / 2, cTop + 12,
                    OverlayPanelStyle.withAlpha(0xFF667788, a));
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            PlayerModeRow p = list.get(i);
            int rowY = cTop + PAD + i * PLAYER_RH - playersScroll;
            if (rowY + PLAYER_RH <= cTop || rowY >= cBot) continue;

            boolean hov = mx >= bgX + PAD && mx <= bgX + PAD + listW && my >= rowY && my <= rowY + PLAYER_RH;
            int bgA = (int) (animator.easedProgress() * (hov ? 0.45f : 0.3f) * 255);
            g.fill(bgX + PAD, rowY, bgX + PAD + listW, rowY + PLAYER_RH - 1,
                    OverlayPanelStyle.withAlpha(hov ? 0x1A1A2A : 0x07080C, bgA));

            int statusColor = p.isSpectator ? OverlayPanelStyle.ACCENT_RED : 0xFF44E07A;
            g.fill(bgX + PAD, rowY, bgX + PAD + 2, rowY + PLAYER_RH - 1,
                    OverlayPanelStyle.withAlpha(statusColor, a));

            g.drawString(this.font, Component.literal(p.name),
                    bgX + PAD + 6, rowY + (PLAYER_RH - 8) / 2,
                    OverlayPanelStyle.withAlpha(OverlayPanelStyle.TEXT_DEFAULT, a), false);

            int tW = 82;
            String statusKey = p.isSpectator ? "gui.shaurmalib.settings.spectator" : "gui.shaurmalib.settings.player";
            String statusText = Component.translatable(statusKey).getString();
            g.drawString(this.font,
                    Component.literal((p.isSpectator ? "\u00a7c" : "\u00a7a") + statusText),
                    bgX + PAD + listW - tW - 6 - this.font.width(statusText),
                    rowY + (PLAYER_RH - 8) / 2,
                    OverlayPanelStyle.withAlpha(OverlayPanelStyle.TEXT_DEFAULT, a), false);
        }
    }

    private void renderPlayerStats(GuiGraphics g, int a) {
        List<PlayerModeRow> list = bridge.playersList();
        if (list == null) return;
        long players = list.stream().filter(p -> !p.isSpectator).count();
        long spectators = list.stream().filter(p -> p.isSpectator).count();
        g.drawString(this.font,
                Component.translatable("gui.shaurmalib.settings.player_stats", players, spectators),
                bgX + PAD, bgY + bgH - FOOTER_H + 10,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.TEXT_DEFAULT, a), false);
    }

    // ─── Скроллбар / лок / save-лог ─────────────────────────────────

    private void renderScrollbar(GuiGraphics g, int a, int cTop, int cBot) {
        int viewH = cBot - cTop;
        int maxS = activeTab == 0 ? maxScrollSettings(viewH) : maxScrollPlayers(viewH);
        if (maxS <= 0) return;

        int sbX = bgX + bgW - PAD - 4;
        int cur = activeTab == 0 ? settingsScroll : playersScroll;

        g.fill(sbX, cTop, sbX + 4, cTop + viewH,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.BORDER, (int) (animator.rawProgress() * 60)));
        float pct = (float) cur / maxS;
        int hH = Math.max(16, viewH * viewH / (maxS + viewH));
        int hY = cTop + (int) (pct * (viewH - hH));
        g.fill(sbX, hY, sbX + 4, hY + hH,
                OverlayPanelStyle.withAlpha(isDragging ? OverlayPanelStyle.ACCENT_YELLOW : 0xFF7A6000, a));
    }

    private void renderLockedOverlay(GuiGraphics g, int a, int cTop, int cBot) {
        g.fill(bgX + 1, cTop, bgX + bgW - 1, cBot,
                OverlayPanelStyle.withAlpha(0x1A0505, (int) (animator.rawProgress() * 100)));
        g.drawCenteredString(this.font,
                Component.translatable("gui.shaurmalib.settings.locked_hint"),
                bgX + bgW / 2, (cTop + cBot) / 2 - 4,
                OverlayPanelStyle.withAlpha(OverlayPanelStyle.ACCENT_RED, a));
    }

    private void renderSaveLog(GuiGraphics g, float ea) {
        if (saveLog.isEmpty()) return;
        long elapsed = System.currentTimeMillis() - saveLogTimer;
        if (elapsed > 4000) {
            saveLog.clear();
            return;
        }
        float fade = elapsed < 2500 ? 1f : 1f - (float) (elapsed - 2500) / 1500f;
        int logA = (int) (Math.min(1f, fade) * ea * 200);

        int lH = 10;
        int lW = bgW - PAD * 2;
        int lY = bgY + bgH - FOOTER_H - saveLog.size() * lH - 6;

        g.fill(bgX + PAD - 2, lY - 2, bgX + PAD + lW + 2, lY + saveLog.size() * lH + 4,
                OverlayPanelStyle.withAlpha(0x07080C, logA / 3));
        style.hline(g, bgX + PAD - 2, lY - 2, lW + 4, OverlayPanelStyle.withAlpha(0xFF7A6000, logA));
        for (int i = 0; i < saveLog.size(); i++) {
            g.drawString(this.font, Component.literal(saveLog.get(i)),
                    bgX + PAD, lY + i * lH,
                    OverlayPanelStyle.withAlpha(OverlayPanelStyle.TEXT_DEFAULT, logA), false);
        }
    }

    // ─── Input ────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return false;
        if (super.mouseClicked(mx, my, btn)) {
            if (clickSound != null) clickSound.run();
            return true;
        }

        if (isIdle()) {
            int btnW = modeW - 4;
            int btnX = modeX + 2;
            int btnY = modeY + HEADER_H + 8;
            int i = 0;
            for (GameModeContract mode : modeRegistry.all()) {
                int y = btnY + i * (MODE_BTN_H + MODE_BTN_GAP);
                if (mx >= btnX && mx <= btnX + btnW && my >= y && my <= y + MODE_BTN_H) {
                    if (clickSound != null) clickSound.run();
                    bridge.requestModeChange(mode.id());
                    return true;
                }
                i++;
            }
        }

        int cTop = bgY + HEADER_H + (showPlayersTab ? TAB_H : 0);
        int cBot = bgY + bgH - FOOTER_H;
        int viewH = cBot - cTop;
        int sbX = bgX + bgW - PAD - 4;
        int maxS = activeTab == 0 ? maxScrollSettings(viewH) : maxScrollPlayers(viewH);
        if (maxS > 0 && mx >= sbX - 2 && mx <= sbX + 6 && my >= cTop && my <= cTop + viewH) {
            isDragging = true;
            dragScroll(my, cTop, viewH);
            return true;
        }

        if (activeTab == 0 && isIdle()) {
            int rowX = bgX + PAD;
            int rowW = bgW - PAD * 2 - 12;
            int startY = cTop + 4 - settingsScroll;

            for (int i = 0; i < settings.size(); i++) {
                SettingDefinition s = settings.get(i);
                int rowY = startY + i * (ROW_H + 2);
                if (rowY + ROW_H < cTop || rowY > cBot) continue;

                int optCount = s.options.size();
                if (optCount == 0) continue;
                int nameX = rowX + ICON_PAD + ICON_SZ + ICON_PAD;
                int optW = Math.min(64, (rowW - (nameX - rowX)) / optCount - OPT_GAP);
                int optTotalW = optCount * (optW + OPT_GAP) - OPT_GAP;
                int optStartX = rowX + rowW - optTotalW - ICON_PAD;

                for (int j = 0; j < optCount; j++) {
                    int ox = optStartX + j * (optW + OPT_GAP);
                    int oy = rowY + (ROW_H - OPT_H) / 2;
                    if (mx >= ox && mx <= ox + optW && my >= oy && my <= oy + OPT_H) {
                        if (clickSound != null) clickSound.run();
                        selectedValues.put(s.id, s.options.get(j).value);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) isDragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (isDragging) {
            int cTop = bgY + HEADER_H + (showPlayersTab ? TAB_H : 0);
            int cBot = bgY + bgH - FOOTER_H;
            dragScroll(my, cTop, cBot - cTop);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    private void dragScroll(double my, int top, int viewH) {
        int maxS = activeTab == 0 ? maxScrollSettings(viewH) : maxScrollPlayers(viewH);
        if (maxS <= 0) return;
        int hH = Math.max(16, viewH * viewH / (maxS + viewH));
        float pct = (float) (my - top - hH / 2f) / (viewH - hH);
        int val = Mth.clamp((int) (pct * maxS), 0, maxS);
        if (activeTab == 0) settingsScroll = val;
        else {
            playersScroll = val;
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int cTop = bgY + HEADER_H + (showPlayersTab ? TAB_H : 0);
        int cBot = bgY + bgH - FOOTER_H;
        int viewH = cBot - cTop;
        int delta = (int) (-amount * 18);
        if (activeTab == 0) {
            settingsScroll = Mth.clamp(settingsScroll + delta, 0, maxScrollSettings(viewH));
        } else {
            int prev = playersScroll;
            playersScroll = Mth.clamp(playersScroll + delta, 0, maxScrollPlayers(viewH));
            if (playersScroll != prev) rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateWidgetHover(int mx, int my) {
        int newHover = -1;
        for (var w : renderables) {
            if (w instanceof AnimatedMenuTab || w instanceof AnimatedMenuButton) {
                if (((AbstractWidget) w).isMouseOver(mx, my)) {
                    newHover = 1;
                    break;
                }
            }
        }
        if (isIdle()) {
            int btnW = modeW - 4;
            int btnX = modeX + 2;
            int btnY = modeY + HEADER_H + 8;
            int i = 0;
            for (GameModeContract ignored : modeRegistry.all()) {
                int y = btnY + i * (MODE_BTN_H + MODE_BTN_GAP);
                if (mx >= btnX && mx <= btnX + btnW && my >= y && my <= y + MODE_BTN_H) {
                    newHover = 2 + i;
                    break;
                }
                i++;
            }
        }
        if (newHover != lastHoveredSlot && newHover >= 0) {
            lastHoveredSlot = newHover;
            long now = System.currentTimeMillis();
            if (now - lastHoverSoundMs > 60) {
                lastHoverSoundMs = now;
                if (hoverSound != null) hoverSound.run();
            }
        }
        if (newHover < 0) lastHoveredSlot = -1;
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private int maxScrollSettings(int viewH) {
        return Math.max(0, settings.size() * (ROW_H + 2) + 4 - viewH);
    }

    private int maxScrollPlayers(int viewH) {
        List<PlayerModeRow> list = bridge.playersList();
        int count = list != null ? list.size() : 0;
        return Math.max(0, count * PLAYER_RH - viewH);
    }

    private String trimText(String t, int maxPx) {
        if (this.font.width(t) <= maxPx) return t;
        return this.font.plainSubstrByWidth(t, maxPx - this.font.width("\u2026")) + "\u2026";
    }

    // ─── Дії ────────────────────────────────────────────────────────

    private void saveSettings() {
        saveLog.clear();
        for (SettingDefinition s : settings) {
            int ov = previousValues.getOrDefault(s.id, s.defaultValue);
            int nv = selectedValues.getOrDefault(s.id, s.defaultValue);
            if (ov != nv) {
                String ol = s.options.stream().filter(o -> o.value == ov)
                        .map(o -> o.displayTextKey).findFirst().orElse(String.valueOf(ov));
                String nl = s.options.stream().filter(o -> o.value == nv)
                        .map(o -> o.displayTextKey).findFirst().orElse(String.valueOf(nv));
                String nm = Component.translatable(s.displayNameKey).getString();
                if (nm.length() > 16) nm = nm.substring(0, 14) + "..";
                saveLog.add("\u00a77" + nm + ": \u00a7c"
                        + Component.translatable(ol).getString()
                        + " \u00a77\u2192 \u00a7a"
                        + Component.translatable(nl).getString());
            }
            previousValues.put(s.id, nv);
        }
        if (saveLog.isEmpty()) {
            saveLog.add("\u00a77" + Component.translatable("gui.shaurmalib.settings.saved").getString());
        }
        saveLogTimer = System.currentTimeMillis();
        bridge.saveSettings(new HashMap<>(selectedValues));
    }

    private void resetToDefaults() {
        bridge.saveSettings(Map.of());
        saveLog.clear();
        saveLog.add("\u00a77" + Component.translatable("gui.shaurmalib.settings.reset_done").getString());
        saveLogTimer = System.currentTimeMillis();
    }
}
