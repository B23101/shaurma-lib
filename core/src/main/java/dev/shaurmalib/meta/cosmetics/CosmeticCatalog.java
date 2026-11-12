package dev.shaurmalib.meta.cosmetics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Каталог косметики одного режиму. Порядок додавання = порядок карток у гардеробі. */
public final class CosmeticCatalog {

    private final Map<String, CosmeticDef> defs = new LinkedHashMap<>();

    public CosmeticCatalog add(CosmeticDef def) {
        if (defs.putIfAbsent(def.id(), def) != null) throw new IllegalArgumentException("косметика вже є: " + def.id());
        return this;
    }

    public CosmeticDef get(String id) { return id == null ? null : defs.get(id); }

    public Collection<CosmeticDef> all() { return defs.values(); }

    public List<CosmeticDef> bySlot(CosmeticSlot slot) {
        List<CosmeticDef> out = new ArrayList<>();
        for (CosmeticDef d : defs.values()) if (d.slot() == slot) out.add(d);
        return out;
    }

    /** id предметів-моделей (для реєстрації відтінку в лоадері). */
    public List<String> models() {
        List<String> out = new ArrayList<>();
        for (CosmeticDef d : defs.values()) if (!d.model().isEmpty() && !out.contains(d.model())) out.add(d.model());
        return out;
    }
}
