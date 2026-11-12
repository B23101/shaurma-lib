package dev.shaurmalib.common.overlay;

/**
 * Одноразовий центрований "punch-in" запис (план, п. 3.4) — узагальнення
 * {@code MultiKillOverlay} (streak-повідомлення: Double Kill/Rampage) і
 * {@code GameStartAnnouncementOverlay} (обидва мали ідентичний цикл
 * scale 1.3→1.0 з easeOutBack, коротке утримання з легкою пульсацією,
 * потім fade-out через easeInQuad — відрізнялись лише текстом/кольором).
 * <p>
 * На відміну від {@link TimedOverlayEntry} (стек, що накопичується), тут
 * завжди максимум один активний запис — новий виклик {@code show(...)}
 * замінює попередній, як і в оригінальному {@code MultiKillOverlay.show()}.
 * Ця заміна — відповідальність рушія ({@code AnimatedCountdownSystem}/
 * {@code AlertNotificationSystem} у lib-forge), клас тут лише описує
 * фізику одного конкретного запису.
 */
public final class PunchInEntry {

    private final String title;
    private final String subtitle;
    private final int accentArgb;
    private final long createdAtMs;
    private final int punchMs;
    private final int holdMs;
    private final int fadeMs;

    public PunchInEntry(String title, String subtitle, int accentArgb, int punchMs, int holdMs, int fadeMs) {
        this(title, subtitle, accentArgb, punchMs, holdMs, fadeMs, System.currentTimeMillis());
    }

    public PunchInEntry(String title, String subtitle, int accentArgb,
                         int punchMs, int holdMs, int fadeMs, long createdAtMs) {
        this.title = title;
        this.subtitle = subtitle;
        this.accentArgb = accentArgb;
        this.punchMs = punchMs;
        this.holdMs = holdMs;
        this.fadeMs = fadeMs;
        this.createdAtMs = createdAtMs;
    }

    /** 1:1 тайминги оригінального {@code MultiKillOverlay} (PUNCH=260, HOLD=1100, FADE=400). */
    public static PunchInEntry multiKillDefault(String title, String subtitle, int accentArgb) {
        return new PunchInEntry(title, subtitle, accentArgb, 260, 1100, 400);
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public int accentArgb() {
        return accentArgb;
    }

    public long ageMs(long nowMs) {
        return nowMs - createdAtMs;
    }

    public int totalMs() {
        return punchMs + holdMs + fadeMs;
    }

    public boolean isExpired(long nowMs) {
        return ageMs(nowMs) > totalMs();
    }

    /**
     * Масштаб (напр. 1.3 → 1.0 → легка синусоїдна пульсація на hold → 1.0)
     * — формула 1:1 з {@code MultiKillOverlay.render}.
     */
    public float scaleAt(long nowMs) {
        long age = ageMs(nowMs);
        if (age < punchMs) {
            float t = (float) age / punchMs;
            float eased = Easing.easeOutBack(t);
            return 1.3f - 0.3f * eased;
        } else if (age < punchMs + holdMs) {
            float holdT = (float) (age - punchMs) / holdMs;
            return 1.0f + 0.02f * (float) Math.sin(holdT * Math.PI * 3);
        }
        return 1.0f;
    }

    /** Альфа 0..1 — punch-in fade-in, повна непрозорість на hold, easeInQuad fade-out наприкінці. */
    public float alphaAt(long nowMs) {
        long age = ageMs(nowMs);
        float alpha;
        if (age < punchMs) {
            float t = (float) age / punchMs;
            alpha = Math.min(1f, Easing.easeOutBack(t) + 0.3f);
        } else if (age < punchMs + holdMs) {
            alpha = 1f;
        } else {
            float fadeT = (float) (age - punchMs - holdMs) / fadeMs;
            alpha = 1f - Easing.easeInQuad(fadeT);
        }
        return Math.max(0f, Math.min(1f, alpha));
    }
}
