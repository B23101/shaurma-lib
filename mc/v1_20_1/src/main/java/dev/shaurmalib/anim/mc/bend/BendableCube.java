package dev.shaurmalib.anim.mc.bend;

/** Кеш розрізаної сітки на {@code ModelPart.Cube} ({@code MixinModelPartCubeBend}). */
public interface BendableCube {

    BendTemplate shaurma$bendTemplate();

    void shaurma$setBendTemplate(BendTemplate template);

    /** Кубоїд не вдалося розібрати (чужа геометрія) — не пробувати щокадру. */
    boolean shaurma$bendTemplateFailed();

    void shaurma$markBendTemplateFailed();
}
