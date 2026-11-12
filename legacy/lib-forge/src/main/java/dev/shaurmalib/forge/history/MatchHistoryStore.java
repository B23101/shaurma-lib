package dev.shaurmalib.forge.history;

import dev.shaurmalib.common.history.MatchPlayerEntry;
import dev.shaurmalib.common.history.MatchRecord;
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
 * MatchHistoryStore (план, п. 3.20) — узагальнення
 * {@code MatchHistoryManager} snipers_shaurma (167 рядків), з двома
 * принциповими змінами:
 * <ul>
 *   <li>записи серіалізуються через {@link MatchExtraCodec} для
 *       {@code extra()}-мап замість жорстких {@code putXxx(...)} на
 *       кожне snipers-специфічне поле (SCN/SD/BR більше не хардкодяться
 *       в самому сховищі);</li>
 *   <li>один {@link net.minecraft.world.level.saveddata.SavedData}-файл
 *       на весь namespace консюмера ({@code <namespace>_match_history}),
 *       але {@link #addRecord} і {@link #getAll}/{@link #getByMode}
 *       фільтрують за {@code modeId} — та сама "розділена по режимам"
 *       організація, що і решта {@code ShaurmaConfigTree}-дерева (3.1),
 *       без потреби в окремому SavedData-файлі на кожен режим (що
 *       ускладнило б {@code /reload}-подібні операції та дало б N
 *       файлів для N режимів одного мода).</li>
 * </ul>
 * <p>
 * Consumer отримує інстанс через {@link #get(ServerLevel, String)} —
 * той самий {@code namespace}, що передається в
 * {@code ShaurmaLib.Builder.withConfig(...)}, використовується як
 * префікс імені файлу, щоб {@code snipers_shaurma} і майбутній
 * {@code maniac_mode} писали в різні файли навіть на спільному сервері.
 */
public final class MatchHistoryStore extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/history");

    private final List<MatchRecord> records = new ArrayList<>();

    public MatchHistoryStore() {}

    /** @return сховище для namespace консюмера на конкретному {@link ServerLevel} (типово overworld). */
    public static MatchHistoryStore get(ServerLevel level, String namespace) {
        String dataName = namespace + "_match_history";
        return level.getDataStorage().computeIfAbsent(
                tag -> load(tag, dataName), MatchHistoryStore::new, dataName);
    }

    public void addRecord(MatchRecord record) {
        records.add(record);
        setDirty();
        LOGGER.info("[MatchHistory] Збережено матч {} (mode={})", record.id(), record.modeId());
    }

    public boolean deleteRecord(String id) {
        boolean removed = records.removeIf(r -> r.id().equals(id));
        if (removed) {
            setDirty();
            LOGGER.info("[MatchHistory] Видалено матч {}", id);
        }
        return removed;
    }

    /** Усі записи, найновіші перші. */
    public List<MatchRecord> getAll() {
        List<MatchRecord> copy = new ArrayList<>(records);
        copy.sort((a, b) -> Long.compare(b.startTime(), a.startTime()));
        return Collections.unmodifiableList(copy);
    }

    /** Записи конкретного режиму (наприклад лише "scn"), найновіші перші. */
    public List<MatchRecord> getByMode(String modeId) {
        List<MatchRecord> copy = new ArrayList<>();
        for (MatchRecord r : records) {
            if (r.modeId().equals(modeId)) copy.add(r);
        }
        copy.sort((a, b) -> Long.compare(b.startTime(), a.startTime()));
        return Collections.unmodifiableList(copy);
    }

    public int size() {
        return records.size();
    }

    // ── Збереження ───────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (MatchRecord r : records) list.add(serializeRecord(r));
        tag.put("Matches", list);
        return tag;
    }

    private static CompoundTag serializeRecord(MatchRecord r) {
        CompoundTag t = new CompoundTag();
        t.putString("id", r.id());
        t.putString("modeId", r.modeId());
        t.putBoolean("teamMode", r.teamMode());
        t.putLong("startTime", r.startTime());
        t.putLong("endTime", r.endTime());
        t.putString("winnerUuid", r.winnerUuid() != null ? r.winnerUuid() : "");
        t.putString("winnerName", r.winnerName() != null ? r.winnerName() : "");
        t.put("extra", MatchExtraCodec.write(r.extra()));

        ListTag players = new ListTag();
        for (MatchPlayerEntry p : r.players()) {
            CompoundTag pt = new CompoundTag();
            pt.putString("uuid", p.uuid());
            pt.putString("name", p.name());
            pt.putString("teamId", p.teamId());
            pt.putInt("kills", p.kills());
            pt.putInt("deaths", p.deaths());
            pt.putInt("assists", p.assists());
            pt.put("extra", MatchExtraCodec.write(p.extra()));
            players.add(pt);
        }
        t.put("players", players);
        return t;
    }

    // ── Завантаження ─────────────────────────────────────────────────────

    private static MatchHistoryStore load(CompoundTag tag, String dataName) {
        MatchHistoryStore store = new MatchHistoryStore();
        if (!tag.contains("Matches", Tag.TAG_LIST)) return store;
        ListTag list = tag.getList("Matches", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                store.records.add(deserializeRecord(list.getCompound(i)));
            } catch (Exception e) {
                LOGGER.warn("[MatchHistory] Помилка читання запису {} з {}: {}", i, dataName, e.getMessage());
            }
        }
        LOGGER.info("[MatchHistory] Завантажено {} матчів з {}", store.records.size(), dataName);
        return store;
    }

    private static MatchRecord deserializeRecord(CompoundTag t) {
        String id = t.getString("id");
        String modeId = t.getString("modeId");
        boolean teamMode = t.getBoolean("teamMode");
        long startTime = t.getLong("startTime");
        long endTime = t.getLong("endTime");
        String winnerUuid = t.getString("winnerUuid");
        String winnerName = t.getString("winnerName");

        List<MatchPlayerEntry> players = new ArrayList<>();
        ListTag pList = t.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < pList.size(); i++) {
            CompoundTag pt = pList.getCompound(i);
            players.add(new MatchPlayerEntry(
                    pt.getString("uuid"), pt.getString("name"), pt.getString("teamId"),
                    pt.getInt("kills"), pt.getInt("deaths"), pt.getInt("assists"),
                    pt.contains("extra") ? MatchExtraCodec.read(pt.getCompound("extra")) : Collections.emptyMap()));
        }

        return MatchRecord.builder(id, modeId)
                .teamMode(teamMode)
                .timeRange(startTime, endTime)
                .winner(winnerUuid.isEmpty() ? null : winnerUuid, winnerName.isEmpty() ? null : winnerName)
                .players(players)
                .extraAll(t.contains("extra") ? MatchExtraCodec.read(t.getCompound("extra")) : Collections.emptyMap())
                .build();
    }
}
