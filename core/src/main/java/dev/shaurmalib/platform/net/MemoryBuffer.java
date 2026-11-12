package dev.shaurmalib.platform.net;

import dev.shaurmalib.platform.text.Text;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Буфер у пам'яті — для тестів кодеків і для адаптерів, яким потрібен проміжний byte[]. */
public final class MemoryBuffer implements ByteSink, ByteSource {
    private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bos);
    private DataInputStream in;

    private DataInputStream in() { if (in == null) in = new DataInputStream(new ByteArrayInputStream(bos.toByteArray())); return in; }
    private static UncheckedIOException io(IOException e) { return new UncheckedIOException(e); }

    public byte[] toByteArray() { return bos.toByteArray(); }

    @Override public void writeBool(boolean v) { try { out.writeBoolean(v); } catch (IOException e) { throw io(e); } }
    @Override public void writeInt(int v) { try { out.writeInt(v); } catch (IOException e) { throw io(e); } }
    @Override public void writeVarInt(int v) {
        try { while ((v & -128) != 0) { out.writeByte(v & 127 | 128); v >>>= 7; } out.writeByte(v); } catch (IOException e) { throw io(e); }
    }
    @Override public void writeLong(long v) { try { out.writeLong(v); } catch (IOException e) { throw io(e); } }
    @Override public void writeFloat(float v) { try { out.writeFloat(v); } catch (IOException e) { throw io(e); } }
    @Override public void writeDouble(double v) { try { out.writeDouble(v); } catch (IOException e) { throw io(e); } }
    @Override public void writeUtf(String s, int maxLen) {
        if (s.length() > maxLen) throw new IllegalArgumentException("string longer than " + maxLen);
        byte[] b = s.getBytes(StandardCharsets.UTF_8); writeVarInt(b.length); try { out.write(b); } catch (IOException e) { throw io(e); }
    }
    @Override public void writeUUID(UUID id) { writeLong(id.getMostSignificantBits()); writeLong(id.getLeastSignificantBits()); }
    @Override public void writeBytes(byte[] b) { writeVarInt(b.length); try { out.write(b); } catch (IOException e) { throw io(e); } }
    @Override public void writeText(Text t) { writeUtf(t.plain(), 262144); }
    @Override public void writeBlockPos(int x, int y, int z) { writeInt(x); writeInt(y); writeInt(z); }

    @Override public boolean readBool() { try { return in().readBoolean(); } catch (IOException e) { throw io(e); } }
    @Override public int readInt() { try { return in().readInt(); } catch (IOException e) { throw io(e); } }
    @Override public int readVarInt() {
        try { int r = 0, s = 0; byte b; do { b = in().readByte(); r |= (b & 127) << s; s += 7; } while ((b & 128) != 0); return r; }
        catch (IOException e) { throw io(e); }
    }
    @Override public long readLong() { try { return in().readLong(); } catch (IOException e) { throw io(e); } }
    @Override public float readFloat() { try { return in().readFloat(); } catch (IOException e) { throw io(e); } }
    @Override public double readDouble() { try { return in().readDouble(); } catch (IOException e) { throw io(e); } }
    @Override public String readUtf(int maxLen) {
        try { byte[] b = new byte[readVarInt()]; in().readFully(b); String s = new String(b, StandardCharsets.UTF_8);
            if (s.length() > maxLen) throw new IllegalStateException("string longer than " + maxLen); return s; }
        catch (IOException e) { throw io(e); }
    }
    @Override public UUID readUUID() { return new UUID(readLong(), readLong()); }
    @Override public byte[] readBytes() { try { byte[] b = new byte[readVarInt()]; in().readFully(b); return b; } catch (IOException e) { throw io(e); } }
    @Override public Text readText() { return Text.literal(readUtf(262144)); }
    @Override public int[] readBlockPos() { return new int[]{readInt(), readInt(), readInt()}; }
}
