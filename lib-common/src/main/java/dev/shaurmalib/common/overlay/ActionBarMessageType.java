package dev.shaurmalib.common.overlay;

/**
 * Тип actionbar-повідомлення (план, п. 3.4 + 3.10, {@code ActionBarMessageSystem})
 * — узагальнення {@code StatusMessagePacket.Type} (в оригіналі лише
 * {@code ERROR}/{@code ACTIVATION}/{@code INFO}). {@code SUCCESS} і
 * {@code COOLDOWN} додано, бо план явно називає їх окремими типами
 * ("actionbar з анімаціями помилки" — SUCCESS/ERROR/COOLDOWN/INFO),
 * а в оригіналі COOLDOWN-подібні повідомлення просто малювались як INFO
 * без окремого візуального акценту.
 * <p>
 * Тип навмисно не прив'язаний до кітів — це загальний actionbar-рушій,
 * яким може скористатись будь-яка підсистема мода (кулдаун здібності,
 * помилка команди, підтвердження дії), а не лише система кітів snipers.
 * <p>
 * {@code shakeOnShow} — чи анімація появи має "тремтіти" (1:1 поведінка
 * ERROR у {@code StatusMessageOverlay}: синусоїдний зсув XY, що згасає за
 * {@code SHAKE_MS}), а не плавно fade-in, як решта типів.
 */
public enum ActionBarMessageType {

    SUCCESS(0xFF55FF55, false),
    ERROR(0xFFFF5555, true),
    COOLDOWN(0xFFFFC04D, false),
    INFO(0xFFFFFFFF, false);

    private final int defaultColorArgb;
    private final boolean shakeOnShow;

    ActionBarMessageType(int defaultColorArgb, boolean shakeOnShow) {
        this.defaultColorArgb = defaultColorArgb;
        this.shakeOnShow = shakeOnShow;
    }

    public int defaultColorArgb() {
        return defaultColorArgb;
    }

    public boolean shakeOnShow() {
        return shakeOnShow;
    }
}
