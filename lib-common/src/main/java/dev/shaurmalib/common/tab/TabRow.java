package dev.shaurmalib.common.tab;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Один рядок гравця в Tab-таблиці (план, п. 3.3 + 3.7) — узагальнення
 * запису циклу {@code for (var entry : sorted) { ... }} з кожного з 6
 * (SC solo/team, BR solo/team, SCN, SD solo/team) майже паралельних
 * render-методів {@code CustomTabOverlay} snipers_shaurma. Режим сам
 * вирішує сортування/фільтрацію/які саме дані показувати в
 * {@link #cells} (kit/money/kills/deaths для SC — зовсім інший набір
 * колонок для SCN/SD) — {@link TabListStyle} лише малює те, що отримав,
 * рядок за рядком, з готовим спільним визуальним "движком" (панель,
 * зебра-фон, respawn-пульсація, goal-пульсація, перекреслений спектатор,
 * анімація позиції/прозорості).
 */
public final class TabRow {

    /** Стабільний ключ рядка для анімації позиції/прозорості (типово {@code uuid.toString()}) — той самий "key" з оригінального {@code rowAnimY}/{@code moneyAnim}. */
    public final String animKey;
    public final UUID skinUuid;
    public final List<TabCell> cells;
    public final boolean isMe;
    public final boolean isSpectator;
    public final boolean isRespawning;
    /** {@code goalReached} у оригіналі (SC: {@code money >= contractGoal}) — рядок отримує золоту пульсацію замість звичайного фону. */
    public final boolean highlighted;
    /** Rank-текст лівіше голови (у оригіналі — номер місця "1", "2", ...); {@code null} — колонка рангу не рендериться. */
    public final String rankText;

    private TabRow(Builder b) {
        this.animKey = b.animKey;
        this.skinUuid = b.skinUuid;
        this.cells = Collections.unmodifiableList(b.cells);
        this.isMe = b.isMe;
        this.isSpectator = b.isSpectator;
        this.isRespawning = b.isRespawning;
        this.highlighted = b.highlighted;
        this.rankText = b.rankText;
    }

    public static Builder builder(String animKey) {
        return new Builder(animKey);
    }

    public static final class Builder {
        private final String animKey;
        private UUID skinUuid;
        private final java.util.List<TabCell> cells = new java.util.ArrayList<>();
        private boolean isMe;
        private boolean isSpectator;
        private boolean isRespawning;
        private boolean highlighted;
        private String rankText;

        private Builder(String animKey) {
            this.animKey = animKey;
        }

        public Builder skinUuid(UUID uuid) {
            this.skinUuid = uuid;
            return this;
        }

        public Builder cell(TabCell cell) {
            this.cells.add(cell);
            return this;
        }

        public Builder isMe(boolean v) {
            this.isMe = v;
            return this;
        }

        public Builder isSpectator(boolean v) {
            this.isSpectator = v;
            return this;
        }

        public Builder isRespawning(boolean v) {
            this.isRespawning = v;
            return this;
        }

        public Builder highlighted(boolean v) {
            this.highlighted = v;
            return this;
        }

        public Builder rankText(String v) {
            this.rankText = v;
            return this;
        }

        public TabRow build() {
            return new TabRow(this);
        }
    }
}
