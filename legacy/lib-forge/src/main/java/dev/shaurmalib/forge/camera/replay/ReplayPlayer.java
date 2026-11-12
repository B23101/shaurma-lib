package dev.shaurmalib.forge.camera.replay;

import dev.shaurmalib.common.camera.replay.ReplayClip;
import dev.shaurmalib.common.camera.replay.ReplayPose;
import dev.shaurmalib.common.camera.replay.ReplayScript;
import dev.shaurmalib.common.camera.replay.ReplayTransition;
import dev.shaurmalib.forge.camera.CameraOwner;
import dev.shaurmalib.forge.camera.CameraOwnershipRegistry;
import dev.shaurmalib.forge.camera.FreeCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * КЛІЄНТСЬКИЙ програвач Replay-сценаріїв ({@link ReplayScript}) на
 * {@link FreeCameraEntity}.
 *
 * <ul>
 *   <li>Скільки завгодно клипів, у кожному скільки завгодно точок, спільний або
 *       окремі інтервали — див. {@link ReplayClip}.</li>
 *   <li>Камера оновлюється КОЖЕН КАДР (а не щотік): рух і поворот плавні при
 *       будь-якому FPS.</li>
 *   <li>Час іде від {@link System#nanoTime()}: не залежить від лагів тіків і
 *       від відкритих екранів.</li>
 *   <li>Жорстка стеля ({@link ReplayScript#totalCutMs()}) обриває сценарій у
 *       заданий момент, навіть посеред руху.</li>
 *   <li>Камера береться/віддається через {@link CameraOwnershipRegistry} — без
 *       стрибків на гравця між клипами й при завершенні.</li>
 * </ul>
 *
 * Чанки під камеру доставляє СЕРВЕР ({@link ReplayDirector}) — саме тому Replay
 * далеко від гравця має повну сцену. Цей клас лише малює.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT)
public final class ReplayPlayer {

    /** Подія життєвого циклу для консюмера (звуки, ефекти, статистика). Усі методи необов'язкові. */
    public interface Listener {
        /** Камеру вперше поставлено на перший кадр (під затемненням). */
        default void onStart(ReplayScript script) {}
        /** Почався наступний клип (і для першого теж). */
        default void onClip(int index, ReplayClip clip) {}
        /** Кінець: {@code interrupted} — обірвано ззовні ({@link #stop}), а не досягнуто кінця/стелі. */
        default void onFinish(boolean interrupted) {}
    }

    private static final class Owner implements CameraOwner {
        @Override
        public void resumeOwnership() {
            Minecraft mc = Minecraft.getInstance();
            if (camera != null && mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
        }
    }

    private static final Owner OWNER = new Owner();

    private static ReplayScript script;
    private static long armedAtNanos;
    private static int delayMs;
    private static boolean started;
    private static int lastClipIndex = -1;

    private static FreeCameraEntity camera;
    private static CameraOwnershipRegistry.OwnershipToken token;

    private static float dipAlpha;
    private static float rollDeg;
    private static float fovNow;
    private static ReplayPose lastPose;
    private static Listener listener = new Listener() {};

    private ReplayPlayer() {}

    // ── Керування ────────────────────────────────────────────────────────

    public static void setListener(Listener l) {
        listener = l != null ? l : new Listener() {};
    }

    /** Запускає сценарій; камера стартує через {@code delayMs} мс. Попередній сценарій тихо скасовується. */
    public static void play(ReplayScript newScript, int startDelayMs) {
        if (newScript == null || newScript.isEmpty()) return;
        if (script != null) finish(true);
        script = newScript;
        delayMs = Math.max(0, startDelayMs);
        armedAtNanos = System.nanoTime();
        started = false;
        lastClipIndex = -1;
        dipAlpha = 0f;
        rollDeg = 0f;
        fovNow = 0f;
        lastPose = null;
    }

    /** Негайно припиняє показ і повертає камеру гравцю. */
    public static void stop() {
        if (script != null) finish(true);
    }

    // ── Стан для консюмера ───────────────────────────────────────────────

    /** Сценарій запущено (навіть якщо камера ще чекає своєї затримки). */
    public static boolean isActive() { return script != null; }

    /** Камера вже летить (затримка минула). */
    public static boolean isCameraRunning() { return script != null && started; }

    /** 0..1: наскільки зараз «провал у чорне» між клипами ({@link ReplayTransition#DIP_TO_BLACK}) — консюмер малює чорний з цією альфою. */
    public static float dipAlpha() { return dipAlpha; }

    /** Мс від старту камери (0, поки не стартувала). */
    public static long elapsedMs() {
        if (script == null || !started) return 0L;
        return Math.max(0L, elapsedSinceArmMs() - delayMs);
    }

    /** Остання застосована поза (для перевірки чанків навколо камери); {@code null} до старту. */
    public static ReplayPose lastPose() { return lastPose; }

    /** Чи завантажені на клієнті чанки в квадраті {@code (2r+1)²} навколо (x, z). */
    public static boolean isAreaLoaded(double x, double z, int radiusChunks) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return false;
        ClientChunkCache cache = level.getChunkSource();
        int cx = Mth.floor(x) >> 4;
        int cz = Mth.floor(z) >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                if (!cache.hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return true;
    }

    // ── Кадр ─────────────────────────────────────────────────────────────

    private static long elapsedSinceArmMs() {
        return (System.nanoTime() - armedAtNanos) / 1_000_000L;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || script == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            hardReset();
            return;
        }

        long sinceArm = elapsedSinceArmMs();
        if (sinceArm < delayMs) return;

        long t = sinceArm - delayMs;
        if (!started) {
            ReplayPose first = script.sampleAt(0);
            if (first == null) { finish(false); return; }
            spawnCamera(mc, first);
            started = true;
            listener.onStart(script);
        }
        if (t >= script.totalMs()) {
            finish(false);
            return;
        }

        ReplayScript.Located at = script.locate(t);
        ReplayClip clip = script.clips().get(at.clipIndex());
        if (at.clipIndex() != lastClipIndex) {
            lastClipIndex = at.clipIndex();
            listener.onClip(lastClipIndex, clip);
        }

        ReplayPose pose = clip.sample(at.localMs());
        lastPose = pose;

        float yaw = pose.yaw();
        float pitch = pose.pitch();
        float roll = 0f;
        float shake = clip.shake();
        if (shake > 0f) {
            double s = t / 1000.0;
            yaw += shake * (float) (Math.sin(s * 1.7) * 0.6 + Math.sin(s * 3.1 + 1.3) * 0.4);
            pitch += shake * (float) (Math.sin(s * 1.3 + 0.7) * 0.6 + Math.sin(s * 2.6) * 0.4);
            roll = shake * 0.5f * (float) Math.sin(s * 0.9 + 2.0);
        }
        rollDeg = roll;
        fovNow = pose.fov();
        dipAlpha = computeDip(t, at.clipIndex());

        if (camera != null) {
            if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
            camera.teleport(pose.x(), pose.y(), pose.z(), yaw, Mth.clamp(pitch, -90f, 90f));
        }
    }

    private static float computeDip(long t, int clipIndex) {
        float best = 0f;
        for (int i = Math.max(1, clipIndex); i <= clipIndex + 1 && i < script.clips().size(); i++) {
            ReplayClip c = script.clips().get(i);
            if (c.transition() != ReplayTransition.DIP_TO_BLACK || c.dipMs() <= 0) continue;
            float half = c.dipMs() / 2f;
            float d = Math.abs(t - script.clipStartMs(i));
            best = Math.max(best, Math.max(0f, 1f - d / half));
        }
        return best;
    }

    private static void spawnCamera(Minecraft mc, ReplayPose p) {
        if (token != null) {
            CameraOwnershipRegistry.discardSilently(token);
            token = null;
        }
        if (camera != null) {
            camera.despawn();
            camera = null;
        }
        camera = new FreeCameraEntity((ClientLevel) mc.level,
            (float) p.x(), (float) p.y(), (float) p.z(), p.yaw(), p.pitch());
        camera.spawn();
        mc.setCameraEntity(camera);
        token = CameraOwnershipRegistry.acquire(OWNER);
    }

    private static void finish(boolean interrupted) {
        boolean wasStarted = started;
        // Порядок як у контролерів бібліотеки: СПЕРШУ віддаємо камеру
        // (release сам ставить її на гравця/наступного власника), і лише
        // потім прибираємо сутність — інакше один кадр на власному тілі.
        if (token != null) {
            CameraOwnershipRegistry.release(token);
            token = null;
        }
        if (camera != null) {
            camera.despawn();
            camera = null;
        }
        script = null;
        started = false;
        lastClipIndex = -1;
        dipAlpha = 0f;
        rollDeg = 0f;
        fovNow = 0f;
        lastPose = null;
        if (wasStarted || interrupted) listener.onFinish(interrupted);
    }

    /** Світ зник (вихід з гри): сутності й камери вже немає, лише чистимо стан. */
    private static void hardReset() {
        token = null;
        camera = null;
        script = null;
        started = false;
        lastClipIndex = -1;
        dipAlpha = 0f;
        rollDeg = 0f;
        fovNow = 0f;
        lastPose = null;
    }

    // ── Події ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (script != null && started && fovNow > 0f) event.setFOV(fovNow);
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (script != null && started && rollDeg != 0f) event.setRoll(event.getRoll() + rollDeg);
    }

    /** Руки гравця не мають висіти в кадрі кінематографічної камери. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (script != null && started) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hardReset();
    }
}
