package dev.shaurmalib.common.animation;

/**
 * Узагальнена сесія запису keyframe-анімації (план, п. 3.16) — стан і
 * тік-логіка {@code AnimRecordManager}/{@code SCNAnimRecordManager}/
 * {@code SDAnimRecordManager} зведені в один клас. Чиста логіка (0
 * Minecraft/Forge-імпортів): кожен тік консюмер (forge-шар бібліотеки)
 * передає вже готову позицію/кут гравця ззовні через {@link #tickCountdown()}
 * / {@link #tickRecordAbsolute} / {@link #tickRecordDelta} — сесія сама не
 * знає про {@code ServerPlayer}.
 * <p>
 * Підтримує обидва формати запису з оригіналу:
 * <ul>
 *   <li><b>Абсолютний</b> (SC/SCN: {@link RecordedPathFrame}) — повна
 *       позиція + кут під одну незмінну точку;</li>
 *   <li><b>Дельта</b> (SD: {@link DeltaAngleFrame}) — лише зміщення кута
 *       відносно базового yaw/pitch, застосовується до довільної точки.</li>
 * </ul>
 * Один екземпляр сесії пише лише в один із двох накопичувачів залежно від
 * того, які {@code tickRecordXxx}-методи консюмер викликає — обидва
 * доступні одночасно просто як порожні контейнери, якщо не
 * використовуються.
 */
public final class RecordSession {

    private final int countdownTicks;
    private final int recordTicks;

    private AnimRecordPhase phase;
    private int countdownTick = 0;

    private final RecordedPath absolutePath = new RecordedPath();
    private final RecordedDeltaPath deltaPath = new RecordedDeltaPath();

    /**
     * @param countdownTicks тривалість відліку в тіках (0 — почати запис
     *                       одразу, без фази COUNTDOWN, як в оригінальному
     *                       {@code AnimRecordManager} для SC).
     * @param recordTicks    тривалість самого запису в тіках.
     */
    public RecordSession(int countdownTicks, int recordTicks) {
        this.countdownTicks = countdownTicks;
        this.recordTicks = recordTicks;
        this.phase = countdownTicks <= 0 ? AnimRecordPhase.RECORDING : AnimRecordPhase.COUNTDOWN;
    }

    public AnimRecordPhase phase() {
        return phase;
    }

    public int countdownTicks() {
        return countdownTicks;
    }

    public int recordTicks() {
        return recordTicks;
    }

    public RecordedPath absolutePath() {
        return absolutePath;
    }

    public RecordedDeltaPath deltaPath() {
        return deltaPath;
    }

    /**
     * Просуває фазу COUNTDOWN на один тік. Повертає секунду відліку для
     * показу (3, 2, 1), або {@code 0}, якщо цей тік не є секундною межею
     * (той самий розрахунок {@code countdownTick % 20 == 0}, що в оригіналі).
     * Викликати лише коли {@link #phase()} == {@code COUNTDOWN}.
     *
     * @return секунда відліку до показу (3..1), або 0 якщо нічого показувати.
     */
    public int tickCountdown() {
        countdownTick++;
        int secondPassed = countdownTick / 20;
        int tickInSecond = countdownTick % 20;

        int display = 0;
        if (tickInSecond == 0 && secondPassed >= 1 && secondPassed <= (countdownTicks / 20)) {
            display = (countdownTicks / 20) - secondPassed + 1;
        }

        if (countdownTick >= countdownTicks) {
            phase = AnimRecordPhase.RECORDING;
        }
        return display;
    }

    /**
     * Чи щойно (у щойно виконаному {@link #tickCountdown()}) сталася зміна
     * фази на RECORDING — консюмер перевіряє це одразу після виклику
     * {@code tickCountdown()}, щоб зіграти стартовий звук/повідомлення
     * рівно один раз.
     */
    public boolean justStartedRecording() {
        return phase == AnimRecordPhase.RECORDING && countdownTick == countdownTicks;
    }

    /**
     * Записує один абсолютний кадр (SC/SCN-формат) і повертає {@code true},
     * якщо це був останній кадр запису (досягнуто {@link #recordTicks}).
     * Викликати лише коли {@link #phase()} == {@code RECORDING}.
     */
    public boolean tickRecordAbsolute(double x, double y, double z, float yaw, float pitch) {
        absolutePath.addFrame(x, y, z, yaw, pitch);
        return absolutePath.size() >= recordTicks;
    }

    /**
     * Записує один дельта-кадр (SD-формат) і повертає {@code true}, якщо
     * це був останній кадр запису. Викликати лише коли {@link #phase()}
     * == {@code RECORDING}.
     */
    public boolean tickRecordDelta(float yawDelta, float pitchDelta) {
        deltaPath.addFrame(yawDelta, pitchDelta);
        return deltaPath.size() >= recordTicks;
    }

    /** {@code true} кожні 20 записаних кадрів (1 секунда) — той самий
     *  розрахунок {@code recorded % 20 == 0}, що в оригіналі, для
     *  проміжних повідомлень прогресу. */
    public boolean isProgressReportTick(int recordedSoFar) {
        return recordedSoFar > 0 && recordedSoFar % 20 == 0;
    }

    public double recordDurationSeconds() {
        return recordTicks / 20.0;
    }
}
