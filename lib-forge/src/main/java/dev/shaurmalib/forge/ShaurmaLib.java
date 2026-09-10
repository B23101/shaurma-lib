package dev.shaurmalib.forge;

import dev.shaurmalib.common.chat.TeamChatContext;
import dev.shaurmalib.common.item.PhantomSlotBridge;
import dev.shaurmalib.common.license.LicenseProvider;
import dev.shaurmalib.common.lifecycle.DisconnectPolicy;
import dev.shaurmalib.common.lifecycle.JoinPolicy;
import dev.shaurmalib.common.lobby.LobbySpawnPointProvider;
import dev.shaurmalib.common.modesettings.SettingsSyncBridge;
import dev.shaurmalib.forge.chat.ChatModule;
import dev.shaurmalib.forge.client.chat.ChatScreenInterceptHandler;
import dev.shaurmalib.forge.config.ConfigModule;
import dev.shaurmalib.forge.item.ItemAnimationEngine;
import dev.shaurmalib.forge.license.LicenseModule;
import dev.shaurmalib.forge.lobby.LobbyModule;
import dev.shaurmalib.forge.lock.InteractionLockModule;
import dev.shaurmalib.forge.lock.PlayerFreezeService;
import dev.shaurmalib.forge.mode.LifecycleModule;
import dev.shaurmalib.forge.mode.ModeContractModule;
import dev.shaurmalib.forge.mode.PlayerLifecycleModule;
import dev.shaurmalib.forge.mode.PlayerReturnFlow;
import dev.shaurmalib.forge.modesettings.ModeSettingsModule;
import dev.shaurmalib.forge.overlay.ActionBarMessageSystem;
import dev.shaurmalib.forge.overlay.AnimatedCountdownSystem;
import dev.shaurmalib.forge.overlay.OverlayEngine;
import dev.shaurmalib.forge.overlay.VanillaHudCancelModule;
import dev.shaurmalib.forge.overlay.WorldTintOverlay;
import dev.shaurmalib.forge.playeranim.PlayerPoseController;
import dev.shaurmalib.forge.radio.RadioAudioDucking;
import dev.shaurmalib.forge.radio.RadioDialogManager;
import dev.shaurmalib.forge.radio.RadioDialogOverlay;
import dev.shaurmalib.forge.radio.RadioVoiceVolumeProvider;
import dev.shaurmalib.forge.sound.SoundCenter;
import dev.shaurmalib.forge.teleport.TeleportService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Точка входу в бібліотеку для Forge-споживачів — "той самий файл java у
 * головному моді де сказано що будемо використовувати".
 * <p>
 * Кожен споживач (snipers_shaurma, maniac-mode) викликає один раз у
 * конструкторі свого {@code @Mod}-класу:
 *
 * <pre>{@code
 * ShaurmaLib.Handle lib = ShaurmaLib.init("snipers_shaurma", modEventBus)
 *     .withConfig(worldRoot, "snipers_shaurma", MyMod.class::getResourceAsStream)
 *     .withTeleport()
 *     .withPlayerFreeze()
 *     .withInteractionLock()
 *     .withModeContract("mode.yml")
 *     .withLifecycle(2)
 *     .withPlayerLifecycle(JoinPolicy.SPECTATE, null,
 *         DisconnectPolicy.PRESERVE_STATE, myStatePreserver)
 *     .withLobby("sc_nametag_hide", () -> currentLobbySpawn())
 *     .withAnimatedItems()
 *     // опційно, лише якщо мод має свою фічу підміни предмета в слоті
 *     // (як параглайдер/зіплайн по F/G у snipers) — інакше не викликати:
 *     .withPhantomSlotBridge(myPhantomSlotBridge)
 *     .withSound()
 *     .withOverlays()
 *     .withRadio(myAnnouncerVolumeProvider,
 *         ModSounds.RADIO_START.get(), ModSounds.RADIO_NOISE.get(), ModSounds.RADIO_END.get(),
 *         () -> true)
 *     .build();
 * }</pre>
 * <p>
 * Нічого не активується, якщо модуль не запитано — {@link #build()} лише
 * створює обгортки над тим, що явно ввімкнено; жоден модуль не реєструє
 * event-listeners/network-канали/конфіги сам по собі.
 * <p>
 * <b>Стан на цей момент:</b> {@link #withConfig}, {@link #withTeleport},
 * {@link #withPlayerFreeze}, {@link #withPlayerAnim}, {@link #withInteractionLock}, {@link #withFreeCamera},
 * {@link #withModeContract}, {@link #withLifecycle},
 * {@link #withPlayerLifecycle}, {@link #withLicense}, {@link #withLobby}, {@link #withAnimatedItems},
 * {@link #withSound} (звуковий центр — незалежний від {@link #withOverlays}),
 * {@link #withOverlays} (+ {@link #withActionBarMessages},
 * {@link #withAnimatedCountdown}, {@link #withWorldTint}),
 * {@link #withChat}, {@link #withRadio}, {@link #withMixins},
 * {@link #withDamageGuard}, {@link #withInvisibleZones}, {@link #withAnimatedBlocks},
 * {@link #withSkinnableEntities}, {@link #withGraffiti} (серверне ядро —
 * дивись докстрінг методу для того, що навмисно лишилось поза цим
 * етапом), {@link #withAnimationRecording},
 * {@link #withSpectator}, {@link #withScreenEffects} і {@link #withModeSettings} — робочі, повністю
 * реалізовані модулі (звірені рядок-в-рядок з оригінальним кодом
 * snipers_shaurma, дивись docs/ARCHITECTURE.md).
 * <p>
 * <b>Модулі без стану ініціалізації (свідомо НЕ підключаються через цей
 * {@link Builder}):</b> частина модулів плану — статичні/безстанові
 * утиліти, для яких {@code withXxx()}-гейт додав би лише зайвий рядок
 * коду без жодної реальної переваги (немає ні event-listener'а, ні
 * мережевого каналу, ні конфігу, який треба було б умовно вмикати).
 * Кожен з них має власний {@code XxxModuleHook}-маркер у пакеті
 * {@link dev.shaurmalib.forge.module} з докстрінгом і прикладом
 * використання напряму, без {@link Builder}:
 * <ul>
 *   <li>{@link dev.shaurmalib.forge.module.CommandModuleHook} (план, п. 3.30) —
 *       {@link dev.shaurmalib.forge.command.ShaurmaCommandRoot}; команди
 *       реєструються у власній точці Forge lifecycle
 *       ({@code RegisterCommandsEvent}), не в конструкторі {@code @Mod}-класу.
 *       Створюйте {@code ShaurmaCommandRoot} напряму у своєму
 *       {@code @SubscribeEvent}-хендлері, маючи вже готовий {@link Handle}
 *       для {@code licenseModule().gate()}, {@code lifecycleModule().lifecycleBus()}
 *       і {@code modeContractModule().registry()}.</li>
 *   <li>{@link dev.shaurmalib.forge.module.MatchHistoryModuleHook} (план, п. 3.20) —
 *       {@code MatchHistoryStore.get(level, namespace)} викликається напряму
 *       в потрібному місці (типово наприкінці фази завершення матчу).</li>
 *   <li>{@link dev.shaurmalib.forge.module.LeaderboardModuleHook} (план, п. 3.21) —
 *       {@code LeaderboardEngine}/{@code LeaderboardType} (lib-common) і
 *       {@link dev.shaurmalib.forge.leaderboard.HologramLocationStore#get}
 *       (lib-forge) викликаються напряму; рендер карток консюмер компонує
 *       сам з {@code MarkerRenderRules}/{@code WorldBillboardPrimitives}
 *       нижче.</li>
 *   <li>{@link dev.shaurmalib.forge.module.MarkersModuleHook} (план, п. 3.13) —
 *       {@link dev.shaurmalib.forge.markers.MarkerRenderRules} +
 *       {@link dev.shaurmalib.forge.markers.WorldBillboardPrimitives}
 *       консюмер створює як {@code static final}-константи й викликає з
 *       власного {@code RenderLevelStageEvent}-хендлера.</li>
 *   <li>{@link dev.shaurmalib.forge.module.StyleModuleHook} (план, п. 3.3) —
 *       {@code new StyleTheme(StylePalette.DEFAULT)} створюється один раз і
 *       зберігається в полі клієнтського стану консюмера.</li>
 *   <li>{@link dev.shaurmalib.forge.module.CinematicModuleHook} (доповнення
 *       до плану — {@code dev.shaurmalib.client.cinematic} package
 *       README.md) — {@code CineTimeline}/{@code CinePresets} для
 *       дата-driven кінематики старту матчу; консюмер створює конкретний
 *       {@code CineTimeline} у момент старту сесії й сам тикає його.</li>
 * </ul>
 * {@code withAnimatedItems()} не приймає власний
 * мережевий міст — {@link ItemAnimationEngine} використовує окремий Forge
 * networking-канал бібліотеки ({@code shaurma_lib}, див.
 * {@link dev.shaurmalib.forge.network.ShaurmaLibNetwork}), а не канал
 * споживача (план, розділ 6, п. 2: окремі канали, щоб версіонування
 * пакетів lib і мода не переплуталось при незалежних апдейтах). Реєстрація
 * конкретних {@code ItemDefinition} для конкретних предметів (Medkit,
 * Adrenaline, ...) лишається на боці споживача через
 * {@code ItemDefinitionRegistry.register(...)} — {@code withAnimatedItems()}
 * лише вмикає сам рушій (тік/toss-хендлери), нічого не реєструє замість
 * споживача. Виклик гетера ще не підключеного {@link Builder}-модуля на
 * {@link Handle} кидає зрозумілий {@link IllegalStateException} із назвою
 * потрібного {@code withXxx(...)}, а не мовчазний {@code null}.
 */
public final class ShaurmaLib {

    private ShaurmaLib() {}

    public static Builder init(String consumerModId, IEventBus modEventBus) {
        return new Builder(consumerModId, modEventBus);
    }

    public static final class Builder {
        private final String consumerModId;
        private final IEventBus eventBus;

        private boolean teleportEnabled = false;
        private boolean mixinsConfigured = false;
        private boolean damageGuardEnabled = false;
        private boolean invisibleZonesEnabled = false;
        private boolean animatedBlocksEnabled = false;
        private boolean skinnableEntitiesEnabled = false;
        private boolean graffitiEnabled = false;
        private String graffitiNamespace = null;
        private boolean animationRecordingEnabled = false;
        private boolean spectatorEnabled = false;
        private boolean screenEffectsEnabled = false;
        private boolean animatedItemsEnabled = false;
        private boolean playerFreezeEnabled = false;
        private boolean playerAnimEnabled = false;
        private boolean freeCameraEnabled = false;
        private boolean overlaysEnabled = false;
        private boolean actionBarEnabled = false;
        private boolean animatedCountdownEnabled = false;
        private boolean soundEnabled = false;
        private boolean chatEnabled = false;
        private TeamChatContext chatTeamContext;
        private String chatFeedId;
        private dev.shaurmalib.forge.chat.ChatModule.ChatDisplaySink chatDisplaySink;
        private final java.util.List<String> worldTintChannels = new java.util.ArrayList<>();
        private PhantomSlotBridge<ServerPlayer> phantomSlotBridge;
        private boolean radioEnabled = false;
        private RadioVoiceVolumeProvider radioVolumeProvider;
        private SoundEvent radioStartSound;
        private SoundEvent radioNoiseSound;
        private SoundEvent radioEndSound;
        private BooleanSupplier radioOverlayVisible = () -> true;

        private ConfigModule configModule;
        private ModeContractModule modeContractModule;
        private LifecycleModule lifecycleModule;
        private LicenseModule licenseModule;
        private LobbyModule lobbyModule;
        private PlayerLifecycleModule playerLifecycleModule;
        private InteractionLockModule interactionLockModule;
        private ModeSettingsModule modeSettingsModule;

        private Builder(String consumerModId, IEventBus eventBus) {
            this.consumerModId = consumerModId;
            this.eventBus = eventBus;
        }

        /**
         * Реєструє рушій конфігів (п. 3.1). {@code resourceLoader} — типово
         * {@code MyModMainClass.class::getResourceAsStream}, бо ресурси
         * дефолтних .yml лежать у jar-і споживача, а не в lib-forge.
         */
        public Builder withConfig(Path worldRoot, String namespace, Function<String, InputStream> resourceLoader) {
            this.configModule = new ConfigModule(worldRoot, namespace, resourceLoader);
            return this;
        }

        /**
         * Вмикає {@link TeleportService} (п. 3.2). Сервіс — набір статичних
         * методів (без стану), тому тут просто прапорець доступності: якщо
         * не викликано, {@link Handle#teleportService()} кине виключення —
         * консьюмер має усвідомлено підключити модуль, а не отримати сервіс
         * "за замовчуванням завжди доступний" без запиту.
         */
        public Builder withTeleport() {
            this.teleportEnabled = true;
            return this;
        }

        /**
         * Реєструє стан toggle-миксинів (план, п. 3.17) —
         * {@code ShaurmaLibMixinPlugin} звіряється з цим станом під час
         * Mixin-трансформації, тому виклик має відбутись у конструкторі
         * {@code @Mod}-класу консюмера, ДО того, як FML торкнеться
         * цільових (рендер/модель) класів — дивись докстрінг
         * {@link dev.shaurmalib.common.mixin.MixinToggleRegistry}.
         * <p>
         * Приклад: {@code withMixins(MixinToggle.disable(MixinId.ZIPLINE_GAME_RENDERER))}
         * якщо консюмер не має zipline-фічі й хоче повністю прибрати цей
         * патч із застосування (а не покладатись на internal-guard).
         * Дефолт для будь-якого {@link dev.shaurmalib.common.mixin.MixinId},
         * не переданого сюди — увімкнено.
         */
        public Builder withMixins(MixinToggle... toggles) {
            for (MixinToggle t : toggles) {
                if (t.enabled()) {
                    dev.shaurmalib.common.mixin.MixinToggleRegistry.enable(t.id());
                } else {
                    dev.shaurmalib.common.mixin.MixinToggleRegistry.disable(t.id());
                }
            }
            this.mixinsConfigured = true;
            return this;
        }

        /**
         * Вмикає {@link PlayerFreezeService} (п. 3.25 плану, звужено):
         * перенесено лише саму механіку заморозки з
         * {@code MovementBlockHandler.freezePlayer}, БЕЗ жодного рішення
         * "коли" заморожувати — це лишається продуктовою логікою консюмера
         * (KIT_SELECT, round-intro тощо — гра сама вирішує). Статичний
         * сервіс без стану, як і {@link TeleportService} — тут лише
         * прапорець свідомого підключення.
         */
        public Builder withPlayerFreeze() {
            this.playerFreezeEnabled = true;
            return this;
        }

        /**
         * Вмикає {@link PlayerPoseController} (план, п. 3.24) —
         * узагальнення {@code PlayerAnimDropBridge} snipers_shaurma
         * (єдина точка дотику до PlayerAnimationLib API в оригіналі) на
         * довільну кількість незалежних {@link
         * dev.shaurmalib.common.playeranim.PoseLayerId}-шарів кастомної
         * пози (сидіння в літаку, зіплайн, лежача смерть — той самий
         * список кандидатів, що план п. 3.24 називає для {@code
         * PlayerPoseController}), з переносом ОБОХ задокументованих у
         * оригіналі виправлень (WeakHashMap-memory-leak і lazy-retry на
         * гонку ініціалізації PAL) — дивись клас-докстрінг {@link
         * PlayerPoseController} для деталей.
         * <p>
         * <b>Виправляє додатково задокументований баг relog/respawn</b>
         * (якого не було в оригінальному {@code PlayerAnimDropBridge}):
         * Minecraft/Forge пересоздає {@code AbstractClientPlayer}-
         * екземпляр при повторному вході на сервер, зберігаючи той самий
         * UUID — оригінал ключував свій шар лише по UUID і повертав
         * старий, вже нерендерений {@code ModifierLayer} зі старого
         * {@code AnimationStack}, тому анімація мовчки переставала
         * відображатись після виходу й повторного заходу. {@link
         * PlayerPoseController#trigger} звіряє ідентичність поточного
         * {@code AnimationStack} гравця щоразу, не лише при першому
         * створенні запису, і best-effort переносить активну позу на
         * новий шар, якщо relog стався посеред її відтворення.
         * <p>
         * {@link dev.shaurmalib.forge.playeranim.PlayerPoseEvents}
         * завжди підписаний на клієнтський {@code
         * ClientPlayerNetworkEvent.LoggingOut} (незалежно від цього
         * прапорця — той самий принцип, що {@link ItemAnimationEngine}),
         * тому сама подієва обв'язка активна одразу; цей метод лише
         * підтверджує свідоме підключення на {@link Handle}, як решта
         * "статичних сервісів" лібу ({@link #withTeleport()}, {@link
         * #withPlayerFreeze()}).
         */
        public Builder withPlayerAnim() {
            this.playerAnimEnabled = true;
            return this;
        }

        /**
         * Реєструє {@link InteractionLockModule} (п. 3.25 плану) —
         * узагальнення {@code MovementBlockHandler} (частина руху),
         * {@code BlockInteractHandler} і {@code PhantomModeWeaponGuard} з
         * оригіналу в один центральний реєстр з {@code LockType} +
         * рядком-причиною ({@code lockReason}), а не три окремі
         * event-підписники з власною ad-hoc умовою кожен.
         * <p>
         * {@link dev.shaurmalib.forge.lock.InteractionLockHooks} завжди
         * підписані на bus (як {@link ItemAnimationEngine}), але
         * бездіяльні, доки консюмер не викличе
         * {@code interactionLockModule().lock(...)} — на відміну від
         * {@link #withPlayerFreeze()} (голий примітив заморозки без
         * прив'язки до причини), тут МОЖНА тримати кілька незалежних
         * причин блокування одночасно на різних типах дій, і зняття однієї
         * причини не розлочує тип, поки лишається інша.
         */
        public Builder withInteractionLock() {
            this.interactionLockModule = new InteractionLockModule();
            return this;
        }

        /**
         * Вмикає власне блокування урону (план, п. 3.26) —
         * узагальнення {@code PlayerDamageEventHandler}/{@code
         * AttackPreventionHandler}/{@code PigProtectionHandler}/{@code
         * SapperPassiveHandler}/{@code SCNDomeShieldGuard}/{@code
         * BodyDamageEventHandler} в один реєстр іменованих {@code
         * DamageInterceptor}-ів замість окремого
         * {@code @Mod.EventBusSubscriber}-класу на кожну нову причину
         * недоторканості.
         * <p>
         * {@code DamageGuardHooks} (ванільний {@code LivingHurtEvent}
         * шлях, {@code EventPriority.HIGH}) завжди підписаний на bus
         * незалежно від цього прапорця — як і {@code
         * InteractionLockHooks}, він бездіяльний, доки консюмер не
         * зареєстрував жодного {@code DamageInterceptor}. Прапорець
         * тут лише документує свідоме підключення, а
         * {@link Handle#damageGuardModule()} кидає зрозумілу помилку,
         * якщо консюмер спробує отримати доступ без виклику цього
         * методу — той самий принцип, що й решта модулів лібу.
         * <p>
         * Сторонні джерела урону, що обходять {@code LivingHurtEvent}
         * (TACZ {@code EntityHurtByGunEvent} і подібні) — окремий шлях
         * через {@code GunDamageBridge.tryBlock(...)}, який консюмер
         * викликає зі свого власного TACZ-специфічного хука (бібліотека
         * не залежить від TACZ напряму, план розділ 4 п. 3); обидва
         * шляхи звіряються з тим самим реєстром причин, тому
         * зареєстровану причину блокування не треба дублювати під
         * кожне джерело урону окремо.
         */
        public Builder withDamageGuard() {
            this.damageGuardEnabled = true;
            return this;
        }

        /**
         * Вмикає пакет "сутність-зона без хітбоксу" (план, п. 3.28) —
         * {@link dev.shaurmalib.forge.entity.invisiblezone.InvisibleZoneEntityBase},
         * узагальнення {@code SCNDomeShellEntity} snipers_shaurma. Найризикованіший
         * модуль плану (див. клас-докстрінг {@code InvisibleZoneEntityBase} —
         * власний regression-чеклист), тому свідомо винесений в окремий
         * прапорець замість "завжди доступно" — консюмер має явно
         * підтвердити, що читав застереження класу.
         * <p>
         * Сам {@code EntityType} реєструє консюмер через власний
         * {@code DeferredRegister} (як і решта Forge-сутностей — база не
         * містить власного реєстру екземплярів), консюмер лише розширює
         * {@code InvisibleZoneEntityBase} власним підкласом і викликає
         * {@code init(...)} після спавну. Опційний міст до стороннього
         * (TACZ-подібного) джерела урону підключається per-instance через
         * {@code InvisibleZoneEntityBase.bindExternalHitBridge(...)}, не тут —
         * бібліотека не залежить від TACZ напряму (план розділ 4, п. 3).
         */
        public Builder withInvisibleZones() {
            this.invisibleZonesEnabled = true;
            return this;
        }

        /**
         * Вмикає пакет "анімований GeckoLib-блок з меню" (план, п. 3.27) —
         * {@link dev.shaurmalib.forge.blocks.AnimatedBlockEntityBase},
         * узагальнення {@code CaseBlockEntity} snipers_shaurma. На відміну
         * від {@link #withInvisibleZones()} цей модуль не має власного
         * regression-застереження (менш ризикована зміна — чиста
         * lifecycle+GeckoLib-обгортка без кастомних AABB/tick-хаків), але
         * так само не має власного реєстру екземплярів: консюмер сам
         * реєструє {@code BlockEntityType} через власний
         * {@code DeferredRegister} і сам розширює базовий клас, надаючи
         * лише {@code AnimatedBlockClipSet} (назви GeckoLib-кліпів).
         * Контейнерна частина (луут, розмір інвентаря, {@code createMenu})
         * лишається на боці консюмера — база описує лише
         * {@code IDLE/OPENING/OPEN/CLOSING/REFILLING} lifecycle і мережеву
         * синхронізацію, не інвентар.
         */
        public Builder withAnimatedBlocks() {
            this.animatedBlocksEnabled = true;
            return this;
        }

        /**
         * Вмикає пакет "сутність з рантайм-скіном гравця" (план, п. 3.22) —
         * {@link dev.shaurmalib.forge.entity.skinnable.SkinnableEntityBase},
         * узагальнення {@code DeathCorpseEntity} snipers_shaurma. Як і
         * {@link #withAnimatedBlocks()} та {@link #withInvisibleZones()},
         * не має власного реєстру екземплярів — консюмер сам реєструє
         * {@code EntityType} через свій {@code DeferredRegister} і сам
         * реалізує {@code GeoEntity} (GeckoLib-контролер/анімації) у
         * власному підкласі; база надає лише скін-UUID/армор-синхронізацію,
         * fade-out anti-kick фікс і explosion knockback. Head-tracking
         * камера (матрична bone-композиція для конкретної анімації)
         * НЕ входить сюди — лишається продуктовим кодом консюмера (див.
         * клас-докстрінг {@code SkinnableEntityBase} для пояснення чому,
         * той самий принцип, що {@code ItemCameraTrack} у 3.24b).
         */
        public Builder withSkinnableEntities() {
            this.skinnableEntitiesEnabled = true;
            return this;
        }

        /**
         * Вмикає пакет "движок графіті" (план, п. 3.14) —
         * {@link dev.shaurmalib.forge.graffiti.GraffitiBlockBase}/
         * {@link dev.shaurmalib.forge.graffiti.GraffitiBlockEntityBase},
         * узагальнення серверного ядра {@code graffiti.block}/
         * {@code graffiti.server}/{@code graffiti.network} snipers_shaurma.
         * <p>
         * <b>Цей етап переносу — лише серверне ядро:</b> дані блоку
         * ({@link dev.shaurmalib.common.graffiti.GraffitiSpec}), NBT/
         * мережева серіалізація, файлове сховище PNG
         * ({@link dev.shaurmalib.forge.graffiti.GraffitiFileStore}) і
         * chunked-передача зображень
         * ({@link dev.shaurmalib.forge.graffiti.GraffitiTransferManager}).
         * Клієнтський кеш рендера, world-renderer
         * ({@code RenderLevelStageEvent}-малювання текстури на стіні) і
         * canvas-редактор ({@code GraffitiMenuScreen}, 1164 рядки в
         * оригіналі) НЕ входять сюди — лишаються продуктовим кодом
         * консюмера до наступного етапу бібліотеки; до того часу
         * консюмер реєструє свій клієнтський кеш через
         * {@link dev.shaurmalib.forge.graffiti.GraffitiClientCacheBridge#register}.
         * <p>
         * Як і {@link #withAnimatedBlocks()}/{@link #withSkinnableEntities()},
         * не має власного реєстру екземплярів — консюмер сам реєструє
         * {@code Block}/{@code Item}/{@code BlockEntityType} через власний
         * {@code DeferredRegister} і сам розширює
         * {@code GraffitiBlockBase}/{@code GraffitiBlockEntityBase}.
         *
         * @param namespace простір імен консюмера (типово той самий, що
         *                  переданий у {@link #withConfig}) — визначає
         *                  теку PNG-файлів на диску
         *                  ({@code <world>/<namespace>/graffiti/*.png}),
         *                  щоб декілька модів-споживачів (snipers_shaurma,
         *                  maniac-mode) не ділили одну теку.
         */
        public Builder withGraffiti(String namespace) {
            if (namespace == null || namespace.isBlank()) {
                throw new IllegalArgumentException("withGraffiti(namespace) вимагає непорожній namespace.");
            }
            this.graffitiEnabled = true;
            this.graffitiNamespace = namespace;
            return this;
        }

        /**
         * Вмикає узагальнений рушій запису keyframe-анімацій (план, п. 3.16)
         * — {@link dev.shaurmalib.forge.animation.AnimationRecordFacade},
         * заміна {@code AnimRecordManager} (SC) + {@code SCNAnimRecordManager}
         * + {@code SDAnimRecordManager} snipers_shaurma одним статичним
         * рушієм. {@code onServerTick(...)} треба викликати з власного
         * {@code ServerTickEvent}-хендлера консюмера (той самий принцип,
         * що й решта статичних сервісів лібу — рушій не підписується на
         * події сам). Обидва формати запису (абсолютний SC/SCN і дельта SD)
         * доступні через один і той самий рушій — консюмер обирає
         * {@code startAbsolute(...)}/{@code startDelta(...)} залежно від
         * конкретного випадку.
         */
        public Builder withAnimationRecording() {
            this.animationRecordingEnabled = true;
            return this;
        }

        /**
         * Вмикає {@link dev.shaurmalib.forge.spectator.SpectatorSessionController}
         * (план, п. 3.34) — узагальнене ядро "спостереження за живим
         * гравцем" (не ванільний {@code Spectator}-геймтайп, а прив'язана
         * камера на ціль з можливістю циклічного перемикання). Узагальнює
         * {@code SpectatorAnimationApplier}/{@code SpectatorAnimationCollector}
         * snipers_shaurma, БЕЗ TACZ/GeckoLib/Superbwarfare-специфічної
         * передачі анімацій рук зброї — та частина лишається продуктовою
         * специфікою консюмера (план, розділ 4), який підключає власний
         * animation-bridge поверх цього контролера. Death-camera ефекти
         * (vignette/heartbeat/rise) теж лишаються в моді — це продуктова
         * кінематографія конкретного консюмера, не універсальний движок.
         * <p>
         * Вимагає {@link #withTeleport()} — контролер повертає глядача на
         * збережену позицію через {@code TeleportService} при виході
         * ({@link dev.shaurmalib.forge.teleport.TeleportReason#SPECTATOR_TOGGLE}).
         */
        public Builder withSpectator() {
            if (!teleportEnabled) {
                throw new IllegalStateException(
                        "withSpectator() вимагає попереднього виклику withTeleport() на Builder.");
            }
            this.spectatorEnabled = true;
            return this;
        }

        /**
         * Вмикає узагальнений рушій повноекранних post-chain ефектів
         * (план, п. 3.23) — {@link dev.shaurmalib.forge.fx.ScreenEffectPostChain},
         * заміна {@code BlackAndWhiteScreenEffect} snipers_shaurma одним
         * централізованим реєстром. Консюмер реєструє свій ефект (напр.
         * монохром) через {@code ScreenEffectPostChain.register(PostChainEffectSpec)}
         * і керує його активацією через
         * {@link dev.shaurmalib.common.fx.PostChainEffectCauses} —
         * {@code activate}/{@code deactivate} з довільним рядком-причиною
         * (той самий "reason key" підхід, що {@code InteractionLockRegistry},
         * план 3.25). {@code onRenderGuiPre(...)} і
         * {@code registerReloadListener(...)} треба підключити до
         * відповідних Forge-подій консюмера вручну (рушій сам не
         * підписується на події — той самий принцип, що решта static-
         * service модулів лібу).
         * <p>
         * Шейдер-ресурси (post-chain {@code .json}, {@code .fsh}/{@code .vsh})
         * лишаються в namespace консюмера — бібліотека не постачає власних
         * шейдер-асетів, лише механіку завантаження/ресайзу/reload
         * (той самий принцип, що {@code ShaurmaConfigTree}: дані завжди
         * постачає споживач).
         */
        public Builder withScreenEffects() {
            this.screenEffectsEnabled = true;
            return this;
        }

        /**
         * Вмикає пакет "вільна камера" (п. 3.15 плану) —
         * {@link dev.shaurmalib.forge.camera.CameraOwnershipRegistry} +
         * {@link dev.shaurmalib.forge.camera.FreeCameraEntity} + готові
         * контролери ({@link dev.shaurmalib.forge.camera.StationaryCameraController},
         * {@link dev.shaurmalib.forge.camera.EntitySpectateController},
         * {@link dev.shaurmalib.forge.camera.CinematicPathController},
         * {@link dev.shaurmalib.forge.camera.FreeFlyCameraController}) —
         * узагальнення {@code FreeCamera}/{@code KitSelectCameraManager}/
         * {@code CameraSpectateHandler}/{@code EndGameCameraManager}/
         * {@code SDRoundCameraManager} snipers_shaurma в один
         * координований пакет: усі контролери ділять один спільний
         * {@code CameraOwnershipRegistry} замість того, щоб кожен новий
         * менеджер камери писав ручні {@code isActive()}-перевірки
         * ВСІХ інших менеджерів (джерело задокументованих у оригіналі
         * race conditions — дивись докстрінг {@code CameraOwnershipRegistry}).
         * <p>
         * {@link dev.shaurmalib.forge.camera.FreeFlyCameraController}
         * (керована гравцем камера, WASD+миша) — НОВА функціональність,
         * якої не було в оригінальному коді (там усі камери — scripted/
         * нерухомі); потрібна для інструментів реплею матчу/огляду
         * карти в майбутньому режимі-другові.
         * <p>
         * Motion-класи для складнішої сценарної кінематики
         * ({@code PathMotion}/{@code LoopPathMotion}/{@code BezierApproachMotion},
         * пакет {@code dev.shaurmalib.forge.camera.motion}) доступні
         * незалежно від цього прапорця — вони не мають стану реєстрації,
         * консюмер компонує їх напряму з {@link dev.shaurmalib.forge.camera.FreeCameraEntity}
         * для власних сценаріїв (як SD round-intro: idle-петля + approach
         * + possess).
         */
        public Builder withFreeCamera() {
            this.freeCameraEnabled = true;
            return this;
        }

        /**
         * Реєструє основу режимів (п. 3.18) — {@code GameModeRegistry}.
         * Вимагає, щоб {@link #withConfig} було викликано раніше (реєстр
         * режимів працює через те саме дерево конфігів namespace).
         *
         * @param modeStateFileName ім'я файлу персистенції обраного режиму
         *                          в корені world (в оригіналі — "mode.yml").
         */
        public Builder withModeContract(String modeStateFileName) {
            requireConfigModule("withModeContract");
            this.modeContractModule = new ModeContractModule(
                    configModule.configTree(), configModule.reloadBus(), modeStateFileName);
            return this;
        }

        /**
         * Реєструє lifecycle (п. 3.19) — {@code MatchLifecycleBus} + {@code IdleBehavior}.
         *
         * @param minPlayersToStart мінімальна кількість гравців для старту матчу.
         */
        public Builder withLifecycle(int minPlayersToStart) {
            this.lifecycleModule = new LifecycleModule(minPlayersToStart);
            return this;
        }

        /**
         * Реєструє єдине меню налаштування режиму (план, п. 3.32) —
         * {@link ModeSettingsModule}, фабрика узагальненого
         * {@code ModeSettingsScreen}, заміна {@code GameSettingsScreen}
         * (1059 рядків) snipers_shaurma. Вимагає {@link #withModeContract}
         * (ліва панель рендерить {@code GameModeRegistry.all()}) і
         * {@link #withLifecycle} (блокування редагування під час гри) —
         * викликайте цей метод ПІСЛЯ обох.
         * <p>
         * {@code style} — типово один {@link StyleTheme}, спільний з
         * рештою екранів/оверлеїв консюмера (план, п. 3.3). {@code bridge}
         * — власна реалізація мережевого шару консюмера (див. докстрінг
         * {@link SettingsSyncBridge}); бібліотека не постачає дефолтної.
         *
         * @param showPlayersTab чи показувати вкладку "Гравці" —
         *                       {@code false} для консюмерів без ручного
         *                       призначення режиму гравцям (план, п. 3.32:
         *                       "не кожному режиму/моду потрібен цей таб").
         */
        public Builder withModeSettings(dev.shaurmalib.forge.style.StyleTheme style,
                                         SettingsSyncBridge bridge, boolean showPlayersTab) {
            requireModeContractModule("withModeSettings");
            requireLifecycleModule("withModeSettings");
            this.modeSettingsModule = new ModeSettingsModule(
                    style, modeContractModule.registry(), lifecycleModule, bridge, showPlayersTab);
            return this;
        }

        /**
         * Реєструє {@link PlayerLifecycleModule} (п. 3.8 плану) — join/
         * return-політику для конкретного гравця відносно матчу, окремо
         * від грубого стану матчу в цілому ({@link #withLifecycle(int)}).
         * Узагальнює ту частину {@code PlayerJoinEventHandler}
         * snipers_shaurma, що вирішує "гравець зайшов під час активної
         * гри → спостерігач/черга/відмова" і "гравець повернувся після
         * виходу → відновити стан/скинути в спостерігачі/вважати таким,
         * що вибув".
         * <p>
         * Три готові {@link DisconnectPolicy} покривають типові випадки;
         * якщо жодна не підходить (наприклад "зберегти інвентар, але
         * скинути прогрес раунду"), не викликайте цей метод і працюйте з
         * {@link PlayerReturnFlow.StatePreserver}/{@link dev.shaurmalib.forge.mode.DisconnectHandler}
         * напряму — вони лишаються публічними класами лібу незалежно від
         * цього зручного обгортаючого методу.
         *
         * @param joinPolicy        поведінка для гравця, що приєднався під час активного матчу.
         * @param joinRejectMessage повідомлення при {@link JoinPolicy#REJECT} ({@code null} — дефолтне лібове).
         * @param disconnectPolicy  поведінка для гравця, що повернувся після виходу.
         * @param statePreserver    доменно-специфічне відновлення стану для {@link DisconnectPolicy#PRESERVE_STATE}
         *                          ({@code null}, якщо ця політика не використовується).
         */
        public Builder withPlayerLifecycle(JoinPolicy joinPolicy, Component joinRejectMessage,
                                            DisconnectPolicy disconnectPolicy,
                                            PlayerReturnFlow.StatePreserver statePreserver) {
            this.playerLifecycleModule = new PlayerLifecycleModule(
                    joinPolicy, joinRejectMessage, disconnectPolicy, statePreserver);
            return this;
        }

        /**
         * Реєструє ліцензійний гейт (п. 3.31). {@code provider} — власна
         * реалізація споживача; бібліотека не постачає жодного дефолтного
         * провайдера з вбудованим секретом.
         */
        public Builder withLicense(LicenseProvider provider, Path licenseDir) {
            this.licenseModule = new LicenseModule(provider, licenseDir);
            return this;
        }

        /**
         * Реєструє {@link LobbyModule} (п. 3.8 плану) — перенесення
         * {@code sendToLobby}/{@code hideNameTag} з
         * {@code PlayerJoinEventHandler} snipers_shaurma. Свідомо НЕ
         * вирішує ЗА мод, коли гравця треба відправити в лобі (реконект
         * vs новий глядач vs звичайний старт) — це лишається продуктовою
         * логікою консюмера (детально в докстрінгу {@link LobbyModule}).
         * Не вимагає {@link #withTeleport()} — {@link LobbyModule}
         * використовує {@link TeleportService} напряму (той самий
         * статичний сервіс, підключення прапорцем тут не потрібне, бо
         * {@link LobbyModule} — не публічний API навколо самого сервісу,
         * а власна вища механіка, що його викликає).
         *
         * @param nameTagHideTeamId  ідентифікатор scoreboard-команди для
         *                           приховування ніків (в оригіналі
         *                           захардкоджено як {@code "sc_nametag_hide"}).
         * @param spawnPointProvider постачальник поточної точки лобі —
         *                           консюмер сам вирішує спільна вона чи
         *                           per-mode override (як SCN у snipers).
         */
        public Builder withLobby(String nameTagHideTeamId, LobbySpawnPointProvider spawnPointProvider) {
            this.lobbyModule = new LobbyModule(nameTagHideTeamId, spawnPointProvider);
            return this;
        }

        /**
         * Вмикає систему анімованих предметів (п. 3.6 + 3.24a + 3.24b
         * плану) — {@link ItemAnimationEngine} (заміна
         * {@code core.items.base.AnimatedItemSystem}: сесії/тик/consume/
         * anti-dupe стейт-машина), {@code ItemLeftArmHideEngine} (заміна
         * {@code ItemLeftArmHideHandler}+{@code LeftArmHideState}, тепер
         * на обидві руки через декларативний {@code ItemDefinition.armOverride(...)},
         * а не жорсткий whitelist класів) і {@code ItemCameraController}
         * (заміна {@code DeminingCameraHandler}, узагальнена на опційний
         * {@code cameraTrack} у {@code ItemDefinition} для будь-якого предмета).
         * <p>
         * Рушій сам підписаний на {@code TickEvent.ServerTickEvent} і
         * {@code ItemTossEvent} (через {@code @Mod.EventBusSubscriber} на
         * {@link ItemAnimationEngine}), тому окремої реєстрації на
         * {@code eventBus()} тут не потрібно — ці підписки завжди присутні
         * в jar-і lib-forge, але бездіяльні (мапа активних сесій порожня),
         * якщо жоден предмет не зареєстровано в
         * {@code ItemDefinitionRegistry} і {@link #withAnimatedItems()}
         * взагалі не викликано з боку споживача — це задокументований
         * виняток із загального правила "нічого не реєструється без
         * запиту", виправданий тим, що сам рушій статичний і не має
         * стану, доки немає активних сесій.
         * <p>
         * Реєстрація конкретних {@code ItemDefinition} (Medkit,
         * Adrenaline, ...) — відповідальність споживача, не цього методу.
         */
        public Builder withAnimatedItems() {
            this.animatedItemsEnabled = true;
            return this;
        }

        /**
         * Опційний anti-dupe міст до продуктово-специфічних "фантомних"
         * слотів споживача (як параглайдер/зіплайн по F/G у snipers,
         * див. {@link PhantomSlotBridge}). Викликати ЛИШЕ якщо
         * {@link #withAnimatedItems()} також викликано, і ДО першого
         * реального {@code startUse} в грі — {@link ItemAnimationEngine}
         * ліниво створює свій внутрішній {@code UseSessionEngine} з цим
         * мостом при першому виклику {@link Handle#build()}.
         */
        public Builder withPhantomSlotBridge(PhantomSlotBridge<ServerPlayer> bridge) {
            this.phantomSlotBridge = bridge;
            return this;
        }

        /**
         * Реєструє {@link OverlayEngine} (п. 3.4) — заміна 40+ окремих
         * {@code event.registerAboveAll(...)} з {@code ClientRegistration}
         * snipers_shaurma одним {@code IGuiOverlay}-хуком. Викликати з
         * клієнтського mod-event listener-a, передавши подію
         * {@code RegisterGuiOverlaysEvent} споживача:
         * <pre>{@code
         * @SubscribeEvent
         * static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
         *     ShaurmaLib.attachOverlayEngine(event);
         * }
         * }</pre>
         * Сам {@link #withOverlays()} лише вмикає прапорець доступності
         * решти overlay-підсистем ({@link #withActionBarMessages()},
         * {@link #withAnimatedCountdown()}, {@link #withWorldTint(String)}) —
         * реальне підключення {@code IGuiOverlay} відбувається окремим
         * статичним викликом {@link ShaurmaLib#attachOverlayEngine}, бо
         * {@code RegisterGuiOverlaysEvent} приходить у зовсім іншій точці
         * життєвого циклу мода (реєстрація клієнтських подій), ніж
         * конструктор {@code @Mod}-класу, де викликається весь інший
         * {@link Builder}.
         */
        public Builder withOverlays() {
            this.overlaysEnabled = true;
            return this;
        }

        /**
         * Вмикає {@link ActionBarMessageSystem} (п. 3.4 + 3.10) — заміна
         * {@code StatusMessageOverlay}. Вимагає {@link #withOverlays()}.
         */
        public Builder withActionBarMessages() {
            requireOverlays("withActionBarMessages");
            this.actionBarEnabled = true;
            return this;
        }

        /**
         * Вмикає {@link AnimatedCountdownSystem} (п. 3.4) — базовий
         * pop-in/pop-out каркас цифри відліку (без snipers-специфічного
         * glitch-VFX, який лишається кастомним {@code CineHooks} у моді,
         * план п. 3.35). Вимагає {@link #withOverlays()}.
         */
        public Builder withAnimatedCountdown() {
            requireOverlays("withAnimatedCountdown");
            this.animatedCountdownEnabled = true;
            return this;
        }

        /**
         * Вмикає звуковий центр (план, п. 3.5) — {@link SoundCenter} +
         * {@link dev.shaurmalib.forge.network.packets.PlaySoundPacket}/
         * {@code StopSoundPacket}, заміна {@code PlaySoundPacket}/
         * {@code StopSoundPacket}/{@code KitSoundsPacket}/
         * {@code MenuSoundHelper}/{@code MenuSounds}/{@code AirdropPlaneSound}
         * snipers_shaurma. НЕ вимагає {@link #withOverlays()} — звуковий
         * центр незалежний від UI-оверлеїв (як {@link #withTeleport()}).
         * <p>
         * Незалежно від цього прапорця (той самий принцип, що
         * {@link ItemAnimationEngine}) бібліотека завжди підписує
         * {@link dev.shaurmalib.forge.sound.SoundCategoryDucker#tick()} на
         * клієнтський тік — {@code withRadio(...)} вже покладається на
         * ducking незалежно від того, чи консюмер викликав
         * {@code withSound()}. Цей метод підключає ЛИШЕ прошарок
         * play/stop поверх ducking-двигуна, який уже активний завжди.
         * <p>
         * <b>Модель роботи "звукового центру"</b> (три незалежні реєстри,
         * усі заповнює консюмер, бібліотека лише читає):
         * <ol>
         *   <li>{@link dev.shaurmalib.common.sound.SoundCategoryRegistry} —
         *       консюмер реєструє свої category-слайдери (в оригіналі:
         *       {@code ModSoundCategory} enum — MOD_MUSIC/MOD_SFX/KILL/
         *       ANNOUNCER/DEATH_CONCUSSION/TRAINING_MUSIC/MENU_UI), кожен
         *       з власним {@link dev.shaurmalib.common.sound.VolumeSource},
         *       що читає значення з екрана налаштувань консюмера.</li>
         *   <li>{@link dev.shaurmalib.common.sound.SoundCueRegistry} —
         *       консюмер реєструє {@link dev.shaurmalib.common.sound.SoundCue}
         *       для кожного {@code SoundEvent}, прив'язуючи його до однієї
         *       з зареєстрованих вище категорій і до
         *       {@link dev.shaurmalib.common.sound.SoundStage} (2D UI /
         *       3D фіксований / 3D рухомий) — замінює три захардкоджені
         *       {@code Set.of(...)}-списки, які раніше жили всередині
         *       самого мережевого пакета.</li>
         *   <li>{@link SoundCenter} — рушій відтворення: {@code play}/
         *       {@code playAt}/{@code playUi}/{@code trackMovingSource}.
         *       Консюмер НІКОЛИ не будує {@code SimpleSoundInstance} сам
         *       — лише викликає ці статичні методи (напряму на клієнті,
         *       або через {@code PlaySoundPacket.send(...)} із сервера).</li>
         * </ol>
         * Мережеві пакети {@code PlaySoundPacket}/{@code StopSoundPacket}
         * реєструються в {@link dev.shaurmalib.forge.network.ShaurmaLibNetwork}
         * незалежно від цього прапорця (стабільність packet ID між
         * релізами lib) — прапорець тут документує свідоме підключення
         * консюмера, як і решта модулів білдера.
         */
        public Builder withSound() {
            this.soundEnabled = true;
            return this;
        }

        /**
         * Реєструє один канал {@link WorldTintOverlay} (п. 3.4 + 3.12) —
         * заміна {@code ScreenFadeOverlay}/{@code DeathFadeOverlay}.
         * Можна викликати кілька разів з різними {@code tintId}, якщо
         * потрібні незалежні одночасні тінти (рідкісний випадок — типово
         * достатньо одного дефолтного каналу). Вимагає {@link #withOverlays()}.
         */
        public Builder withWorldTint(String tintId) {
            requireOverlays("withWorldTint");
            this.worldTintChannels.add(tintId);
            return this;
        }

        /**
         * Вмикає чат-модуль (план, п. 3.9 + 3.29) — заміна
         * {@code ServerChatHandler} + {@code ChatHistoryScreen} +
         * {@code ChatScreenInterceptHandler} + {@code MixinChatComponent}
         * snipers_shaurma. Показ повідомлень типово йде через
         * {@link #withOverlays()}-фід (типово той самий, що вже
         * зареєстрований для kill-feed) — вимагає {@link #withOverlays()}
         * так само, як {@link #withActionBarMessages()}, <b>якщо</b>
         * {@link #withChatDisplaySink} НЕ викликано. Якщо консюмер уже
         * має власний notification overlay (як
         * {@code AlertNotificationOverlay} у snipers) і хоче рендерити
         * чат ним, а не переходити на бібліотечний overlay-рушій —
         * викличте {@link #withChatDisplaySink} ДО {@code build()}, і
         * вимога {@code withOverlays()} знімається.
         * <p>
         * <b>Виправлений баг оригіналу</b> (двічі, сервер + клієнт):
         * подвійний провідний {@code '/'} (наприклад {@code "//tp @s"})
         * мовчки не виконувався, бо і {@code ServerChatHandler}, і
         * {@code ChatHistoryScreen} знімали рівно ОДИН символ {@code '/'}
         * замість усіх — див. {@code ChatFormatEngine.stripLeadingSlashes}.
         * Тут це виправлено на обох сторонах одночасно.
         *
         * @param teamContext міст до команд/кольорів консюмера, або
         *                    {@code null} для чисто-global чату (соло-
         *                    режими без команд взагалі).
         * @param feedId      {@link #withOverlays()}-фід, у який пушити
         *                    вхідні повідомлення (типово kill-feed).
         *                    Ігнорується, якщо {@link #withChatDisplaySink}
         *                    також викликано — можна передати {@code null}
         *                    у цьому випадку.
         */
        public Builder withChat(TeamChatContext teamContext, String feedId) {
            if (chatDisplaySink == null) {
                requireOverlays("withChat");
            }
            this.chatTeamContext = teamContext;
            this.chatFeedId = feedId;
            this.chatEnabled = true;
            return this;
        }

        /**
         * Підключає власний рендер показу чат-повідомлень замість
         * дефолтного бібліотечного {@link dev.shaurmalib.forge.overlay.AlertNotificationSystem}
         * фіду — консюмер отримує вже готовий, відформатований
         * {@code Component} + accent-колір і сам вирішує, як і де його
         * намалювати (напр. через уже наявний власний notification
         * overlay). Дані (форматування на сервері, історія для T-екрана,
         * командний routing) лишаються повністю бібліотечними незалежно
         * від того, викликано цей метод чи ні — змінюється лише те, ЯК
         * повідомлення з'являється на екрані клієнта.
         * <p>
         * Викликати ДО {@link #withChat}, або в будь-якому порядку разом
         * з ним — обидва лише встановлюють поля {@code Builder}, реальне
         * підключення відбувається в {@link #build()}. Якщо викликано —
         * {@link #withChat} більше не потребує {@link #withOverlays()}.
         *
         * @param sink рендер показу повідомлення; {@code null} — явно
         *             скинути на дефолтну бібліотечну поведінку.
         */
        public Builder withChatDisplaySink(dev.shaurmalib.forge.chat.ChatModule.ChatDisplaySink sink) {
            this.chatDisplaySink = sink;
            return this;
        }

        /**
         * Вмикає радіо-модуль (план, п. 3.33) — заміна
         * {@code RadioDialogConfig}/{@code RadioDialogManager}/
         * {@code RadioDialogOverlay}/{@code RadioDialogSoundManager}/
         * {@code SoundCategoryDucker} snipers_shaurma. Показ репліки
         * (текст + 3D-іконка предмету-диктора над хотбаром) підключається
         * як жилець {@link OverlayEngine} — вимагає {@link #withOverlays()}.
         * <p>
         * Завантаження {@code radio_dialogs.yml} НЕ відбувається тут —
         * викличте окремо {@code dev.shaurmalib.forge.radio.RadioDialogConfigLoader.load(handle.configModule().configTree())}
         * після {@code build()} (тобто маючи вже готовий {@link Handle}) і
         * після того, як консюмер додав {@code "radio_dialogs.yml"} до
         * {@code sharedFile(...)} свого {@code ModeConfigDescriptor} —
         * типово підписане на {@code handle.configModule().reloadBus()}
         * так само, як решта конфіг-класів консюмера (loot/kits/items).
         * Репліки диктора лишаються спільним ({@code sharedFiles}) файлом
         * namespace, не per-mode.
         * <p>
         * Серверний trigger-API ({@code RadioDialogManager.trigger(...)}/
         * {@code broadcast(...)}) доступний одразу після {@link #build()},
         * незалежно від того, чи вже завантажено конфіг — виклик на
         * невідомий ключ лише пише попередження в лог, як в оригіналі.
         *
         * @param volumeProvider постачальник гучності голосу диктора
         *                       (0..1), типово прив'язаний до власного
         *                       слайдера консюмера; {@code null} —
         *                       завжди повна гучність.
         * @param startSound     звук "клац" на старті репліки (jar-ресурс
         *                       консюмера), {@code null} — пропустити крок.
         * @param noiseSound     циклічний фоновий шум рації на час
         *                       голосу, {@code null} — без шуму.
         * @param endSound       звук завершення репліки, {@code null} — пропустити.
         * @param overlayVisible умова показу overlay (опції відео,
         *                       death-камера консюмера тощо);
         *                       {@code () -> true}, якщо немає таких умов.
         */
        public Builder withRadio(RadioVoiceVolumeProvider volumeProvider,
                                  SoundEvent startSound, SoundEvent noiseSound, SoundEvent endSound,
                                  BooleanSupplier overlayVisible) {
            requireOverlays("withRadio");
            this.radioEnabled = true;
            this.radioVolumeProvider = volumeProvider;
            this.radioStartSound = startSound;
            this.radioNoiseSound = noiseSound;
            this.radioEndSound = endSound;
            this.radioOverlayVisible = overlayVisible != null ? overlayVisible : () -> true;
            return this;
        }

        private void requireOverlays(String callerMethod) {
            if (!overlaysEnabled) {
                throw new IllegalStateException(
                        callerMethod + "() вимагає попереднього виклику withOverlays() на Builder.");
            }
        }

        private void requireConfigModule(String callerMethod) {
            if (configModule == null) {
                throw new IllegalStateException(
                        callerMethod + "() вимагає попереднього виклику withConfig(...) — " +
                        "модуль конфігів має бути ініціалізований першим.");
            }
        }

        private void requireModeContractModule(String callerMethod) {
            if (modeContractModule == null) {
                throw new IllegalStateException(
                        callerMethod + "() вимагає попереднього виклику withModeContract(...) на Builder.");
            }
        }

        private void requireLifecycleModule(String callerMethod) {
            if (lifecycleModule == null) {
                throw new IllegalStateException(
                        callerMethod + "() вимагає попереднього виклику withLifecycle(...) на Builder.");
            }
        }

        public Handle build() {
            if (animatedItemsEnabled && phantomSlotBridge != null) {
                ItemAnimationEngine.registerPhantomSlotBridge(phantomSlotBridge);
            }
            if (actionBarEnabled) {
                ActionBarMessageSystem.attach();
            }
            if (animatedCountdownEnabled) {
                AnimatedCountdownSystem.attach();
            }
            for (String tintId : worldTintChannels) {
                WorldTintOverlay.attach(tintId);
            }
            if (chatEnabled) {
                ChatModule.attachDisplaySink(chatDisplaySink);
                ChatModule.attach(chatTeamContext, chatFeedId);
            }
            if (radioEnabled) {
                RadioAudioDucking.configure(radioVolumeProvider, radioStartSound, radioNoiseSound, radioEndSound);
                RadioDialogOverlay.attach(radioOverlayVisible);
            }
            if (graffitiEnabled) {
                dev.shaurmalib.forge.graffiti.GraffitiSyncManager.bind(
                        new dev.shaurmalib.forge.graffiti.GraffitiFileStore(graffitiNamespace));
            }
            return new Handle(consumerModId, eventBus, teleportEnabled, animatedItemsEnabled,
                    playerFreezeEnabled, playerAnimEnabled, freeCameraEnabled, overlaysEnabled, actionBarEnabled, animatedCountdownEnabled,
                    soundEnabled, chatEnabled, radioEnabled, mixinsConfigured, damageGuardEnabled, invisibleZonesEnabled,
                    animatedBlocksEnabled, skinnableEntitiesEnabled, graffitiEnabled, animationRecordingEnabled, spectatorEnabled, screenEffectsEnabled,
                    configModule, modeContractModule,
                    lifecycleModule, licenseModule, lobbyModule, playerLifecycleModule, interactionLockModule,
                    modeSettingsModule);
        }
    }

    /**
     * Пара (миксин, чи вмикати) для {@link Builder#withMixins}. Статичні
     * фабрики {@link #enable}/{@link #disable} читаються природніше
     * інлайн у виклику білдера, ніж голий boolean-параметр.
     */
    public static final class MixinToggle {
        private final dev.shaurmalib.common.mixin.MixinId id;
        private final boolean enabled;

        private MixinToggle(dev.shaurmalib.common.mixin.MixinId id, boolean enabled) {
            this.id = id;
            this.enabled = enabled;
        }

        public static MixinToggle enable(dev.shaurmalib.common.mixin.MixinId id) {
            return new MixinToggle(id, true);
        }

        public static MixinToggle disable(dev.shaurmalib.common.mixin.MixinId id) {
            return new MixinToggle(id, false);
        }

        public dev.shaurmalib.common.mixin.MixinId id() {
            return id;
        }

        public boolean enabled() {
            return enabled;
        }
    }

    /**
     * Точка підключення {@link OverlayEngine} до Forge — окремий статичний
     * метод, а не частина {@link Builder}/{@link Handle}, бо
     * {@code RegisterGuiOverlaysEvent} приходить на клієнтському
     * mod-event bus у своїй власній точці життєвого циклу, не в
     * конструкторі {@code @Mod}-класу. Викликати з
     * {@code @SubscribeEvent}-методу споживача незалежно від того, коли
     * було зроблено {@link ShaurmaLib.Builder#withOverlays()}.
     */
    public static void attachOverlayEngine(RegisterGuiOverlaysEvent event) {
        OverlayEngine.attach(event);
    }

    /**
     * Точка підключення {@link ChatScreenInterceptHandler} до Forge —
     * той самий патерн, що {@link #attachOverlayEngine}. Викликати з
     * {@code @SubscribeEvent}-методу консюмера на {@code ScreenEvent.Opening}:
     * <pre>{@code
     * @SubscribeEvent
     * static void onScreenOpening(ScreenEvent.Opening event) {
     *     ShaurmaLib.attachChatScreenIntercept(event,
     *         () -> ClientGameState.teamMode,
     *         visible -> CustomTabOverlay.forceVisible = visible);
     * }
     * }</pre>
     *
     * @param teamModeAvailable      чи показувати кнопки Global/Team у T-екрані.
     * @param tabOverlayForceVisible консюмерський tab-list тримається
     *                               видимим, поки {@code true}; передайте
     *                               {@code b -> {}}, якщо такого оверлею немає.
     */
    public static void attachChatScreenIntercept(ScreenEvent.Opening event,
                                                   BooleanSupplier teamModeAvailable,
                                                   Consumer<Boolean> tabOverlayForceVisible) {
        ChatScreenInterceptHandler.onScreenOpening(event, teamModeAvailable, tabOverlayForceVisible);
    }

    /**
     * Підключає {@link VanillaHudCancelModule} (план, п. 3.17 —
     * {@code MixinId.VANILLA_HUD_CANCEL}) — приховує ванільний хотбар/HP/
     * ситість/досвід/броню (замінені кастомними оверлеями консюмера) і,
     * опційно, ванільний чат. Викликати один раз, типово одразу після
     * {@link Builder#build()}, разом з іншими клієнтськими
     * {@code attachXxx}-точками:
     * <pre>{@code
     * ShaurmaLib.attachVanillaHudCancel(() -> chatModuleEnabled);
     * }</pre>
     * Безпечно викликати повторно — фактична підписка на event bus
     * відбувається лише один раз. На відміну від решти
     * {@code attachXxx}-методів, не прив'язаний до конкретної Forge-події
     * (сам реєструє себе на {@code MinecraftForge.EVENT_BUS}), тому не
     * приймає параметр події.
     *
     * @param chatActive {@code true}, якщо консюмер підключив бібліотечний
     *                   чат-модуль ({@code withChat(...)}) і він лишається
     *                   єдиним видимим фідом повідомлень — тоді ванільний
     *                   чат теж ховається, щоб уникнути дублювання.
     *                   {@code () -> false}, якщо чат-модуль не
     *                   підключено (ванільний чат лишається видимим).
     */
    public static void attachVanillaHudCancel(BooleanSupplier chatActive) {
        VanillaHudCancelModule.attach(chatActive);
    }

    /**
     * Оброблений handle з доступом до активованих модулів. Виклик гетера
     * ще не активованого модуля кидає {@link IllegalStateException} із
     * зрозумілим повідомленням, яку саме {@code withXxx(...)} треба було
     * викликати на {@link Builder} — це навмисно голосно, а не мовчазний
     * null, щоб помилка інтеграції виявлялась одразу при старті мода, а
     * не глибоко в рантаймі під час матчу.
     */
    public static final class Handle {
        private final String consumerModId;
        private final IEventBus eventBus;
        private final boolean teleportEnabled;
        private final boolean animatedItemsEnabled;
        private final boolean playerFreezeEnabled;
        private final boolean playerAnimEnabled;
        private final boolean freeCameraEnabled;
        private final boolean overlaysEnabled;
        private final boolean actionBarEnabled;
        private final boolean animatedCountdownEnabled;
        private final boolean soundEnabled;
        private final boolean chatEnabled;
        private final boolean radioEnabled;
        private final boolean mixinsConfigured;
        private final boolean damageGuardEnabled;
        private final boolean invisibleZonesEnabled;
        private final boolean animatedBlocksEnabled;
        private final boolean skinnableEntitiesEnabled;
        private final boolean graffitiEnabled;
        private final boolean animationRecordingEnabled;
        private final boolean spectatorEnabled;
        private final boolean screenEffectsEnabled;
        private final ConfigModule configModule;
        private final ModeContractModule modeContractModule;
        private final LifecycleModule lifecycleModule;
        private final LicenseModule licenseModule;
        private final LobbyModule lobbyModule;
        private final PlayerLifecycleModule playerLifecycleModule;
        private final InteractionLockModule interactionLockModule;
        private final ModeSettingsModule modeSettingsModule;

        private Handle(String consumerModId, IEventBus eventBus, boolean teleportEnabled,
                        boolean animatedItemsEnabled, boolean playerFreezeEnabled, boolean playerAnimEnabled, boolean freeCameraEnabled,
                        boolean overlaysEnabled,
                        boolean actionBarEnabled, boolean animatedCountdownEnabled, boolean soundEnabled,
                        boolean chatEnabled,
                        boolean radioEnabled, boolean mixinsConfigured, boolean damageGuardEnabled,
                        boolean invisibleZonesEnabled, boolean animatedBlocksEnabled,
                        boolean skinnableEntitiesEnabled, boolean graffitiEnabled,
                        boolean animationRecordingEnabled,
                        boolean spectatorEnabled, boolean screenEffectsEnabled,
                        ConfigModule configModule,
                        ModeContractModule modeContractModule, LifecycleModule lifecycleModule,
                        LicenseModule licenseModule, LobbyModule lobbyModule,
                        PlayerLifecycleModule playerLifecycleModule, InteractionLockModule interactionLockModule,
                        ModeSettingsModule modeSettingsModule) {
            this.consumerModId = consumerModId;
            this.eventBus = eventBus;
            this.teleportEnabled = teleportEnabled;
            this.animatedItemsEnabled = animatedItemsEnabled;
            this.playerFreezeEnabled = playerFreezeEnabled;
            this.playerAnimEnabled = playerAnimEnabled;
            this.freeCameraEnabled = freeCameraEnabled;
            this.overlaysEnabled = overlaysEnabled;
            this.actionBarEnabled = actionBarEnabled;
            this.animatedCountdownEnabled = animatedCountdownEnabled;
            this.soundEnabled = soundEnabled;
            this.chatEnabled = chatEnabled;
            this.radioEnabled = radioEnabled;
            this.mixinsConfigured = mixinsConfigured;
            this.damageGuardEnabled = damageGuardEnabled;
            this.invisibleZonesEnabled = invisibleZonesEnabled;
            this.animatedBlocksEnabled = animatedBlocksEnabled;
            this.skinnableEntitiesEnabled = skinnableEntitiesEnabled;
            this.graffitiEnabled = graffitiEnabled;
            this.animationRecordingEnabled = animationRecordingEnabled;
            this.spectatorEnabled = spectatorEnabled;
            this.screenEffectsEnabled = screenEffectsEnabled;
            this.configModule = configModule;
            this.modeContractModule = modeContractModule;
            this.lifecycleModule = lifecycleModule;
            this.licenseModule = licenseModule;
            this.lobbyModule = lobbyModule;
            this.playerLifecycleModule = playerLifecycleModule;
            this.interactionLockModule = interactionLockModule;
            this.modeSettingsModule = modeSettingsModule;
        }

        public String consumerModId() {
            return consumerModId;
        }

        public IEventBus eventBus() {
            return eventBus;
        }

        /**
         * {@link TeleportService} — статичний сервіс без стану; цей гетер
         * лише підтверджує, що модуль було свідомо підключено через
         * {@code withTeleport()}.
         */
        public Class<TeleportService> teleportService() {
            if (!teleportEnabled) {
                throw new IllegalStateException("TeleportService не підключено — викличте withTeleport() на Builder.");
            }
            return TeleportService.class;
        }

        /**
         * {@link PlayerFreezeService} — статичний сервіс без стану екземпляра
         * (як {@link TeleportService}); гетер лише підтверджує свідоме
         * підключення через {@code withPlayerFreeze()}.
         */
        public Class<PlayerFreezeService> playerFreezeService() {
            if (!playerFreezeEnabled) {
                throw new IllegalStateException("PlayerFreezeService не підключено — викличте withPlayerFreeze() на Builder.");
            }
            return PlayerFreezeService.class;
        }

        /**
         * {@link PlayerPoseController} — статичний сервіс без стану
         * екземпляра (як {@link #teleportService()} і {@link
         * #playerFreezeService()}); гетер лише підтверджує, що {@code
         * withPlayerAnim()} було свідомо викликано. Реальні виклики
         * ({@code trigger}/{@code stop}/{@code stopAll}) відбуваються
         * напряму на статичному класі з клієнтського коду консюмера
         * (вхід у літак/зіплайн/лежача смерть — той самий момент, коли
         * оригінал викликав {@code PlayerAnimDropBridge.play(...)}).
         */
        public Class<PlayerPoseController> playerPoseController() {
            if (!playerAnimEnabled) {
                throw new IllegalStateException("PlayerPoseController не підключено — викличте withPlayerAnim() на Builder.");
            }
            return PlayerPoseController.class;
        }

        /**
         * Підтверджує підключення пакету "вільна камера" (п. 3.15).
         * Класи пакету ({@code CameraOwnershipRegistry},
         * {@code FreeCameraEntity}, {@code StationaryCameraController},
         * {@code EntitySpectateController}, {@code CinematicPathController},
         * {@code FreeFlyCameraController}) — самі по собі не статичні
         * реєстри без стану екземпляра (окрім {@code CameraOwnershipRegistry},
         * що навмисно статичний — стек власників один на весь клієнт), тому
         * консюмер створює власні екземпляри контролерів напряму; цей
         * гетер лише підтверджує свідоме підключення пакету, як і решта
         * {@code withXxx()}-модулів.
         */
        public boolean freeCameraEnabled() {
            return freeCameraEnabled;
        }

        /**
         * {@code true}, якщо консюмер явно викликав
         * {@link Builder#withMixins} хоча б раз. Не є передумовою для
         * коректної роботи toggle-миксинів — {@link
         * dev.shaurmalib.common.mixin.MixinToggleRegistry} за
         * замовчуванням тримає всі миксини увімкненими, навіть якщо
         * {@code withMixins(...)} не викликано взагалі; цей гетер лише
         * документує намір, як і решта простих boolean-підтверджень.
         */
        public boolean mixinsConfigured() {
            return mixinsConfigured;
        }

        /**
         * Підтверджує підключення власного блокування урону (план,
         * п. 3.26). {@code DamageInterceptorRegistry} — статичний
         * реєстр без стану ініціалізації (як {@link #teleportService()}),
         * тому цей гетер повертає клас лише як підтвердження свідомого
         * підключення через {@code withDamageGuard()} — реальна
         * реєстрація конкретних причин відбувається напряму через
         * {@code DamageInterceptorRegistry.register(id, interceptor)}.
         */
        public Class<dev.shaurmalib.common.damage.DamageInterceptorRegistry> damageGuardModule() {
            if (!damageGuardEnabled) {
                throw new IllegalStateException(
                        "Damage-guard модуль не підключено — викличте withDamageGuard() на Builder.");
            }
            return dev.shaurmalib.common.damage.DamageInterceptorRegistry.class;
        }

        /**
         * Підтверджує підключення "сутність-зона без хітбоксу" (план,
         * п. 3.28). {@link dev.shaurmalib.forge.entity.invisiblezone.InvisibleZoneEntityBase}
         * не має власного реєстру екземплярів (консюмер сам реєструє
         * {@code EntityType} через свій {@code DeferredRegister}), тому цей
         * гетер лише підтверджує свідоме підключення через
         * {@code withInvisibleZones()} — той самий принцип, що
         * {@link #damageGuardModule()} — перш ніж консюмер почне
         * розширювати базовий клас у власному підкласі-сутності.
         */
        public Class<dev.shaurmalib.forge.entity.invisiblezone.InvisibleZoneEntityBase> invisibleZoneModule() {
            if (!invisibleZonesEnabled) {
                throw new IllegalStateException(
                        "InvisibleZone-модуль не підключено — викличте withInvisibleZones() на Builder.");
            }
            return dev.shaurmalib.forge.entity.invisiblezone.InvisibleZoneEntityBase.class;
        }

        /**
         * Підтверджує підключення "анімований GeckoLib-блок" (план,
         * п. 3.27). {@link dev.shaurmalib.forge.blocks.AnimatedBlockEntityBase}
         * не має власного реєстру екземплярів (консюмер сам реєструє
         * {@code BlockEntityType} через свій {@code DeferredRegister}), тому
         * цей гетер лише підтверджує свідоме підключення через
         * {@code withAnimatedBlocks()} — той самий принцип, що
         * {@link #invisibleZoneModule()} — перш ніж консюмер почне
         * розширювати базовий клас у власному підкласі.
         */
        public Class<dev.shaurmalib.forge.blocks.AnimatedBlockEntityBase> animatedBlockModule() {
            if (!animatedBlocksEnabled) {
                throw new IllegalStateException(
                        "AnimatedBlock-модуль не підключено — викличте withAnimatedBlocks() на Builder.");
            }
            return dev.shaurmalib.forge.blocks.AnimatedBlockEntityBase.class;
        }

        /**
         * Підтверджує підключення "сутність з рантайм-скіном гравця"
         * (план, п. 3.22). {@link dev.shaurmalib.forge.entity.skinnable.SkinnableEntityBase}
         * не має власного реєстру екземплярів (консюмер сам реєструє
         * {@code EntityType} через свій {@code DeferredRegister}), тому цей
         * гетер лише підтверджує свідоме підключення через
         * {@code withSkinnableEntities()} — той самий принцип, що
         * {@link #animatedBlockModule()} і {@link #invisibleZoneModule()}.
         */
        public Class<dev.shaurmalib.forge.entity.skinnable.SkinnableEntityBase> skinnableEntityModule() {
            if (!skinnableEntitiesEnabled) {
                throw new IllegalStateException(
                        "SkinnableEntity-модуль не підключено — викличте withSkinnableEntities() на Builder.");
            }
            return dev.shaurmalib.forge.entity.skinnable.SkinnableEntityBase.class;
        }

        /**
         * Підтверджує підключення движка графіті (план, п. 3.14) і
         * повертає {@link dev.shaurmalib.forge.graffiti.GraffitiFileStore},
         * прив'язаний до namespace, переданого у {@code withGraffiti(...)}
         * — консюмеру він потрібен напряму (наприклад, щоб отримати
         * {@code listAvailable(server)} для власного меню вибору PNG у
         * canvas-редакторі, який лишається продуктовим кодом консюмера в
         * цьому переносі — див. {@link dev.shaurmalib.forge.graffiti.GraffitiBlockBase}).
         * {@code GraffitiBlockBase}/{@code GraffitiBlockEntityBase} не
         * мають власного реєстру екземплярів (консюмер сам реєструє
         * {@code Block}/{@code Item}/{@code BlockEntityType} через власний
         * {@code DeferredRegister}), тому цей гетер лише підтверджує
         * свідоме підключення через {@code withGraffiti(namespace)} — той
         * самий принцип, що {@link #animatedBlockModule()} і
         * {@link #skinnableEntityModule()}.
         */
        public dev.shaurmalib.forge.graffiti.GraffitiFileStore graffitiModule() {
            if (!graffitiEnabled) {
                throw new IllegalStateException(
                        "Graffiti-модуль не підключено — викличте withGraffiti(namespace) на Builder.");
            }
            return dev.shaurmalib.forge.graffiti.GraffitiSyncManager.fileStore();
        }

        /**
         * Підтверджує підключення {@link dev.shaurmalib.forge.animation.AnimationRecordFacade}
         * (план, п. 3.16). Статичний рушій без стану ініціалізації (як
         * {@link #teleportService()}); гетер лише підтверджує свідоме
         * підключення через {@code withAnimationRecording()} — реальні
         * виклики {@code startAbsolute(...)}/{@code startDelta(...)}/
         * {@code onServerTick(...)} відбуваються напряму на статичному класі.
         */
        public Class<dev.shaurmalib.forge.animation.AnimationRecordFacade> animationRecordFacade() {
            if (!animationRecordingEnabled) {
                throw new IllegalStateException(
                        "AnimationRecordFacade не підключено — викличте withAnimationRecording() на Builder.");
            }
            return dev.shaurmalib.forge.animation.AnimationRecordFacade.class;
        }

        /**
         * Підтверджує підключення {@link dev.shaurmalib.forge.spectator.SpectatorSessionController}
         * (план, п. 3.34). Статичний рушій без стану ініціалізації (як
         * {@link #teleportService()}); гетер лише підтверджує свідоме
         * підключення через {@code withSpectator()}.
         */
        public Class<dev.shaurmalib.forge.spectator.SpectatorSessionController> spectatorModule() {
            if (!spectatorEnabled) {
                throw new IllegalStateException(
                        "Spectator-модуль не підключено — викличте withSpectator() на Builder.");
            }
            return dev.shaurmalib.forge.spectator.SpectatorSessionController.class;
        }

        /**
         * Підтверджує підключення {@link dev.shaurmalib.forge.fx.ScreenEffectPostChain}
         * (план, п. 3.23). Статичний рушій без стану ініціалізації (як
         * {@link #teleportService()}); гетер лише підтверджує свідоме
         * підключення через {@code withScreenEffects()}.
         */
        public Class<dev.shaurmalib.forge.fx.ScreenEffectPostChain> screenEffectModule() {
            if (!screenEffectsEnabled) {
                throw new IllegalStateException(
                        "ScreenEffectPostChain не підключено — викличте withScreenEffects() на Builder.");
            }
            return dev.shaurmalib.forge.fx.ScreenEffectPostChain.class;
        }

        public ConfigModule configModule() {
            if (configModule == null) {
                throw new IllegalStateException("Config-модуль не підключено — викличте withConfig(...) на Builder.");
            }
            return configModule;
        }

        public ModeContractModule modeContractModule() {
            if (modeContractModule == null) {
                throw new IllegalStateException("Mode-контракт не підключено — викличте withModeContract(...) на Builder.");
            }
            return modeContractModule;
        }

        public LifecycleModule lifecycleModule() {
            if (lifecycleModule == null) {
                throw new IllegalStateException("Lifecycle-модуль не підключено — викличте withLifecycle(...) на Builder.");
            }
            return lifecycleModule;
        }

        /**
         * {@link PlayerLifecycleModule} (п. 3.8) — join/return-політика
         * конкретного гравця, окремо від {@link #lifecycleModule()}
         * (грубий стан матчу в цілому). Див. докстрінг
         * {@link Builder#withPlayerLifecycle}.
         */
        public PlayerLifecycleModule playerLifecycleModule() {
            if (playerLifecycleModule == null) {
                throw new IllegalStateException(
                        "PlayerLifecycle-модуль не підключено — викличте withPlayerLifecycle(...) на Builder.");
            }
            return playerLifecycleModule;
        }

        public LicenseModule licenseModule() {
            if (licenseModule == null) {
                throw new IllegalStateException("License-модуль не підключено — викличте withLicense(...) на Builder.");
            }
            return licenseModule;
        }

        /**
         * {@link InteractionLockModule} (п. 3.25) — доступний на
         * {@link Handle}, а не лише на {@link Builder}, бо консюмер
         * зазвичай тримає {@code Handle} довготривало (в полі свого
         * {@code @Mod}-класу) і викликає {@code lock}/{@code unlock} з
         * будь-якого місця коду (Phase-класи, phantom-режими), а не лише
         * в момент побудови.
         */
        public InteractionLockModule interactionLockModule() {
            if (interactionLockModule == null) {
                throw new IllegalStateException(
                        "InteractionLock-модуль не підключено — викличте withInteractionLock() на Builder.");
            }
            return interactionLockModule;
        }

        public LobbyModule lobbyModule() {
            if (lobbyModule == null) {
                throw new IllegalStateException("Lobby-модуль не підключено — викличте withLobby(...) на Builder.");
            }
            return lobbyModule;
        }

        /**
         * {@link ModeSettingsModule} (план, п. 3.32) — фабрика
         * {@code ModeSettingsScreen}. Доступний на {@link Handle}, а не
         * лише на {@link Builder}, бо консюмер типово тримає
         * {@code Handle} довготривало і викликає
         * {@code modeSettingsModule().newScreen(title)} з мережевого
         * хендлера, не в момент побудови.
         */
        public ModeSettingsModule modeSettingsModule() {
            if (modeSettingsModule == null) {
                throw new IllegalStateException(
                        "ModeSettings-модуль не підключено — викличте withModeSettings(...) на Builder.");
            }
            return modeSettingsModule;
        }

        /**
         * Підтверджує, що {@code withAnimatedItems()} було викликано.
         * {@link ItemAnimationEngine} — статичний рушій без стану екземпляра
         * (як і {@link TeleportService}), тому тут лише прапорець свідомого
         * підключення; сам рушій викликається напряму через його статичні
         * методи ({@code ItemAnimationEngine.startUse(...)} і т.д.).
         */
        public Class<ItemAnimationEngine> animatedItemsEngine() {
            if (!animatedItemsEnabled) {
                throw new IllegalStateException(
                        "Animated-item модуль не підключено — викличте withAnimatedItems() на Builder.");
            }
            return ItemAnimationEngine.class;
        }

        /**
         * Підтверджує підключення {@link OverlayEngine} (п. 3.4). Сам рушій
         * — статичний клас без стану; підключення реального
         * {@code IGuiOverlay} до Forge відбувається окремо через
         * {@link ShaurmaLib#attachOverlayEngine}, не тут.
         */
        public Class<OverlayEngine> overlayEngine() {
            if (!overlaysEnabled) {
                throw new IllegalStateException("Overlay-рушій не підключено — викличте withOverlays() на Builder.");
            }
            return OverlayEngine.class;
        }

        public Class<ActionBarMessageSystem> actionBarMessageSystem() {
            if (!actionBarEnabled) {
                throw new IllegalStateException(
                        "ActionBarMessageSystem не підключено — викличте withOverlays().withActionBarMessages() на Builder.");
            }
            return ActionBarMessageSystem.class;
        }

        public Class<AnimatedCountdownSystem> animatedCountdownSystem() {
            if (!animatedCountdownEnabled) {
                throw new IllegalStateException(
                        "AnimatedCountdownSystem не підключено — викличте withOverlays().withAnimatedCountdown() на Builder.");
            }
            return AnimatedCountdownSystem.class;
        }

        /**
         * Підтверджує підключення звукового центру (план, п. 3.5). Сам
         * {@link SoundCenter} — статичний рушій без стану екземпляра, як
         * {@link TeleportService}/{@link ChatModule}; гетер лише
         * підтверджує, що {@code withSound()} було свідомо викликано —
         * реальні виклики йдуть напряму через статичні методи
         * {@code SoundCenter.play(...)}/{@code playAt(...)}/
         * {@code playUi(...)}/{@code trackMovingSource(...)}, а реєстрація
         * категорій/кʼю — через {@code SoundCategoryRegistry}/
         * {@code SoundCueRegistry} (lib-common, доступні без {@link Handle}
         * взагалі, як і решта чистих реєстрів даних лібу).
         */
        public Class<SoundCenter> soundCenter() {
            if (!soundEnabled) {
                throw new IllegalStateException("Sound-центр не підключено — викличте withSound() на Builder.");
            }
            return SoundCenter.class;
        }

        /**
         * {@link WorldTintOverlay} — статичний рушій без стану екземпляра;
         * гетер лише підтверджує, що {@link #overlaysEnabled} було ввімкнено
         * (сам {@code WorldTintOverlay} доступний завжди через overlaysEnabled,
         * бо конкретні {@code tintId}-канали реєструються по одному через
         * {@code withWorldTint(...)}, а не єдиним прапорцем).
         */
        public Class<WorldTintOverlay> worldTintOverlay() {
            if (!overlaysEnabled) {
                throw new IllegalStateException("Overlay-рушій не підключено — викличте withOverlays() на Builder.");
            }
            return WorldTintOverlay.class;
        }

        /**
         * Підтверджує підключення {@link ChatModule} (план, п. 3.9 + 3.29).
         * Сам модуль — статичний рушій без стану екземпляра, як і решта
         * (TeleportService, ItemAnimationEngine); підключення реального
         * {@code ScreenEvent.Opening}-перехоплювача до Forge відбувається
         * окремо через {@link ShaurmaLib#attachChatScreenIntercept}, не тут
         * — той самий патерн, що {@link ShaurmaLib#attachOverlayEngine} для
         * {@link #overlayEngine()}, бо подія приходить у власній точці
         * життєвого циклу клієнтських подій, не в конструкторі {@code @Mod}.
         */
        public Class<ChatModule> chatModule() {
            if (!chatEnabled) {
                throw new IllegalStateException("Chat-модуль не підключено — викличте withChat(...) на Builder (і withOverlays() перед ним, якщо withChatDisplaySink(...) не використовується).");
            }
            return ChatModule.class;
        }

        /**
         * Підтверджує підключення радіо-модуля (план, п. 3.33). Сам
         * {@link RadioDialogManager} — статичний рушій без стану
         * екземпляра (як {@code TeleportService}/{@code ChatModule});
         * гетер лише підтверджує, що {@code withRadio(...)} було
         * викликано, і документує залежність у {@code Handle} так само,
         * як решта модулів лібу.
         */
        public Class<RadioDialogManager> radioDialogManager() {
            if (!radioEnabled) {
                throw new IllegalStateException("Radio-модуль не підключено — викличте withOverlays().withRadio(...) на Builder.");
            }
            return RadioDialogManager.class;
        }

        /**
         * {@link dev.shaurmalib.forge.menu.AnimatedMenuButton} /
         * {@link dev.shaurmalib.forge.menu.AnimatedMenuTab} (план, п. 3.3,
         * {@code MenuStyleTheme}) — перенесені {@code OverlayBtn}/
         * {@code OverlayTab} snipers_shaurma, готові до використання в
         * будь-якому кастомному {@code Screen} консюмера разом з
         * {@link dev.shaurmalib.common.overlay.ScreenOpenAnimator} (заміна
         * {@code GameSettingsScreen.animProgress}). На відміну від решти
         * overlay-підсистем ці класи — прямі конструктори без реєстрації
         * events, тому не вимагають {@link #withOverlays()}: гетер лише
         * підтверджує намір консюмера і документує залежність у
         * {@code Handle}, як і решта модулів лібу.
         */
        public Class<dev.shaurmalib.common.overlay.ScreenOpenAnimator> menuAnimator() {
            return dev.shaurmalib.common.overlay.ScreenOpenAnimator.class;
        }
    }
}