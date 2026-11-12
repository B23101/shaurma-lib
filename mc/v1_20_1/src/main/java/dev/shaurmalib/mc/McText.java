package dev.shaurmalib.mc;

import dev.shaurmalib.platform.service.TextBridge;
import dev.shaurmalib.platform.text.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@link Text} ↔ {@link Component} для 1.20.1. */
public final class McText implements TextBridge {

    public static MutableComponent toComponent(Text t) {
        if (t == null) return Component.empty();
        MutableComponent c;
        if (t.content() instanceof Text.Translatable tr) {
            Object[] args = new Object[tr.args().size()];
            for (int i = 0; i < args.length; i++) args[i] = toComponent(tr.args().get(i));
            c = Component.translatable(tr.key(), args);
        } else {
            c = Component.literal(((Text.Literal) t.content()).value());
        }
        Text.Style s = t.style();
        Style st = Style.EMPTY.withBold(s.bold()).withItalic(s.italic()).withUnderlined(s.underlined())
                .withStrikethrough(s.strikethrough()).withObfuscated(s.obfuscated());
        if (s.rgb() != null) st = st.withColor(TextColor.fromRgb(s.rgb()));
        c.setStyle(st);
        for (Text sib : t.siblings()) c.append(toComponent(sib));
        return c;
    }

    public static Text fromComponent(Component c) {
        if (c == null) return Text.empty();
        Text t;
        if (c.getContents() instanceof TranslatableContents tr) {
            List<Text> args = new ArrayList<>();
            for (Object a : tr.getArgs()) args.add(a instanceof Component ac ? fromComponent(ac) : Text.literal(String.valueOf(a)));
            t = Text.translatable(tr.getKey(), args.toArray(Text[]::new));
        } else if (c.getContents() instanceof LiteralContents lit) {
            t = Text.literal(lit.text());
        } else {
            // інший вміст (score, selector, keybind, nbt): лишаємо видимий текст цього вузла
            StringBuilder sb = new StringBuilder();
            c.getContents().visit(part -> { sb.append(part); return Optional.empty(); });
            t = Text.literal(sb.toString());
        }
        Style st = c.getStyle();
        t = t.withStyle(new Text.Style(st.getColor() == null ? null : st.getColor().getValue(), st.isBold(), st.isItalic(),
                st.isUnderlined(), st.isStrikethrough(), st.isObfuscated()));
        for (Component sib : c.getSiblings()) t = t.append(fromComponent(sib));
        return t;
    }

    @Override public Object toNative(Text text) { return toComponent(text); }
    @Override public Text fromNative(Object nativeComponent) { return fromComponent((Component) nativeComponent); }
    @Override public String toJson(Text text) { return Component.Serializer.toJson(toComponent(text)); }
    @Override public Text fromJson(String json) { return fromComponent(Component.Serializer.fromJson(json)); }
}
