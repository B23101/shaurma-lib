package dev.shaurmalib.meta.client;

import dev.shaurmalib.meta.MetaMode;
import dev.shaurmalib.meta.MetaModes;
import dev.shaurmalib.meta.cosmetics.Look;
import dev.shaurmalib.meta.cosmetics.Wardrobe;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.fx.PlusBurst;
import dev.shaurmalib.meta.hud.Waypoint;
import dev.shaurmalib.meta.hud.WaypointCompass;
import dev.shaurmalib.meta.io.J;
import dev.shaurmalib.meta.match.MatchResult;
import dev.shaurmalib.meta.net.MetaMessage;
import dev.shaurmalib.meta.net.MetaMessage.Op;
import dev.shaurmalib.meta.net.MetaNetwork;
import dev.shaurmalib.meta.profile.MetaProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Клієнтський стан мета-гри (без Minecraft): власний профіль, чужі профілі, образи гравців для рендера,
 * лідерборди, останній підсумок матчу. Все, що треба показати, шар версії отримує через {@link Ui}.
 */
public final class MetaClient {

    /** Те, що вміє лише шар версії: відкривати екрани й показувати сповіщення. */
    public interface Ui {
        void showResult(MatchResult result);
        void open(String screen);
        void notice(String text, String kind);
        void granted(String reason, List<Reward> rewards, int trackLevel);
        /** Профіль оновився (екрани перемальовують дані). */
        default void profileChanged(MetaProfile p, boolean self) { }
        default void leaderboard(String key, List<Map<String, Object>> rows) { }
    }

    private static volatile Ui ui;
    private static MetaProfile self;
    private static UUID selfId;
    private static final Map<UUID, MetaProfile> others = new HashMap<>();
    private static final Map<UUID, Look> looks = new HashMap<>();
    private static final Map<String, List<Map<String, Object>>> boards = new HashMap<>();
    private static MatchResult lastResult;
    private static final java.util.LinkedList<Map<String, Object>> feed = new java.util.LinkedList<>();
    /** Остання відкрита скриня: id і рідкість, що випала (для анімації відкриття). */
    public static volatile String lastChest = "", lastChestRarity = "";

    private MetaClient() {}

    public static void setUi(Ui u) { ui = u; }

    public static MetaMode mode() { return MetaModes.client(); }

    public static MetaProfile self() { return self; }

    public static MetaProfile profile(UUID id) {
        if (id == null || id.equals(selfId)) return self;
        return others.get(id);
    }

    /** Образ гравця для рендера (порожній, якщо нічого не вдягнено). */
    public static Look look(UUID id) { return looks.get(id); }

    public static List<Map<String, Object>> board(String key) { return boards.get(key); }

    public static MatchResult lastResult() { return lastResult; }

    /** Стрічка останніх випадінь (новіші — першими). */
    public static List<Map<String, Object>> feed() { return new ArrayList<>(feed); }

    public static void reset() {
        self = null;
        selfId = null;
        others.clear();
        looks.clear();
        boards.clear();
        lastResult = null;
        feed.clear();
        WaypointCompass.clear();
        PlusBurst.clear();
    }

    // ── вхідні ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static void handle(MetaMessage m) {
        switch (m.op()) {
            case HELLO -> MetaModes.setClient(m.str("mode"));
            case PROFILE -> {
                Map<String, Object> pj = m.obj("profile");
                UUID id;
                try { id = UUID.fromString(J.str(pj, "id", "")); } catch (IllegalArgumentException e) { return; }
                MetaProfile p = MetaProfile.fromJson(id, pj);
                boolean me = J.bool(m.body(), "self", false);
                if (me) { self = p; selfId = id; } else others.put(id, p);
                Ui u = ui;
                if (u != null) u.profileChanged(p, me);
            }
            case LOOKS -> m.obj("players").forEach((k, v) -> {
                try { looks.put(UUID.fromString(k), Look.fromJson(J.asObj(v))); } catch (IllegalArgumentException ignored) { }
            });
            case LEADERBOARD -> {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object o : J.arr(m.body(), "rows")) rows.add(J.asObj(o));
                boards.put(m.str("key"), rows);
                Ui u = ui;
                if (u != null) u.leaderboard(m.str("key"), rows);
            }
            case WAYPOINTS -> {
                List<Waypoint> list = new ArrayList<>();
                for (Object o : J.arr(m.body(), "list")) list.add(Waypoint.fromJson(J.asObj(o)));
                WaypointCompass.replaceAll(list);
            }
            case FX_PLUS -> {
                Map<String, Object> b = m.body();
                PlusBurst.spawn(d(b, "x"), d(b, "y"), d(b, "z"), (int) m.num("n"), (int) m.num("c"), .85,
                        b.get("t") instanceof Number t ? t.floatValue() : (int) m.num("n") * .035f);
            }
            case RESULT -> {
                lastResult = MatchResult.fromJson(m.obj("result"));
                Ui u = ui;
                if (u != null) u.showResult(lastResult);
            }
            case OPEN -> { Ui u = ui; if (u != null) u.open(m.str("screen")); }
            case NOTICE -> { Ui u = ui; if (u != null) u.notice(m.str("text"), m.str("kind")); }
            case LOOT_FEED -> {
                if (m.body().get("add") != null) feed.addFirst(m.obj("add"));
                else { feed.clear(); for (Object o : J.arr(m.body(), "entries")) feed.add(J.asObj(o)); }
                while (feed.size() > 24) feed.removeLast();
            }
            case GRANTED -> {
                if (!m.str("chest").isEmpty()) { lastChest = m.str("chest"); lastChestRarity = m.str("rarity"); }
                List<Reward> list = new ArrayList<>();
                for (Object o : J.arr(m.body(), "rewards")) list.add(Reward.fromJson(J.asObj(o)));
                Ui u = ui;
                if (u != null) u.granted(m.str("reason"), list, (int) m.num("level"));
            }
            default -> { }
        }
    }

    private static double d(Map<String, Object> b, String k) {
        return b.get(k) instanceof Number n ? n.doubleValue() : 0;
    }

    // ── вихідні ──────────────────────────────────────────────────────────

    public static void requestProfile(UUID target) {
        send(MetaMessage.of(Op.REQ_PROFILE, "target", target == null ? "" : target.toString()));
    }

    public static void saveWardrobe(Wardrobe w) { send(MetaMessage.of(Op.SAVE_WARDROBE, "wardrobe", w.toJson())); }

    public static void buy(String offer) { send(MetaMessage.of(Op.BUY, "offer", offer)); }

    public static void craft(String recipe) { send(MetaMessage.of(Op.CRAFT, "recipe", recipe)); }

    public static void claim(int level) { send(MetaMessage.of(Op.CLAIM, "level", level)); }

    public static void upgrade(String id) { send(MetaMessage.of(Op.UPGRADE, "upgrade", id)); }

    public static void openChest(String chest) { send(MetaMessage.of(Op.OPEN_CHEST, "chest", chest)); }

    public static void daily() {
        if (Boolean.getBoolean("shaurma.meta.selftest")) new Throwable("[MetaSelfTest] daily() викликано").printStackTrace();
        send(MetaMessage.of(Op.DAILY));
    }

    public static void requestLeaderboard(String key) { send(MetaMessage.of(Op.REQ_LEADERBOARD, "key", key)); }

    public static void seen(List<String> items) {
        if (self != null) items.forEach(self.unseen::remove);
        send(MetaMessage.of(Op.SEEN, "items", new ArrayList<Object>(items)));
    }

    private static void send(MetaMessage m) { MetaNetwork.get().toServer(m); }
}
