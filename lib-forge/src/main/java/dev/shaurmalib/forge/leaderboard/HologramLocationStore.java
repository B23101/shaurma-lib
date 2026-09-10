package dev.shaurmalib.forge.leaderboard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HologramLocationStore (план, п. 3.21) — узагальнення
 * {@code HologramLocations} snipers_shaurma (188 рядків): зберігання
 * позицій/орієнтації голограм-лідербордів у світі.
 * <p>
 * Принципова відмінність від оригіналу — {@link HologramPoint#typeId()}
 * зберігає РЯДКОВИЙ id {@code LeaderboardType} (lib-common), а не
 * {@code enum.name()}. Оригінал при завантаженні невідомого імені типу
 * (наприклад тип зареєстрував мод, який відтоді вимкнули) мовчки
 * підміняв його на {@code KILLS_SC} — точка лишалась у файлі, але
 * тихо показувала геть іншу метрику, без жодного попередження в лог.
 * Тут невідомий {@code typeId} зберігається як є (рядок переживає
 * перезапуск без мода, що його зареєстрував) — рушій рендеру
 * (консюмер) сам вирішує, що робити, якщо {@code LeaderboardType.get(typeId)}
 * повертає {@code null} (типово — пропустити точку й залогувати
 * попередження, а не підміняти чужими даними).
 * <p>
 * Не включає власне рендер/синхронізацію з клієнтами (аналог
 * {@code LeaderboardSyncPacket}/{@code HologramDisplayManager} з
 * оригіналу) — це свідомо залишено консюмеру на цьому етапі: рендер
 * голограми в світі (billboard-текст) — окрема, суттєвіша частина
 * роботи, яку варто узагальнювати окремо разом з рештою
 * {@code dev.shaurmalib.forge.markers} (план, п. 3.13). Цей клас дає
 * готове, персистентне, розділене по namespace сховище позицій, з
 * яким консюмер вже сьогодні може написати власний рендер без
 * необхідності самому писати NBT-серіалізацію.
 */
public final class HologramLocationStore extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/leaderboard");

    private final List<HologramPoint> points = new ArrayList<>();

    public HologramLocationStore() {}

    public static HologramLocationStore get(ServerLevel level, String namespace) {
        String dataName = namespace + "_holograms";
        return level.getDataStorage().computeIfAbsent(
                tag -> load(tag, dataName), HologramLocationStore::new, dataName);
    }

    // ── API ──────────────────────────────────────────────────────────────

    /** Додає точку, замінюючи існуючу на тій самій позиції, якщо є. */
    public void addPoint(BlockPos pos, double minY, float yaw, float scale,
                          String typeId, List<String> multiTypeIds) {
        points.removeIf(p -> p.pos.equals(pos));
        points.add(new HologramPoint(pos, minY, yaw, clampScale(scale), typeId,
                multiTypeIds != null ? new ArrayList<>(multiTypeIds) : Collections.emptyList()));
        setDirty();
        LOGGER.info("[Hologram] Додано '{}' на {}", typeId, pos);
    }

    public boolean removeNearest(BlockPos pos, double maxDist) {
        HologramPoint nearest = null;
        double best = maxDist * maxDist;
        for (HologramPoint p : points) {
            double d = p.pos.distSqr(pos);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest != null) {
            points.remove(nearest);
            setDirty();
            return true;
        }
        return false;
    }

    public void removeAt(BlockPos pos) {
        points.removeIf(p -> p.pos.equals(pos));
        setDirty();
    }

    public List<HologramPoint> getAll() {
        return Collections.unmodifiableList(points);
    }

    private static float clampScale(float scale) {
        return Math.max(0.1f, Math.min(5.0f, scale));
    }

    // ── NBT ──────────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (HologramPoint p : points) {
            CompoundTag pt = new CompoundTag();
            pt.putLong("Pos", p.pos.asLong());
            pt.putDouble("MinY", p.minY);
            pt.putFloat("Yaw", p.yaw);
            pt.putFloat("Scale", p.scale);
            pt.putString("TypeId", p.typeId);
            if (!p.multiTypeIds.isEmpty()) {
                ListTag ml = new ListTag();
                for (String t : p.multiTypeIds) {
                    CompoundTag mt = new CompoundTag();
                    mt.putString("T", t);
                    ml.add(mt);
                }
                pt.put("MultiTypeIds", ml);
            }
            list.add(pt);
        }
        tag.put("Points", list);
        return tag;
    }

    private static HologramLocationStore load(CompoundTag tag, String dataName) {
        HologramLocationStore store = new HologramLocationStore();
        if (!tag.contains("Points", Tag.TAG_LIST)) return store;

        ListTag list = tag.getList("Points", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                CompoundTag pt = list.getCompound(i);
                BlockPos pos = BlockPos.of(pt.getLong("Pos"));
                double minY = pt.getDouble("MinY");
                float yaw = pt.getFloat("Yaw");
                float scale = pt.contains("Scale") ? pt.getFloat("Scale") : 1.0f;
                String typeId = pt.getString("TypeId");

                List<String> multi = new ArrayList<>();
                if (pt.contains("MultiTypeIds", Tag.TAG_LIST)) {
                    ListTag ml = pt.getList("MultiTypeIds", Tag.TAG_COMPOUND);
                    for (int j = 0; j < ml.size(); j++) {
                        multi.add(ml.getCompound(j).getString("T"));
                    }
                }
                store.points.add(new HologramPoint(pos, minY, yaw, scale, typeId, multi));
            } catch (Exception e) {
                LOGGER.warn("[Hologram] Помилка читання точки {} з {}: {}", i, dataName, e.getMessage());
            }
        }
        LOGGER.info("[Hologram] Завантажено {} точок з {}", store.points.size(), dataName);
        return store;
    }

    // ── Data class ───────────────────────────────────────────────────────

    public static final class HologramPoint {
        public final BlockPos pos;
        public final double minY;
        public final float yaw;
        public final float scale;
        public final String typeId;
        /** Для {@code typeId == LeaderboardType.MULTI} — список id для ротації. */
        public final List<String> multiTypeIds;

        public HologramPoint(BlockPos pos, double minY, float yaw, float scale,
                              String typeId, List<String> multiTypeIds) {
            this.pos = pos;
            this.minY = minY;
            this.yaw = yaw;
            this.scale = scale;
            this.typeId = typeId;
            this.multiTypeIds = multiTypeIds != null ? multiTypeIds : Collections.emptyList();
        }
    }
}
