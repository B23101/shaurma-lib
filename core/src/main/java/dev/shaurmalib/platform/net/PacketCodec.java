package dev.shaurmalib.platform.net;

/** Як (де)серіалізувати пакет. Однаковий для всіх версій і лоадерів → wire-формат не залежить від збірки. */
public interface PacketCodec<T> {
    void encode(T packet, ByteSink out);
    T decode(ByteSource in);
}
