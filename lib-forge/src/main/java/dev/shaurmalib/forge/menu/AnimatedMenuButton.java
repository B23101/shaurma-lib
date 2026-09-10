package dev.shaurmalib.forge.menu;

import dev.shaurmalib.common.overlay.ScreenOpenAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import dev.shaurmalib.forge.overlay.OverlayPanelStyle;

/**
 * Стандартна кнопка меню бібліотеки (план, п. 3.3, {@code MenuStyleTheme})
 * — перенесення {@code OverlayBtn} з {@code core/client/gui/widget/}
 * snipers_shaurma. У оригіналі клас уже сам був спільною точкою правди
 * для {@code GameSettingsScreen} і {@code ModOptionsScreen} (коментар
 * класу прямо каже "план v4, §2.3") — тут він переноситься практично
 * без змін, лише {@code Supplier<Float> animProgress} замінено на
 * {@link ScreenOpenAnimator}, щоб консюмер не мусив сам писати lambda
 * {@code () -> myAnimField}, а передавав готовий аніматор екрана,
 * спільний для всіх кнопок і табів на ньому.
 * <p>
 * Дефолтна палітра — {@link OverlayPanelStyle} кольори; консюмер, якому
 * потрібна інша палітра (напр. акцент кольору режиму з {@code accentColor()},
 * план 3.18+3.32), передає власні ARGB через повний конструктор.
 */
public class AnimatedMenuButton extends Button {

    private final int accent;
    private final ScreenOpenAnimator animator;
    private final int rowHoverColor;
    private final int bgColor;
    private final int borderColor;
    private final int textColor;

    public AnimatedMenuButton(int x, int y, int w, int h, Component label, OnPress press, int accent,
                               ScreenOpenAnimator animator,
                               int rowHoverColor, int bgColor, int borderColor, int textColor) {
        super(x, y, w, h, label, press, DEFAULT_NARRATION);
        this.accent = accent;
        this.animator = animator;
        this.rowHoverColor = rowHoverColor;
        this.bgColor = bgColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
    }

    /** Конструктор зі стандартною палітрою бібліотеки (для нових екранів, що не задають власну тему). */
    public AnimatedMenuButton(int x, int y, int w, int h, Component label, OnPress press,
                               int accent, ScreenOpenAnimator animator) {
        this(x, y, w, h, label, press, accent, animator,
                0x2AFFFFFF, OverlayPanelStyle.BG_BASE, OverlayPanelStyle.BORDER,
                OverlayPanelStyle.TEXT_DEFAULT);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        int a = animator.panelAlpha255();
        int bx = getX(), by = getY(), bw = getWidth(), bh = getHeight();

        g.fill(bx, by, bx + bw, by + bh,
                OverlayPanelStyle.withAlpha(isHovered() ? rowHoverColor : bgColor, (int) (a * 0.5f)));
        OverlayPanelStyle.border1px(g, bx, by, bw, bh,
                OverlayPanelStyle.withAlpha(isHovered() ? accent : borderColor, a));
        if (isHovered()) {
            g.fill(bx + 1, by + bh - 2, bx + bw - 1, by + bh - 1,
                    OverlayPanelStyle.withAlpha(accent, (int) (a * 0.4f)));
        }
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                bx + bw / 2, by + (bh - 8) / 2,
                OverlayPanelStyle.withAlpha(isHovered() ? accent : textColor, a));
    }
}
