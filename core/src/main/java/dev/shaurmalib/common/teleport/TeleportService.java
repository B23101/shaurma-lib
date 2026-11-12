package dev.shaurmalib.common.teleport;

import dev.shaurmalib.platform.Platform;
import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.service.TeleportBackend;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Оркестрація телепорту гравця — чиста логіка. Усе, що залежить від версії гри
 * (сигнатура {@code teleportTo}, чанк-тікети, motion), делеговано в
 * {@link TeleportBackend}; тут лише порядок кроків, подія й підписники.
 * <p>
 * Порядок кроків і семантика збережені з попередньої реалізації:
 * 1) прогрів чанка цілі (лише в межах одного виміру); 2) скидання fall-distance і motion;
 * 3) зняття гравця з транспорту; 4) власне телепорт; 5) подія {@link TeleportedEvent}.
 */
public final class TeleportService {
    private static final List<Consumer<TeleportedEvent>> LISTENERS = new CopyOnWriteArrayList<>();

    private TeleportService() {}

    public static void addListener(Consumer<TeleportedEvent> l) { LISTENERS.add(Objects.requireNonNull(l)); }
    public static void removeListener(Consumer<TeleportedEvent> l) { LISTENERS.remove(l); }

    public static void teleport(PlayerRef player, LevelRef target,
                                double x, double y, double z,
                                float yaw, float pitch, TeleportReason reason) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        TeleportBackend be = Platform.teleport();

        LevelRef current = player.level();
        boolean crossDimension = !current.dimension().equals(target.dimension());
        if (!crossDimension) {
            be.ensureChunkLoaded(target, x, z);
        }

        be.resetMotionAndFall(player);
        Vec3d before = player.position();

        if (be.isPassenger(player)) {
            be.ejectPassengers(player);
        }
        // Якщо гравець сам везе пасажирів — ванільний механізм перенесе їх разом із ним;
        // вручну їх не рухаємо, щоб не отримати подвійний телепорт.
        be.teleport(player, target, x, y, z, yaw, pitch);

        dispatch(new TeleportedEvent(player, current, target, before, new Vec3d(x, y, z), yaw, pitch, reason));
    }

    public static void teleport(PlayerRef player, LevelRef target,
                                double x, double y, double z, float yaw, float pitch) {
        teleport(player, target, x, y, z, yaw, pitch, TeleportReason.UNSPECIFIED);
    }

    private static void dispatch(TeleportedEvent e) {
        for (Consumer<TeleportedEvent> l : LISTENERS) l.accept(e);
    }

    public enum TeleportReason {
        LOBBY_JOIN, RESPAWN, KIT_SELECT_ZONE, ZIPLINE, FREECAM_TOGGLE, ADMIN_COMMAND, UNSPECIFIED
    }

    public record TeleportedEvent(PlayerRef player, LevelRef fromLevel, LevelRef toLevel,
                                  Vec3d from, Vec3d to, float yaw, float pitch, TeleportReason reason) {
        public double fromX() { return from.x(); }
        public double fromY() { return from.y(); }
        public double fromZ() { return from.z(); }
        public double toX() { return to.x(); }
        public double toY() { return to.y(); }
        public double toZ() { return to.z(); }
    }
}
