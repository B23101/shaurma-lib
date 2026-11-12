package dev.shaurmalib.anim.emote;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверний облік активних емоцій: хто що грає і звідки почав. Шар версії щотіка дає позицію
 * гравця і результат хука дозволу — тут вирішується, чи гасити (рух, заборона).
 * Без гри: числа на вході, рішення на виході — тому покрито тестами ядра.
 */
public final class EmoteSessions {

    /** Зрушив горизонтально далі — «пішов». Менше за крок, більше за поштовх/ковзання. */
    public static final double MOVE_EPS = 0.2;
    /** Підскочив/упав по вертикалі — теж рух. */
    public static final double JUMP_EPS = 0.35;
    /**
     * Перші тіки після старту точка відліку їде за гравцем: зупинився з бігу й одразу вибрав емоцію —
     * інерція ще несе його пару десятих блоку, і без пільги «сісти» гасло б саме.
     */
    public static final int GRACE_TICKS = 8;

    public record Active(Emote emote, double x, double y, double z, long startTick) {}

    public enum Decision { KEEP, STOP_MOVED, STOP_DENIED }

    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    public Active start(UUID player, Emote e, double x, double y, double z, long tick) {
        Active a = new Active(e, x, y, z, tick);
        active.put(player, a);
        return a;
    }

    public Active stop(UUID player) { return active.remove(player); }

    public Active active(UUID player) { return active.get(player); }

    public Set<UUID> players() { return Set.copyOf(active.keySet()); }

    public void clear() { active.clear(); }

    /** Рішення на цей тік; сам запис не прибирає (це робить {@link #stop} разом з мережевою зупинкою). */
    public Decision check(UUID player, double x, double y, double z, boolean stillAllowed, long tick) {
        Active a = active.get(player);
        if (a == null) return Decision.KEEP;
        if (!stillAllowed) return Decision.STOP_DENIED;
        if (tick - a.startTick < GRACE_TICKS) {
            active.replace(player, a, new Active(a.emote, x, y, z, a.startTick)); // ще гальмує — відлік з нової точки
            return Decision.KEEP;
        }
        if (a.emote.interruptOnMove() && moved(a, x, y, z)) return Decision.STOP_MOVED;
        return Decision.KEEP;
    }

    static boolean moved(Active a, double x, double y, double z) {
        double dx = x - a.x, dz = z - a.z;
        return dx * dx + dz * dz > MOVE_EPS * MOVE_EPS || Math.abs(y - a.y) > JUMP_EPS;
    }
}
