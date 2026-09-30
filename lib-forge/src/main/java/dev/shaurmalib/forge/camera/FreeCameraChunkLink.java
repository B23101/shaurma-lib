package dev.shaurmalib.forge.camera;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.FreeCameraChunkRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * КЛІЄНТСЬКА половина виправлення чанків для КЕРОВАНИХ ГРАВЦЕМ/клієнтом камер
 * ({@link StationaryCameraController}, {@link CinematicPathController},
 * {@link FreeFlyCameraController}): повідомляє серверу, над яким чанком зараз
 * камера, щоб він слав чанки туди ({@link FreeCameraChunkService}).
 * <p>
 * Контролери викликають {@link #update} самі — консюмеру нічого підключати не
 * треба. Сервер відповідає лише гравцям, яких він дозволив
 * ({@link FreeCameraChunkService#authorize}); для решти запит мовчки ігнорується,
 * тож без явної згоди сервера поведінка не змінюється.
 * <p>
 * Серверні сценарії ({@code ReplayDirector}) цей клас НЕ використовують: сервер
 * і так знає, де камера.
 */
@OnlyIn(Dist.CLIENT)
public final class FreeCameraChunkLink {

    private static final long MIN_INTERVAL_MS = 250L;

    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static long lastSentAtMs = 0L;
    private static boolean active = false;

    private FreeCameraChunkLink() {}

    /** Камера тепер у (x, z). Шле запит, лише коли змінився чанк і минув мінімальний інтервал. */
    public static void update(double x, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        int cx = Mth.floor(x) >> 4;
        int cz = Mth.floor(z) >> 4;
        if (cx == lastChunkX && cz == lastChunkZ) return;
        long now = System.currentTimeMillis();
        if (active && now - lastSentAtMs < MIN_INTERVAL_MS) return;
        lastChunkX = cx;
        lastChunkZ = cz;
        lastSentAtMs = now;
        active = true;
        ShaurmaLibNetwork.sendToServer(new FreeCameraChunkRequestPacket(x, z, false));
    }

    /** Камеру вимкнено: сервер поверне гравця на місце. */
    public static void release() {
        if (!active) return;
        active = false;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        ShaurmaLibNetwork.sendToServer(new FreeCameraChunkRequestPacket(0, 0, true));
    }
}
