package dev.shaurmalib.client.cinematic;

import java.util.ArrayList;
import java.util.List;

/**
 * Запис проходження одного {@link CineTimeline} — момент часу, коли
 * настав кожен крок. Це навмисно НЕ прив'язано до кінематики
 * старту матчу конкретно: будь-який мод, що хоче зробити "реплей
 * карти"/"реплей матчу", отримує готовий формат запису подій без
 * потреби писати власний сериалізатор.
 *
 * Дизайн навмисно мінімальний (timestamp + stepId), а не "запис
 * усього стану гри" — {@link CineTimeline} відповідає лише за
 * ПОСЛІДОВНІСТЬ немовних подій (overlay/звук/камера-тригер), а не
 * за фізичну симуляцію світу. Реплей позицій сутностей/блоків — це
 * окрема система (поза межами цього модуля); {@link CineRecording}
 * дає лише "коли що показати", яке той реплей-мод накладає зверху
 * на власний запис руху сутностей.
 *
 * Формат серіалізації: список рядків "timestampMs;stepId" — свідомо
 * найпростіший текстовий формат, легко читається/пишеться будь-яким
 * консьюмером (YAML/JSON-обгортку споживач додає сам через
 * {@link #entries()}, бібліотека не нав'язує конкретний файловий
 * формат, як і решта конфіг-шару бібліотеки з розділу config).
 */
public final class CineRecording {

    public record Entry(long timestampMs, String stepId) {}

    private final List<Entry> entries = new ArrayList<>();
    private long recordingStartMs = -1;

    void recordStepEnter(String stepId, long atMs) {
        if (recordingStartMs < 0) recordingStartMs = atMs;
        entries.add(new Entry(atMs - recordingStartMs, stepId));
    }

    /** Записані входи в кроки, з часом відносно початку запису (0 = перший крок). */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    /**
     * Створює {@link CineTimeline}, що відтворює цей запис на
     * заданому наборі кроків, ЗА ЗАПИСАНИМ ЧАСОМ кожного кроку, а не
     * за оригінальними {@code durationMs} з {@link CineStep} —
     * інакше це був би просто повторний живий прогін з тими самими
     * тривалостями, а не справжній реплей (де тривалості могли
     * відрізнятись через ping-compensation чи ручне втручання під час
     * запису). {@code steps} має містити ті самі id, з якими
     * записувалась оригінальна сесія (мод сам гарантує відповідність
     * — бібліотека не серіалізує самі {@link CineStep}, вони містять
     * java-лямбди onEnter-кʼю і не годяться для збереження на диск;
     * зберігається лише ПОСЛІДОВНІСТЬ+ЧАС).
     *
     * Кроки без відповідного запису в {@link #entries()} пропускаються
     * (мод міг додати новий крок у steps вже після того, як запис було
     * зроблено старою версією моду — реплей просто ігнорує невідомий
     * крок, а не падає).
     *
     * Використання:
     * <pre>{@code
     * CineRecording recording = CineRecording.load(savedEntries);
     * CineTimeline replay = recording.replay(sameStepsAsOriginal, new CineContext(modeId, true));
     * replay.begin();
     * // replay.tick() кожен клієнтський тік, як звичайний timeline —
     * // кожен крок триває рівно стільки, скільки записано, а не
     * // оригінальний durationMs.
     * }</pre>
     */
    public CineTimeline replay(List<CineStep> steps, CineContext replayContext) {
        if (!replayContext.isReplay()) {
            throw new IllegalArgumentException("replay() вимагає CineContext з isReplay()=true");
        }
        CineTimeline.Builder builder = CineTimeline.builder(replayContext);
        for (CineStep original : steps) {
            long recordedDurationMs = durationOf(original.id());
            builder.step(original.withDuration(recordedDurationMs));
        }
        return builder.build();
    }

    /** Тривалість кроку за записом: різниця між часом входу в нього і наступний (або 0 для останнього). */
    private long durationOf(String stepId) {
        Entry current = findEntry(stepId);
        if (current == null) return 0L; // крок відсутній у записі — пропускається миттєво
        int entryIndex = entries.indexOf(current);
        if (entryIndex + 1 < entries.size()) {
            return entries.get(entryIndex + 1).timestampMs() - current.timestampMs();
        }
        return 0L; // останній записаний крок — тривалість не мала значення для запису, тримати 0 і покластись на onDone
    }

    private Entry findEntry(String stepId) {
        for (Entry e : entries) {
            if (e.stepId().equals(stepId)) return e;
        }
        return null;
    }

    /** Відновлює запис із раніше збережених записів (напр. прочитаних із json/yml модом-споживачем). */
    public static CineRecording load(List<Entry> savedEntries) {
        CineRecording recording = new CineRecording();
        recording.recordingStartMs = 0;
        recording.entries.addAll(savedEntries);
        return recording;
    }
}
