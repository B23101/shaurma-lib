package dev.shaurmalib.forge.menu;

import dev.shaurmalib.common.overlay.ScreenOpenAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import dev.shaurmalib.forge.overlay.OverlayPanelStyle;

/**
 * Кнопка-таб для перемикання вкладок у меню бібліотеки (план, п. 3.3,
 * {@code MenuStyleTheme}) — перенесення {@code OverlayTab} з
 * {@code core/client/gui/widget/} snipers_shaurma. Оригінальний
 * коментар класу вже прямо констатував, що це мало стати спільним
 * кодом ("щоб GameSettingsScreen і ModOptionsScreen користувались одним
 * класом замість двох копій коду") — тут це завершено на бібліотечному
 * рівні: будь-який екран мода (включно з {@code ModeSettingsScreen},
 * план 3.32) використовує один і той самий {@code AnimatedMenuTab}, а не
 * копію коду під кожен новий Screen.
 */
public class AnimatedMenuTab extends Button {

    private final boolean active;
    private final ScreenOpenAnimator animator;
    private final int rowHoverColor;
    private final int borderColor;
    private final int bgColor;
    private final int accentColor;
    private final int grayColor;

    public AnimatedMenuTab(int x, int y, int w, int h, Component label, OnPress press, boolean active,
                            ScreenOpenAnimator animator,
                            int rowHoverColor, int borderColor, int bgColor,
                            int accentColor, int grayColor) {
        super(x, y, w, h, label, press, DEFAULT_NARRATION);
        this.active = active;
        this.animator = animator;
        this.rowHoverColor = rowHoverColor;
        this.borderColor = borderColor;
        this.bgColor = bgColor;
        this.accentColor = accentColor;
        this.grayColor = grayColor;
    }

    /** Конструктор зі стандартною палітрою бібліотеки (для нових екранів, що не задають власну тему). */
    public AnimatedMenuTab(int x, int y, int w, int h, Component label, OnPress press, boolean active,
                            ScreenOpenAnimator animator) {
        this(x, y, w, h, label, press, active, animator,
                0x2AFFFFFF, OverlayPanelStyle.BORDER, OverlayPanelStyle.BG_BASE,
                OverlayPanelStyle.ACCENT_YELLOW, 0xFFAAAAAA);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        int a = animator.panelAlpha255();
        int bg = active
                ? OverlayPanelStyle.withAlpha(rowHoverColor, (int) (a * 0.4f))
                : isHovered()
                ? OverlayPanelStyle.withAlpha(borderColor, (int) (a * 0.2f))
                : OverlayPanelStyle.withAlpha(bgColor, (int) (a * 0.05f));
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                OverlayPanelStyle.withAlpha(active ? accentColor : grayColor, a));
    }
}
