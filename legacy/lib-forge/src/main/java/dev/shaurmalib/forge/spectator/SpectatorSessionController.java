package dev.shaurmalib.forge.spectator;

import dev.shaurmalib.common.spectator.SpectatorPolicy;
import dev.shaurmalib.common.spectator.TargetCycle;
import dev.shaurmalib.forge.teleport.TeleportReason;
import dev.shaurmalib.forge.teleport.TeleportService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Серверний контролер режиму "спостереження за живим гравцем" (вид B) —
 * камера глядача прив'язується до конкретної цілі через
 * {@code Entity.setCamera(...)}, БЕЗ overlay/respawn/hand-animation sync
 * (усе це лишається продуктовою специфікою консюмера, не бібліотеки).
 * <p>
 * <b>Важлива відмінність від ванільного {@code Spectator}-геймтайпу:</b> це
 * НЕ вільний політ — глядач лишається у своєму поточному геймтайпі, а
 * камера прив'язується до цілі, з можливістю ручного (
 * {@link #cycleNext}/{@link #cyclePrevious}) і АВТОМАТИЧНОГО (
 * {@link #tick}) перемикання, коли поточна ціль стає недоступною.
 * <p>
 * Позиція/поворот глядача на момент входу зберігаються і відновлюються при
 * виході через {@link TeleportService} з {@link TeleportReason#SPECTATOR_TOGGLE}.
 */
public final class SpectatorSessionController {

    private SpectatorSessionController() {}

    private static final Map<UUID, SavedState> SAVED = new ConcurrentHashMap<>();
    private static final Map<UUID, ActiveSession> SESSIONS = new ConcurrentHashMap<>();

    // ── Вхід/вихід ───────────────────────────────────────────────────────

    /**
     * Починає спостереження глядача за конкретною ціллю. Зберігає поточну
     * позицію/поворот/вимір глядача для {@link #stop}, потім прив'язує
     * камеру через {@code setCamera(target)}.
     * <p>
     * Реєструє (або оновлює) {@link ActiveSession} для {@code viewer} —
     * потрібно, щоб {@link #tick} міг надалі сам переселяти глядача, якщо
     * ця ціль стане недоступною. Якщо консюмеру автопереселення не
     * потрібне — просто ніколи не викликати {@link #tick} для цього
     * сервера, поведінка лишається ідентичною попередній версії.
     */
    public static void start(ServerPlayer viewer, ServerPlayer target) {
        UUID uuid = viewer.getUUID();
        if (!SAVED.containsKey(uuid)) {
            SAVED.put(uuid, new SavedState(
                    viewer.serverLevel(), viewer.getX(), viewer.getY(), viewer.getZ(),
                    viewer.getYRot(), viewer.getXRot()));
        }
        viewer.setCamera(target);

        ActiveSession session = SESSIONS.computeIfAbsent(uuid, u -> new ActiveSession());
        session.currentTargetUUID = target.getUUID();
        session.noTarget = false;
    }

    /**
     * Завершує спостереження — відв'язує камеру ({@code setCamera(viewer)})
     * і повертає глядача на збережену позицію через {@link TeleportService}.
     * Безпечно викликати навіть якщо збереженого стану немає. Прибирає
     * {@link ActiveSession}, тому подальші {@link #tick} цю сесію
     * ігноруватимуть.
     */
    public static void stop(ServerPlayer viewer) {
        viewer.setCamera(viewer);
        SESSIONS.remove(viewer.getUUID());
        SavedState saved = SAVED.remove(viewer.getUUID());
        if (saved != null) {
            TeleportService.teleport(viewer, saved.level, saved.x, saved.y, saved.z,
                    saved.yaw, saved.pitch, TeleportReason.SPECTATOR_TOGGLE);
        }
    }

    public static boolean isSpectating(UUID viewerId) {
        return SAVED.containsKey(viewerId);
    }

    /** {@code true}, якщо сесія активна, але наразі немає жодного придатного кандидата на ціль. */
    public static boolean hasNoTarget(UUID viewerId) {
        ActiveSession session = SESSIONS.get(viewerId);
        return session != null && session.noTarget;
    }

    /**
     * Починає спостереження, коли консюмер (головний мод) вказує ціль
     * за ніком, а не через готовий {@code ServerPlayer}-об'єкт.
     * <p>
     * Якщо {@code targetName} задано і такий гравець зараз онлайн і
     * живий — прив'язує камеру саме до нього. Якщо {@code targetName}
     * порожній/{@code null}, або вказаний гравець не знайдений/мертвий —
     * бібліотека сама шукає найближчого живого гравця до глядача зі
     * списку {@code candidates} (виключаючи самого глядача). Якщо навіть
     * такого немає — нічого не робить і повертає {@code null}.
     *
     * @param viewer     гравець, який починає спостерігати.
     * @param targetName нік бажаної цілі, або {@code null}/порожній
     *                    рядок — тоді вмикається пошук найближчого.
     * @param candidates список гравців, серед яких шукати "найближчого" і
     *                    (надалі, у {@link #tick}) "запасний" пул —
     *                    консюмер сам вирішує, що туди входить.
     * @return фактичну ціль, за якою почалось спостереження, або
     *         {@code null}, якщо жодного придатного гравця не знайдено.
     */
    public static ServerPlayer startByName(MinecraftServer server, ServerPlayer viewer,
                                            String targetName, List<ServerPlayer> candidates) {
        ServerPlayer resolved = null;

        if (targetName != null && !targetName.isBlank()) {
            ServerPlayer named = server.getPlayerList().getPlayerByName(targetName);
            if (named != null && !named.isDeadOrDying() && !named.equals(viewer)) {
                resolved = named;
            }
            // Якщо named не знайдений або мертвий — навмисно НЕ падаємо
            // в помилку: одразу переходимо до пошуку найближчого нижче,
            // так само як і при повністю не вказаному targetName.
        }

        if (resolved == null) {
            resolved = findNearest(viewer, candidates);
        }

        if (resolved != null) {
            start(viewer, resolved);
        }
        return resolved;
    }

    // ── Ручне перемикання (без змін) ─────────────────────────────────────

    /**
     * Перемикає на наступну доступну ціль зі списку {@code allCandidates},
     * відфільтрованого через {@code policy}, циклічно. Якщо жодна ціль не
     * доступна — нічого не робить і повертає {@code null}.
     */
    public static ServerPlayer cycleNext(ServerPlayer viewer, List<ServerPlayer> allCandidates,
                                          SpectatorPolicy<ServerPlayer> policy,
                                          Supplier<ServerPlayer> currentTargetSupplier) {
        return cycle(viewer, allCandidates, policy, currentTargetSupplier, true);
    }

    /** Симетричний до {@link #cycleNext}, у зворотному напрямку. */
    public static ServerPlayer cyclePrevious(ServerPlayer viewer, List<ServerPlayer> allCandidates,
                                              SpectatorPolicy<ServerPlayer> policy,
                                              Supplier<ServerPlayer> currentTargetSupplier) {
        return cycle(viewer, allCandidates, policy, currentTargetSupplier, false);
    }

    private static ServerPlayer cycle(ServerPlayer viewer, List<ServerPlayer> allCandidates,
                                       SpectatorPolicy<ServerPlayer> policy,
                                       Supplier<ServerPlayer> currentTargetSupplier, boolean forward) {
        List<ServerPlayer> allowed = allCandidates.stream()
                .filter(candidate -> policy.canSpectate(viewer, candidate))
                .toList();
        ServerPlayer current = currentTargetSupplier.get();
        ServerPlayer next = forward
                ? TargetCycle.next(allowed, current)
                : TargetCycle.previous(allowed, current);
        if (next != null) {
            start(viewer, next);
        }
        return next;
    }

    // ── Автоматичне переселення ──────────────────────────────────────────

    /**
     * Повідомляє контролер, хто вбив ціль — щоб {@link #tick} на
     * наступному виклику спробував переселити глядача саме на вбивцю
     * першим (killer-priority), перш ніж падати до allowedTargets/nearest.
     * Консюмер викликає це зі свого обробника смерті гравця, до того, як
     * {@link #tick} вперше побачить ціль мертвою.
     */
    public static void onTargetKilled(UUID targetUUID, UUID killerUUID) {
        for (ActiveSession session : SESSIONS.values()) {
            if (targetUUID.equals(session.currentTargetUUID)) {
                session.lastKillerUUID = killerUUID;
            }
        }
    }

    /**
     * Викликається консюмером щотік (напр. з {@code ServerTickEvent}) для
     * всіх активних сесій одразу. Для кожної сесії: якщо поточна ціль
     * мертва/офлайн — шукає нову за пріоритетом:
     * <ol>
     *   <li>вбивця попередньої цілі ({@link #onTargetKilled}), якщо живий
     *       і дозволений {@code policy};</li>
     *   <li>перший придатний елемент {@code allowedTargetsSupplier}
     *       (вужчий, пріоритетний пул — напр. тімейти);</li>
     *   <li>найближчий живий гравець з {@code allCandidatesSupplier}
     *       (фолбек "будь-хто").</li>
     * </ol>
     * Якщо жодного кандидата немає — сесія переходить у
     * {@link #hasNoTarget}, без винятків і без нескінченних спроб.
     * <p>
     * Не викликає жодних мережевих пакетів/звуків сама — якщо консюмеру
     * потрібно повідомити клієнта чи відтворити звук при автоперемиканні,
     * передайте {@code onRetarget} (може бути {@code null}).
     *
     * @param server                  сервер, для гравців якого виконується тік.
     * @param allCandidatesSupplier   постачає повний список кандидатів
     *                                 "фолбек" — консюмер сам вирішує, що
     *                                 вважати "усіма".
     * @param allowedTargetsSupplier  постачає вужчий, пріоритетний пул
     *                                 (можна передати {@code List::of},
     *                                 якщо такого пулу немає).
     * @param policy                  фільтр "чи viewer може дивитись на
     *                                 candidate" — застосовується до обох
     *                                 списків вище і до killer-кандидата.
     * @param onRetarget              опційний callback (viewer, newTarget),
     *                                 що викликається щоразу, коли сесія
     *                                 автоматично переселена на нову ціль.
     */
    public static void tick(MinecraftServer server,
                             Supplier<List<ServerPlayer>> allCandidatesSupplier,
                             Supplier<List<ServerPlayer>> allowedTargetsSupplier,
                             SpectatorPolicy<ServerPlayer> policy,
                             BiConsumer<ServerPlayer, ServerPlayer> onRetarget) {
        if (SESSIONS.isEmpty()) {
            return;
        }

        SESSIONS.forEach((viewerUUID, session) -> {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerUUID);
            if (viewer == null) {
                // Глядач офлайн — сесію прибере stop() при наступному
                // логіні/явному виклику; tick сам не видаляє записи, щоб
                // не губити збережену позицію передчасно.
                return;
            }

            ServerPlayer currentTarget = session.currentTargetUUID != null
                    ? server.getPlayerList().getPlayer(session.currentTargetUUID) : null;
            boolean targetInvalid = (currentTarget == null || currentTarget.isDeadOrDying());
            if (!targetInvalid) {
                return; // усе гаразд, нічого робити не треба
            }

            ServerPlayer next = findReplacement(server, viewer, session,
                    allCandidatesSupplier, allowedTargetsSupplier, policy);

            if (next != null) {
                session.currentTargetUUID = next.getUUID();
                session.noTarget = false;
                session.lastKillerUUID = null;
                viewer.setCamera(next);
                if (onRetarget != null) {
                    onRetarget.accept(viewer, next);
                }
            } else {
                session.currentTargetUUID = null;
                session.noTarget = true;
                // Камеру не знімаємо (viewer.setCamera(viewer)) — це
                // залишило б глядача "підвішеним" у точці останньої живої
                // цілі без явного stop(); консюмер сам викликає stop(),
                // якщо для нього "немає кого дивитись" означає вихід.
            }
        });
    }

    private static ServerPlayer findReplacement(MinecraftServer server, ServerPlayer viewer,
                                                 ActiveSession session,
                                                 Supplier<List<ServerPlayer>> allCandidatesSupplier,
                                                 Supplier<List<ServerPlayer>> allowedTargetsSupplier,
                                                 SpectatorPolicy<ServerPlayer> policy) {
        // 1. Killer-priority.
        if (session.lastKillerUUID != null) {
            ServerPlayer killer = server.getPlayerList().getPlayer(session.lastKillerUUID);
            if (killer != null && !killer.isDeadOrDying()
                    && !killer.equals(viewer) && policy.canSpectate(viewer, killer)) {
                return killer;
            }
        }

        // 2. Вужчий, пріоритетний пул (напр. тімейти).
        List<ServerPlayer> allowed = allowedTargetsSupplier.get();
        for (ServerPlayer candidate : allowed) {
            if (!candidate.equals(viewer) && !candidate.isDeadOrDying()
                    && policy.canSpectate(viewer, candidate)) {
                return candidate;
            }
        }

        // 3. Фолбек — найближчий живий з повного списку.
        List<ServerPlayer> all = allCandidatesSupplier.get().stream()
                .filter(candidate -> policy.canSpectate(viewer, candidate))
                .toList();
        return findNearest(viewer, all);
    }

    /** Найближчий (за прямою відстанню) живий гравець зі списку, крім самого {@code viewer}. */
    private static ServerPlayer findNearest(ServerPlayer viewer, List<ServerPlayer> candidates) {
        double bestDistSqr = Double.MAX_VALUE;
        ServerPlayer nearest = null;
        for (ServerPlayer candidate : candidates) {
            if (candidate.equals(viewer) || candidate.isDeadOrDying()) {
                continue;
            }
            double distSqr = candidate.distanceToSqr(viewer.position());
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private record SavedState(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
    }

    /** Тіковий стан однієї активної сесії спостереження (вид B). */
    private static final class ActiveSession {
        volatile UUID currentTargetUUID;
        volatile UUID lastKillerUUID;
        volatile boolean noTarget;
    }
}
