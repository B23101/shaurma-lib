package dev.shaurmalib.common.graffiti;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Чиста дата-модель стану одного графіті-блоку (план, п. 3.14) —
 * узагальнення полів {@code GraffitiBlockEntity} snipers_shaurma,
 * винесене з самого {@code BlockEntity} (Minecraft/Forge-специфічного),
 * щоб цей стан можна було передавати між шарами (NBT-кодек, мережевий
 * пакет, клієнтський кеш, майбутній canvas-редактор) як звичайний POJO,
 * незалежно один від одного.
 * <p>
 * Поля:
 * <ul>
 *   <li>{@code contentMode} — {@link GraffitiContentMode#IMAGE} чи
 *       {@link GraffitiContentMode#TEXT};</li>
 *   <li>{@code imageName} — ім'я PNG-файлу (без шляху), напр.
 *       "tag_01.png"; порожній рядок = не вибрано;</li>
 *   <li>{@code textLines} — рядки тексту для TEXT-режиму;</li>
 *   <li>{@code scale} — масштаб (базовий розмір графіті — 3x3 блоки при
 *       {@code scale=1.0});</li>
 *   <li>{@code offsetX}/{@code offsetY} — зміщення картинки в площині
 *       стіни (у блоках), +X = вправо, +Y = вгору;</li>
 *   <li>{@code rotationDeg} — додатковий поворот картинки навколо своєї
 *       нормалі (у градусах).</li>
 * </ul>
 * Сторона стіни (`side`/`FACING`) навмисно НЕ входить сюди — вона
 * належить {@code BlockState}, не даним контенту, так само як і в
 * оригіналі {@code GraffitiBlockEntity.getSide()} читає її з блок-стейту,
 * а не тримає власне поле.
 */
public final class GraffitiSpec {

    public static final int MAX_TEXT_LINES = 12;

    private GraffitiContentMode contentMode = GraffitiContentMode.IMAGE;
    private String imageName = "";
    private final List<GraffitiTextLine> textLines = new ArrayList<>();
    private float scale = 1.0f;
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;
    private float rotationDeg = 0.0f;

    public GraffitiContentMode contentMode() { return contentMode; }

    public void setContentMode(GraffitiContentMode mode) {
        this.contentMode = mode == null ? GraffitiContentMode.IMAGE : mode;
    }

    public String imageName() { return imageName; }

    public void setImageName(String imageName) {
        this.imageName = imageName == null ? "" : imageName;
    }

    /** Незмінна копія поточних рядків тексту — виклик коду ззовні не може
     *  випадково мутувати внутрішній стан напряму. */
    public List<GraffitiTextLine> textLines() {
        return Collections.unmodifiableList(textLines);
    }

    /** Повністю замінює список рядків тексту (копіює й санітизує кожен
     *  рядок, щоб не тримати посилання на об'єкти виклика). Обмежено
     *  {@link #MAX_TEXT_LINES} рядками — решта відкидається. */
    public void setTextLines(List<GraffitiTextLine> lines) {
        textLines.clear();
        if (lines != null) {
            for (GraffitiTextLine line : lines) {
                if (textLines.size() >= MAX_TEXT_LINES) break;
                GraffitiTextLine copy = line.copy();
                copy.sanitize();
                textLines.add(copy);
            }
        }
    }

    public float scale() { return scale; }
    public void setScale(float scale) { this.scale = clamp(scale, 0.25f, 6.0f); }

    public float offsetX() { return offsetX; }
    public float offsetY() { return offsetY; }

    public void setOffsets(float offsetX, float offsetY) {
        this.offsetX = clamp(offsetX, -8f, 8f);
        this.offsetY = clamp(offsetY, -8f, 8f);
    }

    public float rotationDeg() { return rotationDeg; }

    public void setRotationDeg(float rotationDeg) {
        float r = rotationDeg % 360f;
        if (r < 0) r += 360f;
        this.rotationDeg = r;
    }

    /** direction: +1 за годинниковою, -1 проти; крок 15°, як в оригіналі. */
    public void rotate(int direction) {
        setRotationDeg(this.rotationDeg + direction * 15f);
    }

    /** Чи потрібна мережева передача PNG-байтів для поточного стану
     *  (лише для IMAGE-режиму з непорожнім іменем файлу). */
    public boolean needsImageTransfer() {
        return contentMode == GraffitiContentMode.IMAGE && !imageName.isEmpty();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
