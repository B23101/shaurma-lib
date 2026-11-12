package dev.shaurmalib.common.net;

import dev.shaurmalib.common.chat.ChatChannel;
import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.platform.net.ByteSink;
import dev.shaurmalib.platform.net.ByteSource;
import dev.shaurmalib.platform.net.PacketCodec;

/** Клієнт → сервер: текст і канал. Обробник (ChatModule) прив'язується через {@code ShaurmaPackets.CHAT_SEND.bind(..)}. */
public record ChatSendPacket(String text, String channelId) {
    public ChatSendPacket {
        channelId = channelId == null || channelId.isBlank() ? ChatChannel.GLOBAL_ID : channelId;
    }
    public static final PacketCodec<ChatSendPacket> CODEC = new PacketCodec<>() {
        @Override public void encode(ChatSendPacket p, ByteSink b) {
            b.writeUtf(p.text, ChatFormatEngine.MAX_LENGTH); b.writeUtf(p.channelId, 64);
        }
        @Override public ChatSendPacket decode(ByteSource b) {
            return new ChatSendPacket(b.readUtf(ChatFormatEngine.MAX_LENGTH), b.readUtf(64));
        }
    };
}
