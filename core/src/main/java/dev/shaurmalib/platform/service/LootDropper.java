package dev.shaurmalib.platform.service;

import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.ref.ItemRef;
import dev.shaurmalib.platform.ref.LevelRef;
import java.util.List;

/** Створення {@code ItemEntity} у світі (конструктори й pickup-delay відрізняються між версіями). */
public interface LootDropper {
    void dropItems(LevelRef level, Vec3d pos, List<ItemRef> items);
}
