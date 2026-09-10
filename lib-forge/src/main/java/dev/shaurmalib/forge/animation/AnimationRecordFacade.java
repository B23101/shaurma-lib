package dev.shaurmalib.forge.animation;

import dev.shaurmalib.common.animation.RecordSession;
import dev.shaurmalib.common.config.ShaurmaConfigTree;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Узагальнений рушій запису keyframe-анімацій (план, п. 3.16) — єдиний API
 * замість трьох майже паралельних менеджерів snipers_shaurma
 * ({@code AnimRecordManager} для SC, {@code SCNAnimRecordManager},
 * {@code SDAnimRecordManager}). Кожен консюмер реєструє одну
 * {@code record}-сесію через {@link #startAbsolute}/{@link #startDelta};
 * рушій сам відповідає за тік (лічильник, фаза COUNTDOWN→RECORDING,
 * прогрес-повідомлення кожну секунду, автозбереження при завершенні) —
 * режим більше не пише власний {@code onServerTick}-цикл під кожен новий
 * тип запису.
 * <p>
 * Два формати запису (обидва підтримуються тим самим фасадом, консюмер
 * обирає виклик залежно від типу):
 * <ul>
 *   <li>{@link #startAbsolute} — повна позиція+кут під одну незмінну
 *       точку (формат SC/SCN, {@code RecordedPath});</li>
 *   <li>{@link #startDelta} — лише дельта кута відносно базового
 *       yaw/pitch у момент старту, застосовується до довільної точки
 *       (формат SD, {@code RecordedDeltaPath}).</li>
 * </ul>
 * <p>
 * Callback-и (прогрес/countdown/старт/завершення) — консюмер сам вирішує,
 * що показати гравцю (переклад, звук, чат-повідомлення) — рушій лише
 * повідомляє про факт події, не знає нічого про {@code Component}/звуки
 * консюмера (той самий принцип інверсії залежності, що
 * {@code RadioDialogManager}/{@code GunDamageBridge}).
 */
public final class AnimationRecordFacade {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/animation");

    private AnimationRecordFacade() {}

    private static final Map<UUID, ActiveRecording> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Починає абсолютний (SC/SCN-формат) запис для гравця.
     *
     * @param player         гравець, що записує.
     * @param countdownTicks 0 — почати запис одразу (SC-поведінка); &gt;0 —
     *                       фаза COUNTDOWN спершу (SCN-поведінка).
     * @param recordTicks    тривалість запису в тіках.
     * @param saveTarget     куди й під яким іменем зберегти результат
     *                       після завершення/примусової зупинки.
     * @param callbacks      опційні реакції на події сесії; {@code null}-
     *                       поля callback-об'єкта просто пропускаються.
     * @return {@code false}, якщо для цього гравця вже є активна сесія
     *         (запис не почато).
     */
    public static boolean startAbsolute(ServerPlayer player, int countdownTicks, int recordTicks,
                                         SaveTarget saveTarget, RecordCallbacks callbacks) {
        UUID uuid = player.getUUID();
        if (ACTIVE.containsKey(uuid)) {
            return false;
        }
        RecordSession session = new RecordSession(countdownTicks, recordTicks);
        ACTIVE.put(uuid, new ActiveRecording(session, saveTarget, callbacks, Mode.ABSOLUTE, 0f, 0f));
        return true;
    }

    /**
     * Починає дельта (SD-формат) запис для гравця. Базовий yaw/pitch
     * фіксується в момент виклику (не в момент старту фази RECORDING —
     * той самий момент, що в оригінальному {@code SDAnimRecordManager}).
     */
    public static boolean startDelta(ServerPlayer player, int countdownTicks, int recordTicks,
                                      float baseYaw, float basePitch,
                                      SaveTarget saveTarget, RecordCallbacks callbacks) {
        UUID uuid = player.getUUID();
        if (ACTIVE.containsKey(uuid)) {
            return false;
        }
        RecordSession session = new RecordSession(countdownTicks, recordTicks);
        ACTIVE.put(uuid, new ActiveRecording(session, saveTarget, callbacks, Mode.DELTA, baseYaw, basePitch));
        return true;
    }

    /**
     * Примусово зупиняє запис і зберігає вже накопичені кадри (якщо фаза
     * вже була RECORDING і кадри є) — та сама поведінка "stop = зберегти
     * часткове", що в оригіналі.
     *
     * @return {@code true}, якщо запис було зупинено й збережено;
     *         {@code false}, якщо активного запису не було, або фаза була
     *         ще COUNTDOWN/без кадрів (скасовано без збереження).
     */
    public static boolean stop(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ActiveRecording rec = ACTIVE.remove(uuid);
        if (rec == null) {
            return false;
        }
        boolean hasFrames = rec.mode == Mode.ABSOLUTE
                ? !rec.session.absolutePath().isEmpty()
                : !rec.session.deltaPath().isEmpty();
        if (hasFrames) {
            save(rec);
            return true;
        }
        return false;
    }

    public static boolean isRecording(UUID uuid) {
        return ACTIVE.containsKey(uuid);
    }

    /**
     * Викликається консюмером щотік з {@code ServerTickEvent}-хендлера
     * (заміна трьох окремих {@code onServerTick(...)}) — один виклик
     * покриває всі активні сесії, незалежно від формату (абсолютний/дельта).
     */
    public static void onServerTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, ActiveRecording> entry : ACTIVE.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ActiveRecording rec = entry.getValue();

            if (player == null) {
                // Гравець вийшов посеред запису — зберігаємо те, що встигли
                // накопичити (як у автоматичному "player == null" гілку
                // оригінальних менеджерів).
                finished.add(entry.getKey());
                boolean hasFrames = rec.mode == Mode.ABSOLUTE
                        ? !rec.session.absolutePath().isEmpty()
                        : !rec.session.deltaPath().isEmpty();
                if (hasFrames) save(rec);
                continue;
            }

            if (rec.session.phase() == dev.shaurmalib.common.animation.AnimRecordPhase.COUNTDOWN) {
                int display = rec.session.tickCountdown();
                if (display > 0 && rec.callbacks != null && rec.callbacks.onCountdownTick != null) {
                    rec.callbacks.onCountdownTick.accept(player, display);
                }
                if (rec.session.justStartedRecording() && rec.callbacks != null
                        && rec.callbacks.onRecordingStarted != null) {
                    rec.callbacks.onRecordingStarted.accept(player);
                }
                continue;
            }

            boolean done = rec.mode == Mode.ABSOLUTE
                    ? rec.session.tickRecordAbsolute(player.getX(), player.getY(), player.getZ(),
                            player.getYRot(), player.getXRot())
                    : rec.session.tickRecordDelta(
                            dev.shaurmalib.common.animation.DeltaAngleFrame.normalizeYaw(player.getYRot() - rec.baseYaw),
                            player.getXRot() - rec.basePitch);

            int recorded = rec.mode == Mode.ABSOLUTE ? rec.session.absolutePath().size() : rec.session.deltaPath().size();
            if (rec.session.isProgressReportTick(recorded) && rec.callbacks != null
                    && rec.callbacks.onProgress != null) {
                rec.callbacks.onProgress.accept(player, recorded);
            }

            if (done) {
                finished.add(entry.getKey());
                save(rec);
                if (rec.callbacks != null && rec.callbacks.onFinished != null) {
                    rec.callbacks.onFinished.accept(player, recorded);
                }
            }
        }
        finished.forEach(ACTIVE::remove);
    }

    private static void save(ActiveRecording rec) {
        Path dir = rec.saveTarget.configTree.modeDir(rec.saveTarget.subFolder);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[AnimationRecordFacade] Не вдалося створити директорію {}", dir, e);
            return;
        }
        Path filePath = dir.resolve(rec.saveTarget.fileName);
        try {
            String json = rec.mode == Mode.ABSOLUTE
                    ? RecordedPathJson.toJson(rec.session.absolutePath())
                    : RecordedPathJson.toJson(rec.session.deltaPath());
            Files.writeString(filePath, json);
            LOGGER.info("[AnimationRecordFacade] Збережено {} кадрів у {}",
                    rec.mode == Mode.ABSOLUTE ? rec.session.absolutePath().size() : rec.session.deltaPath().size(),
                    filePath);
        } catch (IOException e) {
            LOGGER.error("[AnimationRecordFacade] Помилка збереження {}", filePath, e);
        }
    }

    private enum Mode { ABSOLUTE, DELTA }

    private static final class ActiveRecording {
        final RecordSession session;
        final SaveTarget saveTarget;
        final RecordCallbacks callbacks;
        final Mode mode;
        final float baseYaw;
        final float basePitch;

        ActiveRecording(RecordSession session, SaveTarget saveTarget, RecordCallbacks callbacks,
                         Mode mode, float baseYaw, float basePitch) {
            this.session = session;
            this.saveTarget = saveTarget;
            this.callbacks = callbacks;
            this.mode = mode;
            this.baseYaw = baseYaw;
            this.basePitch = basePitch;
        }
    }

    /**
     * Куди зберегти результат запису — заміна захардкодженого шляху
     * кожного оригінального менеджера ({@code airplane/<file>},
     * {@code snipers_control/animations/<teamId>_slot<slot>_lookaround.json},
     * {@code sniper_duels/animations/sd_lookaround_base.json}) єдиним
     * описом на основі вже наявного {@link ShaurmaConfigTree} (план, п. 3.1)
     * — консюмер сам вирішує modeFolder/підпапку/ім'я файлу для кожного
     * конкретного виклику.
     *
     * @param configTree дерево конфігів namespace консюмера.
     * @param subFolder  тека відносно {@code namespaceDir} (типово
     *                   {@code "<modeFolder>/animations"}).
     * @param fileName   ім'я файлу (наприклад {@code "red_slot1_lookaround.json"}
     *                   або {@code "sd_lookaround_base.json"}).
     */
    public record SaveTarget(ShaurmaConfigTree configTree, String subFolder, String fileName) {
    }

    /**
     * Опційні реакції на події сесії — усі поля можуть бути {@code null},
     * якщо консюмеру не потрібна відповідна подія (наприклад SC-запис без
     * countdown просто не заповнює {@link #onCountdownTick}).
     */
    public static final class RecordCallbacks {
        /** (гравець, секунда відліку 3..1). */
        public BiConsumer<ServerPlayer, Integer> onCountdownTick;
        /** Викликається рівно один раз, коли фаза перемкнулась у RECORDING. */
        public Consumer<ServerPlayer> onRecordingStarted;
        /** (гравець, кількість вже записаних кадрів) — раз на секунду. */
        public BiConsumer<ServerPlayer, Integer> onProgress;
        /** (гравець, фінальна кількість кадрів) — раз при завершенні. */
        public BiConsumer<ServerPlayer, Integer> onFinished;

        public RecordCallbacks() {}
    }
}
