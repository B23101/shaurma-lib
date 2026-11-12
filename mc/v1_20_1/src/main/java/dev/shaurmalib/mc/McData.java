package dev.shaurmalib.mc;

import dev.shaurmalib.platform.data.DataTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@link DataTag} ↔ NBT ({@link CompoundTag}) для 1.20.1. */
public final class McData {
    private McData() {}

    public static CompoundTag toNbt(DataTag d) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, Object> e : d.entries().entrySet()) {
            Tag t = toTag(e.getValue());
            if (t != null) out.put(e.getKey(), t);
        }
        return out;
    }

    private static Tag toTag(Object v) {
        if (v instanceof String s) return StringTag.valueOf(s);
        if (v instanceof Integer i) return IntTag.valueOf(i);
        if (v instanceof Long l) return LongTag.valueOf(l);
        if (v instanceof Float f) return FloatTag.valueOf(f);
        if (v instanceof Double x) return DoubleTag.valueOf(x);
        if (v instanceof Boolean b) return ByteTag.valueOf(b);
        if (v instanceof byte[] bytes) return new ByteArrayTag(bytes);
        if (v instanceof DataTag d) return toNbt(d);
        if (v instanceof List<?> list) {
            ListTag lt = new ListTag();
            for (Object o : list) {
                Tag t = toTag(o);
                if (t != null) lt.add(t);
            }
            return lt;
        }
        return null;
    }

    public static DataTag fromNbt(CompoundTag tag) {
        DataTag d = new DataTag();
        for (String k : tag.getAllKeys()) put(d, k, tag.get(k));
        return d;
    }

    private static void put(DataTag d, String k, Tag t) {
        if (t instanceof StringTag s) d.putString(k, s.getAsString());
        else if (t instanceof IntTag i) d.putInt(k, i.getAsInt());
        else if (t instanceof LongTag l) d.putLong(k, l.getAsLong());
        else if (t instanceof FloatTag f) d.putFloat(k, f.getAsFloat());
        else if (t instanceof DoubleTag x) d.putDouble(k, x.getAsDouble());
        else if (t instanceof ByteTag b) d.putBool(k, b.getAsByte() != 0);
        else if (t instanceof NumericTag n) d.putDouble(k, n.getAsDouble());
        else if (t instanceof ByteArrayTag ba) d.putBytes(k, ba.getAsByteArray());
        else if (t instanceof CompoundTag c) d.putTag(k, fromNbt(c));
        else if (t instanceof ListTag lt) {
            List<Object> out = new ArrayList<>();
            for (Tag e : lt) {
                if (e instanceof CompoundTag c) out.add(fromNbt(c));
                else if (e instanceof StringTag s) out.add(s.getAsString());
                else if (e instanceof NumericTag n) out.add(n.getAsDouble());
            }
            d.putList(k, out);
        }
    }
}
