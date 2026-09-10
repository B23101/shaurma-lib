package dev.shaurmalib.common.radio;

import dev.shaurmalib.common.config.YamlConfigSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Парсер {@code radio_dialogs.yml} у {@link RadioDialogEntry} (план,
 * п. 3.33) — узагальнення {@code RadioDialogConfig.load(...)} snipers_shaurma
 * над уже розпарсеним SnakeYAML-деревом ({@code Map<String,Object>}),
 * без прямої залежності на бібліотеку YAML тут — {@code lib-forge} сам
 * вирішує, чим читати файл із диска (SnakeYAML, як в оригіналі), і
 * передає сюди вже готову {@code Map}.
 * <p>
 * Формат YAML — 1:1 з оригіналом:
 * <pre>
 * settings:
 *   default_language: uk_ua
 *   duck_ratio: 0.30
 *   model_item: snipers_shaurma:commander_radio
 * dialogs:
 *   RADIO_KIT_SELECTION_START:
 *     model_item: snipers_shaurma:commander_radio   # опційний override
 *     uk_ua:
 *       text: "..."
 *       sound: sniper_contract:radio.kit_selection.uk
 *       duration_ticks: 80
 *       hold_ticks: 40
 * </pre>
 */
public final class RadioDialogParser {

    private RadioDialogParser() {}

    public static final class ParseResult {
        public final String defaultLanguage;
        public final float duckRatio;
        public final String globalModelItem;
        public final Map<String, RadioDialogEntry> entries;

        ParseResult(String defaultLanguage, float duckRatio, String globalModelItem,
                    Map<String, RadioDialogEntry> entries) {
            this.defaultLanguage = defaultLanguage;
            this.duckRatio = duckRatio;
            this.globalModelItem = globalModelItem;
            this.entries = entries;
        }
    }

    @SuppressWarnings("unchecked")
    public static ParseResult parse(Map<String, Object> root) {
        String defaultLanguage = "uk_ua";
        float duckRatio = 0.30f;
        String globalModelItem = "";

        if (root == null) {
            return new ParseResult(defaultLanguage, duckRatio, globalModelItem, Map.of());
        }

        YamlConfigSection settings = YamlConfigSection.ofNested(root, "settings");
        defaultLanguage = settings.getString("default_language", defaultLanguage);
        duckRatio = (float) settings.getDouble("duck_ratio", duckRatio);
        globalModelItem = settings.getString("model_item", globalModelItem);

        Map<String, RadioDialogEntry> entries = new LinkedHashMap<>();
        Object dialogsRaw = root.get("dialogs");
        if (dialogsRaw instanceof Map<?, ?> dialogsMap) {
            for (Map.Entry<?, ?> e : dialogsMap.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (!(e.getValue() instanceof Map<?, ?> dlgRaw)) continue;
                Map<String, Object> dlg = (Map<String, Object>) dlgRaw;

                String model = dlg.containsKey("model_item")
                        ? String.valueOf(dlg.get("model_item"))
                        : globalModelItem;

                Map<String, RadioLangEntry> langs = new LinkedHashMap<>();
                for (Map.Entry<String, Object> le : dlg.entrySet()) {
                    String langKey = le.getKey();
                    if (langKey.equals("model_item")) continue;
                    if (!(le.getValue() instanceof Map<?, ?> lvRaw)) continue;

                    Map<String, Object> lm = (Map<String, Object>) lvRaw;
                    YamlConfigSection lmSection = new YamlConfigSection(lm);
                    String text = lmSection.getString("text", "");
                    String sound = lmSection.getString("sound", "");
                    int dur = lmSection.getInt("duration_ticks", 80);
                    int hold = lmSection.getInt("hold_ticks", 40);
                    langs.put(langKey, new RadioLangEntry(text, sound, dur, hold));
                }
                entries.put(key, new RadioDialogEntry(model, langs));
            }
        }

        return new ParseResult(defaultLanguage, duckRatio, globalModelItem, entries);
    }
}
