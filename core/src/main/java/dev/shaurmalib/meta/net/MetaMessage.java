package dev.shaurmalib.meta.net;

import dev.shaurmalib.meta.io.J;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Повідомлення мета-двигуна: код операції + JSON-тіло. Формат дротів визначає ядро, лоадер лише несе байти
 * (один канал {@code shaurma_lib:meta} на Forge і Fabric). JSON, а не ручний бінарний формат: профіль і
 * результат матчу мають той самий серіалізатор, що й файл, — розійтись нема чому.
 * Коди операцій фіксовані (нові лише в кінець), тож клієнт і сервер різних збірок не плутаються.
 */
public record MetaMessage(Op op, Map<String, Object> body) {

    public enum Op {
        // ── сервер → клієнт ──
        HELLO(false), PROFILE(false), LOOKS(false), RESULT(false), LEADERBOARD(false), NOTICE(false), OPEN(false),
        WAYPOINTS(false), FX_PLUS(false), GRANTED(false),
        // ── клієнт → сервер ──
        REQ_PROFILE(true), SAVE_WARDROBE(true), BUY(true), CRAFT(true), CLAIM(true), UPGRADE(true),
        REQ_LEADERBOARD(true), SEEN(true),
        // ── додано пізніше (лише в кінець!) ──
        OPEN_CHEST(true), DAILY(true), LOOT_FEED(false);

        private final boolean toServer;

        Op(boolean toServer) { this.toServer = toServer; }

        public boolean toServer() { return toServer; }
    }

    /** Захист від завеликих пакетів (профіль із косметикою — кілька КБ). */
    public static final int MAX_BYTES = 1 << 20;

    public static MetaMessage of(Op op, Object... kv) { return new MetaMessage(op, J.o(kv)); }

    public String str(String k) { return J.str(body, k, ""); }

    public long num(String k) { return J.num(body, k, 0); }

    public Map<String, Object> obj(String k) { return J.obj(body, k); }

    public void write(DataOutput out) throws IOException {
        out.writeByte(op.ordinal());
        byte[] b = J.write(body).getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    public static MetaMessage read(DataInput in) throws IOException {
        int code = in.readUnsignedByte();
        Op[] ops = Op.values();
        if (code >= ops.length) throw new IOException("невідома операція мета-двигуна: " + code);
        int len = in.readInt();
        if (len < 0 || len > MAX_BYTES) throw new IOException("завеликий пакет мета-двигуна: " + len);
        byte[] b = new byte[len];
        in.readFully(b);
        return new MetaMessage(ops[code], J.parse(new String(b, StandardCharsets.UTF_8)));
    }
}
