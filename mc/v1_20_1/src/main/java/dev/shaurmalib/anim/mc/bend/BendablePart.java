package dev.shaurmalib.anim.mc.bend;

/**
 * Згин, домішаний у {@code ModelPart} ({@code MixinModelPartBend}). Обертання нижнього сегмента
 * відносно верхнього — градуси, порядок ZYX, ті самі числа, що в кістці {@code *_forearm}/{@code *_shin}.
 *
 * Стан живе, поки його не скине {@code resetPose()}; {@code copyFrom()} копіює його разом із позою
 * (рукави/штани гравця, броня через {@code HumanoidModel.copyPropertiesTo}).
 */
public interface BendablePart {

    void shaurma$setBend(float rx, float ry, float rz);

    void shaurma$clearBend();

    /** Чи є ненульовий згин (інакше частина малюється ванільно). */
    boolean shaurma$hasBend();

    float shaurma$bendX();

    float shaurma$bendY();

    float shaurma$bendZ();
}
