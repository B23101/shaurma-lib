package dev.shaurmalib.forge.graffiti;

import dev.shaurmalib.forge.network.packets.GraffitiImageChunkPacket;
import dev.shaurmalib.forge.network.packets.GraffitiImageStartPacket;
import dev.shaurmalib.forge.network.packets.GraffitiRemovePacket;
import dev.shaurmalib.forge.network.packets.GraffitiSyncPacket;
import net.minecraft.core.BlockPos;

/**
 * Міст між серверними мережевими пакетами графіті і клієнтським кешем/
 * world-рендерером консюмера (план, п. 3.14). На відміну від
 * {@link dev.shaurmalib.forge.radio.RadioDialogOverlay} (де бібліотека
 * САМА є рендер-рушієм), клієнтський кеш і world-renderer графіті
 * НЕ входять у цей етап переносу (див. клас-докстрінг
 * {@link GraffitiBlockBase}) — тому пакети делегують сюди, а не
 * напряму в бібліотечний клас, якого ще немає.
 * <p>
 * Консюмер реєструє свою реалізацію {@link Listener} один раз при
 * ініціалізації клієнта (типово в {@code FMLClientSetupEvent}):
 * <pre>{@code
 * GraffitiClientCacheBridge.register(new Listener() {
 *     public void onSync(GraffitiSyncPacket pkt) { GraffitiClientCache.updateBlock(pkt); }
 *     public void onRemove(BlockPos pos) { GraffitiClientCache.removeBlock(pos); }
 *     public void onTransferStart(GraffitiImageStartPacket pkt) { GraffitiClientCache.onTransferStart(...); }
 *     public void onChunk(GraffitiImageChunkPacket pkt) { GraffitiClientCache.onChunkReceived(...); }
 * });
 * }</pre>
 * Якщо консюмер не зареєстрував слухача (клієнтський кеш ще не
 * написаний), пакети просто ігноруються — сервер продовжує коректно
 * зберігати/синхронізувати дані навіть без клієнтського рендера.
 */
public final class GraffitiClientCacheBridge {

    /** Клієнтський обробник вхідних графіті-пакетів — реалізує консюмер. */
    public interface Listener {
        void onSync(GraffitiSyncPacket pkt);
        void onRemove(BlockPos pos);
        void onTransferStart(GraffitiImageStartPacket pkt);
        void onChunk(GraffitiImageChunkPacket pkt);
    }

    private static volatile Listener listener;

    private GraffitiClientCacheBridge() {}

    public static void register(Listener l) {
        listener = l;
    }

    public static void dispatchSync(GraffitiSyncPacket pkt) {
        Listener l = listener;
        if (l != null) l.onSync(pkt);
    }

    public static void dispatchRemove(BlockPos pos) {
        Listener l = listener;
        if (l != null) l.onRemove(pos);
    }

    public static void dispatchTransferStart(GraffitiImageStartPacket pkt) {
        Listener l = listener;
        if (l != null) l.onTransferStart(pkt);
    }

    public static void dispatchChunk(GraffitiImageChunkPacket pkt) {
        Listener l = listener;
        if (l != null) l.onChunk(pkt);
    }
}
