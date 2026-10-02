package dev.shaurmalib.forge.offline;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Утримання чанка тіла завантаженим через Forge-тікети (план, §5.6).
 * Тікети прив'язані до UUID власника, ticking, modId {@code shaurma_lib}.
 * <p>
 * Реєстр після рестарту порожній, тож старі тікети осиротіли:
 * {@link #registerValidationCallback()} повертає всі наші entity-тікети як
 * невалідні при завантаженні світу.
 * <p>
 * Усе лише з потоку сервера.
 */
public final class OfflineChunkAnchor {

    private static final Logger LOGGER = LogManager.getLogger("ShaurmaLib/OfflineChunkAnchor");

    /** modId, під яким живуть тікети. Збігається з modId бібліотеки. */
    static final String MOD_ID = "shaurma_lib";

    private static boolean callbackRegistered = false;

    private record Anchor(String dimensionKey, int chunkX, int chunkZ) {}

    private final Map<UUID, Anchor> anchors = new HashMap<>();

    /**
     * Реєструє валідацію тікетів: усі наші entity-тікети при завантаженні рівня знімаються.
     * Викликається з {@code ShaurmaLibMod} у {@code FMLCommonSetupEvent}. Ідемпотентний.
     * Безпечний для консюмерів без офлайн-модуля: зачіпає лише тікети modId {@code shaurma_lib}.
     */
    public static synchronized void registerValidationCallback() {
        if (callbackRegistered) {
            return;
        }
        ForgeChunkManager.setForcedChunkLoadingCallback(MOD_ID, (level, ticketHelper) -> {
            for (UUID owner : new ArrayList<>(ticketHelper.getEntityTickets().keySet())) {
                ticketHelper.removeAllTickets(owner);
            }
        });
        callbackRegistered = true;
    }

    /** Закріплює чанк власника. Якщо він уже закріплював інший чанк — спершу знімає старий. */
    void anchor(MinecraftServer server, ServerLevel level, UUID owner, int chunkX, int chunkZ) {
        release(server, owner);
        String dim = level.dimension().location().toString();
        try {
            ForgeChunkManager.forceChunk(level, MOD_ID, owner, chunkX, chunkZ, true, true);
            anchors.put(owner, new Anchor(dim, chunkX, chunkZ));
        } catch (RuntimeException e) {
            LOGGER.error("Не вдалося закріпити чанк [{}, {}] для {}", chunkX, chunkZ, owner, e);
        }
    }

    /** Перекріплює тіло на новий чанк, якщо воно змістилось. Нічого не робить, якщо чанк той самий або тіло не закріплене. */
    void reanchorIfMoved(MinecraftServer server, ServerLevel level, UUID owner, int chunkX, int chunkZ) {
        Anchor current = anchors.get(owner);
        if (current == null) {
            return;
        }
        String dim = level.dimension().location().toString();
        if (current.dimensionKey().equals(dim) && current.chunkX() == chunkX && current.chunkZ() == chunkZ) {
            return;
        }
        anchor(server, level, owner, chunkX, chunkZ);
    }

    void release(MinecraftServer server, UUID owner) {
        Anchor anchor = anchors.remove(owner);
        if (anchor == null) {
            return;
        }
        ServerLevel level = server.getLevel(
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(anchor.dimensionKey())));
        if (level == null) {
            return;
        }
        try {
            ForgeChunkManager.forceChunk(level, MOD_ID, owner, anchor.chunkX(), anchor.chunkZ(), false, true);
        } catch (RuntimeException e) {
            LOGGER.error("Не вдалося зняти тікет чанка [{}, {}] для {}", anchor.chunkX(), anchor.chunkZ(), owner, e);
        }
    }

    void releaseAll(MinecraftServer server) {
        for (UUID owner : new ArrayList<>(anchors.keySet())) {
            release(server, owner);
        }
    }

    boolean isAnchored(UUID owner) {
        return anchors.containsKey(owner);
    }
}
