package dev.shaurmalib.mc;

import dev.shaurmalib.platform.data.DataTag;
import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.ref.DamageRef;
import dev.shaurmalib.platform.ref.EntityRef;
import dev.shaurmalib.platform.ref.ItemRef;
import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.text.Text;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Усі конвертери «тип гри 1.20.1 ↔ нейтральний тип core» в одному місці:
 * {@code Mc.player(sp)}, {@code Mc.component(text)}, {@code Mc.damage(source)}…
 * Для нової версії гри створюється такий самий клас у {@code mc/<версія>} з тими самими сигнатурами
 * (див. docs/NEW_VERSION.md).
 */
public final class Mc {
    private Mc() {}

    public static PlayerRef player(Player p) { return p == null ? null : new McPlayerRef(p); }

    public static EntityRef entity(Entity e) {
        if (e == null) return null;
        return e instanceof Player p ? new McPlayerRef(p) : new McEntityRef(e);
    }

    public static LevelRef level(Level l) { return l == null ? null : McLevelRef.of(l); }
    public static ItemRef item(ItemStack s) { return new McItemRef(s == null ? ItemStack.EMPTY : s); }
    public static DamageRef damage(DamageSource s) { return s == null ? null : new McDamageRef(s); }
    public static Vec3d vec(Vec3 v) { return new Vec3d(v.x, v.y, v.z); }
    public static Vec3 vec(Vec3d v) { return new Vec3(v.x(), v.y(), v.z()); }

    public static List<ItemRef> items(List<ItemStack> stacks) {
        List<ItemRef> out = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) out.add(item(s));
        return out;
    }

    public static List<ItemStack> stacks(List<ItemRef> items) {
        List<ItemStack> out = new ArrayList<>(items.size());
        for (ItemRef i : items) out.add(i.unwrap(ItemStack.class));
        return out;
    }

    public static Text text(Component c) { return McText.fromComponent(c); }
    public static MutableComponent component(Text t) { return McText.toComponent(t); }

    public static CompoundTag nbt(DataTag d) { return McData.toNbt(d); }
    public static DataTag data(CompoundTag t) { return McData.fromNbt(t); }
}
