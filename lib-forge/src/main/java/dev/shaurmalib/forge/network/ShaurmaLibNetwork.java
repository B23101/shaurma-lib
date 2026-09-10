package dev.shaurmalib.forge.network;

import dev.shaurmalib.forge.network.packets.ChatBroadcastPacket;
import dev.shaurmalib.forge.network.packets.ChatSendPacket;
import dev.shaurmalib.forge.network.packets.GraffitiImageChunkPacket;
import dev.shaurmalib.forge.network.packets.GraffitiImageStartPacket;
import dev.shaurmalib.forge.network.packets.GraffitiRemovePacket;
import dev.shaurmalib.forge.network.packets.GraffitiRequestImagePacket;
import dev.shaurmalib.forge.network.packets.GraffitiSyncPacket;
import dev.shaurmalib.forge.network.packets.ItemAnimPacket;
import dev.shaurmalib.forge.network.packets.PlaySoundPacket;
import dev.shaurmalib.forge.network.packets.RadioDialogPacket;
import dev.shaurmalib.forge.network.packets.RadioDialogStopPacket;
import dev.shaurmalib.forge.network.packets.StopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Власний мережевий канал бібліотеки — {@code shaurma_lib} namespace,
 * НЕЗАЛЕЖНИЙ від каналу будь-якого мода-споживача (snipers_shaurma чи
 * maniac-mode). Це навмисне архітектурне рішення (план, розділ 6, п.2):
 * якщо lib і споживач ділили б один канал, версіонування пакетів
 * переплуталося б між ними при незалежних апдейтах lib і мода.
 * <p>
 * Кожен новий модуль бібліотеки, якому потрібна мережа, реєструє свій
 * пакет тут (у {@link #register()}), з власним послідовним ID.
 */
public final class ShaurmaLibNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("shaurma_lib", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    private ShaurmaLibNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(nextId++, ItemAnimPacket.class,
                ItemAnimPacket::encode, ItemAnimPacket::decode, ItemAnimPacket::handle);
        CHANNEL.registerMessage(nextId++, ChatSendPacket.class,
                ChatSendPacket::encode, ChatSendPacket::decode, ChatSendPacket::handle);
        CHANNEL.registerMessage(nextId++, ChatBroadcastPacket.class,
                ChatBroadcastPacket::encode, ChatBroadcastPacket::decode, ChatBroadcastPacket::handle);
        CHANNEL.registerMessage(nextId++, RadioDialogPacket.class,
                RadioDialogPacket::encode, RadioDialogPacket::decode, RadioDialogPacket::handle);
        CHANNEL.registerMessage(nextId++, RadioDialogStopPacket.class,
                RadioDialogStopPacket::encode, RadioDialogStopPacket::decode, RadioDialogStopPacket::handle);
        CHANNEL.registerMessage(nextId++, GraffitiSyncPacket.class,
                GraffitiSyncPacket::encode, GraffitiSyncPacket::decode, GraffitiSyncPacket::handle);
        CHANNEL.registerMessage(nextId++, GraffitiRemovePacket.class,
                GraffitiRemovePacket::encode, GraffitiRemovePacket::decode, GraffitiRemovePacket::handle);
        CHANNEL.registerMessage(nextId++, GraffitiImageStartPacket.class,
                GraffitiImageStartPacket::encode, GraffitiImageStartPacket::decode, GraffitiImageStartPacket::handle);
        CHANNEL.registerMessage(nextId++, GraffitiImageChunkPacket.class,
                GraffitiImageChunkPacket::encode, GraffitiImageChunkPacket::decode, GraffitiImageChunkPacket::handle);
        CHANNEL.registerMessage(nextId++, GraffitiRequestImagePacket.class,
                GraffitiRequestImagePacket::encode, GraffitiRequestImagePacket::decode, GraffitiRequestImagePacket::handle);
        CHANNEL.registerMessage(nextId++, PlaySoundPacket.class,
                PlaySoundPacket::encode, PlaySoundPacket::decode, PlaySoundPacket::handle);
        CHANNEL.registerMessage(nextId++, StopSoundPacket.class,
                StopSoundPacket::encode, StopSoundPacket::decode, StopSoundPacket::handle);
        // Наступні модулі (worldtint, mode settings, ...)
        // додають свій registerMessage(nextId++, ...) тут, у порядку
        // впровадження — ID мають лишатись стабільними між релізами lib,
        // тому нові пакети додаються В КІНЕЦЬ, старі ніколи не видаляються
        // без bump-у PROTOCOL_VERSION.
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToDimension(Object packet, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }
}
