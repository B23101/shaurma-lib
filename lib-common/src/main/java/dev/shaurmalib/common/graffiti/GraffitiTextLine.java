package dev.shaurmalib.common.graffiti;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Один рядок тексту в текстовому режимі графіті (план, п. 3.14) — 1:1
 * перенесення {@code graffiti.block.GraffitiTextLine} snipers_shaurma в
 * {@code lib-common}, без жодної зміни семантики: клас уже не мав жодного
 * прямого Forge-імпорту в оригіналі (лише {@code net.minecraft.nbt}/
 * {@code net.minecraft.network}, які винесені звідси окремо в
 * {@code dev.shaurmalib.forge.graffiti.GraffitiCodec} (методи
 * {@code saveTextLine}/{@code loadTextLine}/{@code writeTextLine}/
 * {@code readTextLine}), бо NBT/FriendlyByteBuf — Minecraft, а не Forge
 * API, але серіалізація як інфраструктура логічно належить forge-модулю
 * поруч з рештою серіалізаторів бібліотеки (той самий {@code GraffitiCodec}
 * серіалізує й {@link GraffitiSpec} цілком); сам noun-клас лишається
 * чистими даними тут).
 * <p>
 * {@code source} визначає, звідки береться текст, який реально
 * показується в світі:
 * <ul>
 *   <li><b>PLAIN</b> — {@code content} це готовий текст, однаковий для
 *       всіх гравців незалежно від мови гри;</li>
 *   <li><b>LANG_KEY</b> — {@code content} це ключ перекладу, який має
 *       бути визначений у lang-файлах СПОЖИВАЧА (мод, не lib); кожен
 *       гравець бачить {@code Component.translatable(content)} —
 *       резолвиться на клієнті, не тут (це вимагає {@code I18n}, який є
 *       Minecraft, не Forge, але сам резолв — відповідальність
 *       рендер-шару споживача чи бібліотечного world-renderer'а, не
 *       цього класу-даних);</li>
 *   <li><b>MULTI_LANG</b> — {@code translations}: мапа "мовний код
 *       (en_us, uk_ua, ...) → текст", яку автор графіті вводить одразу в
 *       GUI-редакторі, без жодного lang-файлу; фолбек на
 *       {@link #FALLBACK_LANG}, а якщо і його нема — на перший запис у
 *       мапі.</li>
 * </ul>
 * {@code color} зберігається як пакований {@code 0xRRGGBB} (без альфи —
 * альфа завжди 255, текст графіті непрозорий так само, як і картинка).
 */
public final class GraffitiTextLine {

    public enum SourceType { PLAIN, LANG_KEY, MULTI_LANG }

    public static final int DEFAULT_COLOR = 0xFFFFFF;
    public static final int MAX_TEXT_LEN = 256;
    public static final int MAX_LANG_KEY_LEN = 128;
    public static final int MAX_LANG_CODE_LEN = 16;
    public static final int MAX_TRANSLATIONS = 16;
    /** Мова, на яку відкочуємось, якщо в поточного гравця немає перекладу
     *  ні для MULTI_LANG-запису, ні для LANG_KEY (той самий фолбек, що й
     *  ванільний Minecraft використовує для lang-файлів). */
    public static final String FALLBACK_LANG = "en_us";

    private SourceType source;
    /** Для PLAIN — сам текст. Для LANG_KEY — ключ перекладу. Для
     *  MULTI_LANG — не використовується (текст лежить у translations). */
    private String content;
    /** Мовний код → текст цією мовою. Заповнюється лише для MULTI_LANG. */
    private Map<String, String> translations;

    private int color;
    private boolean bold;
    private boolean italic;
    private boolean shadow;
    /** Множник розміру окремого рядка (1.0 = базовий). */
    private float scale;

    public GraffitiTextLine() {
        this(SourceType.PLAIN, "", new LinkedHashMap<>(), DEFAULT_COLOR, false, false, true, 1.0f);
    }

    public GraffitiTextLine(SourceType source, String content, Map<String, String> translations,
                             int color, boolean bold, boolean italic, boolean shadow) {
        this(source, content, translations, color, bold, italic, shadow, 1.0f);
    }

    public GraffitiTextLine(SourceType source, String content, Map<String, String> translations,
                             int color, boolean bold, boolean italic, boolean shadow, float scale) {
        this.source = source == null ? SourceType.PLAIN : source;
        this.content = content == null ? "" : content;
        this.translations = translations == null ? new LinkedHashMap<>() : new LinkedHashMap<>(translations);
        this.color = color & 0xFFFFFF;
        this.bold = bold;
        this.italic = italic;
        this.shadow = shadow;
        this.scale = Math.max(0.1f, Math.min(10f, scale));
    }

    public GraffitiTextLine copy() {
        return new GraffitiTextLine(source, content, translations, color, bold, italic, shadow, scale);
    }

    // ── Getters / Setters ───────────────────────────────────────────────

    public SourceType source() { return source; }
    public void setSource(SourceType source) { this.source = source == null ? SourceType.PLAIN : source; }

    public String content() { return content; }
    public void setContent(String content) { this.content = content == null ? "" : content; }

    /** Незмінна мапа перекладів — мутація лише через {@link #setTranslations}. */
    public Map<String, String> translations() { return translations; }
    public void setTranslations(Map<String, String> translations) {
        this.translations = translations == null ? new LinkedHashMap<>() : new LinkedHashMap<>(translations);
    }

    public int color() { return color; }
    public void setColor(int color) { this.color = color & 0xFFFFFF; }

    public boolean bold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }

    public boolean italic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }

    public boolean shadow() { return shadow; }
    public void setShadow(boolean shadow) { this.shadow = shadow; }

    public float scale() { return scale; }
    public void setScale(float scale) { this.scale = Math.max(0.1f, Math.min(10f, scale)); }

    /**
     * Текст, який реально показується гравцю з мовою {@code currentLang}
     * (напр. "uk_ua"), БЕЗ урахування LANG_KEY (те резолвиться рендер-шаром
     * через {@code Component.translatable}, бо потребує {@code I18n}). Для
     * PLAIN і MULTI_LANG цього достатньо для повного резолву.
     */
    public String resolvePlainOrMultiLang(String currentLang) {
        if (source == SourceType.MULTI_LANG) {
            if (translations.containsKey(currentLang)) return translations.get(currentLang);
            if (translations.containsKey(FALLBACK_LANG)) return translations.get(FALLBACK_LANG);
            for (String v : translations.values()) return v;
            return "";
        }
        return content; // PLAIN
    }

    /**
     * Санітизація перед збереженням на сервері: обрізає задовгі рядки,
     * прибирає керівні символи (кожен {@code GraffitiTextLine} — один
     * рядок, переноси рядків додаються як окремі елементи списку, не
     * всередині), і обмежує кількість перекладів у MULTI_LANG.
     */
    public void sanitize() {
        content = sanitizeOne(content, source == SourceType.LANG_KEY ? MAX_LANG_KEY_LEN : MAX_TEXT_LEN);

        if (translations.size() > MAX_TRANSLATIONS) {
            Map<String, String> trimmed = new LinkedHashMap<>();
            int i = 0;
            for (Map.Entry<String, String> e : translations.entrySet()) {
                if (i++ >= MAX_TRANSLATIONS) break;
                trimmed.put(e.getKey(), e.getValue());
            }
            translations = trimmed;
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : translations.entrySet()) {
            String lang = sanitizeOne(e.getKey(), MAX_LANG_CODE_LEN);
            if (lang.isEmpty()) continue;
            sanitized.put(lang, sanitizeOne(e.getValue(), MAX_TEXT_LEN));
        }
        translations = sanitized;

        color &= 0xFFFFFF;
        scale = Math.max(0.1f, Math.min(10f, scale));
    }

    private static String sanitizeOne(String s, int maxLen) {
        String c = s == null ? "" : s.replace("\n", " ").replace("\r", "");
        if (c.length() > maxLen) c = c.substring(0, maxLen);
        return c;
    }
}
