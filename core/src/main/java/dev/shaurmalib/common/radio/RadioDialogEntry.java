package dev.shaurmalib.common.radio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Одна репліка радіо-диктора з усіма мовними варіантами (план, п. 3.33) —
 * 1:1 перенесення {@code RadioDialogConfig.DialogEntry} snipers_shaurma.
 * <p>
 * {@code modelItemRef} — рядковий {@code ResourceLocation} предмету-моделі
 * диктора (в оригіналі {@code snipers_shaurma:commander_radio}), який
 * рендер-шар (lib-forge, {@code RadioDialogOverlay}) малює як 3D-іконку
 * поруч з текстом репліки. Кожен режим/мод передає власний
 * {@code modelItemRef}, якщо хоче іншу модель диктора для свого namespace —
 * рушій бібліотеки не хардкодить жодного конкретного предмета.
 */
public final class RadioDialogEntry {

    private final String modelItemRef;
    private final Map<String, RadioLangEntry> langs;

    public RadioDialogEntry(String modelItemRef, Map<String, RadioLangEntry> langs) {
        this.modelItemRef = modelItemRef;
        this.langs = Collections.unmodifiableMap(new LinkedHashMap<>(langs));
    }

    public String modelItemRef() {
        return modelItemRef;
    }

    public Map<String, RadioLangEntry> langs() {
        return langs;
    }

    /** Вибирає варіант для {@code locale}, з фолбеком на {@code defaultLang}, потім перший наявний. */
    public RadioLangEntry resolve(String locale, String defaultLang) {
        RadioLangEntry chosen = langs.get(locale);
        if (chosen == null) chosen = langs.get(defaultLang);
        if (chosen == null && !langs.isEmpty()) chosen = langs.values().iterator().next();
        return chosen;
    }
}
