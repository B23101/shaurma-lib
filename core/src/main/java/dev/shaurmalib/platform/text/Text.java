package dev.shaurmalib.platform.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Версійно-незалежний rich text (замінює {@code Component}/{@code MutableComponent}).
 * Імутабельний. Адаптер версії перетворює його у рідний Component
 * (для 1.20.1/1.21.1/26.x відрізняються Style, ClickEvent, HoverEvent тощо).
 */
public final class Text {
    private static final Text EMPTY = new Text(new Literal(""), Style.PLAIN, List.of());

    public sealed interface Content permits Literal, Translatable {}
    public record Literal(String value) implements Content {}
    public record Translatable(String key, List<Text> args) implements Content {
        public Translatable { args = List.copyOf(args); }
    }

    /** {@code rgb == null} — колір успадковується. Колір зберігається як 0xRRGGBB. */
    public record Style(Integer rgb, boolean bold, boolean italic, boolean underlined,
                        boolean strikethrough, boolean obfuscated) {
        public static final Style PLAIN = new Style(null, false, false, false, false, false);
    }

    private final Content content;
    private final Style style;
    private final List<Text> siblings;

    private Text(Content content, Style style, List<Text> siblings) {
        this.content = content;
        this.style = style;
        this.siblings = siblings;
    }

    public static Text empty() { return EMPTY; }
    public static Text literal(String s) { return new Text(new Literal(Objects.requireNonNull(s)), Style.PLAIN, List.of()); }
    public static Text translatable(String key, Text... args) {
        return new Text(new Translatable(key, Arrays.asList(args)), Style.PLAIN, List.of());
    }

    public Content content() { return content; }
    public Style style() { return style; }
    public List<Text> siblings() { return siblings; }

    public Text withColor(int rgb) {
        return new Text(content, new Style(rgb & 0xFFFFFF, style.bold, style.italic, style.underlined,
                style.strikethrough, style.obfuscated), siblings);
    }
    /** Повний стиль одним викликом (адаптери версій переносять стиль рідного компонента). */
    public Text withStyle(Style s) {
        return new Text(content, Objects.requireNonNull(s), siblings);
    }

    public Text bold(boolean v) {
        return new Text(content, new Style(style.rgb, v, style.italic, style.underlined,
                style.strikethrough, style.obfuscated), siblings);
    }
    public Text italic(boolean v) {
        return new Text(content, new Style(style.rgb, style.bold, v, style.underlined,
                style.strikethrough, style.obfuscated), siblings);
    }
    public Text append(Text child) {
        List<Text> l = new ArrayList<>(siblings);
        l.add(Objects.requireNonNull(child));
        return new Text(content, style, Collections.unmodifiableList(l));
    }

    /** Плоский текст без стилів (для логів, тестів, пошуку в історії чату). Translatable → ключ. */
    public String plain() {
        StringBuilder sb = new StringBuilder();
        if (content instanceof Literal l) sb.append(l.value());
        else if (content instanceof Translatable t) sb.append(t.key());
        for (Text s : siblings) sb.append(s.plain());
        return sb.toString();
    }

    @Override public String toString() { return "Text[" + plain() + "]"; }
}
