package dev.shaurmalib.forge.fx.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.shaurmalib.forge.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Розмиття екрана для екранних ефектів (порт EVBlurChannel): ВЛАСНИЙ {@link PostChain} на
 * {@code minecraft:shaders/post/blur.json}, не займає слот {@code GameRenderer.postEffect} — тому не
 * конфліктує з ванільними шейдерами (нудота, вода) і з post-ефектами режимів. Кілька джерел
 * (пульси, серцебиття) комбінуються максимумом.
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenFxBlur {

    private static final Logger LOG = LoggerFactory.getLogger("ShaurmaScreenFx");
    private static final ResourceLocation SHADER = new ResourceLocation("minecraft", "shaders/post/blur.json");
    private static final Map<String, Float> SOURCES = new HashMap<>();
    private static PostChain chain;
    private static int chainW = -1, chainH = -1;
    private static boolean loadFailed;

    private ScreenFxBlur() {}

    static void setSource(String source, float radius) {
        if (radius <= 0.01f) SOURCES.remove(source); else SOURCES.put(source, radius);
    }

    static void clearSources() { SOURCES.clear(); }

    /** Скинути шейдер після перезавантаження ресурсів. */
    static void invalidate() {
        if (chain != null) chain.close();
        chain = null;
        loadFailed = false;
        chainW = chainH = -1;
    }

    /** Перед рендером GUI (RenderGuiEvent.Pre). */
    static void render(float partialTick) {
        float radius = 0f;
        for (float v : SOURCES.values()) radius = Math.max(radius, v);
        if (radius <= 0.15f) return; // менше — візуально не відрізняється, а прохід дорогий
        PostChain pc = chain();
        if (pc == null) return;
        Minecraft mc = Minecraft.getInstance();
        // Стан GL як в оригіналі: без depth test, інакше повноекранний квад частково відсікається (смикання кадру).
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            mc.getMainRenderTarget().bindWrite(true);
            for (PostPass pass : ((PostChainAccessor) pc).shaurmaLib$getPasses()) {
                EffectInstance effect = pass.getEffect();
                Uniform u = effect == null ? null : effect.getUniform("Radius");
                if (u != null) u.set(radius);
            }
            pc.process(partialTick);
        } catch (Throwable t) {
            LOG.warn("[ScreenFx] blur process failed", t);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.clear(256, Minecraft.ON_OSX);
            RenderSystem.depthMask(true);
        }
    }

    private static PostChain chain() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
        if (chain != null) {
            if (w == chainW && h == chainH) return chain;
            chain.close();
            chain = null;
        }
        if (loadFailed && w == chainW && h == chainH) return null;
        try {
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), SHADER);
            chain.resize(w, h);
            loadFailed = false;
        } catch (Exception e) {
            LOG.warn("[ScreenFx] не завантажено {}: {}", SHADER, e.toString());
            loadFailed = true;
            chain = null;
        }
        chainW = w;
        chainH = h;
        return chain;
    }
}
