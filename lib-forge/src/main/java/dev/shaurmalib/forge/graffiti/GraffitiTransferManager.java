package dev.shaurmalib.forge.graffiti;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.GraffitiImageChunkPacket;
import dev.shaurmalib.forge.network.packets.GraffitiImageStartPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обробляє запити клієнтів на отримання PNG-файлів графіті (план, п.
 * 3.14) — 1:1 перенесення {@code GraffitiTransferManager} snipers_shaurma,
 * підключене до {@link GraffitiSyncManager#fileStore()} (namespace, який
 * консюмер зв'язав через {@code withGraffiti(...)}) і
 * {@link ShaurmaLibNetwork} замість каналу консюмера.
 * <p>
 * Продуктивність / анти-спам:
 * <ul>
 *   <li>дедуплікація: якщо передача (player, imageName) вже триває —
 *       повторний запит просто ігнорується (клієнт продовжує чекати те,
 *       що вже летить);</li>
 *   <li>файл читається з диску один раз на запит (ОС і так кешує
 *       сторінки, а PNG графіті — файли на кілька сотень КБ максимум,
 *       тому окремий in-memory кеш байтів не потрібен: мережа однаково
 *       повільніша за диск);</li>
 *   <li>чанки шлються послідовно тим самим тіком (без штучних затримок)
 *       — Netty сам буферизує запис, це не блокує тік сервера.</li>
 * </ul>
 * <p>
 * {@link #clearPlayer(UUID)} треба викликати з обробника дисконнекту
 * консюмера (типово {@code PlayerEvent.PlayerLoggedOutEvent}) — бібліотека
 * не підписується на цю подію сама (той самий принцип "нічого не
 * активується автоматично", що решта статичних сервісів лібу), щоб не
 * плодити event-хендлери, яких консюмер не просив.
 */
public final class GraffitiTransferManager {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/graffiti");

    /** Ключ "playerUUID:imageName" — передачі, що зараз тривають. */
    private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private GraffitiTransferManager() {}

    public static void onImageRequested(ServerPlayer player, String imageName) {
        if (imageName == null || imageName.isBlank()) return;

        String key = player.getUUID() + ":" + imageName;
        if (!inFlight.add(key)) {
            // Передача вже йде — ігноруємо повторний спам-запит клієнта.
            return;
        }

        try {
            MinecraftServer server = player.getServer();
            if (server == null) {
                inFlight.remove(key);
                return;
            }

            GraffitiFileStore fileStore = GraffitiSyncManager.fileStore();
            byte[] bytes = fileStore.readImageBytes(server, imageName);
            if (bytes == null) {
                // Файл відсутній/невалідний — НЕ шлемо START, клієнт лишається
                // на заглушці і сам ретрайне пізніше.
                inFlight.remove(key);
                return;
            }

            long fingerprint = fileStore.fingerprint(server, imageName);
            int totalChunks = (int) Math.ceil(bytes.length / (double) GraffitiImageChunkPacket.CHUNK_SIZE);

            ShaurmaLibNetwork.sendToPlayer(player,
                    new GraffitiImageStartPacket(imageName, fingerprint, bytes.length, totalChunks));

            for (int i = 0; i < totalChunks; i++) {
                int from = i * GraffitiImageChunkPacket.CHUNK_SIZE;
                int to = Math.min(bytes.length, from + GraffitiImageChunkPacket.CHUNK_SIZE);
                byte[] chunk = Arrays.copyOfRange(bytes, from, to);
                ShaurmaLibNetwork.sendToPlayer(player,
                        new GraffitiImageChunkPacket(imageName, fingerprint, i, chunk));
            }
        } catch (Exception e) {
            LOGGER.error("[Graffiti] Помилка передачі {} гравцю {}", imageName, player.getName().getString(), e);
        } finally {
            inFlight.remove(key);
        }
    }

    /** Викликається при дисконнекті гравця, щоб не тримати сміттєві ключі. */
    public static void clearPlayer(UUID uuid) {
        String prefix = uuid + ":";
        inFlight.removeIf(k -> k.startsWith(prefix));
    }
}
