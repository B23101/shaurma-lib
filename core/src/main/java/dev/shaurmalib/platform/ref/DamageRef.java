package dev.shaurmalib.platform.ref;

import java.util.Optional;

/** Джерело шкоди (DamageSource) без прив'язки до версійних класів. */
public interface DamageRef extends Handle {
    String typeId();
    Optional<EntityRef> attacker();
    Optional<EntityRef> directEntity();
    /** Снаряд (стріла, куля тощо) — для {@code DamageContext.isProjectile}. */
    boolean projectile();
}
