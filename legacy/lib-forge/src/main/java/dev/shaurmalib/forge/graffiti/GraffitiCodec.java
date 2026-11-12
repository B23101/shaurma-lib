package dev.shaurmalib.forge.graffiti;

import dev.shaurmalib.common.graffiti.GraffitiContentMode;
import dev.shaurmalib.common.graffiti.GraffitiSpec;
import dev.shaurmalib.common.graffiti.GraffitiTextLine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NBT та мережева (FriendlyByteBuf) серіалізація {@link GraffitiSpec} і
 * {@link GraffitiTextLine} (план, п. 3.14). Винесено в {@code lib-forge}
 * окремо від чистих дата-класів у {@code lib-common}: сам
 * {@code GraffitiSpec}/{@code GraffitiTextLine} не імпортує ні
 * {@code net.minecraft.nbt}, ні {@code net.minecraft.network}, лишаючись
 * простим POJO, який зручно передавати між шарами (включно з майбутнім
 * canvas-редактором на клієнті, де NBT/мережа не потрібні взагалі).
 * <p>
 * Формат 1:1 відповідає {@code GraffitiBlockEntity.saveAdditional/load} і
 * {@code GraffitiTextLine.write/read} snipers_shaurma, включно зі
 * зворотною сумісністю зі старим форматом ({@code "LangKey": boolean}
 * до появи {@code MULTI_LANG}).
 */
public final class GraffitiCodec {

    private GraffitiCodec() {}

    // ── GraffitiSpec × NBT ──────────────────────────────────────────────

    public static void saveSpec(CompoundTag tag, GraffitiSpec spec) {
        tag.putString("ContentMode", spec.contentMode().name());
        tag.putString("ImageName", spec.imageName());
        tag.putFloat("Scale", spec.scale());
        tag.putFloat("OffsetX", spec.offsetX());
        tag.putFloat("OffsetY", spec.offsetY());
        tag.putFloat("Rotation", spec.rotationDeg());

        ListTag linesTag = new ListTag();
        for (GraffitiTextLine line : spec.textLines()) linesTag.add(saveTextLine(line));
        tag.put("TextLines", linesTag);
    }

    public static void loadSpec(CompoundTag tag, GraffitiSpec spec) {
        GraffitiContentMode mode;
        try {
            mode = tag.contains("ContentMode")
                    ? GraffitiContentMode.valueOf(tag.getString("ContentMode"))
                    : GraffitiContentMode.IMAGE;
        } catch (IllegalArgumentException e) {
            mode = GraffitiContentMode.IMAGE;
        }
        spec.setContentMode(mode);
        spec.setImageName(tag.getString("ImageName"));
        spec.setScale(tag.contains("Scale") ? tag.getFloat("Scale") : 1.0f);
        spec.setOffsets(tag.getFloat("OffsetX"), tag.getFloat("OffsetY"));
        spec.setRotationDeg(tag.getFloat("Rotation"));

        List<GraffitiTextLine> lines = new ArrayList<>();
        ListTag linesTag = tag.getList("TextLines", 10); // 10 = CompoundTag id
        for (int i = 0; i < linesTag.size() && lines.size() < GraffitiSpec.MAX_TEXT_LINES; i++) {
            lines.add(loadTextLine(linesTag.getCompound(i)));
        }
        spec.setTextLines(lines);
    }

    // ── GraffitiTextLine × NBT ──────────────────────────────────────────

    public static CompoundTag saveTextLine(GraffitiTextLine line) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Source", line.source().name());
        tag.putString("Content", line.content());
        ListTag transTag = new ListTag();
        for (Map.Entry<String, String> e : line.translations().entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Lang", e.getKey());
            t.putString("Text", e.getValue());
            transTag.add(t);
        }
        tag.put("Translations", transTag);
        tag.putInt("Color", line.color());
        tag.putBoolean("Bold", line.bold());
        tag.putBoolean("Italic", line.italic());
        tag.putBoolean("Shadow", line.shadow());
        tag.putFloat("Scale", line.scale());
        return tag;
    }

    public static GraffitiTextLine loadTextLine(CompoundTag tag) {
        GraffitiTextLine line = new GraffitiTextLine();
        try {
            line.setSource(tag.contains("Source")
                    ? GraffitiTextLine.SourceType.valueOf(tag.getString("Source"))
                    : legacySource(tag));
        } catch (IllegalArgumentException e) {
            line.setSource(GraffitiTextLine.SourceType.PLAIN);
        }
        line.setContent(tag.getString("Content"));

        Map<String, String> translations = new LinkedHashMap<>();
        ListTag transTag = tag.getList("Translations", 10); // 10 = CompoundTag id
        for (int i = 0; i < transTag.size() && translations.size() < GraffitiTextLine.MAX_TRANSLATIONS; i++) {
            CompoundTag t = transTag.getCompound(i);
            translations.put(t.getString("Lang"), t.getString("Text"));
        }
        line.setTranslations(translations);

        line.setColor(tag.contains("Color") ? tag.getInt("Color") : GraffitiTextLine.DEFAULT_COLOR);
        line.setBold(tag.getBoolean("Bold"));
        line.setItalic(tag.getBoolean("Italic"));
        line.setShadow(!tag.contains("Shadow") || tag.getBoolean("Shadow"));
        line.setScale(tag.contains("Scale") ? tag.getFloat("Scale") : 1.0f);
        return line;
    }

    /** Сумісність зі старим форматом ({@code "LangKey": boolean}) для NBT,
     *  збереженого до появи MULTI_LANG. */
    private static GraffitiTextLine.SourceType legacySource(CompoundTag tag) {
        return tag.getBoolean("LangKey") ? GraffitiTextLine.SourceType.LANG_KEY : GraffitiTextLine.SourceType.PLAIN;
    }

    // ── GraffitiTextLine × мережа ────────────────────────────────────────

    public static void writeTextLine(FriendlyByteBuf buf, GraffitiTextLine line) {
        buf.writeEnum(line.source());
        buf.writeUtf(line.content(), line.source() == GraffitiTextLine.SourceType.LANG_KEY
                ? GraffitiTextLine.MAX_LANG_KEY_LEN : GraffitiTextLine.MAX_TEXT_LEN);
        buf.writeVarInt(line.translations().size());
        for (Map.Entry<String, String> e : line.translations().entrySet()) {
            buf.writeUtf(e.getKey(), GraffitiTextLine.MAX_LANG_CODE_LEN);
            buf.writeUtf(e.getValue(), GraffitiTextLine.MAX_TEXT_LEN);
        }
        buf.writeInt(line.color());
        buf.writeBoolean(line.bold());
        buf.writeBoolean(line.italic());
        buf.writeBoolean(line.shadow());
        buf.writeFloat(line.scale());
    }

    public static GraffitiTextLine readTextLine(FriendlyByteBuf buf) {
        GraffitiTextLine line = new GraffitiTextLine();
        line.setSource(buf.readEnum(GraffitiTextLine.SourceType.class));
        line.setContent(buf.readUtf(GraffitiTextLine.MAX_TEXT_LEN));
        int n = buf.readVarInt();
        Map<String, String> translations = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String lang = buf.readUtf(GraffitiTextLine.MAX_LANG_CODE_LEN);
            String text = buf.readUtf(GraffitiTextLine.MAX_TEXT_LEN);
            if (translations.size() < GraffitiTextLine.MAX_TRANSLATIONS) translations.put(lang, text);
        }
        line.setTranslations(translations);
        line.setColor(buf.readInt());
        line.setBold(buf.readBoolean());
        line.setItalic(buf.readBoolean());
        line.setShadow(buf.readBoolean());
        line.setScale(buf.readFloat());
        return line;
    }
}
