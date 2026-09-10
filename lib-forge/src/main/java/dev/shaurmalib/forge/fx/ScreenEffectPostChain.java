package dev.shaurmalib.forge.fx;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.shaurmalib.common.fx.PostChainEffectCauses;
import dev.shaurmalib.common.fx.PostChainEffectSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Узагальнений рушій повноекранних post-chain ефектів (план, п. 3.23) —
 * заміна {@code BlackAndWhiteScreenEffect} snipers_shaurma одним
 * централізованим реєстром замість окремого {@code @Mod.EventBusSubscriber}-
 * класу на кожен новий ефект. Консюмер реєструє {@link PostChainEffectSpec}
 * один раз через {@link #register}, потім лише керує причинами активації
 * через {@link dev.shaurmalib.common.fx.PostChainEffectCauses} — рушій сам
 * підписаний на {@code RenderGuiEvent.Pre} (один хук на всі зареєстровані
 * ефекти) і обробляє кожен активний {@code PostChain}, ресайз вікна й
 * ресурс-релоад.
 * <p>
 * <b>Архітектурне рішення, перенесене з оригіналу без змін</b> (див.
 * докстрінг {@code BlackAndWhiteScreenEffect} — задокументований раніше
 * баг у попередній версії через спільний {@code GameRenderer.postEffect}):
 * кожен ефект отримує СВІЙ незалежний {@link PostChain}, прив'язаний
 * напряму до {@code Minecraft.getMainRenderTarget()}, і обробляється
 * вручну на {@code RenderGuiEvent.Pre} — жодного {@code GameRenderer
 * .loadEffect()/shutdownEffect()} і жодної рефлексії для читання чужого
 * стану. Коли ефект не активний — {@code process()} просто не
 * викликається, "не зникає" стає неможливим за конструкцією (немає
 * чужого спільного стану, який треба вгадувати). Кілька ефектів можуть
 * бути активні одночасно — кожен малює свій незалежний повноекранний
 * quad поверх попереднього результату.
 * <p>
 * Пресети (монохром — перший, glitch/vignette — можливі наступні, план
 * згадує їх як "поряд" кандидати) — це лише різні {@link PostChainEffectSpec}
 * з різним {@code namespace}/{@code path}, самі fragment/vertex шейдери й program-json
 * лишаються ресурсами консюмера (той самий принцип, що {@code ShaurmaConfigTree}:
 * дані завжди постачає споживач, рушій лише виконує механіку).
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenEffectPostChain {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/fx");

    private ScreenEffectPostChain() {}

    private static final Map<String, PostChainEffectSpec> registeredSpecs = new LinkedHashMap<>();
    private static final Map<String, ChainState> chainStates = new LinkedHashMap<>();

    private static final class ChainState {
        PostChain chain;
        int width = -1;
        int height = -1;
        boolean loadFailed = false;
    }

    /**
     * Реєструє новий post-chain ефект — викликати один раз (типово в
     * client-setup консюмера), перед першим {@link PostChainEffectCauses#activate}
     * для цього {@code effectId}. Повторна реєстрація того самого
     * {@code effectId} перезаписує спек (наприклад при hot-reload
     * налаштувань), не створює дублікат хука.
     */
    public static void register(PostChainEffectSpec spec) {
        registeredSpecs.put(spec.effectId(), spec);
        chainStates.putIfAbsent(spec.effectId(), new ChainState());
    }

    /**
     * Централізований рендер-хук — один на всі зареєстровані ефекти
     * (заміна окремого {@code @SubscribeEvent onRenderGuiPre} на кожен
     * ефект). Консюмер підключає це один раз через
     * {@code MinecraftForge.EVENT_BUS.addListener(ScreenEffectPostChain::onRenderGuiPre)}
     * або еквівалентний {@code @Mod.EventBusSubscriber}.
     */
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (registeredSpecs.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean processedAny = false;
        for (PostChainEffectSpec spec : registeredSpecs.values()) {
            if (!PostChainEffectCauses.isActive(spec.effectId())) continue;

            PostChain postChain = getOrCreateChain(spec);
            if (postChain == null) continue;

            if (!processedAny) {
                // Той самий порядок GL-стану, що в оригіналі: до
                // RenderGuiEvent.Pre цього ж кадру міг рендеритись світ з
                // увімкненим depth test — PostChain.process() малює
                // повноекранний quad, і увімкнений depth test спотворив би
                // кадр. Виконуємо це один раз перед першим активним ефектом,
                // не перед кожним.
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                processedAny = true;
            }

            // ФІКС: в оригіналі bindWrite/process сидять у try/finally, де
            // finally ЗАВЖДИ повертає render target — навіть якщо process()
            // кинув виняток. У циклі з кількома ефектами це критично: без
            // per-effect finally виняток одного ефекту лишав би framebuffer
            // у "чужому" прив'язаному стані для наступного ефекту в тому ж
            // кадрі (postChain.process() наступного ефекту почав би роботу
            // з неправильно прив'язаним render target). Тому bindWrite
            // відновлюється відразу після КОЖНОГО process(), не лише
            // наприкінці циклу.
            try {
                mc.getMainRenderTarget().bindWrite(true);
                postChain.process(event.getPartialTick());
            } catch (Throwable t) {
                LOGGER.warn("[ScreenEffectPostChain] process() failed для '{}'", spec.effectId(), t);
            } finally {
                mc.getMainRenderTarget().bindWrite(true);
            }
        }

        if (processedAny) {
            // PostChain.process() лишає depth-стан вимкненим — повертаємо
            // depthMask/depth-buffer один раз наприкінці, після всіх
            // ефектів (render target вже гарантовано коректний завдяки
            // finally вище на кожній ітерації).
            RenderSystem.clear(256 /* GL_DEPTH_BUFFER_BIT */, Minecraft.ON_OSX);
            RenderSystem.depthMask(true);
        }
    }

    /** Перестворює PostChain при зміні розміру вікна; після невдалої
     *  спроби не намагається знову на кадрах з тим самим розміром (щоб не
     *  спамити лог, якщо ассет реально відсутній) — та сама поведінка, що
     *  в оригіналі, тепер per-effect. */
    private static PostChain getOrCreateChain(PostChainEffectSpec spec) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getMainRenderTarget().width;
        int h = mc.getMainRenderTarget().height;

        ChainState state = chainStates.computeIfAbsent(spec.effectId(), id -> new ChainState());

        if (state.chain != null) {
            if (w == state.width && h == state.height) {
                return state.chain;
            }
            state.chain.close();
            state.chain = null;
        }

        if (state.loadFailed && w == state.width && h == state.height) {
            return null;
        }

        try {
            // Двоаргументний конструктор — точна відповідність оригіналу
            // (new ResourceLocation("snipers_shaurma", "shaders/post/bw_screen.json")),
            // без ризику мовчазної підстановки namespace "minecraft" при
            // помилково переданому шляху без роздільника ':'.
            ResourceLocation shaderLoc = new ResourceLocation(spec.namespace(), spec.path());
            state.chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(),
                    mc.getMainRenderTarget(), shaderLoc);
            state.chain.resize(w, h);
            state.loadFailed = false;
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("[ScreenEffectPostChain] Не вдалося завантажити '{}' ({}:{}): {}",
                    spec.effectId(), spec.namespace(), spec.path(), e.toString());
            state.loadFailed = true;
            state.chain = null;
        }
        state.width = w;
        state.height = h;
        return state.chain;
    }

    /**
     * Реєструє resource-reload listener (F3+T / зміна ресурспаків) для
     * ВСІХ зареєстрованих ефектів одразу — консюмер викликає це один раз
     * з {@code RegisterClientReloadListenersEvent}. Позначає всі поточні
     * chain-и недійсними — вони лениво перестворюються на наступному
     * кадрі, коли знадобиться (та сама поведінка, що в оригіналі).
     */
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((PreparableReloadListener) new SimplePreparableReloadListener<Object>() {
            @Override
            protected Object prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Object object, ResourceManager manager, ProfilerFiller profiler) {
                for (ChainState state : chainStates.values()) {
                    if (state.chain != null) {
                        state.chain.close();
                        state.chain = null;
                    }
                    state.loadFailed = false;
                    state.width = -1;
                    state.height = -1;
                }
            }
        });
    }
}
