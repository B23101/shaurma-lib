package dev.shaurmalib.common.playeranim;

/**
 * Іменований шар кастомної пози гравця (план, п. 3.24) — заміна практики
 * оригіналу, де кожна нова "фіча з позою" (дроп-падіння, зіплайн, лежача
 * смерть) писала власний статичний клас-міст з власним
 * {@code Map<UUID, ModifierLayer<IAnimation>>} (див. приклад
 * {@code PlayerAnimDropBridge} snipers_shaurma) — кожен такий клас-міст
 * окремо і незалежно міг наступити на той самий клас багів (втрата
 * запису при relog/respawn, невидалений шар при виході), бо ніхто не
 * ділив спільну інфраструктуру.
 * <p>
 * {@link PlayerPoseController} (lib-forge) тримає РІВНО ОДИН
 * {@code ModifierLayer} на кожен {@link PoseLayerId} на кожного гравця
 * — конкретна поза (drop-фолл, зіплайн, лежача смерть) лише підмінює
 * анімацію ВСЕРЕДИНІ вже зареєстрованого шару свого id, а не створює
 * новий шар щоразу. {@code priority} визначає порядок накладання
 * (вищий число — вище в {@code AnimationStack}, ближче до фінальної
 * пози), той самий сенс, що {@code LAYER_PRIORITY} в оригіналі.
 */
public enum PoseLayerId {

    /** Поза падіння під час дроп-фази (SC) — заміна {@code PlayerAnimDropBridge}. */
    DROP_FALL(1000),

    /** Поза очікування/сидіння в літаку перед дропом. */
    AIRPLANE_SEAT(900),

    /** Поза катання на зіплайні (тіло, не лише рука моделі — див. окремо {@code MixinZiplineHumanoidModel}). */
    ZIPLINE_RIDE(950),

    /** Лежача поза після смерті (death-cam супровід). */
    DEATH_LIE(1100),

    /** Вільний слот для продуктової пози консюмера, що не підпадає під жоден з готових id вище. */
    CUSTOM(500);

    private final int priority;

    PoseLayerId(int priority) {
        this.priority = priority;
    }

    /**
     * Пріоритет у {@code AnimationStack} (вищий = ближче до фінальної
     * пози). Консюмер, що використовує {@link #CUSTOM}, може
     * перевизначити фактичний пріоритет при реєстрації через
     * {@code PlayerPoseController} — це поле лише дефолт.
     */
    public int defaultPriority() {
        return priority;
    }
}
