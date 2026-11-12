package dev.shaurmalib.mc;

import dev.shaurmalib.platform.ref.LevelRef;
import dev.shaurmalib.platform.ref.ResourceId;
import net.minecraft.world.level.Level;

public record McLevelRef(Level level) implements LevelRef {
    public static McLevelRef of(Level l) { return new McLevelRef(l); }
    @Override public Object raw() { return level; }
    @Override public ResourceId dimension() {
        var loc = level.dimension().location(); // 26.x: Identifier — лише тут зміниться
        return ResourceId.of(loc.getNamespace(), loc.getPath());
    }
}
