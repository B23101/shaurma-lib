package dev.shaurmalib.mc;

import dev.shaurmalib.platform.ref.DamageRef;
import dev.shaurmalib.platform.ref.EntityRef;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Optional;

/** {@link DamageRef} над {@link DamageSource} 1.20.1. */
public record McDamageRef(DamageSource source) implements DamageRef {
    @Override public Object raw() { return source; }
    @Override public String typeId() { return source.getMsgId(); }
    @Override public Optional<EntityRef> attacker() { return Optional.ofNullable(source.getEntity()).map(Mc::entity); }
    @Override public Optional<EntityRef> directEntity() { return Optional.ofNullable(source.getDirectEntity()).map(Mc::entity); }
    @Override public boolean projectile() { return source.is(DamageTypeTags.IS_PROJECTILE); }
}
