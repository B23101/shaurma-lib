package dev.shaurmalib.common.history;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MatchRecord (план, п. 3.20) — узагальнення {@code MatchRecord}
 * snipers_shaurma (244 рядки, 4 режим-специфічні набори полів + 11
 * backward-compat конструкторів для {@code PlayerEntry} самого по собі).
 * <p>
 * Лишається фіксованим лише те, що спільне для БУДЬ-ЯКОГО режиму
 * будь-якого мода-споживача: унікальний id, до якого namespace/режиму
 * належить запис ({@link #modeId()}), команда чи ні, часові рамки,
 * переможець (опційно — нічия/без переможця теж валідний стан), і
 * список гравців ({@link MatchPlayerEntry}, кожен зі своєю
 * {@code extra()} мапою). {@link #extra()} на рівні самого матчу — для
 * даних, що стосуються матчу в цілому, а не конкретного гравця
 * (наприклад {@code "br_placements"} як список, якщо режим хоче окремо
 * від {@code players} зберігати порядок вибуття в BR-стилі режимі —
 * бібліотека не нав'язує окрему {@code BRPlacement}-структуру, як
 * оригінал, бо це вже режим-специфічна форма даних).
 * <p>
 * {@code isBR}/{@code winnerTeamId} з оригіналу свідомо не перенесені
 * як окремі поля класу — {@code isBR} було лише прапорцем "чи це той
 * самий режим, що й {@code modeId == "sr"}" (сам {@link #modeId()} вже
 * несе цю інформацію без дублювання), а командний переможець — це
 * {@code extra("winner_team_id")} для режимів, яким командний переможець
 * взагалі потрібен.
 */
public final class MatchRecord {

    private final String id;
    private final String modeId;
    private final boolean teamMode;
    private final long startTime;
    private final long endTime;
    private final String winnerUuid;
    private final String winnerName;
    private final List<MatchPlayerEntry> players;
    private final Map<String, Object> extra;

    public MatchRecord(String id, String modeId, boolean teamMode, long startTime, long endTime,
                        String winnerUuid, String winnerName,
                        List<MatchPlayerEntry> players, Map<String, Object> extra) {
        this.id = id == null ? "" : id;
        this.modeId = modeId == null ? "" : modeId;
        this.teamMode = teamMode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.winnerUuid = winnerUuid;
        this.winnerName = winnerName;
        this.players = players == null ? List.of() : List.copyOf(players);
        this.extra = extra == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    public static Builder builder(String id, String modeId) {
        return new Builder(id, modeId);
    }

    public String id() {
        return id;
    }

    public String modeId() {
        return modeId;
    }

    public boolean teamMode() {
        return teamMode;
    }

    public long startTime() {
        return startTime;
    }

    public long endTime() {
        return endTime;
    }

    public long durationMs() {
        return endTime - startTime;
    }

    /** {@code null}, якщо матч завершився без переможця (нічия/скасовано). */
    public String winnerUuid() {
        return winnerUuid;
    }

    public String winnerName() {
        return winnerName;
    }

    public List<MatchPlayerEntry> players() {
        return players;
    }

    /** Дані рівня матчу, не прив'язані до конкретного гравця. */
    public Map<String, Object> extra() {
        return extra;
    }

    public static final class Builder {
        private final String id;
        private final String modeId;
        private boolean teamMode;
        private long startTime;
        private long endTime;
        private String winnerUuid;
        private String winnerName;
        private final java.util.List<MatchPlayerEntry> players = new java.util.ArrayList<>();
        private final Map<String, Object> extra = new LinkedHashMap<>();

        private Builder(String id, String modeId) {
            this.id = id;
            this.modeId = modeId;
        }

        public Builder teamMode(boolean teamMode) {
            this.teamMode = teamMode;
            return this;
        }

        public Builder timeRange(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
            return this;
        }

        public Builder winner(String uuid, String name) {
            this.winnerUuid = uuid;
            this.winnerName = name;
            return this;
        }

        public Builder player(MatchPlayerEntry entry) {
            players.add(entry);
            return this;
        }

        public Builder players(List<MatchPlayerEntry> entries) {
            players.addAll(entries);
            return this;
        }

        public Builder extra(String key, Object value) {
            extra.put(key, value);
            return this;
        }

        public Builder extraAll(Map<String, Object> values) {
            if (values != null) extra.putAll(values);
            return this;
        }

        public MatchRecord build() {
            return new MatchRecord(id, modeId, teamMode, startTime, endTime,
                    winnerUuid, winnerName, players, extra);
        }
    }
}
