package dev.shaurmalib.anim.emote;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Клієнт → сервер: «хочу зіграти / зупинити емоцію». Сервер перевіряє дозвіл і сам розсилає
 * анімацію всім ({@code ServerAnimations.play}) — клієнт локально нічого не запускає.
 */
public record EmoteMessage(Op op, String emote) {

    public enum Op { PLAY, STOP }

    public static EmoteMessage play(String emote) { return new EmoteMessage(Op.PLAY, emote); }

    public static EmoteMessage stop() { return new EmoteMessage(Op.STOP, ""); }

    public void write(DataOutput out) throws IOException {
        out.writeByte(op.ordinal());
        out.writeUTF(emote == null ? "" : emote);
    }

    public static EmoteMessage read(DataInput in) throws IOException {
        int o = in.readByte();
        Op op = o >= 0 && o < Op.values().length ? Op.values()[o] : Op.STOP;
        String id = in.readUTF();
        if (id.length() > 256) id = id.substring(0, 256); // сміття з мережі не росте безмежно
        return new EmoteMessage(op, id);
    }
}
