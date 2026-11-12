package dev.shaurmalib.common.net;

import dev.shaurmalib.platform.net.PacketRegistry;
import dev.shaurmalib.platform.net.PacketType;
import dev.shaurmalib.platform.net.PacketType.Direction;

/** Усі пакети lib. Перенесено 2 з 18; решта — за таблицею {@link WireIds} (id незмінні, збігаються з legacy). */
public final class ShaurmaPackets {
    private ShaurmaPackets() {}

    public static final PacketType<ChatSendPacket> CHAT_SEND = PacketRegistry.register(
            new PacketType<>(WireIds.CHAT_SEND, "chat_send", ChatSendPacket.class, ChatSendPacket.CODEC, Direction.C2S));
    public static final PacketType<StaminaSyncPacket> STAMINA_SYNC = PacketRegistry.register(
            new PacketType<>(WireIds.STAMINA_SYNC, "stamina_sync", StaminaSyncPacket.class, StaminaSyncPacket.CODEC, Direction.S2C));

    /** Форсує ініціалізацію класу (і реєстрацію) до того, як адаптер викличе {@code PacketRegistry.all()}. */
    public static void ensureLoaded() {}
}
