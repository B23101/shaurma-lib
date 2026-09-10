package dev.shaurmalib.common.radio;

/**
 * Один мовний варіант репліки радіо-диктора (план, п. 3.33) — 1:1
 * перенесення {@code RadioDialogConfig.LangEntry} snipers_shaurma.
 * <p>
 * {@code soundRef} — рядковий {@code ResourceLocation} ("namespace:path"),
 * не Minecraft-тип, щоб цей клас лишався у {@code lib-common} без жодного
 * {@code net.minecraft.*}/{@code net.minecraftforge.*} імпорту (Architecture
 * Sniffer, план п. 2.1) — сам рендер-шар (lib-forge) відповідає за
 * перетворення рядка в реальний {@code SoundEvent}/{@code ResourceLocation}.
 */
public final class RadioLangEntry {

    private final String text;
    private final String soundRef;
    private final int durationTicks;
    private final int holdTicks;

    public RadioLangEntry(String text, String soundRef, int durationTicks, int holdTicks) {
        this.text = text == null ? "" : text;
        this.soundRef = soundRef == null ? "" : soundRef;
        this.durationTicks = Math.max(1, durationTicks);
        this.holdTicks = Math.max(0, holdTicks);
    }

    public String text() {
        return text;
    }

    public String soundRef() {
        return soundRef;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public int holdTicks() {
        return holdTicks;
    }
}
