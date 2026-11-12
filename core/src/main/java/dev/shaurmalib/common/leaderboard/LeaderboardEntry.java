package dev.shaurmalib.common.leaderboard;

import java.util.UUID;

/**
 * Один рядок лідерборду (план, п. 3.21) — 1:1 перенесення
 * {@code core.statistics.leaderboard.LeaderboardEntry} snipers_shaurma.
 * <p>
 * {@code uuid} може бути {@code null} — оригінал так робив для рядків,
 * що не прив'язані до конкретного гравця (наприклад топ популярності
 * кітів: рядок "AWM — 214" не належить нікому персонально).
 * {@code value} — уже відформатований рядок (наприклад {@code "$1.2M"}
 * або {@code "214"}), бо формат відображення (валюта, скорочення K/M,
 * суфікс " pts") — це рішення консюмера, а не бібліотеки.
 */
public final class LeaderboardEntry {

    public final UUID uuid;
    public final String name;
    public final String value;

    public LeaderboardEntry(UUID uuid, String name, String value) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        this.value = value == null ? "" : value;
    }
}
