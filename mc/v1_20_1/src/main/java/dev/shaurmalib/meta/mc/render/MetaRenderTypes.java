package dev.shaurmalib.meta.mc.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Шари рендера мета-двигуна. Наслідуємо RenderType лише заради доступу до захищених станів.
 * <ul>
 *   <li>{@link #GLOW} — аури: адитивне світіння БЕЗ запису глибини (з глибиною півпрозорий стовп аури
 *       працював як невидима стіна й ховав усе, що малювалось після нього);</li>
 *   <li>{@link #PLUS} — плюсики хілу: непрозорі «світні» кубики з глибиною, без освітлення світу.</li>
 * </ul>
 */
public final class MetaRenderTypes extends RenderType {

    public static final RenderType GLOW = RenderType.create("shaurma_meta_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .createCompositeState(false));

    public static final RenderType PLUS = RenderType.create("shaurma_meta_plus",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1024, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private MetaRenderTypes() {
        super("shaurma_meta_dummy", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> { }, () -> { });
    }
}
