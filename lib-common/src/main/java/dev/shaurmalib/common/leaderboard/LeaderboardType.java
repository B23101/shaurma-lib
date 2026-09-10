package dev.shaurmalib.common.leaderboard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LeaderboardType (план, п. 3.21) — заміна жорсткого {@code enum} на 19
 * значень ({@code KILLS_SC}, {@code KILLS_SCN}, {@code WINS_BR}, ...)
 * snipers_shaurma розширюваним реєстром рядкових id: кожен режим (SC/
 * SCN/SD/BR у snipers, і будь-який режим у maniac-mode) реєструє свої
 * типи топів сам, без зміни коду рушія бібліотеки.
 * <p>
 * {@code id} — рядковий ідентифікатор (наприклад {@code "kills_scn"},
 * {@code "captures_scn"}) — саме він, а не enum-константа, зберігається
 * в NBT {@code HologramLocationStore} (lib-forge), тому додавання
 * нового типу режимом ніколи не ламає збережені точки голограм інших
 * режимів (на відміну від {@code enum.valueOf(...)} в оригіналі — там
 * невідоме ім'я типу при завантаженні мовчки підмінялось на
 * {@code KILLS_SC}, що на практиці означало "точку зіпсовано, якщо
 * мод, що реєстрував цей тип, вимкнули").
 * <p>
 * {@code auto} (аналог {@code KILLS_AUTO}/{@code WINS_AUTO} з оригіналу)
 * — {@link AutoResolver}, функціональний інтерфейс "яким {@code id}
 * замінити цей тип прямо зараз" (типово — за поточним активним
 * режимом); {@code null}, якщо тип не потребує автозаміни.
 */
public final class LeaderboardType {

    /** Спеціальний id мульти-борду — рушій сам знає, що для нього використовується список типів, а не {@link #resolve()}. */
    public static final String MULTI = "multi";

    @FunctionalInterface
    public interface AutoResolver {
        /** @return id типу, яким слід відобразити цей запис прямо зараз (наприклад залежно від активного режиму). */
        String resolve();
    }

    private static final Map<String, LeaderboardType> REGISTRY = new LinkedHashMap<>();

    private final String id;
    private final String displayName;
    private final AutoResolver autoResolver;

    private LeaderboardType(String id, String displayName, AutoResolver autoResolver) {
        this.id = id;
        this.displayName = displayName;
        this.autoResolver = autoResolver;
    }

    /** Реєструє звичайний (нерухомий) тип топу. Ідемпотентно — повторна реєстрація того самого id перезаписує запис. */
    public static LeaderboardType register(String id, String displayName) {
        LeaderboardType t = new LeaderboardType(id, displayName, null);
        REGISTRY.put(id, t);
        return t;
    }

    /** Реєструє тип з авто-розв'язанням (аналог KILLS_AUTO/WINS_AUTO) — {@link #resolve()} повертає id поточного цільового типу. */
    public static LeaderboardType registerAuto(String id, String displayName, AutoResolver autoResolver) {
        LeaderboardType t = new LeaderboardType(id, displayName, autoResolver);
        REGISTRY.put(id, t);
        return t;
    }

    public static LeaderboardType get(String id) {
        return REGISTRY.get(id);
    }

    public static Map<String, LeaderboardType> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isAuto() {
        return autoResolver != null;
    }

    /** Для авто-типів — id типу, яким слід відобразити цей запис прямо зараз; для звичайних — власний {@link #id()}. */
    public String resolve() {
        if (autoResolver == null) return id;
        try {
            String resolved = autoResolver.resolve();
            return resolved != null ? resolved : id;
        } catch (Exception e) {
            return id;
        }
    }
}
