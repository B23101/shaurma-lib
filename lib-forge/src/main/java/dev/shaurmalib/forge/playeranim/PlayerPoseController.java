package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.shaurmalib.common.playeranim.PoseLayerId;
import dev.shaurmalib.common.playeranim.PoseSource;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клієнтська обгортка над PlayerAnimationLib (KosmX) для кастомних поз
 * гравця (план, п. 3.24) — узагальнення {@code PlayerAnimDropBridge}
 * snipers_shaurma (єдина на весь оригінал точка дотику до PAL API) на
 * довільну кількість незалежних {@link PoseLayerId}-шарів, з переносом
 * ОБОХ задокументованих у оригіналі виправлень 1:1:
 * <ul>
 *   <li><b>Баг "FPS падає до 5 у одного гравця під час анімації"</b> —
 *       оригінал використовував {@code WeakHashMap<UUID, ModifierLayer>};
 *       GC міг прибрати запис у непередбачуваний момент, і наступний
 *       виклик створював ЩЕ ОДИН шар у {@code AnimationStack}, лишаючи
 *       старий фізично зареєстрованим (нічого не викликало
 *       {@code stack.removeLayer(...)} для загублених записів) —
 *       кількість "мертвих" шарів необмежено росла з кожним повторним
 *       використанням пози. Тут — звичайний {@code HashMap} (запис живе,
 *       доки ми самі його не приберемо), плюс явний {@link #cleanup}.</li>
 *   <li><b>Баг "анімація іноді не програється взагалі"</b> — оригінал
 *       викликав {@code play()} рівно один раз за подію; якщо PAL/ресурс-
 *       пак ще не встигли ініціалізуватись (гонка одразу після старту
 *       клієнта чи першого входу в матч), виклик тихо провалювався і
 *       ніхто не повторював спробу. Тут {@link #trigger} повертає
 *       {@code boolean} успіху — консюмер (як і оригінальний
 *       {@code DropAnimationHandler.beginTopPhase()}) планує ретраї
 *       протягом кількох тіків при {@code false}.</li>
 * </ul>
 * <p>
 * <b>НОВЕ виправлення — relog/respawn (баг, задокументований у
 * задачі, якого НЕ було в оригінальному {@code PlayerAnimDropBridge}):</b>
 * оригінал ключував свій {@code Map<UUID, LayerEntry>} по UUID гравця і
 * створював запис лише один раз через {@code computeIfAbsent} —
 * АЛЕ Minecraft/Forge пересоздає {@code AbstractClientPlayer}-екземпляр
 * при relog (вихід+вхід), зміні виміру чи певних respawn-сценаріях,
 * зберігаючи той самий UUID. {@code PlayerAnimationAccess.getPlayerAnimLayer(player)}
 * для НОВОГО екземпляра повертає НОВИЙ {@code AnimationStack} — але
 * стара {@code computeIfAbsent}-логіка бачила вже існуючий запис у мапі
 * (той самий UUID-ключ) і повертала СТАРИЙ {@code ModifierLayer},
 * зареєстрований у СТАРОМУ (вже нерендереному) {@code AnimationStack}.
 * Результат: рушій "мовчки" не падає, {@code trigger(...)} повертає
 * {@code true}, але анімація ніде не відображається, бо шар керує
 * стеком мертвого об'єкта-гравця, а не того, що зараз реально
 * рендериться.
 * <p>
 * Виправлення тут: кожен запис зберігає не лише {@code ModifierLayer}, а
 * й {@link AbstractClientPlayer}-екземпляр (через {@code WeakReference}
 * лише для порівняння ідентичності, не для утримання жвавості мапи —
 * сам запис лишається в звичайному {@code HashMap} з тих самих причин,
 * що й баг №1 вище), і {@link #layerFor} звіряє, чи
 * {@code PlayerAnimationAccess.getPlayerAnimLayer(player) == entry.stack}
 * (той самий {@code AnimationStack}-екземпляр) щоразу, а не лише при
 * першому створенні запису. Якщо стек не збігається — старий запис
 * вважається мертвим, шар зі старого стека НЕ видаляється explicit
 * (стек мертвого гравця найімовірніше вже недосяжний і сам піде під GC
 * разом з рештою старого {@code AbstractClientPlayer}), створюється
 * новий {@code ModifierLayer} у актуальному {@code AnimationStack}, і
 * якщо шар на момент relog був активний (гравець вийшов посеред пози) —
 * анімація одразу переносяться на новий шар, а не губиться до наступного
 * {@link #trigger}.
 */
public final class PlayerPoseController {

    private static final Logger LOGGER = LogManager.getLogger("PlayerPoseController");

    private PlayerPoseController() {}

    private static final class LayerEntry {
        final AnimationStack stack;
        final ModifierLayer<IAnimation> layer;
        /** Ідентичність гравця, для якого цей запис було створено — див. клас-докстрінг, relog-фікс. */
        final java.lang.ref.WeakReference<AbstractClientPlayer> ownerRef;
        /** Активне джерело на момент останнього trigger(...) — для переносу при relog. Null, якщо шар не грає. */
        volatile PoseSource activeSource;

        LayerEntry(AnimationStack stack, ModifierLayer<IAnimation> layer, AbstractClientPlayer owner) {
            this.stack = stack;
            this.layer = layer;
            this.ownerRef = new java.lang.ref.WeakReference<>(owner);
        }

        boolean ownedBy(AbstractClientPlayer player) {
            return ownerRef.get() == player;
        }
    }

    /** {uuid -> {layerId -> entry}}. Зовнішня мапа per-player, щоб cleanup(uuid) прибирав усі шари гравця одним проходом. */
    private static final Map<UUID, Map<PoseLayerId, LayerEntry>> LAYERS = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, KeyframeAnimation> ANIM_CACHE = new HashMap<>();

    /**
     * Запускає (або перезапускає) позу на вказаному шарі для гравця.
     * Idempotent і safe-to-retry — якщо PAL/ресурс ще не готові,
     * повертає {@code false}, консюмер сам вирішує ретраїти чи ні (той
     * самий контракт, що {@code PlayerAnimDropBridge.play(...)}
     * оригіналу).
     *
     * @return {@code true}, якщо анімацію застосовано; {@code false} —
     *         спробуйте ще раз за кілька тіків.
     */
    public static boolean trigger(AbstractClientPlayer player, PoseLayerId layerId, PoseSource source) {
        try {
            KeyframeAnimation anim = resolveAnimation(source);
            if (anim == null) return false;

            ModifierLayer<IAnimation> layer = layerFor(player, layerId);
            if (layer == null) return false;

            layer.setAnimation(new KeyframeAnimationPlayer(anim));
            markActive(player, layerId, source);
            return true;
        } catch (Throwable t) {
            // Не валимо клієнт через неспівпадіння версії PAL — камера/телепорт
            // консюмера відпрацюють навіть без анімації тіла (той самий підхід
            // до відмовостійкості, що в оригінальному PlayerAnimDropBridge).
            LOGGER.warn("PlayerPoseController.trigger() failed for layer {}", layerId, t);
            return false;
        }
    }

    /**
     * Знімає позу з шару (повертає гравця до ванільної/нижчих-пріоритетом
     * поз). Сам {@code ModifierLayer} зі стеку НЕ видаляється — той самий
     * навмисний вибір, що в оригіналі: гравець може повернутись до цієї ж
     * пози найближчим часом, дешевше лишити зареєстрований, порожній шар,
     * ніж видаляти й реєструвати заново щоразу.
     */
    public static void stop(AbstractClientPlayer player, PoseLayerId layerId) {
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.get(player.getUUID());
        if (perPlayer == null) return;
        LayerEntry entry = perPlayer.get(layerId);
        if (entry == null || !entry.ownedBy(player)) return;
        try {
            entry.layer.setAnimation(null);
        } catch (Throwable t) {
            LOGGER.warn("PlayerPoseController.stop() failed for layer {}", layerId, t);
        }
        entry.activeSource = null;
    }

    /** Знімає ВСІ активні шари гравця одразу — типово на смерть/зміну фази, коли жодна кастомна поза більше не актуальна. */
    public static void stopAll(AbstractClientPlayer player) {
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.get(player.getUUID());
        if (perPlayer == null) return;
        for (Map.Entry<PoseLayerId, LayerEntry> e : perPlayer.entrySet()) {
            if (e.getValue().ownedBy(player)) {
                stop(player, e.getKey());
            }
        }
    }

    /**
     * Остаточно прибирає всі шари гравця, що покинув сервер —
     * викликати з {@code ClientPlayerNetworkEvent.LoggingOut} (клієнтська
     * подія виходу; НЕ плутати з серверним {@code PlayerEvent.PlayerLoggedOutEvent},
     * якого оригінал {@code DropAnimationEvents} підписував замість цього
     * — див. клас-докстрінг щодо relog-бага: та підписка ніколи не
     * чистила клієнтський {@code PlayerAnimDropBridge.LAYERS} взагалі).
     * <p>
     * Без явного виклику relog-фікс у {@link #layerFor} однаково не дає
     * анімації "загубитись" (новий {@code AnimationStack} детектується і
     * підміняється), але запис зі старим мертвим {@code AnimationStack}
     * лишався б у мапі до природної відсутності посилань — цей виклик
     * прибирає його одразу, а не покладається лише на relog-детекцію.
     */
    public static void cleanup(UUID playerUuid) {
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.remove(playerUuid);
        if (perPlayer == null) return;
        for (LayerEntry entry : perPlayer.values()) {
            try {
                if (entry.stack != null) {
                    entry.stack.removeLayer(entry.layer);
                }
            } catch (Throwable t) {
                LOGGER.warn("PlayerPoseController.cleanup() failed to remove layer", t);
            }
        }
    }

    /**
     * Скидає кеш завантажених {@code KeyframeAnimation} — викликати на
     * {@code ResourceManagerReloadEvent} (F3+T), як
     * {@code PlayerAnimDropBridge.invalidateCache()} в оригіналі.
     */
    public static void invalidateAnimationCache() {
        ANIM_CACHE.clear();
    }

    /**
     * Чи зараз активна хоч якась поза на цьому шарі для гравця — консюмер
     * використовує це, щоб не дублювати {@link #trigger} щотік, поки
     * поза вже грає (той самий патерн, що {@code ItemCameraController.isActive()}).
     */
    public static boolean isActive(AbstractClientPlayer player, PoseLayerId layerId) {
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.get(player.getUUID());
        if (perPlayer == null) return false;
        LayerEntry entry = perPlayer.get(layerId);
        return entry != null && entry.ownedBy(player) && entry.activeSource != null;
    }

    /**
     * Повертає (створюючи за потреби) {@code ModifierLayer} для пари
     * (гравець, шар) — центральна точка relog-фіксу. Кожен виклик, не
     * лише перший, звіряє ідентичність поточного {@code AnimationStack}
     * гравця з тим, у якому зареєстровано наявний запис (див.
     * клас-докстрінг).
     */
    private static ModifierLayer<IAnimation> layerFor(AbstractClientPlayer player, PoseLayerId layerId) {
        UUID uuid = player.getUUID();
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.computeIfAbsent(uuid, id -> new EnumMap<>(PoseLayerId.class));

        AnimationStack currentStack = PlayerAnimationAccess.getPlayerAnimLayer(player);
        if (currentStack == null) return null;

        LayerEntry existing = perPlayer.get(layerId);
        if (existing != null && existing.stack == currentStack && existing.ownedBy(player)) {
            // Стек і власник збігаються з поточним рендереним гравцем — той самий
            // живий запис, повторно використовуємо шар без перестворення.
            return existing.layer;
        }

        // Або запису ще не було, або він належить старому (relog/respawn-
        // пересозданому) AnimationStack/AbstractClientPlayer-екземпляру —
        // в обох випадках реєструємо новий шар у актуальному стеку.
        PoseSource carryOver = existing != null ? existing.activeSource : null;

        ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        currentStack.addAnimLayer(layerId.defaultPriority(), layer);
        LayerEntry fresh = new LayerEntry(currentStack, layer, player);
        perPlayer.put(layerId, fresh);

        if (existing != null && existing.stack != null && existing.stack != currentStack) {
            // Best-effort: прибираємо шар зі старого стека, якщо він ще технічно
            // досяжний (звичайний relog у межах тієї ж сесії клієнта) — не
            // критично, якщо не вийде: старий AbstractClientPlayer і його стек
            // все одно більше не рендеряться і природно підуть під GC.
            try {
                existing.stack.removeLayer(existing.layer);
            } catch (Throwable ignored) {
                // старий стек уже міг бути невалідним — нічого критичного.
            }
        }

        if (carryOver != null) {
            // Гравець вийшов/пересоздався ПОСЕРЕД активної пози — переносимо її
            // на новий шар одразу, а не чекаємо наступного явного trigger(...)
            // від консюмера (консюмер міг і не знати, що relog стався).
            KeyframeAnimation anim = resolveAnimation(carryOver);
            if (anim != null) {
                layer.setAnimation(new KeyframeAnimationPlayer(anim));
                fresh.activeSource = carryOver;
            }
        }

        return layer;
    }

    private static void markActive(AbstractClientPlayer player, PoseLayerId layerId, PoseSource source) {
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.get(player.getUUID());
        if (perPlayer == null) return;
        LayerEntry entry = perPlayer.get(layerId);
        if (entry != null) entry.activeSource = source;
    }

    private static KeyframeAnimation resolveAnimation(PoseSource source) {
        if (source == null || source.resourceId() == null) return null;
        ResourceLocation id = new ResourceLocation(source.namespace(), source.resourceId());
        KeyframeAnimation cached = ANIM_CACHE.get(id);
        if (cached != null) return cached;
        try {
            // Реєстр анімацій PAL заповнюється автоматично під час
            // ресурс-релоаду з assets/<ns>/player_animations/<name>.json —
            // той самий контракт, що в PlayerAnimDropBridge.getAnimation().
            KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(id);
            if (anim != null) ANIM_CACHE.put(id, anim);
            return anim;
        } catch (Throwable t) {
            LOGGER.warn("Не вдалось завантажити позу {} — перевір шлях ресурсу і версію PAL", id, t);
            return null;
        }
    }
}
