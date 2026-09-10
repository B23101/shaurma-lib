package dev.shaurmalib.common.overlay;

/**
 * Специфікація однієї нотифікації для {@code AlertNotificationSystem.push(...)}
 * (план, п. 3.4) — узагальнення того, що зараз розкидано по сигнатурах
 * {@code AlertNotificationOverlay.addNotification(String|Component, [int accentArgb])}
 * і {@code LootNotificationOverlay}: один record-подібний об'єкт замість
 * кількох перевантажень методу. Текст навмисно {@code String}, а не
 * {@code net.minecraft.network.chat.Component} — рендер-шар (lib-forge)
 * сам вирішує, чи парсити § -форматування, чи брати вже локалізований
 * рядок від споживача; сам record лишається в lib-common без
 * Minecraft-залежності (Architecture Sniffer, п. 2.1).
 */
public final class AlertSpec {

    private final String text;
    private final int accentArgb;
    private final String iconRef;
    private final String soundRef;
    private final OverlayTimings timings;

    public AlertSpec(String text, int accentArgb, String iconRef, String soundRef, OverlayTimings timings) {
        this.text = text;
        this.accentArgb = accentArgb;
        this.iconRef = iconRef;
        this.soundRef = soundRef;
        this.timings = timings != null ? timings : OverlayTimings.alertDefault();
    }

    /** Найпростіший випадок — лише текст, дефолтний акцент і дефолтні тайминги (як {@code addNotification(String)}). */
    public static AlertSpec of(String text) {
        return new AlertSpec(text, 0xFFE05252, null, null, OverlayTimings.alertDefault());
    }

    public static AlertSpec of(String text, int accentArgb) {
        return new AlertSpec(text, accentArgb, null, null, OverlayTimings.alertDefault());
    }

    public String text() {
        return text;
    }

    public int accentArgb() {
        return accentArgb;
    }

    /** ResourceLocation-рядок іконки, або {@code null} якщо запис без іконки. */
    public String iconRef() {
        return iconRef;
    }

    /** ResourceLocation-рядок звукового cue, або {@code null} якщо мод не хоче звуку на цю подію. */
    public String soundRef() {
        return soundRef;
    }

    public OverlayTimings timings() {
        return timings;
    }

    public AlertSpec withIcon(String iconRef) {
        return new AlertSpec(text, accentArgb, iconRef, soundRef, timings);
    }

    public AlertSpec withSound(String soundRef) {
        return new AlertSpec(text, accentArgb, iconRef, soundRef, timings);
    }

    public AlertSpec withTimings(OverlayTimings timings) {
        return new AlertSpec(text, accentArgb, iconRef, soundRef, timings);
    }
}
