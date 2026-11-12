package dev.shaurmalib.platform.net;

import dev.shaurmalib.platform.text.Text;
import java.util.UUID;

public interface ByteSource {
    boolean readBool();
    int readInt();
    int readVarInt();
    long readLong();
    float readFloat();
    double readDouble();
    String readUtf(int maxLen);
    UUID readUUID();
    byte[] readBytes();
    Text readText();
    /** x,y,z. */
    int[] readBlockPos();

    default <E extends Enum<E>> E readEnum(Class<E> c) { return c.getEnumConstants()[readVarInt()]; }
    default String readUtf() { return readUtf(32767); }
}
