package dev.shaurmalib.forge.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor-mixin для {@link PostChain} — 1:1 перенесення
 * {@code org.example.snipers_shaurma.core.mixin.PostChainAccessor}
 * (план, п. 3.23). {@code PostChain.passes} — приватне поле в офіційних
 * мапінгах 1.20.1, тому доступ до списку прохідів (наприклад щоб дістати
 * конкретний {@code Uniform} шейдера — актуально для майбутніх пресетів
 * ефекту типу blur, де параметр на кшталт радіуса розмиття треба міняти
 * рантайм) потребує accessor-mixin, а не рефлексію.
 * <p>
 * {@link dev.shaurmalib.forge.fx.ScreenEffectPostChain} сам по собі не
 * потребує доступу до окремих uniform-ів для базового монохром-пресету
 * (весь параметр "наскільки чорно-білим" зашитий у сам fragment shader
 * консюмера) — accessor лишається доступним про запас для консюмерів,
 * яким потрібен рантайм-контроль над конкретним post-pass (той самий
 * підхід, що {@code EVBlurChannel} використовував для {@code Uniform "Radius"}).
 */
@OnlyIn(Dist.CLIENT)
@Mixin(PostChain.class)
public interface PostChainAccessor {

    @Accessor("passes")
    List<PostPass> shaurmaLib$getPasses();
}
