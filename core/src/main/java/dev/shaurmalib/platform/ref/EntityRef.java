package dev.shaurmalib.platform.ref;

import dev.shaurmalib.platform.math.Vec3d;
import java.util.UUID;

/** Сутність (Entity). */
public interface EntityRef extends Handle {
    UUID uuid();
    Vec3d position();
    LevelRef level();
}
