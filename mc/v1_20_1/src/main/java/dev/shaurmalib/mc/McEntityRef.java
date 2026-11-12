package dev.shaurmalib.mc;

import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.ref.EntityRef;
import dev.shaurmalib.platform.ref.LevelRef;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** {@link EntityRef} для будь-якої сутності 1.20.1 (гравців обгортає {@link McPlayerRef}). */
public record McEntityRef(Entity entity) implements EntityRef {
    @Override public Object raw() { return entity; }
    @Override public UUID uuid() { return entity.getUUID(); }
    @Override public Vec3d position() { return new Vec3d(entity.getX(), entity.getY(), entity.getZ()); }
    @Override public LevelRef level() { return McLevelRef.of(entity.level()); }
}
