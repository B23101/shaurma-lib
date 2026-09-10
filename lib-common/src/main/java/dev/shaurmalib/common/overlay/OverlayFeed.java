package dev.shaurmalib.common.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Узагальнений стек сповіщень, що спливають одне під іншим (план, п. 3.4,
 * {@code AlertNotificationSystem}). Перенесення логіки з
 * {@code AlertNotificationOverlay}: живий список + повна історія раунду +
 * обчислення вертикального зсуву для запису N через накопичення
 * fade-прогресу всіх записів над ним (0..i-1), щоб фід плавно "стискався"
 * коли верхні записи зникають, а не миттєво стрибав.
 * <p>
 * У оригіналі кожен з {@code AlertNotificationOverlay}, {@code LootNotificationOverlay}
 * і {@code EventNotificationRouter} тримав власну копію статичного
 * {@code List<Entry>} + метод {@code computeYOffsets(...)} — тут це один
 * клас, інстанс якого мод створює на кожен окремий фід (правий верхній
 * кут для kill-feed, окремий для loot тощо), а не статичний стан.
 */
public final class OverlayFeed {

    private final List<TimedOverlayEntry> active = new ArrayList<>();
    private final List<TimedOverlayEntry> history = new ArrayList<>();

    public void push(TimedOverlayEntry entry) {
        active.add(entry);
        history.add(entry);
    }

    /**
     * Видаляє протухлі записи і повертає снапшот активних (для рендеру).
     * Викликається на кожному кадрі рендер-хука.
     */
    public List<TimedOverlayEntry> tick(long nowMs) {
        active.removeIf(e -> e.isExpired(nowMs));
        return new ArrayList<>(active);
    }

    /**
     * Вертикальний зсув запису з індексом {@code index} у поточному
     * активному списку, спричинений fade-стисканням записів над ним.
     * {@code heightOf} — функція обчислення висоти конкретного запису
     * (залежить від кількості рядків тексту й обраного шрифту, тому
     * лишається відповідальністю рендер-шару, а не цього класу).
     */
    public float verticalOffset(int index, long nowMs, int gapPx, ToIntFunction<TimedOverlayEntry> heightOf) {
        float shift = 0f;
        for (int j = 0; j < index && j < active.size(); j++) {
            TimedOverlayEntry above = active.get(j);
            float fadeProgress = above.fadeOutProgressAt(nowMs);
            if (fadeProgress > 0f) {
                shift -= (heightOf.applyAsInt(above) + gapPx) * fadeProgress;
            }
        }
        return shift;
    }

    /** Повна історія раунду (найстаріші першими) — для T-екрана/ChatHistoryScreen-подібного скролу. */
    public List<TimedOverlayEntry> historySnapshot() {
        return new ArrayList<>(history);
    }

    /** Скидає і активні, і історичні записи — викликати на старті нового матчу. */
    public void reset() {
        active.clear();
        history.clear();
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }
}
