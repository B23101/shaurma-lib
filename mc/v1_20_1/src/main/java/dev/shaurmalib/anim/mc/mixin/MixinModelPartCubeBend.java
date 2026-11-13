package dev.shaurmalib.anim.mc.mixin;

import dev.shaurmalib.anim.mc.bend.BendTemplate;
import dev.shaurmalib.anim.mc.bend.BendableCube;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Кеш розрізаної сітки кубоїда для згину (будується при першому згині, живе разом із моделлю). */
@Mixin(ModelPart.Cube.class)
public abstract class MixinModelPartCubeBend implements BendableCube {

    @Unique private BendTemplate shaurma$template;
    @Unique private boolean shaurma$templateFailed;

    @Override public BendTemplate shaurma$bendTemplate() { return shaurma$template; }
    @Override public void shaurma$setBendTemplate(BendTemplate template) { shaurma$template = template; }
    @Override public boolean shaurma$bendTemplateFailed() { return shaurma$templateFailed; }
    @Override public void shaurma$markBendTemplateFailed() { shaurma$templateFailed = true; }
}
