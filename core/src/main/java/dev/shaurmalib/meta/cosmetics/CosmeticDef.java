package dev.shaurmalib.meta.cosmetics;

/**
 * Предмет косметики в каталозі режиму.
 *
 * @param id           унікальний у межах режиму, напр. {@code "witch_hat"}
 * @param slot         куди одягається
 * @param model        id предмета-моделі в реєстрі гри ({@code "mafia_mod:witch_hat"}); для аур — порожньо
 * @param auraType     для {@link CosmeticSlot#AURA}: вид процедурної аури (0 — Зірка, 1 — Хеловін, ...)
 * @param defaultColor колір за замовчуванням (RGB)
 * @param palette      дозволені кольори; порожній — будь-який
 * @param starter      видається кожному одразу (без покупки)
 * @param hidden       не показувати в каталозі, поки не отримано (нагороди доріжки, івенти)
 */
public record CosmeticDef(String id, String name, String description, CosmeticSlot slot, Rarity rarity,
                          String model, int auraType, int defaultColor, int[] palette,
                          boolean starter, boolean hidden) {

    public static Builder builder(String id, CosmeticSlot slot, String name) { return new Builder(id, slot, name); }

    public boolean allowsColor(int rgb) {
        if (palette == null || palette.length == 0) return true;
        for (int c : palette) if ((c & 0xFFFFFF) == (rgb & 0xFFFFFF)) return true;
        return false;
    }

    public static final class Builder {
        private final String id, name;
        private final CosmeticSlot slot;
        private String description = "", model = "";
        private Rarity rarity = Rarity.COMMON;
        private int auraType, defaultColor = 0xFFFFFF;
        private int[] palette = {0xFFFFFF, 0xB8A1D9, 0xF09CB4, 0x72BDD1, 0xD49254, 0x5D626F};
        private boolean starter, hidden;

        Builder(String id, CosmeticSlot slot, String name) { this.id = id; this.slot = slot; this.name = name; }

        public Builder description(String d) { description = d; return this; }
        public Builder model(String m) { model = m; return this; }
        public Builder rarity(Rarity r) { rarity = r; return this; }
        public Builder aura(int type, int color) { auraType = type; defaultColor = color; return this; }
        public Builder color(int c) { defaultColor = c; return this; }
        public Builder palette(int... p) { palette = p; return this; }
        public Builder starter() { starter = true; return this; }
        public Builder hidden() { hidden = true; return this; }

        public CosmeticDef build() {
            return new CosmeticDef(id, name, description, slot, rarity, model, auraType, defaultColor,
                    palette.clone(), starter, hidden);
        }
    }
}
