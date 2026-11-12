package dev.shaurmalib.platform.net;

import dev.shaurmalib.platform.text.Text;
import java.util.UUID;

/** Запис у мережевий буфер. Адаптер версії обгортає {@code FriendlyByteBuf}. Набір — рівно те, що реально використовують пакети. */
public interface ByteSink {
    void writeBool(boolean v);
    void writeInt(int v);
    void writeVarInt(int v);
    void writeLong(long v);
    void writeFloat(float v);
    void writeDouble(double v);
    void writeUtf(String s, int maxLen);
    void writeUUID(UUID id);
    void writeBytes(byte[] b);
    void writeText(Text t);
    void writeBlockPos(int x, int y, int z);

    default void writeEnum(Enum<?> e) { writeVarInt(e.ordinal()); }
    default void writeUtf(String s) { writeUtf(s, 32767); }
}
