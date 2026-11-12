package dev.shaurmalib.common.playeranim;

/**
 * Іменований пресет дії з предметом: <i>що</i> грати ({@link PoseSource}),
 * <i>на якому</i> шарі ({@link PoseLayerId}) і, опційно, яку динамічну правку
 * кісток застосувати ({@link BoneAdjustment.Provider}).
 * <p>
 * Консюмер (наприклад, maniacmod) реєструє пресети один раз при старті,
 * а потім лише запускає їх за іменем — і йому не треба знати, який шар чи
 * fade потрібен конкретній дії:
 * <pre>
 * PoseActionRegistry.register(PoseAction.of("maniac:flashlight",
 *     PoseSource.hold("maniac", "flashlight").withFade(4, 4),
 *     PoseLayerId.ITEM_ACTION));
 * PlayerPoseController.playAction(player, "maniac:flashlight");
 * </pre>
 *
 * @param name       унікальне ім'я, рекомендовано {@code modid:action}
 * @param source     що грати
 * @param layer      на якому шарі
 * @param adjustment опційна динамічна правка кісток; {@code null} — без правки
 */
public record PoseAction(String name, PoseSource source, PoseLayerId layer,
                         BoneAdjustment.Provider adjustment) {

    public PoseAction {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PoseAction.name не може бути порожнім");
        }
        if (source == null) {
            throw new IllegalArgumentException("PoseAction.source не може бути null (дія " + name + ")");
        }
        if (layer == null) {
            throw new IllegalArgumentException("PoseAction.layer не може бути null (дія " + name + ")");
        }
    }

    public static PoseAction of(String name, PoseSource source, PoseLayerId layer) {
        return new PoseAction(name, source, layer, null);
    }

    public PoseAction withAdjustment(BoneAdjustment.Provider provider) {
        return new PoseAction(name, source, layer, provider);
    }

    public boolean hasAdjustment() {
        return adjustment != null;
    }
}
