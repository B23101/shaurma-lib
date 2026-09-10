package dev.shaurmalib.forge.tab;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.shaurmalib.common.tab.TabColumn;
import dev.shaurmalib.common.tab.TabListLineProvider;
import dev.shaurmalib.common.tab.TabRow;
import dev.shaurmalib.common.tab.TabTeamBarSpec;
import dev.shaurmalib.forge.overlay.OverlayPanelStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Рушій рендеру Tab-таблиці (план, п. 3.3 + 3.7) — перенесення спільних
 * рендер-примітивів та анімаційних хелперів {@code CustomTabOverlay}
 * (1428 рядків) snipers_shaurma в один переносний instance-based клас.
 * <p>
 * <b>Що перенесено 1:1</b> (справжній спільний "движок таблиці", той
 * самий код, що повторювався практично незмінним у кожному з 6
 * паралельних render-методів оригіналу — {@code renderSoloGameTab}/
 * {@code renderTeamGameTab}/{@code renderBRSoloTab}/{@code renderBRTeamTab}/
 * {@code renderSCNTab}/{@code renderSDSoloTab}/{@code renderSDTeamTab}):
 * панель з золотою рамкою ({@code drawModernPanel}), заголовок+підзаголовок
 * ({@code drawModernHeader}), фон заголовка колонки ({@code drawColHeaderBg}),
 * зебра-фон рядка з "isMe" підсвіткою ({@code drawRowBg}), золота
 * пульсація досягнутої мети ({@code drawGoalPulse}), помаранчева
 * пульсація "відродження" з бейджем ({@code drawRespawning}),
 * перекреслений текст спектатора ({@code drawStrikeSubtle}), базова
 * командна панель ({@code drawTeamBar}), голова гравця з 3D-скіна
 * ({@code drawHead}) і три незалежні анімаційні системи (позиція рядка,
 * плавна зміна числового значення, прозорість рядка) — усі з тими самими
 * коефіцієнтами згладжування, що в оригіналі ({@code 0.15f}/{@code 0.12}/
 * {@code 0.08f}).
 * <p>
 * <b>Не перенесено</b> (лишається продуктовою специфікою консюмера,
 * план розділ 4): конкретний layout кожного з 6 режимів (які саме
 * колонки, скільки колонок, 1 vs 2-колонкове розкладення команд,
 * SD-специфічні квадрати прогресу перемог {@code drawTeamBarWithSquares})
 * — консюмер сам вирішує це через {@link TabListLineProvider} і власний
 * layout-код навколо викликів цього класу; рушій лише малює те, що
 * отримав.
 * <p>
 * Instance-based — один {@code TabListStyle} на консюмера (типово
 * зберігається в полі клієнтського стану поряд зі {@code StyleTheme}),
 * бо анімаційні мапи (позиція/значення/прозорість по {@code animKey})
 * — стан, що живе довше одного кадру, як і {@code ScreenOpenAnimator}.
 */
public final class TabListStyle {

    // ── Дефолтна палітра (перенесена з CustomTabOverlay) ─────────────
    public static final int COL_GOLD = 0xFFFFAA00;
    public static final int COL_AQUA = 0xFF55FFFF;
    public static final int COL_YELLOW = 0xFFFFFF00;
    public static final int COL_GREEN = 0xFF55FF55;
    public static final int COL_RED = 0xFFFF5555;
    public static final int COL_GRAY = 0xFF808080;
    public static final int COL_WHITE = 0xFFFFFFFF;
    public static final int COL_GOAL = 0xFFFFD700;
    public static final int ACCENT_GOLD = 0xFFD86A;
    public static final int ACCENT_GOLD_DM = 0x7A5A20;
    public static final int RESPAWN_ORANGE = 0xFFAA55;
    public static final int STRIPE_A = 0x0A0F18;
    public static final int STRIPE_B = 0x12182A;
    public static final int COL_HDR_LINE = 0x3A4660;
    public static final int TEXT_DIM = 0xA8B0BE;
    public static final int STRIKE_LINE = 0x6A7180;

    public static final int HEAD_SIZE = 12;
    public static final int ROW_H = 16;
    public static final int TITLE_H = 28;
    public static final int HDR_H = 14;

    private final Function<UUID, ResourceLocation> skinResolver;

    private final Map<String, Float> rowAnimY = new HashMap<>();
    private final Map<String, Float> rowTargetY = new HashMap<>();
    private final Map<String, Double> valueAnim = new HashMap<>();
    private final Map<String, Float> rowOpacity = new HashMap<>();
    private long pulseTimer = 0L;

    /**
     * @param skinResolver постачальник {@code ResourceLocation} 3D-скіна
     *                     гравця за {@code UUID} (в оригіналі —
     *                     {@code PlayerSkinManager.requestSkin}/
     *                     {@code getSkin}) — бібліотека не має власного
     *                     кешу скінів, консюмер підключає свій.
     */
    public TabListStyle(Function<UUID, ResourceLocation> skinResolver) {
        this.skinResolver = skinResolver;
    }

    /** Викликати рівно один раз на кадр, до будь-яких {@code render*} нижче — просуває pulse-таймер goal/respawn ефектів. */
    public void tick() {
        pulseTimer++;
    }

    // ── Панель / заголовок ──────────────────────────────────────────

    /** Золота рамка панелі — 1:1 {@code drawModernPanel}. */
    public void drawPanel(GuiGraphics g, int x, int y, int w, int h, float anim) {
        int a = (int) (anim * 255) & 0xFF;
        g.fill(x, y, x + w, y + h, ((int) (anim * 0x99) << 24) | 0x000000);
        g.fill(x, y, x + w, y + 1, (a << 24) | (ACCENT_GOLD & 0xFFFFFF));
        g.fill(x, y + 1, x + w, y + 2, (Math.max(0, a - 180) << 24) | (ACCENT_GOLD & 0xFFFFFF));
        int side = (((int) (anim * 0x55)) << 24) | (ACCENT_GOLD_DM & 0xFFFFFF);
        g.fill(x, y, x + 1, y + h, side);
        g.fill(x + w - 1, y, x + w, y + h, side);
        g.fill(x, y + h - 1, x + w, y + h, side);
    }

    /** Заголовок + підзаголовок по центру — 1:1 {@code drawModernHeader}. */
    public void drawHeader(GuiGraphics g, Font font, int cx, int sy, float anim, String titleKey, String subtitleKey) {
        int a = (int) (anim * 255);
        g.drawCenteredString(font, Component.literal(Component.translatable(titleKey).getString().toUpperCase()),
                cx, sy + 4, (a << 24) | (ACCENT_GOLD & 0xFFFFFF));
        if (subtitleKey != null) {
            int subA = (int) (anim * 110);
            g.drawCenteredString(font, Component.literal(Component.translatable(subtitleKey).getString()),
                    cx, sy + 15, (subA << 24) | 0xAAAAAA);
        }
    }

    /** Онлайн-лічильник під заголовком (лобі-режим оригіналу). */
    public void drawOnlineCount(GuiGraphics g, Font font, int cx, int y, float anim, String countKey, int count) {
        int color = (int) (alpha(anim) * 0.85f);
        g.drawCenteredString(font, Component.literal("\u25cf " + Component.translatable(countKey, count).getString()),
                cx, y, (color << 24) | 0x55FF55);
    }

    /** Фон заголовка колонки — 1:1 {@code drawColHeaderBg} (висота відповідає {@link #HDR_H}=14: фон 13px + роздільник 1px). */
    public void drawColumnHeaderBg(GuiGraphics g, int x, int y, int w, float anim) {
        g.fill(x, y, x + w, y + 13, ((int) (anim * 40) << 24) | 0x12182A);
        g.fill(x, y + 13, x + w, y + 14, ((int) (anim * 90) << 24) | COL_HDR_LINE);
    }

    /** Малює всі заголовки колонок таблиці в один рядок на висоті {@code y}. */
    public void drawColumns(GuiGraphics g, Font font, int ox, int y, List<TabColumn> columns, float anim) {
        int hc = alpha(anim) << 24 | COL_GRAY;
        for (TabColumn col : columns) {
            g.drawString(font, Component.translatable(col.headerTextKey).getString(), ox + col.offsetX, y, hc, false);
        }
    }

    // ── Рядок гравця ─────────────────────────────────────────────────

    /** Зебра-фон рядка з опційною "isMe" золотою підсвіткою і лівим акцентним бордером — 1:1 {@code drawRowBg}. */
    public void drawRowBg(GuiGraphics g, int x, int y, int w, int h, int rank, boolean isMe, int teamTint, float anim) {
        int stripe = (rank % 2 == 0) ? STRIPE_A : STRIPE_B;
        g.fill(x, y, x + w, y + h, ((int) (anim * 0x38) << 24) | stripe);
        if (teamTint != 0) g.fill(x, y, x + w, y + h, ((int) (anim * 0x18) << 24) | (teamTint & 0xFFFFFF));
        if (isMe) {
            g.fill(x, y, x + w, y + h, ((int) (anim * 0x28) << 24) | 0xFFD700);
            g.fill(x, y, x + 2, y + h, ((int) (anim * 255) << 24) | (ACCENT_GOLD & 0xFFFFFF));
        }
    }

    /** Золота пульсація "мету досягнуто" замість звичайного фону рядка — 1:1 {@code drawGoalPulse}. */
    public void drawGoalPulse(GuiGraphics g, int x, int y, int w, int h, float anim) {
        float pulse = (float) (Math.sin(pulseTimer * 0.08) * 0.3 + 0.5);
        int pA = (int) (anim * pulse * 180);
        if (pA < 4) return;
        g.fill(x, y, x + w, y + h, (pA << 24) | 0xFFD700);
        g.fill(x, y, x + 2, y + h, ((int) (anim * 255) << 24) | (ACCENT_GOLD & 0xFFFFFF));
    }

    /** Помаранчева пульсація "відродження" з {@code [ВІДРОДЖЕННЯ]}-бейджем (скорочується до {@code [\u21ba]}, якщо не вміщається) — 1:1 {@code drawRespawning}. */
    public void drawRespawning(GuiGraphics g, Font font, int x0, int rowW, int rowH,
                                int textX, int textY, String name, String respawnLabel, float anim) {
        float pulse = (float) (Math.sin(pulseTimer * 0.10) * 0.35 + 0.65);
        int rowY = textY - (rowH - font.lineHeight) / 2;
        int aFill = (int) (anim * pulse * 60);
        if (aFill > 4) g.fill(x0, rowY, x0 + rowW, rowY + rowH, (aFill << 24) | (RESPAWN_ORANGE & 0xFFFFFF));
        int aBar = (int) (anim * pulse * 255);
        if (aBar > 6) g.fill(x0, rowY, x0 + 2, rowY + rowH, (aBar << 24) | (RESPAWN_ORANGE & 0xFFFFFF));
        int aName = (int) (anim * (0.7f + 0.3f * pulse) * 255);
        g.drawString(font, name, textX, textY, (aName << 24) | (RESPAWN_ORANGE & 0xFFFFFF), false);

        String badge = "[" + respawnLabel + "]";
        int badgeX = textX + font.width(name) + 3;
        int availW = x0 + rowW - badgeX - 2;
        if (font.width(badge) > availW) badge = "[\u21ba]";
        if (availW > 6) {
            g.fill(badgeX - 1, textY - 1, badgeX + font.width(badge) + 2, textY + font.lineHeight,
                    ((int) (anim * 0x55) << 24) | 0x1A1F2E);
            g.drawString(font, badge, badgeX, textY, ((int) (anim * pulse * 255) << 24) | (ACCENT_GOLD & 0xFFFFFF), false);
        }
    }

    /** Приглушений перекреслений текст (спектатор) — 1:1 {@code drawStrikeSubtle}. */
    public void drawStrikeThrough(GuiGraphics g, Font font, String text, int x, int y, float anim) {
        int aText = (int) (anim * 140);
        g.drawString(font, text, x, y, (aText << 24) | TEXT_DIM, false);
        int midY = y + font.lineHeight / 2;
        int aLine = (int) (anim * 180);
        g.fill(x, midY, x + font.width(text), midY + 1, (aLine << 24) | STRIKE_LINE);
    }

    /**
     * Малює ВСІ {@link TabCell} рядка (включно з нульовою) на заданих
     * X-офсетах колонок — для консюмера, що не використовує ім'я/ранг/
     * respawn-бейдж {@link #drawRow} і хоче повний контроль над кожною
     * клітинкою сам (напр. таблиця без окремої "іменної" колонки).
     * {@link #drawRow} — зручний оркеструючий шлях для типового випадку
     * (ранг + голова + ім'я в {@code cells.get(0)} + решта клітинок як
     * тут); обидва шляхи узгоджені з тими самими {@link TabColumn#offsetX}.
     */
    public void drawCells(GuiGraphics g, Font font, int ox, int textY, List<TabColumn> columns, TabRow row) {
        int n = Math.min(columns.size(), row.cells.size());
        for (int i = 0; i < n; i++) {
            var cell = row.cells.get(i);
            if (cell.text == null || cell.text.isEmpty()) continue;
            if (cell.struckThrough) {
                drawStrikeThrough(g, font, cell.text, ox + columns.get(i).offsetX, textY, 1f);
            } else {
                g.drawString(font, cell.text, ox + columns.get(i).offsetX, textY, cell.argbColor, false);
            }
        }
    }

    /**
     * Оркеструючий метод одного рядка — той самий порядок викликів, що в
     * кожному з 6 паралельних {@code render*GameTab} оригіналу (фон/пульс
     * → ранг → голова → ім'я (звичайне/перекреслене/respawn-бейдж) →
     * клітинки колонок), лише параметризований готовим {@link TabRow}
     * замість повторення блоку if/else в кожному консюмері. Консюмер, якому
     * потрібне нестандартне розташування (напр. власна SD-версія з
     * квадратами прогресу), продовжує викликати примітиви вище напряму —
     * цей метод не обов'язковий, лише зручний дефолтний шлях.
     *
     * @param x        лівий край рядка (колонка вже вибрана консюмером)
     * @param y        верх рядка (уже після {@link #getAnimY})
     * @param w        ширина колонки (== ширина рядка)
     * @param rank     1-based місце гравця, лише для чергування зебри (парність) — сам текст рангу береться з {@code row.rankText}, {@code null} у якому взагалі не малює колонку рангу
     * @param rankColor колір тексту рангу/імені (консюмер вирішує голд/жовтий/аква/сірий сам, як в оригіналі)
     * @param teamTint  колір тінту команди (0 — без тінту, лобі/соло-режими)
     * @param respawnLabel текст бейджу відродження (наприклад "ВІДРОДЖЕННЯ"); ігнорується, якщо {@code row.isRespawning == false}
     * @param anim     загальна прозорість появи таблиці (0..1)
     * @param rowAlpha прозорість саме цього рядка (anim * spectator-fade, вже помножено консюмером через {@link #syncOpacity})
     */
    public void drawRow(GuiGraphics g, Font font, int x, int y, int w, int rowH,
                         TabRow row, List<TabColumn> columns,
                         int rank, int rankColor, int teamTint,
                         String respawnLabel, float anim, float rowAlpha) {
        if (row.highlighted) {
            drawGoalPulse(g, x, y - 1, w, rowH, anim);
        } else {
            drawRowBg(g, x, y, w, rowH, rank, row.isMe, teamTint, rowAlpha);
        }

        int headX = x + 14;
        int headY = y + (rowH - HEAD_SIZE) / 2;
        if (row.skinUuid != null) {
            drawHead(g, row.skinUuid, headX, headY, rowAlpha);
        }

        int textY = y + (rowH - font.lineHeight) / 2;
        if (row.rankText != null) {
            g.drawString(font, row.rankText, x + 3, textY, alpha(rowAlpha) << 24 | (rankColor & 0xFFFFFF), false);
        }

        String nameText = row.cells.isEmpty() ? "" : row.cells.get(0).text;
        int nameX = x + 30;
        if (row.isRespawning && respawnLabel != null) {
            drawRespawning(g, font, x, w, rowH, nameX, textY, nameText, respawnLabel, rowAlpha);
        } else if (row.isSpectator) {
            drawStrikeThrough(g, font, nameText, nameX, textY, rowAlpha);
        } else {
            g.drawString(font, nameText, nameX, textY, alpha(rowAlpha) << 24 | (rankColor & 0xFFFFFF), false);
        }

        // Решта клітинок (крім cells.get(0), яка вже намальована як ім'я
        // вище) — колонки координуються з тими самими TabColumn.offsetX,
        // що й drawColumns; консюмер узгоджує порядок cells з порядком
        // columns сам (той самий контракт, що й drawCells нижче).
        int n = Math.min(columns.size(), row.cells.size());
        for (int i = 1; i < n; i++) {
            var cell = row.cells.get(i);
            if (cell.text == null || cell.text.isEmpty()) continue;
            int cellX = x + columns.get(i).offsetX;
            if (cell.struckThrough) {
                drawStrikeThrough(g, font, cell.text, cellX, textY, rowAlpha);
            } else {
                g.drawString(font, cell.text, cellX, textY, cell.argbColor, false);
            }
        }
    }

    // ── Командна панель ─────────────────────────────────────────────

    /** Базова командна панель (кольорова смуга, назва зліва, значення справа) — 1:1 {@code drawTeamBar}. SD-специфічні квадрати прогресу перемог НЕ входять — план 3.3, докстрінг {@link TabTeamBarSpec}. */
    public void drawTeamBar(GuiGraphics g, Font font, int x, int y, int w, int h, TabTeamBarSpec spec, float anim) {
        int a = (int) (anim * 255);
        g.fill(x, y, x + w, y + h - 1, ((int) (anim * 0x55) << 24) | (spec.colorRGB & 0xFFFFFF));
        g.fill(x, y, x + w, y + h - 1, ((int) (anim * 0x77) << 24) | 0x05080F);
        g.fill(x, y + h - 1, x + w, y + h, (a << 24) | (spec.colorRGB & 0xFFFFFF));
        int textY = y + (h - font.lineHeight) / 2;
        g.drawString(font, "\u25cf", x + 6, textY, (a << 24) | (spec.colorRGB & 0xFFFFFF), false);
        g.drawString(font, spec.nameText, x + 18, textY, (a << 24) | 0xFFFFFF, false);
        if (spec.valueText != null && !spec.valueText.isEmpty()) {
            int vw = font.width(spec.valueText);
            g.drawString(font, spec.valueText, x + w - vw - 8, textY, (a << 24) | (ACCENT_GOLD & 0xFFFFFF), false);
        }
    }

    // ── Голова гравця ───────────────────────────────────────────────

    /** 3D-голова гравця (front+hat layer) — 1:1 {@code drawHead}; {@code null}-результат {@code skinResolver} тихо пропускається (як try/catch-ignore оригіналу). */
    public void drawHead(GuiGraphics g, UUID uuid, int x, int y, float anim) {
        try {
            ResourceLocation skin = skinResolver.apply(uuid);
            if (skin == null) return;
            RenderSystem.setShaderColor(1f, 1f, 1f, anim);
            g.blit(skin, x, y, HEAD_SIZE, HEAD_SIZE, 8, 8, 8, 8, 64, 64);
            g.blit(skin, x, y, HEAD_SIZE, HEAD_SIZE, 40, 8, 8, 8, 64, 64);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } catch (Exception ignored) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    // ── Анімаційні хелпери ───────────────────────────────────────────

    /**
     * Синхронізує цільові Y-позиції рядків для плавного "переливання"
     * при зміні сортування — 1:1 {@code syncRowPositions}
     * (коефіцієнт згладжування {@code 0.15f}). Викликати раз на кадр
     * ПЕРЕД {@link #getAnimY}, передавши бажану цільову позицію кожного
     * рядка обчислену консюмером (rank/колонка вже враховані).
     */
    public void syncRowY(String animKey, float targetY) {
        rowTargetY.put(animKey, targetY);
        rowAnimY.compute(animKey, (k, cur) -> cur == null ? targetY : cur + (targetY - cur) * 0.15f);
    }

    public float getAnimY(String animKey, float fallback) {
        return rowAnimY.getOrDefault(animKey, fallback);
    }

    /** Плавна зміна числового значення (money-анімація оригіналу) — 1:1 {@code getAnimMoneyRaw} (коефіцієнт {@code 0.12}, "доганяє" ціль коли різниця < 1). */
    public double syncValue(String animKey, double target) {
        valueAnim.compute(animKey, (k, cur) -> {
            if (cur == null) return target;
            double diff = target - cur;
            if (Math.abs(diff) < 1) return target;
            return cur + diff * 0.12;
        });
        return valueAnim.getOrDefault(animKey, target);
    }

    /** Плавна зміна прозорості рядка (spectator fade) — 1:1 {@code getRowOpacity} (коефіцієнт {@code 0.08f}). */
    public float syncOpacity(String animKey, boolean dimmed) {
        rowOpacity.compute(animKey, (k, cur) -> {
            float t = dimmed ? 0.45f : 1f;
            return cur == null ? t : cur + (t - cur) * 0.08f;
        });
        return rowOpacity.getOrDefault(animKey, 1f);
    }

    /** Очищує анімаційний стан рядків, що зникли зі списку (консюмер викликає з набором актуальних {@code animKey} на кадр, щоб мапи не росли необмежено при плинності гравців). */
    public void pruneAnimState(java.util.Set<String> activeKeys) {
        rowAnimY.keySet().retainAll(activeKeys);
        rowTargetY.keySet().retainAll(activeKeys);
        valueAnim.keySet().retainAll(activeKeys);
        rowOpacity.keySet().retainAll(activeKeys);
    }

    public static int alpha(float p) {
        return (int) (p * 255);
    }
}
