package dev.shaurmalib.common.history;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Один запис гравця в {@link MatchRecord} (план, п. 3.20) — узагальнення
 * {@code MatchRecord.PlayerEntry}/{@code MatchRecord.BRPlacement}
 * snipers_shaurma.
 * <p>
 * Оригінал мав жорсткі поля під КОЖЕН режим одразу в одному класі
 * ({@code capturesDone}/{@code contribPts} для SCN, {@code roundWins}
 * для SD, {@code place}/{@code survivedMs} для BR/SR, {@code weaponKills}
 * прив'язаний до TACZ-зброї) — і 6 backward-compat конструкторів, що
 * підставляли нулі/порожні мапи під поля, не потрібні конкретному
 * виклику. Тут лишається фіксованим лише те, що дійсно спільне для
 * БУДЬ-ЯКОГО режиму гри (хто грав, з ким, скільки вбивств/смертей/
 * асистів), решта — {@link #extra()}: режим сам вирішує, які додаткові
 * ключі писати (наприклад {@code "captures_done"}, {@code "round_wins"},
 * {@code "place"}, {@code "survived_ms"}, {@code "weapon_kills"} —
 * ключі й типи значень режим документує сам, рушій лише серіалізує
 * мапу як є).
 * <p>
 * Значення {@link #extra()} обмежені типами, які {@code MatchRecordCodec}
 * (lib-forge) уміє писати в NBT напряму: {@link String}, {@link Number}
 * (int/long/float/double), {@link Boolean}, і {@code Map<String,Integer>}
 * (для лічильників на кшталт weaponKills/killsAgainst/deathsFrom) — тип,
 * що не підпадає під жоден з них, буде збережений через
 * {@code String.valueOf(...)} при серіалізації, з попередженням у лог.
 */
public final class MatchPlayerEntry {

    private final String uuid;
    private final String name;
    private final String teamId;
    private final int kills;
    private final int deaths;
    private final int assists;
    private final Map<String, Object> extra;

    public MatchPlayerEntry(String uuid, String name, String teamId,
                             int kills, int deaths, int assists, Map<String, Object> extra) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name == null ? "" : name;
        this.teamId = teamId == null ? "" : teamId;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.extra = extra == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    public static Builder builder(String uuid, String name) {
        return new Builder(uuid, name);
    }

    public String uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public String teamId() {
        return teamId;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int assists() {
        return assists;
    }

    /** Режим-специфічні дані (kitId, weaponKills, capturesDone, place, survivedMs, ...). */
    public Map<String, Object> extra() {
        return extra;
    }

    public Object extra(String key) {
        return extra.get(key);
    }

    public int extraInt(String key, int def) {
        Object v = extra.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public long extraLong(String key, long def) {
        Object v = extra.get(key);
        return v instanceof Number n ? n.longValue() : def;
    }

    public double extraDouble(String key, double def) {
        Object v = extra.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public String extraString(String key, String def) {
        Object v = extra.get(key);
        return v instanceof String s ? s : def;
    }

    public static final class Builder {
        private final String uuid;
        private final String name;
        private String teamId = "";
        private int kills, deaths, assists;
        private final Map<String, Object> extra = new LinkedHashMap<>();

        private Builder(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public Builder teamId(String teamId) {
            this.teamId = teamId;
            return this;
        }

        public Builder kills(int kills) {
            this.kills = kills;
            return this;
        }

        public Builder deaths(int deaths) {
            this.deaths = deaths;
            return this;
        }

        public Builder assists(int assists) {
            this.assists = assists;
            return this;
        }

        public Builder extra(String key, Object value) {
            extra.put(key, value);
            return this;
        }

        public MatchPlayerEntry build() {
            return new MatchPlayerEntry(uuid, name, teamId, kills, deaths, assists, extra);
        }
    }
}
