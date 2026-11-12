package dev.shaurmalib.meta.mc.screen;

import dev.shaurmalib.meta.economy.Currency;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.match.MatchResult;
import dev.shaurmalib.meta.mc.MetaScreens;
import dev.shaurmalib.meta.mc.skin.MetaSkin;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import dev.shaurmalib.meta.progress.RankLadder;
import dev.shaurmalib.meta.stats.StatDef;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Екран «Матч завершено»: зліва рядки «Досвід за дії» (по черзі в'їжджають), у центрі емблема рівня,
 * лічильник XP, що росте разом із рядками, смужка, яка заповнюється й перескакує рівні (спалах, звук),
 * знизу картки «очко покращень» і монет. Enter/«Пропустити» домотує анімацію, друге натискання закриває.
 * Усі цифри — з {@link MatchResult}, нічого не рахується вдруге. Вигляд — скін режиму.
 */
public class ResultScreen extends MetaScreen {

    private final MatchResult r;
    private float skipT = -1;
    private int lastLevel;
    private float levelFlash;
    private boolean doneSound;

    private static final float LINE_START = .55f, LINE_STEP = .32f, BAR_DUR = 1.7f;

    public ResultScreen(MatchResult result, Screen parent) {
        super("Підсумки", parent);
        this.r = result;
        this.nav = false;
        this.lastLevel = result.levelBefore;
    }

    @Override protected String section() { return "result"; }

    private float t() { return skipT >= 0 ? Math.max(time, 99f) : time; }
    private float barStart() { return LINE_START + Math.min(9, r.lines.size()) * LINE_STEP + .25f; }
    private float cardsStart() { return barStart() + BAR_DUR + .1f; }
    private boolean finished() { return t() >= cardsStart() + .6f; }

    @Override
    protected void draw(GuiGraphics g) {
        float t = t();
        float sTop = MetaUi.outCubic(t / .4f), sL = MetaUi.outCubic((t - .1f) / .5f), sC = MetaUi.outCubic((t - .2f) / .5f);
        int resultColor = r.won ? theme.good() : theme.bad();

        // ── верх: результат
        int topY = 14 + Math.round((1f - sTop) * -60);
        panel(g, 20, topY, 920, 54, true);
        skin.header(g, font, 34, topY + 8, "МАТЧ ЗАВЕРШЕНО", r.won ? "ПЕРЕМОГА" : "ПОРАЗКА", time);
        String right = (r.role.isEmpty() ? "" : r.role.toUpperCase() + "   ·   ") + StatDef.formatSeconds(r.durationSeconds);
        MetaUi.textR(g, font, right, 926, topY + 22, MetaUi.solid(theme.dim()), false);

        // ── ліва: досвід за дії
        int lx = 20 + Math.round((1f - sL) * -360);
        panel(g, lx, 80, 310, 322, false);
        skin.sectionTitle(g, font, lx + 14, 92, 282, "ДОСВІД ЗА ДІЇ", null, time);
        int ly = 116;
        for (int i = 0; i < r.lines.size() && i < 9; i++) {
            MatchResult.Line line = r.lines.get(i);
            float a = MetaUi.clamp01((t - LINE_START - i * LINE_STEP) / .35f);
            if (a <= .01f) { ly += 31; continue; }
            int rx = lx + 10 + Math.round((1f - MetaUi.outBack(a)) * -40);
            skin.card(g, rx, ly, 290, 27, theme.border(), false, 0, time);
            icon(g, line.icon(), rx + 7, ly + 6, 2, MetaUi.a(theme.text(), a));
            MetaUi.text(g, font, MetaUi.fit(font, line.name(), 190), rx + 30, ly + (line.count() > 1 ? 5 : 10), MetaUi.a(theme.text(), a), false);
            if (line.count() > 1) MetaUi.textS(g, font, "×" + line.count(), rx + 30, ly + 16, .8f, MetaUi.a(theme.dim(), a), false);
            MetaUi.textR(g, font, "+" + line.xp(), rx + 282, ly + 10, MetaUi.a(theme.gold(), a), true);
            skin.cardOverlay(g, rx, ly, 290, 27, theme.border(), false, 0, time);
            ly += 31;
        }
        if (MetaScreens.onResultStats != null) {
            button(g, "stats", 20, 412, 150, 24, "СТАТИСТИКА", false, () -> { click(); MetaScreens.onResultStats.accept(this); });
        }

        // ── центр: рівень
        int cy0 = 80 + Math.round((1f - sC) * 40);
        panel(g, 346, cy0, 594, 322, true);
        MetaUi.textC(g, font, "ЗАГАЛЬНИЙ РІВЕНЬ", 643, cy0 + 14, MetaUi.solid(theme.dim()), false);

        float barT = MetaUi.clamp01((t - barStart()) / BAR_DUR);
        float eased = 1f - (float) Math.pow(1f - barT, 2.2);
        long total = r.xpBefore + Math.round((r.xpAfter - r.xpBefore) * eased);
        int level = mode.curve.levelOf(total);
        if (level > lastLevel) {
            lastLevel = level;
            levelFlash = 1f;
            if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1f, .8f));
        }
        levelFlash = Math.max(0, levelFlash - dt * 1.6f);

        int hx = 643, hy = cy0 + 92;
        skin.levelBadge(g, font, hx, hy, 38, level, theme.accent(), levelFlash, time);
        skin.divider(g, hx - 150, hy, 90);
        skin.divider(g, hx + 60, hy, 90);
        RankLadder lad = mode.ladder(r.ladder);
        if (lad != null) {
            RankLadder.Rank rk = lad.ofLevel(level);
            MetaUi.textC(g, font, rk.title().toUpperCase() + "   ·   " + lad.name().toUpperCase(), hx, hy + 50, MetaUi.solid(rk.color()), false);
        }
        int shown = 0;
        for (int i = 0; i < r.lines.size(); i++) shown += Math.round(r.lines.get(i).xp() * MetaUi.clamp01((t - LINE_START - i * LINE_STEP) / .35f));
        MetaUi.textSC(g, font, "+" + shown + " XP", hx, hy + 66, 2.4f, MetaUi.solid(theme.gold()), true);
        MetaUi.textSC(g, font, "ЗАРАХУВАННЯ ДОСВІДУ", hx, hy + 92, .75f, MetaUi.solid(theme.dim()), false);

        int bx = 366, bw = 554, by = cy0 + 224;
        int needed = mode.curve.needed(level);
        long into = mode.curve.intoLevel(total);
        float fill = level >= mode.curve.maxLevel() ? 1f : MetaUi.clamp01(into / (float) needed);
        skin.bar(g, bx, by, bw, 8, fill, theme.accent(), time);
        if (levelFlash > 0) g.fill(bx, by, bx + bw, by + 8, MetaUi.a(0xFFFFFF, levelFlash * .5f));
        MetaUi.text(g, font, MetaUi.num(into) + " / " + MetaUi.num(needed) + " XP", bx, by + 14, MetaUi.solid(theme.dim()), false);
        MetaUi.textR(g, font, "ДАЛІ — РІВЕНЬ " + (level + 1), bx + bw, by + 14, MetaUi.solid(theme.dim()), false);

        if (!r.drops.isEmpty()) {
            int dx = bx, dy = by + 34;
            MetaUi.text(g, font, "ЗНАЙДЕНО:", dx, dy + 3, MetaUi.solid(theme.dim()), false);
            dx += font.width("ЗНАЙДЕНО:") + 8;
            for (Reward d : r.drops) dx += skin.chip(g, font, dx, dy, MetaScreens.rewardText(mode, d), MetaScreens.rewardIcon(mode, d), MetaScreens.rewardColor(mode, d)) + 6;
        }

        // ── картки внизу
        float sCard = MetaUi.outBack(MetaUi.clamp01((t - cardsStart()) / .45f));
        if (sCard > .01f) {
            int cyy = 412 + Math.round((1f - sCard) * 30);
            int cw = r.coins.isEmpty() ? 594 : 400;
            panel(g, 346, cyy, cw, 52, false);
            icon(g, "upgrade", 360, cyy + 18, 2, MetaUi.solid(theme.accent()));
            String head, sub;
            if (r.upgradePoints > 0) {
                head = "+" + r.upgradePoints + (r.upgradePoints == 1 ? " очко покращень" : " очки покращень");
                sub = "Уже доступні у спільному запасі. Обери покращення.";
            } else if (!r.unlockedTrack.isEmpty()) {
                head = "Відкрито ступінь пропуску " + r.unlockedTrack.get(0);
                sub = "Забери нагороду на екрані пропуску.";
            } else {
                head = "До наступного рівня: " + MetaUi.num(mode.curve.needed(level) - mode.curve.intoLevel(total)) + " XP";
                sub = "Кожен новий рівень — очко покращень.";
            }
            MetaUi.text(g, font, head, 384, cyy + 12, MetaUi.solid(theme.text()), true);
            MetaUi.textS(g, font, sub, 384, cyy + 28, .8f, MetaUi.solid(theme.dim()), false);
            if (!r.unlockedTrack.isEmpty() && r.upgradePoints > 0) {
                MetaUi.textR(g, font, "+ ступінь пропуску " + r.unlockedTrack.get(0), 346 + cw - 12, cyy + 12, MetaUi.solid(theme.gold()), false);
            }
            if (!r.coins.isEmpty()) {
                MatchResult.Coins c = r.coins.get(0);
                Currency cur = mode.currency(c.currency());
                int col = cur == null ? theme.gold() : cur.color();
                panel(g, 756, cyy, 184, 52, false);
                icon(g, cur == null ? "coin" : cur.icon(), 768, cyy + 18, 2, MetaUi.solid(col));
                long sum = 0;
                for (MatchResult.Coins x : r.coins) if (x.currency().equals(c.currency())) sum += x.amount();
                long anim = Math.round(sum * MetaUi.clamp01((t - cardsStart() - .2f) / .8f));
                MetaUi.text(g, font, "+" + anim + " " + (cur == null ? c.currency() : cur.name()), 790, cyy + 12, MetaUi.solid(theme.text()), true);
                MetaUi.textS(g, font, MetaUi.fit(font, c.note(), 186), 790, cyy + 28, .75f, MetaUi.solid(theme.dim()), false);
            }
        }

        String up = !mode.upgrades.isEmpty() ? "ДО ПОКРАЩЕНЬ" : "ДО ПРОПУСКУ";
        button(g, "up", 346, 480, 200, 26, up, false, () -> {
            click();
            if (MetaScreens.onResultUpgrades != null) MetaScreens.onResultUpgrades.accept(this);
            else MetaScreens.open(mode.upgrades.isEmpty() ? "track" : "upgrades", null);
        });
        button(g, "skip", 740, 480, 200, 26, finished() ? "ЗАКРИТИ  [ENTER]" : "ПРОПУСТИТИ  [ENTER]", MetaSkin.Kind.PRIMARY, this::skip);

        if (finished() && !doneSound) {
            doneSound = true;
            if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, .6f));
        }
    }

    private void skip() {
        click();
        if (finished()) onClose();
        else skipT = time;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) { skip(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
