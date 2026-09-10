package dev.shaurmalib.common.radio;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Реєстр реплік радіо-диктора (план, п. 3.33) — узагальнення статичного
 * стану {@code RadioDialogConfig} snipers_shaurma. Тримає останній
 * розпарсений {@link RadioDialogParser.ParseResult} за атомарним
 * посиланням, щоб {@code reload} (перезавантаження конфігу під час гри,
 * наприклад через {@code /sg config reload}) було безпечним відносно
 * одночасного читання з тіку сервера/рендер-потоку клієнта — читачі
 * завжди бачать або повністю старий, або повністю новий набір реплік,
 * ніколи проміжний стан половини перезаписаної {@code Map}.
 */
public final class RadioDialogRegistry {

    private static final AtomicReference<RadioDialogParser.ParseResult> CURRENT =
            new AtomicReference<>(new RadioDialogParser.ParseResult("uk_ua", 0.30f, "", Map.of()));

    private RadioDialogRegistry() {}

    public static void load(Map<String, Object> yamlRoot) {
        CURRENT.set(RadioDialogParser.parse(yamlRoot));
    }

    public static RadioDialogEntry getEntry(String key) {
        return CURRENT.get().entries.get(key);
    }

    public static Map<String, RadioDialogEntry> getAll() {
        return CURRENT.get().entries;
    }

    public static String getDefaultLanguage() {
        return CURRENT.get().defaultLanguage;
    }

    public static float getDuckRatio() {
        return CURRENT.get().duckRatio;
    }

    public static String getGlobalModelItem() {
        return CURRENT.get().globalModelItem;
    }
}
