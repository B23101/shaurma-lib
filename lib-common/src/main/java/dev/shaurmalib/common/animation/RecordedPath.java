package dev.shaurmalib.common.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Записаний шлях руху — узагальнення {@code RecordedPath} snipers_shaurma
 * (план, п. 3.16). Чиста структура даних (0 Minecraft/Forge-імпортів),
 * серіалізація в/з JSON лишається завданням консюмера чи forge-шару
 * бібліотеки (Gson доступний транзитивно через Minecraft classpath, так
 * само, як в оригіналі) — сам клас лише тримає список фреймів і базові
 * операції над ним, щоб зберегти сумісність зі старим форматом файлу
 * ({@code {"frames":[{"x":...,"y":...,"z":...,"yaw":...,"pitch":...}]}} —
 * той самий JSON, який уже пишуть/читають старі персонажі snipers, у т.ч.
 * старі файли з зайвим полем {@code bodyYaw}, яке нова версія просто
 * ігнорує при читанні).
 */
public final class RecordedPath {

    private final List<RecordedPathFrame> frames;

    public RecordedPath() {
        this.frames = new ArrayList<>();
    }

    public RecordedPath(List<RecordedPathFrame> frames) {
        this.frames = new ArrayList<>(frames);
    }

    public void addFrame(double x, double y, double z, float yaw, float pitch) {
        frames.add(new RecordedPathFrame(x, y, z, yaw, pitch));
    }

    public List<RecordedPathFrame> frames() {
        return Collections.unmodifiableList(frames);
    }

    public int size() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    /** Тривалість запису в секундах при 20 тіках/сек (той самий розрахунок,
     *  що і в оригінальних AnimRecordManager/SCNAnimRecordManager). */
    public double durationSeconds() {
        return frames.size() / 20.0;
    }
}
