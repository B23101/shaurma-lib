package dev.shaurmalib.meta.mc;

import dev.shaurmalib.meta.MetaMode;
import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.cosmetics.CosmeticDef;
import dev.shaurmalib.meta.economy.Currency;
import dev.shaurmalib.meta.economy.MaterialDef;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.match.MatchResult;
import dev.shaurmalib.meta.mc.screen.LeaderboardScreen;
import dev.shaurmalib.meta.mc.screen.MetaScreen;
import dev.shaurmalib.meta.mc.screen.ProfileScreen;
import dev.shaurmalib.meta.mc.screen.ResultScreen;
import dev.shaurmalib.meta.mc.screen.ShopScreen;
import dev.shaurmalib.meta.mc.screen.TrackScreen;
import dev.shaurmalib.meta.mc.screen.WardrobeScreen;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import dev.shaurmalib.meta.profile.MetaProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Клієнтський клей мета-двигуна: відкриває екрани, малює спливаючі «Отримано!» поверх гри й меню,
 * передає повідомлення сервера у відкритий екран. Режим налаштовує кнопки екрана підсумків
 * ({@link #onResultStats}) і може підмінити будь-який екран своїм ({@link #override}).
 */
public final class MetaScreens implements MetaClient.Ui {

    public static final MetaScreens INSTANCE = new MetaScreens();

    /** Кнопка «Статистика» на екрані підсумків (у маніяку — його детальний екран матчу). */
    public static volatile Consumer<Screen> onResultStats;
    /** Що відкривається кнопкою «До покращень» (за замовчуванням — екран прогресу). */
    public static volatile Consumer<Screen> onResultUpgrades;
    /** Підміна стандартного екрана розділу: {@code section → (parent) -> власний екран}. */
    private static final Map<String, java.util.function.Function<Screen, Screen>> OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();
    /** Чи показувати екран підсумків бібліотеки автоматично (режим може відкривати сам, після своєї сцени). */
    public static volatile boolean autoShowResult = true;
    /** Відкладений підсумок, якщо {@link #autoShowResult} вимкнено. */
    private static MatchResult pendingResult;

    private record Toast(String reason, List<Reward> rewards, long start) {}

    private static final Deque<Toast> TOASTS = new ArrayDeque<>();

    private MetaScreens() {}

    public static void override(String section, java.util.function.Function<Screen, Screen> factory) {
        if (factory == null) OVERRIDES.remove(section); else OVERRIDES.put(section, factory);
    }

    public static void open(String section, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        var custom = OVERRIDES.get(section);
        if (custom != null) { mc.setScreen(custom.apply(parent)); return; }
        Screen s = switch (section) {
            case "wardrobe" -> new WardrobeScreen(parent);
            case "shop" -> new ShopScreen(parent, false);
            case "craft" -> new ShopScreen(parent, true);
            case "track" -> new TrackScreen(parent, false);
            case "upgrades" -> new TrackScreen(parent, true);
            case "leaderboard" -> new LeaderboardScreen(parent);
            default -> new ProfileScreen(parent, null, null);
        };
        mc.setScreen(s);
    }

    public static void openProfile(Screen parent, UUID target, String name) {
        Minecraft.getInstance().setScreen(new ProfileScreen(parent, target, name));
    }

    /** Показати відкладений підсумок (коли режим закінчив свою сцену перемоги). */
    public static void showPendingResult() {
        MatchResult r = pendingResult;
        pendingResult = null;
        if (r != null) Minecraft.getInstance().setScreen(new ResultScreen(r, Minecraft.getInstance().screen));
    }

    public static boolean hasPendingResult() { return pendingResult != null; }

    // ══ MetaClient.Ui ══════════════════════════════════════════════════

    @Override
    public void showResult(MatchResult result) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (autoShowResult) mc.setScreen(new ResultScreen(result, null));
            else pendingResult = result;
        });
    }

    @Override
    public void open(String screen) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> open(screen, mc.screen instanceof MetaScreen ms ? null : mc.screen));
    }

    @Override
    public void notice(String text, String kind) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof MetaScreen s) s.serverNotice(text, kind);
            else if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true);
        });
    }

    @Override
    public void granted(String reason, List<Reward> rewards, int trackLevel) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            TOASTS.addLast(new Toast(reason, List.copyOf(rewards), 0));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f, .5f));
        });
    }

    @Override
    public void profileChanged(MetaProfile p, boolean self) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.screen instanceof MetaScreen s) s.profileChanged(); });
    }

    // ══ спливаючі «Отримано» ═══════════════════════════════════════════

    private static long toastStart;

    /** Малюється і в HUD, і поверх екранів бібліотеки. */
    public static void renderToasts(GuiGraphics g, int sw, int sh) {
        Toast t = TOASTS.peekFirst();
        if (t == null) return;
        long now = System.currentTimeMillis();
        if (toastStart == 0) toastStart = now;
        float age = (now - toastStart) / 1000f;
        float dur = 3.2f;
        if (age > dur) { TOASTS.pollFirst(); toastStart = 0; return; }
        MetaMode mode = MetaClient.mode();
        if (mode == null) return;
        Font f = Minecraft.getInstance().font;
        float in = MetaUi.outBack(Math.min(1f, age / .45f)), out = age > dur - .35f ? (dur - age) / .35f : 1f;
        int w = 190, h = 26 + 18 * Math.min(4, t.rewards().size());
        // компактніше на великих GUI-масштабах: картка не має перекривати пів екрана
        float sc = .8f;
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1f);
        sw = Math.round(sw / sc);
        sh = Math.round(sh / sc);
        // знизу праворуч: не перекриває навігацію, гаманець і компас угорі
        int x = sw - w - 12 + Math.round((1f - in) * (w + 20)), y = sh - h - 48;
        float a = MetaUi.clamp01(out);
        var skin = dev.shaurmalib.meta.mc.skin.MetaSkins.of(mode);
        skin.toast(g, x, y, w, h, a, age);
        MetaUi.text(g, f, MetaUi.fit(f, "ОТРИМАНО · " + t.reason().toUpperCase(), w - 20), x + 10, y + 8, MetaUi.a(skin.theme().gold(), a), false);
        int ry = y + 24;
        int i = 0;
        for (Reward r : t.rewards()) {
            if (i++ >= 4) break;
            float ri = MetaUi.outBack(MetaUi.clamp01((age - .25f - i * .12f) / .35f));
            if (ri <= 0) continue;
            int rx = x + 10 + Math.round((1f - ri) * 30);
            int color = rewardColor(mode, r);
            MetaIcons.draw(g, rewardIcon(mode, r), rx + 3, ry + 3, 1, MetaUi.a(color, a));
            MetaUi.text(g, f, MetaUi.fit(f, rewardText(mode, r), w - 40), rx + 20, ry + 3, MetaUi.a(0xFFFFFF, a * ri), false);
            ry += 18;
        }
        g.pose().popPose();
    }

    public static String rewardText(MetaMode mode, Reward r) {
        return switch (r.type()) {
            case CURRENCY -> {
                Currency c = mode.currency(r.id());
                yield "+" + MetaUi.num(r.amount()) + " " + (c == null ? r.id() : c.name());
            }
            case COSMETIC -> {
                CosmeticDef d = mode.cosmetics.get(r.id());
                yield d == null ? r.id() : d.name() + " · " + d.rarity().title;
            }
            case MATERIAL -> {
                MaterialDef m = mode.materials.get(r.id());
                yield "+" + r.amount() + " " + (m == null ? r.id() : m.name());
            }
            case UPGRADE_POINTS -> "+" + r.amount() + " очк. покращень";
            case CHEST -> {
                var c = mode.chest(r.id());
                yield (c == null ? r.id() : c.name()) + (r.amount() > 1 ? " ×" + r.amount() : "");
            }
        };
    }

    public static String rewardIcon(MetaMode mode, Reward r) {
        return switch (r.type()) {
            case CURRENCY -> { Currency c = mode.currency(r.id()); yield c == null ? "coin" : c.icon(); }
            case COSMETIC -> {
                CosmeticDef d = mode.cosmetics.get(r.id());
                yield d == null ? "box" : switch (d.slot()) { case HEAD -> "head"; case BODY -> "body"; case LEGS -> "legs"; case BACK -> "back"; case PET -> "pet"; case AURA -> "aura"; };
            }
            case MATERIAL -> { MaterialDef m = mode.materials.get(r.id()); yield m == null ? "box" : m.icon(); }
            case UPGRADE_POINTS -> "upgrade";
            case CHEST -> "box";
        };
    }

    public static int rewardColor(MetaMode mode, Reward r) {
        return switch (r.type()) {
            case CURRENCY -> { Currency c = mode.currency(r.id()); yield c == null ? mode.theme.gold() : c.color(); }
            case COSMETIC -> { CosmeticDef d = mode.cosmetics.get(r.id()); yield d == null ? 0xFFFFFF : d.rarity().color; }
            case MATERIAL -> { MaterialDef m = mode.materials.get(r.id()); yield m == null ? 0xFFFFFF : m.rarity().color; }
            case UPGRADE_POINTS -> mode.theme.accent();
            case CHEST -> { var c = mode.chest(r.id()); yield c == null ? 0xFFFFFF : c.rarity().color; }
        };
    }
}
