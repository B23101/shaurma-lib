package dev.shaurmalib.platform.net;

import java.util.Objects;

/** Опис пакета: клас, кодек, напрям, обробник. {@code id} — ЯВНИЙ і незмінний (wire-сумісність між релізами/версіями). */
public final class PacketType<T> {
    public enum Direction { C2S, S2C }
    public interface Handler<T> { void handle(T packet, PacketContext ctx); }

    private final String name; private final Class<T> type; private final PacketCodec<T> codec;
    private final Direction direction; private volatile Handler<T> handler; private final int id;

    public PacketType(int id, String name, Class<T> type, PacketCodec<T> codec, Direction d) {
        this.id = id; this.name = name; this.type = type; this.codec = codec; this.direction = d;
    }
    public String name() { return name; }
    public Class<T> type() { return type; }
    public PacketCodec<T> codec() { return codec; }
    public Direction direction() { return direction; }
    public int id() { return id; }
    public PacketType<T> bind(Handler<T> h) { this.handler = Objects.requireNonNull(h); return this; }
    public void dispatch(T packet, PacketContext ctx) {
        Handler<T> h = handler;
        if (h != null) h.handle(packet, ctx);
    }
}
