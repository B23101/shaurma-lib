package dev.shaurmalib.common.radio;

/**
 * Контекст одного виклику {@code RadioDialogManager.trigger(...)} (план,
 * п. 3.33) — узагальнення того, що в оригіналі snipers_shaurma було
 * неявним порядком виклику {@code broadcast(...)}/{@code send(...)} з
 * 20+ місць коду (фази режимів, event-менеджери), без єдиного поняття
 * "наскільки важлива ця репліка відносно тієї, що вже грає".
 * <p>
 * {@link #priority()} вищий за поточну активну репліку → нова репліка
 * перериває поточну негайно; нижчий або рівний → нова репліка
 * ігнорується (лишається чекати своєї черги природним шляхом, а не
 * ставиться в буфер — короткі командирські репліки не повинні
 * накопичуватись і "вистрілювати" з запізненням).
 */
public final class RadioTriggerContext {

    /** Стандартний пріоритет для більшості ігрових подій (airdrop, зона, kit-select тощо). */
    public static final int PRIORITY_NORMAL = 0;
    /** Для критичних оголошень (переможець, фінальна двійка) — завжди перериває звичайні репліки. */
    public static final int PRIORITY_HIGH = 10;

    private final int priority;
    private final String cooldownGroup;

    public RadioTriggerContext(int priority, String cooldownGroup) {
        this.priority = priority;
        this.cooldownGroup = cooldownGroup;
    }

    public static RadioTriggerContext normal() {
        return new RadioTriggerContext(PRIORITY_NORMAL, null);
    }

    public static RadioTriggerContext high() {
        return new RadioTriggerContext(PRIORITY_HIGH, null);
    }

    /**
     * Анти-спам групу — репліки з однаковим {@code cooldownGroup} не
     * повторюються, доки не спливе {@code cooldownTicks}, переданий у
     * {@code RadioDialogManager.trigger(...)}. {@code null} — без
     * обмеження (кожен виклик завжди програється, як у поточному коді
     * snipers для більшості реплік).
     */
    public String cooldownGroup() {
        return cooldownGroup;
    }

    public int priority() {
        return priority;
    }
}
