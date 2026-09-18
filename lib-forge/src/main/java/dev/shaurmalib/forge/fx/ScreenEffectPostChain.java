package dev.shaurmalib.forge.fx;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.shaurmalib.common.fx.PostChainEffectCauses;
import dev.shaurmalib.common.fx.PostChainEffectSpec;
import dev.shaurmalib.common.fx.PostChainFadeSpec;
import dev.shaurmalib.forge.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
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
 * стану. Коли ефект не активний і його інтенсивність вже спала до 0 —
 * {@code process()} просто не викликається. Кілька ефектів можуть
 * бути активні одночасно — кожен малює свій незалежний повноекранний
 * quad поверх попереднього результату.
 * <p>
 * <b>Плавний перехід</b> (план: "перемикач з плавним виходом і появою",
 * напр. затемнення локації): {@link #register(PostChainEffectSpec, dev.shaurmalib.common.fx.PostChainFadeSpec)}
 * з ненульовими {@code fadeInMs}/{@code fadeOutMs} — рушій рахує
 * інтенсивність 0..1 щокадру незалежно від
 * {@link dev.shaurmalib.common.fx.PostChainEffectCauses#isActive}, тому
 * після деактивації ефект ще кілька кадрів дограє fade-out замість
 * миттєвого зникнення, і передає це значення шейдеру консюмера як uniform
 * {@code float Intensity}.
 * <p>
 * Пресети (монохром — перший, glitch/vignette — можливі наступні, план
 * згадує їх як "поряд" кандидати) — це лише різні {@link PostChainEffectSpec}
 * з різним {@code namespace}/{@code path}, самі fragment/vertex шейдери й program-json
 * лишаються ресурсами консюмера (той самий принцип, що {@code ShaurmaConfigTree}:
 * дані завжди постачає споживач, рушій лише виконує механіку).
 * <p>
 * <b>Виняток — вбудоване затемнення локації</b> ({@link #enableDarkness()}/
 * {@link #disableDarkness()}): консюмеру не треба ні власного шейдера, ні
 * {@link #register}, ні власного {@link PostChainEffectSpec} — це готовий
 * пресет із шейдер-асетами в namespace самої бібліотеки
 * ({@code assets/shaurma_lib/shaders/post/darkness.json}), керований одним
 * прапорцем. На відміну від {@code WorldTintOverlay} (напівпрозорий
 * прямокутник поверх готової картинки), тут кожен піксель кадру
 * МНОЖИТЬСЯ на коефіцієнт яскравості ({@code color.rgb *= factor} у
 * фрагментному шейдері) — тому це "картинка сама стає темнішою", а не
 * "чорна плівка зверху": деталі не просвічують крізь неї на високій
 * інтенсивності.
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenEffectPostChain {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/fx");

    private ScreenEffectPostChain() {}

    /**
     * effectId вбудованого пресету "справжнього" затемнення (план: "перемикач
     * з плавним виходом і появою... реальне затемнення картинки, не оверлей").
     * Шейдер-ассети — ресурс самої бібліотеки
     * ({@code assets/shaurma_lib/shaders/post/darkness.json} +
     * {@code program/darkness.json/.vsh/.fsh}), не консюмера: на відміну від
     * {@link #register}, консюмеру не треба приносити жодного власного
     * asset-файлу — лише керувати {@link #enableDarkness}/{@link #disableDarkness}.
     * Множить яскравість кадру ({@code color.rgb *= factor}), а не блендить
     * з чорним кольором — деталі не "просвічують" крізь темряву.
     */
    private static final String DARKNESS_EFFECT_ID = "shaurma_lib:darkness";
    private static final PostChainEffectSpec DARKNESS_SPEC =
            new PostChainEffectSpec(DARKNESS_EFFECT_ID, "shaurma_lib", "shaders/post/darkness.json");
    /** Причина активації, яку використовують {@link #enableDarkness}/{@link #disableDarkness}. */
    private static final String DARKNESS_REASON = "flag";
    private static volatile PostChainFadeSpec darknessFade = PostChainFadeSpec.symmetric(700);
    private static volatile boolean darknessRegistered = false;

    private static final Map<String, PostChainEffectSpec> registeredSpecs = new LinkedHashMap<>();
    private static final Map<String, ChainState> chainStates = new LinkedHashMap<>();
    private static final Map<String, PostChainFadeSpec> fadeSpecs = new LinkedHashMap<>();
    private static final Map<String, FadeState> fadeStates = new LinkedHashMap<>();

    private static final class ChainState {
        PostChain chain;
        int width = -1;
        int height = -1;
        boolean loadFailed = false;
    }

    /**
     * Поточний прогрес плавного переходу для одного ефекту (план:
     * "перемикач з плавним виходом і появою"). {@code intensity} — те
     * значення 0..1, що передається шейдеру як uniform {@code "Intensity"}
     * (див. {@link #applyIntensityUniform}); консюмер множить його на колір
     * затемнення у фрагментному шейдері — тому "тінт стає темнішим", а не
     * різко вмикається/вимикається.
     */
    private static final class FadeState {
        float intensity;
        long lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * Реєструє новий post-chain ефект — викликати один раз (типово в
     * client-setup консюмера), перед першим {@link PostChainEffectCauses#activate}
     * для цього {@code effectId}. Повторна реєстрація того самого
     * {@code effectId} перезаписує спек (наприклад при hot-reload
     * налаштувань), не створює дублікат хука.
     * <p>
     * Перемикається миттєво ({@link PostChainFadeSpec#instant()}) — щоб
     * отримати плавний вихід/появу (напр. "затемнення локації"), реєструй
     * через {@link #register(PostChainEffectSpec, PostChainFadeSpec)}.
     */
    public static void register(PostChainEffectSpec spec) {
        register(spec, PostChainFadeSpec.instant());
    }

    /**
     * Те саме, що {@link #register(PostChainEffectSpec)}, але з плавним
     * переходом інтенсивності: {@code fade.fadeInMs()} на появу при
     * {@link PostChainEffectCauses#activate}, {@code fade.fadeOutMs()} на
     * зникнення при {@link PostChainEffectCauses#deactivate}. Фрагментний
     * шейдер консюмера читає прогрес через uniform {@code float Intensity}
     * (0..1) — напр. {@code gl_FragColor = mix(SourceColor, DarkColor, Intensity)}
     * для ефекту затемнення "картинка стає темнішою", а не суцільна плашка.
     */
    public static void register(PostChainEffectSpec spec, PostChainFadeSpec fade) {
        if (fade == null) {
            throw new IllegalArgumentException("PostChainFadeSpec не може бути null — передай instant().");
        }
        registeredSpecs.put(spec.effectId(), spec);
        chainStates.putIfAbsent(spec.effectId(), new ChainState());
        fadeSpecs.put(spec.effectId(), fade);
        fadeStates.putIfAbsent(spec.effectId(), new FadeState());
    }

    /**
     * Опційно налаштовує тайминги плавного переходу вбудованого пресету
     * затемнення ПЕРЕД першим {@link #enableDarkness()} — типово в
     * client-setup консюмера. Якщо не викликати, діє дефолт
     * {@link PostChainFadeSpec#symmetric(int) symmetric(700)}. Виклик після
     * того, як пресет вже зареєстровано (тобто після першого
     * enable/disableDarkness), просто оновлює тайминги для наступних
     * переходів.
     */
    public static void setDarknessFade(PostChainFadeSpec fade) {
        if (fade == null) {
            throw new IllegalArgumentException("PostChainFadeSpec не може бути null.");
        }
        darknessFade = fade;
        if (darknessRegistered) {
            fadeSpecs.put(DARKNESS_EFFECT_ID, fade);
        }
    }

    /**
     * Вмикає (якщо ще не активний) вбудований пресет "справжнього"
     * затемнення локації — план: "буде темна локація... затемнення, те
     * зображення стає темнішим... перемикач з плавним виходом цього режиму
     * і появою". Це просто прапор: жодних власних шейдер-асетів чи
     * {@link #register} з боку консюмера не потрібно — механіка й ресурси
     * вже вшиті в бібліотеку (namespace {@code shaurma_lib}).
     * <p>
     * Безпечно викликати повторно, поки ефект вже увімкнений — ідемпотентно
     * (як і {@link PostChainEffectCauses#activate}, на якому це збудовано).
     * Кілька незалежних систем консюмера можуть викликати
     * {@code enableDarkness()}/{@code disableDarkness()} одночасно з різних
     * місць — див. {@link #enableDarkness(String)} для власного reason-key,
     * якщо потрібно розрізняти причини активації.
     */
    public static void enableDarkness() {
        enableDarkness(DARKNESS_REASON);
    }

    /**
     * Те саме, що {@link #enableDarkness()}, але з власним reason-key
     * замість дефолтного {@code "flag"} — використовуй, коли кілька
     * незалежних систем консюмера (напр. "темна локація" і окремо "ефект
     * сліпоти") мають вмикати той самий вбудований ефект незалежно одна від
     * одної: {@link PostChainEffectCauses} тримає ефект активним, поки хоч
     * одна причина не знята, тому дві системи не гасять темряву одна за
     * одну передчасно.
     */
    public static void enableDarkness(String reason) {
        ensureDarknessRegistered();
        PostChainEffectCauses.activate(DARKNESS_EFFECT_ID, reason);
    }

    /** Знімає причину {@code "flag"} — еквівалент {@link #disableDarkness(String)} з дефолтним reason-key. */
    public static void disableDarkness() {
        disableDarkness(DARKNESS_REASON);
    }

    /**
     * Знімає конкретний reason-key вбудованого затемнення. Ефект плавно
     * згасає за {@code fadeOutMs} лише коли ОСТАННЯ активна причина знята —
     * якщо інша система досі тримає {@code enableDarkness(otherReason)},
     * темрява лишається.
     */
    public static void disableDarkness(String reason) {
        ensureDarknessRegistered();
        PostChainEffectCauses.deactivate(DARKNESS_EFFECT_ID, reason);
    }

    /** {@code true}, якщо вбудоване затемнення зараз активне (хоч би одна причина). */
    public static boolean isDarknessEnabled() {
        return PostChainEffectCauses.isActive(DARKNESS_EFFECT_ID);
    }

    /**
     * Аварійне скидання всіх причин вбудованого затемнення одразу (той самий
     * момент, коли консюмер типово скидає інші клієнтські ефекти — вихід з
     * гри/дисконнект/респавн), щоб жодна забута причина не тримала екран
     * темним у наступній сесії/на іншому сервері. Наступний рендер-кадр
     * після цього почне fade-out з поточної інтенсивності до 0.
     */
    public static void resetDarkness() {
        PostChainEffectCauses.clearAll(DARKNESS_EFFECT_ID);
    }

    /** Лениво реєструє вбудований darkness-пресет при першому зверненні до нього. */
    private static void ensureDarknessRegistered() {
        if (darknessRegistered) {
            return;
        }
        register(DARKNESS_SPEC, darknessFade);
        darknessRegistered = true;
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

        long now = System.currentTimeMillis();
        boolean processedAny = false;
        for (PostChainEffectSpec spec : registeredSpecs.values()) {
            float intensity = updateIntensity(spec.effectId(), now);
            if (intensity <= 0f) continue;

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
                applyIntensityUniform(postChain, intensity);
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

    /**
     * Рахує поточну інтенсивність ефекту з урахуванням {@link PostChainFadeSpec}
     * і повертає її. Викликається щокадру для кожного зареєстрованого
     * ефекту, незалежно від {@link PostChainEffectCauses#isActive} — саме
     * тому fade-out дограє до кінця: після деактивації рушій ще кілька
     * кадрів бачить {@code intensity > 0} і продовжує викликати
     * {@code process()}, лише зменшуючи силу ефекту, а не обриваючи його.
     */
    private static float updateIntensity(String effectId, long now) {
        FadeState state = fadeStates.computeIfAbsent(effectId, id -> new FadeState());
        PostChainFadeSpec fade = fadeSpecs.getOrDefault(effectId, PostChainFadeSpec.instant());
        boolean active = PostChainEffectCauses.isActive(effectId);

        long elapsed = Math.max(0, now - state.lastUpdateMs);
        state.lastUpdateMs = now;

        if (active) {
            int fadeInMs = fade.fadeInMs();
            if (fadeInMs <= 0) {
                state.intensity = 1f;
            } else {
                state.intensity = Math.min(1f, state.intensity + elapsed / (float) fadeInMs);
            }
        } else {
            int fadeOutMs = fade.fadeOutMs();
            if (fadeOutMs <= 0) {
                state.intensity = 0f;
            } else {
                state.intensity = Math.max(0f, state.intensity - elapsed / (float) fadeOutMs);
            }
        }
        return state.intensity;
    }

    /**
     * Передає поточну інтенсивність шейдеру консюмера як uniform
     * {@code float Intensity} (0..1), якщо такий uniform визначений у
     * program-json ефекту. {@link EffectInstance#safeGetUniform} ніколи не
     * повертає {@code null} — для незнайденого імені він дає безпечну
     * заглушку ("dummy"), виклик {@code set(...)} на якій просто нічого не
     * робить, тому консюмери, чий шейдер не читає {@code Intensity} (напр.
     * старий монохром-пресет без цього uniform-а), нічим не ризикують.
     */
    private static void applyIntensityUniform(PostChain postChain, float intensity) {
        for (PostPass pass : ((PostChainAccessor) postChain).shaurmaLib$getPasses()) {
            EffectInstance effect = pass.getEffect();
            if (effect == null) continue;
            effect.safeGetUniform("Intensity").set(intensity);
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
                // Не рахувати паузу reload-у як "минулий час" fade-у —
                // інакше наступний кадр після довгого F3+T стрибнув би
                // intensity одразу на 1/0 замість плавного продовження.
                long now = System.currentTimeMillis();
                for (FadeState fadeState : fadeStates.values()) {
                    fadeState.lastUpdateMs = now;
                }
            }
        });
    }
}
