package dev.shaurmalib.anim.rig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Скелет: імена кісток, ієрархія, півоти (простір Blockbench, пікселі) і іменовані групи для масок.
 *
 * Два різновиди:
 * <ul>
 *   <li>{@link #PLAYER} — ванільна модель гравця/гуманоїда; той самий риг, що в
 *       {@code player-animation_model.bbmodel} ({@code body} — корінь усього тіла, {@code torso} — тулуб).</li>
 *   <li>{@link #fromHierarchy} — будь-яка geo-модель (маньяк, моб): кістки з {@code .geo.json},
 *       групи ({@code upper_body}, {@code legs}...) виводяться з імен та ієрархії.</li>
 * </ul>
 */
public final class Rig {

    private final String name;
    private final String[] bones;
    private final int[] parent;
    private final float[][] pivots;
    private final Map<String, Integer> index = new HashMap<>();
    private final Map<String, Set<String>> groups = new LinkedHashMap<>();
    private final Map<String, BoneMask> maskCache = new ConcurrentHashMap<>();
    private final UnaryOperator<String> canon;
    private final String root;
    private float[] eye;

    private Rig(String name, List<String> boneList, List<String> parents, List<float[]> pivotList,
                UnaryOperator<String> canon) {
        this.name = name;
        this.canon = canon;
        this.bones = boneList.toArray(new String[0]);
        this.parent = new int[bones.length];
        this.pivots = pivotList.toArray(new float[0][]);
        for (int i = 0; i < bones.length; i++) index.put(bones[i], i);
        String r = null;
        for (int i = 0; i < bones.length; i++) {
            String p = parents.get(i);
            parent[i] = p == null ? -1 : index.getOrDefault(p, -1);
            if (parent[i] < 0 && r == null) r = bones[i];
        }
        this.root = r;
        buildDefaultGroups();
    }

    // ── Ванільний гравець ────────────────────────────────────────────────

    /** Півоти — з Blockbench-ригу гравця (Y вгору, ноги на 0). */
    public static final Rig PLAYER = builder("player", BoneNames::player)
        .bone("body", null, 0, 12, 0)
        .bone("head", "body", 0, 24, 0)
        .bone("torso", "body", 0, 24, 0)
        .bone("right_arm", "body", 5, 22, 0)
        .bone("right_item", "right_arm", 6, 12, -2)
        .bone("left_arm", "body", -5, 22, 0)
        .bone("left_item", "left_arm", -6, 12, -2)
        .bone("right_leg", "body", 1.9f, 12, 0)
        .bone("left_leg", "body", -1.9f, 12, 0)
        .eye(0, 25.92f, 0) // ванільна висота очей 1.62 блока
        .build();

    public static Builder builder(String name, UnaryOperator<String> canon) { return new Builder(name, canon); }

    /**
     * Риг geo-моделі за списком кісток (ім'я, батько, півот у просторі Blockbench). Імена не
     * нормалізуються до snake_case — geo-анімації посилаються на кістки рівно як у файлі моделі,
     * але порівняння нечутливе до регістру.
     */
    public static Rig fromHierarchy(String name, List<String> names, List<String> parents, List<float[]> pivots) {
        Builder b = builder(name, s -> s);
        for (int i = 0; i < names.size(); i++) {
            float[] p = pivots == null || i >= pivots.size() || pivots.get(i) == null ? new float[3] : pivots.get(i);
            b.bone(names.get(i), parents.get(i), p[0], p[1], p[2]);
        }
        return b.build();
    }

    public static final class Builder {
        private final String name;
        private final UnaryOperator<String> canon;
        private final List<String> names = new ArrayList<>();
        private final List<String> parents = new ArrayList<>();
        private final List<float[]> pivots = new ArrayList<>();
        private final Map<String, List<String>> extraGroups = new LinkedHashMap<>();
        private float[] eye;

        Builder(String name, UnaryOperator<String> canon) { this.name = name; this.canon = canon; }

        public Builder bone(String bone, String parent, float px, float py, float pz) {
            names.add(bone);
            parents.add(parent);
            pivots.add(new float[] { px, py, pz });
            return this;
        }

        public Builder group(String group, String... boneNames) {
            extraGroups.put(group, List.of(boneNames));
            return this;
        }

        /** Точка ока в просторі Blockbench (пікселі від ступнів) — для камери, що йде за головою. */
        public Builder eye(float x, float y, float z) { this.eye = new float[] { x, y, z }; return this; }

        public Rig build() {
            Rig r = new Rig(name, names, parents, pivots, canon);
            r.eye = eye;
            extraGroups.forEach((g, list) -> r.groups.put(g, new LinkedHashSet<>(list)));
            return r;
        }
    }

    // ── Доступ ───────────────────────────────────────────────────────────

    public String name() { return name; }
    public int size() { return bones.length; }
    public String bone(int i) { return bones[i]; }
    public int parent(int i) { return parent[i]; }
    public String root() { return root; }

    /** Точка ока (простір Blockbench) або null — камера за головою для цього ригу недоступна. */
    public float[] eye() { return eye; }

    public Rig withEye(float x, float y, float z) { this.eye = new float[] { x, y, z }; return this; }

    /** Півот кістки в просторі Blockbench (пікселі, Y вгору). */
    public float[] pivot(int i) { return pivots[i]; }

    /** Індекс кістки за будь-яким написанням імені; -1 — немає в ригу (наприклад, кістка пропса). */
    public int indexOf(String boneName) {
        if (boneName == null) return -1;
        Integer i = index.get(canonical(boneName));
        if (i != null) return i;
        for (int k = 0; k < bones.length; k++) if (bones[k].equalsIgnoreCase(boneName)) return k;
        return -1;
    }

    public String canonical(String boneName) {
        return canon.apply(boneName);
    }

    public boolean isDescendant(int bone, int ancestor) {
        for (int b = bone; b >= 0; b = parent[b]) if (b == ancestor) return true;
        return false;
    }

    public Set<String> groupNames() { return Collections.unmodifiableSet(groups.keySet()); }

    /** Маска за специфікацією ({@code "upper_body"}, {@code "full-legs"}, {@code "right_arm+torso*0.5"}). Кешується. */
    public BoneMask mask(String spec) {
        String key = spec == null || spec.isBlank() ? "full" : spec.trim().toLowerCase(Locale.ROOT);
        return maskCache.computeIfAbsent(key, k -> BoneMask.parse(this, k));
    }

    /** Кістки групи або, якщо такої групи нема, кістка з цим іменем + її нащадки. */
    Set<Integer> resolve(String term, boolean onlySelf) {
        Set<Integer> out = new LinkedHashSet<>();
        Set<String> g = groups.get(term);
        if (g != null && !onlySelf) {
            for (String b : g) {
                int i = indexOf(b);
                if (i >= 0) out.add(i);
            }
            return out;
        }
        int i = indexOf(term);
        if (i < 0) return out;
        out.add(i);
        if (!onlySelf && i != rootIndex()) {
            for (int k = 0; k < bones.length; k++) if (isDescendant(k, i)) out.add(k);
        }
        return out;
    }

    private int rootIndex() { return root == null ? -1 : index.get(root); }

    // ── Групи за замовчуванням ───────────────────────────────────────────

    private void buildDefaultGroups() {
        Set<String> all = new LinkedHashSet<>(List.of(bones));
        groups.put("full", all);
        groups.put("all", all);

        Set<Integer> legs = new LinkedHashSet<>(), arms = new LinkedHashSet<>(), heads = new LinkedHashSet<>();
        Set<Integer> right = new LinkedHashSet<>(), left = new LinkedHashSet<>();
        for (int i = 0; i < bones.length; i++) {
            String n = bones[i].toLowerCase(Locale.ROOT);
            if (n.contains("leg") || n.contains("foot") || n.contains("thigh") || n.contains("shin")) legs.addAll(withDescendants(i));
            if (n.contains("arm") || n.contains("hand") || n.contains("item")) arms.addAll(withDescendants(i));
            if (n.contains("head") || n.contains("neck")) heads.addAll(withDescendants(i));
            if (n.contains("right") && (n.contains("arm") || n.contains("hand") || n.contains("item"))) right.addAll(withDescendants(i));
            if (n.contains("left") && (n.contains("arm") || n.contains("hand") || n.contains("item"))) left.addAll(withDescendants(i));
        }
        // Верх тіла = усе, крім ніг і їхніх предків (корінь тягне за собою ноги — його не беремо).
        Set<Integer> upper = new LinkedHashSet<>();
        for (int i = 0; i < bones.length; i++) {
            if (legs.contains(i)) continue;
            boolean legAncestor = false;
            for (int l : legs) if (l != i && isDescendant(l, i)) { legAncestor = true; break; }
            if (!legAncestor) upper.add(i);
        }
        groups.put("legs", names(legs));
        groups.put("arms", names(arms));
        groups.put("head", names(heads));
        groups.put("right_arm", names(right));
        groups.put("left_arm", names(left));
        groups.put("upper_body", names(upper));
        Set<String> torso = names(upper);
        torso.removeAll(names(arms));
        torso.removeAll(names(heads));
        groups.put("spine", torso);
        if (root != null) groups.put("root", new LinkedHashSet<>(List.of(root)));
    }

    private Set<Integer> withDescendants(int i) {
        Set<Integer> s = new LinkedHashSet<>();
        for (int k = 0; k < bones.length; k++) if (isDescendant(k, i)) s.add(k);
        return s;
    }

    private Set<String> names(Set<Integer> idx) {
        Set<String> s = new LinkedHashSet<>();
        for (int i : idx) s.add(bones[i]);
        return s;
    }

    @Override
    public String toString() { return "Rig[" + name + ", " + bones.length + " bones]"; }
}
