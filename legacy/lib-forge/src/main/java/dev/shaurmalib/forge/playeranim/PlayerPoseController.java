package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.shaurmalib.common.playeranim.BoneAdjustment;
import dev.shaurmalib.common.playeranim.PoseAction;
import dev.shaurmalib.common.playeranim.PoseActionRegistry;
import dev.shaurmalib.common.playeranim.PoseLayerId;
import dev.shaurmalib.common.playeranim.PoseSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
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
 * що й баг №1 вище), і {@link #entryFor} звіряє, чи
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
 * <p>
 * <b>Рушій анімацій (v3).</b> Уся PAL-логіка одного шару (fade-in/out,
 * пре-емптивне завершення one-shot, чистка модифікаторів, динамічна правка
 * кісток) живе в {@link PoseLayerRuntime}; цей клас лишається тонкою
 * обгорткою "гравець → шар" + relog-фікс. Стан шару тікається
 * <b>ззовні</b> — {@link #tickAll()} з {@code ClientTickEvent(END)}, бо PAL
 * не тікає неактивні шари, а чистити треба саме тоді.
 * <p>
 * Третя особа працює автоматично: {@code PlayerModelMixin} PAL застосовує
 * {@code AnimationStack} кожного {@code AbstractClientPlayer}, тож поза
 * гравця видна всім клієнтам без власного мережевого пакета (умова —
 * shaurma-lib + PAL встановлені в усіх клієнтів).
 */
public final class PlayerPoseController {

    private static final Logger LOGGER = LogManager.getLogger("PlayerPoseController");

    private PlayerPoseController() {}

    private static final class LayerEntry {
        final AnimationStack stack;
        final PoseLayerRuntime runtime;
        /** Ідентичність гравця, для якого цей запис було створено — див. клас-докстрінг, relog-фікс. */
        final java.lang.ref.WeakReference<AbstractClientPlayer> ownerRef;

        LayerEntry(AnimationStack stack, PoseLayerRuntime runtime, AbstractClientPlayer owner) {
            this.stack = stack;
            this.runtime = runtime;
            this.ownerRef = new java.lang.ref.WeakReference<>(owner);
        }

        boolean ownedBy(AbstractClientPlayer player) {
            return ownerRef.get() == player;
        }

        ModifierLayer<IAnimation> layer() {
            return runtime.layer();
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
     * <p>
     * Fade-in береться з {@link PoseSource#fadeInTicks()}; якщо шар уже
     * щось показує (навіть посеред fade-out), перехід неперервний — нова
     * поза проявляється поверх того, що видно зараз.
     *
     * @return {@code true}, якщо анімацію застосовано; {@code false} —
     *         спробуйте ще раз за кілька тіків.
     */
    public static boolean trigger(AbstractClientPlayer player, PoseLayerId layerId, PoseSource source) {
        try {
            KeyframeAnimation anim = resolveAnimation(source);
            if (anim == null) return false;

            LayerEntry entry = entryFor(player, layerId);
            if (entry == null) return false;

            entry.runtime.start(anim, source, true);
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
     * Знімає позу з шару: плавно за {@link PoseSource#fadeOutTicks()}
     * активної пози (або миттєво, якщо 0). Сам {@code ModifierLayer} зі
     * стеку НЕ видаляється — той самий навмисний вибір, що в оригіналі:
     * гравець може повернутись до цієї ж пози найближчим часом, дешевше
     * лишити зареєстрований порожній шар, ніж реєструвати заново щоразу.
     * <p>
     * Під час fade-out {@link #isActive} вже {@code false} — тож консюмер
     * може одразу запустити наступну позу, не чекаючи кінця згасання.
     */
    public static void stop(AbstractClientPlayer player, PoseLayerId layerId) {
        LayerEntry entry = existingEntry(player, layerId);
        if (entry == null) return;
        try {
            entry.runtime.requestStop();
        } catch (Throwable t) {
            LOGGER.warn("PlayerPoseController.stop() failed for layer {}", layerId, t);
            hardStopQuietly(entry);
        }
    }

    /** Знімає ВСІ активні шари гравця плавно — типово на зміну фази, коли жодна кастомна поза більше не актуальна. */
    public static void stopAll(AbstractClientPlayer player) {
        stopAll(player, false);
    }

    /**
     * @param hard {@code true} — миттєво, без fade і разом із динамічними
     *             правками кісток (смерть, телепорт, зміна виміру), коли
     *             плавне згасання виглядало б недоречно.
     */
    public static void stopAll(AbstractClientPlayer player, boolean hard) {
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.get(player.getUUID());
        if (perPlayer == null) return;
        for (LayerEntry entry : perPlayer.values()) {
            if (!entry.ownedBy(player)) continue;
            if (hard) {
                hardStopQuietly(entry);
            } else {
                try {
                    entry.runtime.requestStop();
                } catch (Throwable t) {
                    LOGGER.warn("PlayerPoseController.stopAll() failed", t);
                    hardStopQuietly(entry);
                }
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
     * Без явного виклику relog-фікс у {@link #entryFor} однаково не дає
     * анімації "загубитись" (новий {@code AnimationStack} детектується і
     * підміняється), але запис зі старим мертвим {@code AnimationStack}
     * лишався б у мапі до природної відсутності посилань — цей виклик
     * прибирає його одразу, а не покладається лише на relog-детекцію.
     */
    public static void cleanup(UUID playerUuid) {
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.remove(playerUuid);
        if (perPlayer == null) return;
        for (LayerEntry entry : perPlayer.values()) {
            dispose(entry);
        }
    }

    /**
     * Чи поза на цьому шарі запитана й не знімається. Під час fade-out —
     * {@code false} (консюмер може запускати наступну позу); чи щось ще
     * видно в шарі — {@link #isVisible}. Консюмер використовує це, щоб не
     * дублювати {@link #trigger} щотік, поки поза вже грає (той самий
     * патерн, що {@code ItemCameraController.isActive()}).
     */
    public static boolean isActive(AbstractClientPlayer player, PoseLayerId layerId) {
        LayerEntry entry = existingEntry(player, layerId);
        return entry != null && entry.runtime.isActive();
    }

    /** Чи щось ще видно в шарі: поза грає або згасає. */
    public static boolean isVisible(AbstractClientPlayer player, PoseLayerId layerId) {
        LayerEntry entry = existingEntry(player, layerId);
        return entry != null && entry.runtime.isVisible();
    }

    // ───────────────────────── іменовані дії ─────────────────────────

    /**
     * Запускає зареєстровану {@link PoseAction} за ім'ям: її шар, fade і (за
     * наявності) динамічну правку кісток. Правка ставиться <b>до</b> старту
     * пози, щоб з'являтись плавно разом із нею.
     *
     * @return {@code false}, якщо дії з таким іменем нема або PAL ще не
     *         готовий (як {@link #trigger})
     */
    public static boolean playAction(AbstractClientPlayer player, String actionName) {
        PoseAction action = PoseActionRegistry.get(actionName).orElse(null);
        if (action == null) {
            LOGGER.warn("PlayerPoseController.playAction: дію '{}' не зареєстровано", actionName);
            return false;
        }
        try {
            if (action.hasAdjustment()) {
                LayerEntry entry = entryFor(player, action.layer());
                if (entry == null) return false;
                entry.runtime.setAdjustment(action.adjustment());
            }
        } catch (Throwable t) {
            LOGGER.warn("PlayerPoseController.playAction: правку кісток дії '{}' не застосовано", actionName, t);
        }
        return trigger(player, action.layer(), action.source());
    }

    /** Плавно знімає шар дії (fade-out з її {@link PoseSource}). */
    public static void stopAction(AbstractClientPlayer player, String actionName) {
        PoseActionRegistry.get(actionName).ifPresent(a -> stop(player, a.layer()));
    }

    // ─────────────────── динамічна правка кісток (опційно) ───────────────────

    /**
     * Опційна додаткова правка кісток поверх шару (нахил за поточним
     * {@code getXRot()} тощо) — <b>не вмикається автоматично</b>. Якщо
     * достатньо намалювати нахил у {@code .json} ({@code body}/{@code
     * torso}), цей метод не потрібен. Повторний виклик із тим самим
     * провайдером — no-op, тож безпечно викликати щотік. Для плавної появи
     * викликайте <b>до</b> {@link #trigger}. Правка живе одну "сесію" пози:
     * скидається, коли поза завершується або її знято жорстко.
     *
     * @return {@code false}, якщо PAL ще не готовий
     */
    public static boolean applyBoneAdjustment(AbstractClientPlayer player, PoseLayerId layerId,
                                              BoneAdjustment.Provider provider) {
        try {
            LayerEntry entry = entryFor(player, layerId);
            if (entry == null) return false;
            entry.runtime.setAdjustment(provider);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("PlayerPoseController.applyBoneAdjustment() failed for layer {}", layerId, t);
            return false;
        }
    }

    public static void clearBoneAdjustment(AbstractClientPlayer player, PoseLayerId layerId) {
        LayerEntry entry = existingEntry(player, layerId);
        if (entry == null) return;
        try {
            entry.runtime.clearAdjustment();
        } catch (Throwable t) {
            LOGGER.warn("PlayerPoseController.clearBoneAdjustment() failed for layer {}", layerId, t);
        }
    }

    // ─────────────── камера першої особи (опційно) ───────────────

    private static volatile boolean cameraFollowEnabled = true;

    /**
     * Глобальний вимикач стеження камери за головою (типово {@code true}, але
     * саме по собі нічого не робить: камеру чіпають лише пози з
     * {@code PoseSource.withCameraFollow(k != 0)}). Мод, якому ця функція не
     * потрібна (або гравець вимкнув її в конфігу), ставить {@code false} —
     * тоді камера не рухається за жодною позою, хоч би що було в її джерелі.
     */
    public static void setCameraFollowEnabled(boolean enabled) {
        cameraFollowEnabled = enabled;
    }

    public static boolean isCameraFollowEnabled() {
        return cameraFollowEnabled;
    }

    /**
     * Сумарний зсув камери гравця від усіх його шарів, що просили стеження:
     * {@code {pitch, yaw, roll}} у <b>радіанах</b>, або {@code null}, якщо
     * зсуву нема (не вмикали, вимкнено глобально, шари порожні). Викликається
     * з {@link PlayerPoseEvents} для власного гравця в першій особі.
     */
    public static float[] cameraOffset(AbstractClientPlayer player, float partialTick) {
        if (!cameraFollowEnabled) return null;
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.get(player.getUUID());
        if (perPlayer == null) return null;
        float x = 0, y = 0, z = 0;
        boolean any = false;
        for (LayerEntry entry : perPlayer.values()) {
            if (!entry.ownedBy(player)) continue;
            try {
                dev.kosmx.playerAnim.core.util.Vec3f v = entry.runtime.cameraOffset(partialTick);
                if (v == null) continue;
                x += v.getX(); y += v.getY(); z += v.getZ();
                any = true;
            } catch (Throwable t) {
                LOGGER.debug("PlayerPoseController.cameraOffset() пропущено для шару", t);
            }
        }
        return any ? new float[] {x, y, z} : null;
    }

    // ───────────────────────────── тік ─────────────────────────────

    /**
     * Тікає стан усіх шарів усіх гравців: авто-завершення one-shot,
     * пре-емптивний fade-out, чистка. Викликається з
     * {@code ClientTickEvent(END)} ({@link PlayerPoseEvents}); консюмеру
     * викликати не треба.
     * <p>
     * На паузі одиночної гри тік пропускається: {@code Player.tick()} не
     * виконується, PAL не тікає стек, і наш лічильник побіг би вперед за
     * анімацією. Записи, чий екземпляр гравця вже зібрано GC, прибираються
     * тут же — без цього {@code LAYERS} ріс би вічно для гравців, що
     * пішли, не викликавши {@link #cleanup}.
     */
    public static void tickAll() {
        if (LAYERS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.isPaused()) return;

        Iterator<Map.Entry<UUID, Map<PoseLayerId, LayerEntry>>> players = LAYERS.entrySet().iterator();
        while (players.hasNext()) {
            Map<PoseLayerId, LayerEntry> perPlayer = players.next().getValue();
            boolean anyAlive = false;
            for (LayerEntry entry : perPlayer.values()) {
                AbstractClientPlayer owner = entry.ownerRef.get();
                if (owner == null) {
                    continue;       // екземпляр зібрано GC — його стек теж мертвий
                }
                anyAlive = true;
                if (owner.isRemoved()) {
                    // Вивантажений/пересоздаваний гравець: не тікаємо, але й не знищуємо
                    // запис — позу ще можна перенести на новий екземпляр (relog-фікс).
                    continue;
                }
                try {
                    entry.runtime.tick();
                } catch (Throwable t) {
                    LOGGER.warn("PoseLayerRuntime.tick() failed — шар скинуто", t);
                    hardStopQuietly(entry);
                }
            }
            if (!anyAlive) {
                for (LayerEntry entry : perPlayer.values()) dispose(entry);
                players.remove();
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

    /** Скільки гравців зараз відстежується (для тестів витоку; у продакшні не потрібно). */
    static int debugTrackedPlayers() {
        return LAYERS.size();
    }

    private static LayerEntry existingEntry(AbstractClientPlayer player, PoseLayerId layerId) {
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.get(player.getUUID());
        if (perPlayer == null) return null;
        LayerEntry entry = perPlayer.get(layerId);
        return entry != null && entry.ownedBy(player) ? entry : null;
    }

    /**
     * Повертає (створюючи за потреби) запис для пари (гравець, шар) —
     * центральна точка relog-фіксу. Кожен виклик, не лише перший, звіряє
     * ідентичність поточного {@code AnimationStack} гравця з тим, у якому
     * зареєстровано наявний запис (див. клас-докстрінг).
     */
    private static LayerEntry entryFor(AbstractClientPlayer player, PoseLayerId layerId) {
        UUID uuid = player.getUUID();
        Map<PoseLayerId, LayerEntry> perPlayer = LAYERS.computeIfAbsent(uuid, id -> new EnumMap<>(PoseLayerId.class));

        AnimationStack currentStack = PlayerAnimationAccess.getPlayerAnimLayer(player);
        if (currentStack == null) return null;

        LayerEntry existing = perPlayer.get(layerId);
        if (existing != null && existing.stack == currentStack && existing.ownedBy(player)) {
            return existing; // той самий живий запис
        }

        // Або запису ще не було, або він належить старому (relog/respawn-
        // пересозданому) AnimationStack/AbstractClientPlayer-екземпляру —
        // в обох випадках реєструємо новий шар у актуальному стеку.
        PoseSource carryOver = existing != null ? existing.runtime.activeSource() : null;
        BoneAdjustment.Provider carryAdjustment = existing != null ? existing.runtime.adjustmentProvider() : null;

        ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        currentStack.addAnimLayer(layerId.defaultPriority(), layer);
        LayerEntry fresh = new LayerEntry(currentStack, new PoseLayerRuntime(layer), player);
        perPlayer.put(layerId, fresh);

        if (existing != null) {
            // Best-effort: прибираємо шар зі старого стека, якщо він ще технічно
            // досяжний. Не критично, якщо не вийде: старий AbstractClientPlayer
            // усе одно більше не рендериться і піде під GC.
            dispose(existing);
        }

        if (carryOver != null) {
            // Гравець пересоздався ПОСЕРЕД активної пози — переносимо її на новий
            // шар одразу (без fade-in: він "вже був у позі", fade виглядав би як
            // хибне повторне натискання), а не чекаємо наступного trigger.
            KeyframeAnimation anim = resolveAnimation(carryOver);
            if (anim != null) {
                if (carryAdjustment != null) fresh.runtime.setAdjustment(carryAdjustment);
                fresh.runtime.start(anim, carryOver, false);
            }
        }
        return fresh;
    }

    /** Знімає шар зі стека й скидає його стан; безпечно на вже мертвому стеку. */
    private static void dispose(LayerEntry entry) {
        hardStopQuietly(entry);
        try {
            if (entry.stack != null) {
                entry.stack.removeLayer(entry.layer());
            }
        } catch (Throwable t) {
            LOGGER.debug("PlayerPoseController: не вдалось зняти шар зі стека (стек уже недійсний)", t);
        }
    }

    private static void hardStopQuietly(LayerEntry entry) {
        try {
            entry.runtime.hardStop();
        } catch (Throwable t) {
            LOGGER.warn("PlayerPoseController: hardStop() failed", t);
        }
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
