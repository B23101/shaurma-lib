# ShaurmaLib — як підключити та як користуватись кожним модулем

Бібліотека спільних систем для Forge-модів **Minecraft 1.20.1**.
Координати артефакту:

```
dev.shaurmalib:shaurma-lib-forge:0.1.0-SNAPSHOT
```

Референсний приклад використання — мод `snipers_shaurma` (усі модулі нижче
там реально підключені й працюють).

> **Версія довідника:** 2026-09-14. Цей файл описує публічний спосіб
> підключення бібліотеки, порядок ініціалізації, залежності між модулями,
> серверні та клієнтські API, lifecycle і правила безпечного використання.
> Усі системи opt-in: наявність jar сама по собі не вмикає ігрову механіку.

---

## 0. Головний принцип

**Нічого не активується без явного `withXxx(...)`.** Модуль, який не
запросили, не реєструє ні слухачів подій, ні мережевих пакетів, ні конфігів.
Якщо звернутись до гетера непідключеного модуля на `Handle`
(`lib.spectatorModule()` без `withSpectator()`) — отримаєте
`IllegalStateException` з назвою потрібного `withXxx(...)`, а не мовчазний
`NullPointerException` десь посеред матчу.

---

## 1. Підключення

### 1.1. Обов'язкові вимоги

| Що | Значення |
|---|---|
| Minecraft | 1.20.1 |
| Forge | `1.20.1-47.3.0` |
| Mappings | `official 1.20.1` (ОБОВ'ЯЗКОВО ті самі) |
| Java | 17 (і Gradle-демон теж на 17) |
| ForgeGradle | 6.x |

Якщо ваш мод компілюється з іншими mappings — бібліотека не підійде без
перезбирання, бо jar публікується в **official (Mojang) mappings**.

### 1.2. Токен доступу

GitHub Packages **вимагає автентифікації навіть для публічних пакетів** —
анонімний запит завжди дає `401`. Потрібен Personal Access Token (classic)
зі scope **`read:packages`**.

Покласти його треба **поза репозиторієм**, щоб не закомітити:

`~/.gradle/gradle.properties` (Windows: `C:\Users\<ти>\.gradle\gradle.properties`):

```properties
gpr.user=ВАШ_НІК_НА_GITHUB
gpr.key=ghp_ВАШ_ТОКЕН
```

У CI (GitHub Actions) ці змінні підставляються автоматично з
`GITHUB_ACTOR` / `GITHUB_TOKEN`.

### 1.3. `build.gradle` вашого мода

```gradle
plugins {
    id 'eclipse'
    id 'idea'
    id 'net.minecraftforge.gradle' version '[6.0.16,6.2)'
    id 'org.spongepowered.mixin' version '0.7.+'
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

minecraft {
    mappings channel: 'official', version: '1.20.1'
}

repositories {
    mavenCentral()

    // ── shaurma-lib з GitHub Packages ────────────────────────────────────
    maven {
        name = 'GitHubPackagesShaurmaLib'
        url = uri('https://maven.pkg.github.com/B23101/shaurma-lib')
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = findProperty('gpr.key')  ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

// SNAPSHOT — "changing module": за замовчуванням Gradle кешує його 24 год.
// Скидаємо TTL у нуль, щоб кожна збірка питала свіжий SNAPSHOT.
configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor 0, 'seconds'
    }
}

dependencies {
    minecraft "net.minecraftforge:forge:1.20.1-47.3.0"
    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'

    // ГОЛОВНА залежність. lib-common і snakeyaml уже всередині цього jar.
    implementation 'dev.shaurmalib:shaurma-lib-forge:0.1.0-SNAPSHOT'
}
```

> #### SnakeYAML — беріть його з бібліотеки, а не з Maven
>
> Бібліотека вшиває SnakeYAML 2.2 **релокованим** у пакет
> `dev.shaurmalib.libs.snakeyaml`, тому у своєму коді пишете:
>
> ```java
> import dev.shaurmalib.libs.snakeyaml.Yaml;
> import dev.shaurmalib.libs.snakeyaml.DumperOptions;
> import dev.shaurmalib.libs.snakeyaml.constructor.Constructor;
> import dev.shaurmalib.libs.snakeyaml.representer.Representer;
> // замість org.yaml.snakeyaml.*
> ```
>
> **НЕ додавайте** `org.yaml:snakeyaml` собі в `dependencies` — навіть якщо код
> компілюється, у рантаймі він буде недоступний. Причина не в стилі, а в тому,
> як Forge вантажить класи у dev-запусках (`./gradlew runServer`/`runClient`):
> класи мода вантажаться `TransformingClassLoader`-ом із `parent=null`, який
> бачить ЛИШЕ *game layer* — mod-jar'и (`shaurma-lib-forge`, `tacz`, `geckolib`…)
> плюс minecraft-бібліотеки. Усе, що ви підключили звичайним
> `implementation`/`compileOnly` і що не має свого `mods.toml`, лежить на `-cp`
> і **кодам мода не видне** — отримаєте `NoClassDefFoundError` вже на старті
> (перевірено на Forge 1.20.1-47.3.0 + ForgeGradle 6). `shaurma-lib-forge` — це
> mod-jar, тому його класи доступні — разом із вшитим у нього SnakeYAML.
>
> Практичний висновок для вашого мода: **будь-яка стороння бібліотека, потрібна
> в рантаймі, має лежати всередині якогось mod-jar'а**. Вшивання у власний jar
> (`shadowJar`/``jarJar``) лікує тільки виданий `.jar` — у dev-запуску така
> копія однаково невидима (це справджується і для `org.concentus` у snipers:
> тому бібліотека -- єдиний надійний транспорт для спільних сторонніх класів).

### 1.4. «Без версії» — чому `0.1.0-SNAPSHOT` і чому це ніколи не треба правити

`0.1.0-SNAPSHOT` — це **канал**, а не реліз:

* кожен `git push` у репозиторій бібліотеки → GitHub Actions збирає й
  публікує новий SNAPSHOT (див. `.github/workflows/publish.yml`);
* Gradle бачить, що це changing module, і при кожній збірці тягне
  **найсвіжішу** збірку з GitHub Packages;
* тому у своєму `build.gradle` ви **ніколи не змінюєте версію** — просто
  перезбираєте мод (`./gradlew build`) і отримуєте актуальну бібліотеку.

Якщо треба прибити конкретний стан — вкажіть точну версію замість SNAPSHOT.

### 1.5. Не забудьте про рантайм

`shaurma-lib-forge` — це **окремий Forge-мод** (modId `shaurma_lib`), а не
просто бібліотека класів. Варіанти:

1. **Покласти jar у `/mods`** поруч зі своїм модом (найпростіше) і оголосити
   залежність у `mods.toml`:

   ```toml
   [[dependencies."ваш_mod_id"]]
   modId = "shaurma_lib"
   mandatory = true
   versionRange = "[0.1,)"
   ordering = "AFTER"
   side = "BOTH"
   ```

2. Вшити (`shade`) jar бібліотеки у свій артефакт, якщо не хочете два файли.

### 1.6. Опційні залежності

Потрібні лише тим модулям, які ви реально підключаєте:

```gradle
// потрібно для withAnimatedItems() / withAnimatedBlocks() / withSkinnableEntities()
implementation fg.deobf("software.bernie.geckolib:geckolib-forge-1.20.1:4.7.2")

// потрібно для withPlayerAnim()
implementation fg.deobf("dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20")
```

---

## 2. Швидкий старт

Уся ініціалізація — **один ланцюжок у конструкторі `@Mod`-класу**:

```java
@Mod("mymod")
public class MyMod {

    public MyMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        dev.shaurmalib.forge.ShaurmaLib.Handle lib =
                dev.shaurmalib.forge.ShaurmaLib.init("mymod", modEventBus)
                        .withTeleport()
                        .withPlayerFreeze()
                        .withConfig(worldRoot, "mymod", MyMod.class::getResourceAsStream)
                        .withModeContract("mode.yml")
                        .withLifecycle(2)
                        .withOverlays()
                        .withActionBarMessages()
                        .withSound()
                        .build();

        // далі — реєстрація предметів, команд тощо
    }
}
```

### 2.1. Повний шаблон для режиму з inventory allocation і stamina

Це робочий каркас для мода, який має конфіги, матч, кастомний inventory
screen, обмежений hotbar і stamina. Вилучайте непотрібні `.withXxx(...)`,
але не викликайте гетери модулів, які не були підключені.

```java
@Mod("mymod")
public final class MyMod {
    public MyMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ShaurmaLib.Handle lib = ShaurmaLib.init("mymod", bus)
                .withConfig(worldRoot, "mymod", MyMod.class::getResourceAsStream)
                .withTeleport()
                .withPlayerFreeze()
                .withModeContract("mode.yml")
                .withLifecycle(2)
                .withInventorySlotAllocation()
                .withStamina(StaminaRules.builder()
                        .maxStamina(100)
                        .drainPerSecond(20)
                        .recoveryPerSecond(25)
                        .emptyRecoveryDelaySeconds(3)
                        .recoveryDelaySeconds(2)
                        .forceFullHungerWhileActive(true)
                        .blockJumpWhenDepleted(true)
                        .build())
                .withOverlays()
                .withActionBarMessages()
                .withSound()
                .build();
    }
}
```

Після `build()` правила режиму застосовуються продуктовою логікою, а не
конструктором бібліотеки:

```java
// Початок гри: 4 доступні hotbar-слоти і stamina активна.
InventorySlotAllocation.setHotbarSlotCount(player, 4);
StaminaService.setRules(player, gameRules);

// Лобі: inventory можна заблокувати, stamina не витрачається.
InventorySlotAllocation.setInventoryScreenBlocked(player, true);
StaminaService.setRules(player, StaminaRules.builder().active(false).build());
```

Для клієнтських API (`setInventoryScreenFactory`, власний hotbar/HUD renderer
та `attachOverlayEngine`) код має виконуватися лише на клієнтській стороні.

`Handle` — дескриптор зі вже підключеними модулями. Гетери на ньому
потрібні лише для того, щоб дістати модуль як об'єкт (наприклад
`lib.configModule().configTree()`); більшість API статичні й викликаються
напряму з класу.

### Порядок і залежності між `withXxx(...)`

| Метод | Вимагає раніше викликаного |
|---|---|
| `withModeContract(...)` | `withConfig(...)` |
| `withModeSettings(...)` | `withModeContract(...)` і `withLifecycle(...)` |
| `withSpectator()` | `withTeleport()` |
| `withActionBarMessages()`, `withAnimatedCountdown()`, `withWorldTint(...)`, `withRadio(...)` | `withOverlays()` |
| `withChat(...)` | `withOverlays()` — **крім** випадку, коли викликано `withChatDisplaySink(...)` |
| `withModeSettings(...)` | `withModeContract(...)` і `withLifecycle(...)` |
| `withPhantomSlotBridge(...)` | `withAnimatedItems()` |

### 2.2. Повний список `Builder`-методів

| Метод | Призначення |
|---|---|
| `withConfig(...)` | YAML-конфіги та reload bus |
| `withTeleport()` | безпечна телепортація |
| `withPlayerFreeze()` | серверне замороження гравця |
| `withPlayerAnim()` | Player Animation Library-пози |
| `withInteractionLock()` | блокування дій |
| `withDamageGuard()` | фільтрація урону |
| `withAnimatedItems()` | анімовані предмети, рука й item camera |
| `withPhantomSlotBridge(...)` | anti-dupe для фантомних слотів |
| `withAnimatedBlocks()` | GeckoLib-блоки |
| `withSkinnableEntities()` | сутності з runtime skin |
| `withInvisibleZones()` | невидимі зони |
| `withSpectator()` | spectator session |
| `withAnimationRecording()` | запис animation keyframes |
| `withScreenEffects()` | post-chain ефекти |
| `withFreeCamera()` | camera ownership і freecam |
| `withGraffiti(namespace)` | серверне ядро графіті |
| `withMixins(...)` | вмикання/вимикання library mixins |
| `withModeContract(...)` | реєстр і persistence режиму |
| `withLifecycle(...)` | lifecycle матчу |
| `withModeSettings(...)` | стандартний екран налаштувань режиму |
| `withPlayerLifecycle(...)` | join/return/disconnect policy |
| `withLicense(...)` | license gate |
| `withLobby(...)` | lobby spawn і name-tag policy |
| `withOverlays()` | базовий GUI overlay engine |
| `withActionBarMessages()` | action-bar notification system |
| `withAnimatedCountdown()` | animated countdown |
| `withWorldTint(...)` | world tint channels |
| `withChat(...)` / `withChatDisplaySink(...)` | chat history і display |
| `withRadio(...)` | radio dialog, overlay і ducking |
| `withSound()` | sound center |
| `withInventorySlotAllocation()` | персональні inventory rules |
| `withStamina(...)` | server-authoritative stamina |

Порушення порядку = `IllegalStateException` на старті з назвою потрібного
методу.

---

## 3. Модулі `Builder` — повний довідник

### 3.1. `withConfig(worldRoot, namespace, resourceLoader)` — конфіги

Класи: `dev.shaurmalib.forge.config.ConfigModule`,
`dev.shaurmalib.common.config.ShaurmaConfigTree`, `YamlConfigSection`,
`ConfigReloadBus`.

Дерево файлів: `<worldRoot>/<namespace>/` — спільні `.yml` плюс підпапка
на кожен режим. Дефолти копіюються з ресурсів вашого мода **лише якщо файл
ще не існує**, наявний файл ніколи не перезаписується.

```java
.withConfig(server.getWorldPath(LevelResource.ROOT), "mymod", MyMod.class::getResourceAsStream)
```

Після `build()`:

```java
ShaurmaConfigTree tree = lib.configModule().configTree();
Path nsDir = tree.namespaceDir();            // <world>/mymod
String v   = tree.loadSingleValue("mode.yml", "mode");   // прочитати одне значення
tree.saveSingleValue("mode.yml", "mode", "br");           // записати
```

Опис набору файлів режиму — через `ModeConfigDescriptor` +
`tree.copyModeDefaults(descriptor)`.

**Підписка на перезавантаження** замість ручних `reloadX()`:

```java
lib.configModule().reloadBus().subscribe(modeId -> {
    if ("br".equals(modeId)) { /* перечитати свої конфіги */ }
});
// Listener — функціональний інтерфейс з єдиним методом onConfigReload(String modeId)
```

---

### 3.2. `withTeleport()` — телепорт

Клас: `dev.shaurmalib.forge.teleport.TeleportService` (статичний, без стану).
Всередині: гарантія завантаженого чанку, скидання падіння, безпека між
вимірами, каскад пасажирів, подія `TeleportedEvent` після завершення.

```java
TeleportService.teleport(player, level, x, y, z, yaw, pitch, TeleportReason.LOBBY_JOIN);
TeleportService.teleport(player, level, x, y, z);   // без кута
TeleportService.dismount(player);                    // зняти пасажирів
```

`TeleportReason` — enum для документування причини (`LOBBY_JOIN`, `RESPAWN`,
`SPECTATOR_TOGGLE`, `ADMIN_COMMAND`, ...): зручно в логах і при відладці,
хто саме перемістив гравця.

Реакція на завершений телепорт:

```java
@SubscribeEvent
static void onTeleported(TeleportedEvent event) { /* ... */ }
```

---

### 3.3. `withPlayerFreeze()` — заморозка руху

Клас: `PlayerFreezeService` (статичний). Бібліотека дає лише **механіку**;
**коли** заморожувати — вирішує ваш мод (вибір кіта, передстартовий відлік…).

```java
// НЕ "увімкнути й забути": викликати ЩОТІК, доки умова заморозки виконується
// (типово з власного LivingEvent.LivingTickEvent).
PlayerFreezeService.freeze(player);

// Коли умова перестала виконуватись — скинути лічильник ресинку пози.
PlayerFreezeService.clearResyncState(player.getUUID());
```

Що всередині: обнулення delta movement, зняття спринту/плавання та
періодичний (раз на 5 тіків) `connection.teleport(...)` — він надсилає
`ClientboundPlayerPositionPacket` і скидає client-side prediction, щоб
накопичений рух не «вистрілив» після розморозки.

---

### 3.4. `withPlayerAnim()` — кастомні пози гравця (PlayerAnimationLib)

Класи: `dev.shaurmalib.forge.playeranim.PlayerPoseController` (статичний),
`PlayerPoseEvents`, дані — `dev.shaurmalib.common.playeranim.PoseLayerId`,
`PoseSource`.

Замінює власні «класи-мости» на кшталт `PlayerAnimDropBridge`: кожна поза —
це **іменований шар** (`PoseLayerId`), а не власна `Map<UUID, ModifierLayer>`.

Готові `PoseLayerId`: `DROP_FALL` (1000), `AIRPLANE_SEAT` (900),
`ZIPLINE_RIDE` (950), `DEATH_LIE` (1100), `CUSTOM` (500) — число це
пріоритет шару в `AnimationStack`.

```java
private static final PoseSource DROP_POSE =
        PoseSource.jsonAnimation("mymod", "drop_fall", true);

// увімкнути позу (повертає false, якщо PAL/ресурс ще не готові — ретраймо)
boolean ok = PlayerPoseController.trigger(player, PoseLayerId.DROP_FALL, DROP_POSE);

// зняти позу (шар лишається зареєстрованим — дешево увімкнути знову)
PlayerPoseController.stop(player, PoseLayerId.DROP_FALL);

// зняти ВСІ пози гравця (смерть, зміна фази)
PlayerPoseController.stopAll(player);

// скинути кеш анімацій на F3+T
PlayerPoseController.invalidateAnimationCache();
```

Анімації вантажаться з `assets/<namespace>/player_animations/<name>.json` —
той самий контракт, що у PAL.

Бібліотека вже містить виправлення, знайдені в оригіналі: `HashMap` замість
`WeakHashMap` (баг «FPS падає до 5»), безпечні повтори при гонці
ініціалізації, а також додатковий фікс relog/respawn (творення нового
`AnimationStack` на той самий UUID) і автоматичний `cleanup` на
`ClientPlayerNetworkEvent.LoggingOut`.

---

### 3.5. `withInteractionLock()` — блокування дій

Класи: `InteractionLockModule`, `InteractionLockRegistry`,
`InteractionLockHooks`.

Реєстр **незалежних причин** блокування: дві системи можуть одночасно
блокувати рух, і зняття однієї не розблокує дію, поки не зникне друга.

```java
UUID id = player.getUUID();
lib.interactionLockModule().lock(id, LockType.MOVEMENT, "kit_select");
lib.interactionLockModule().lock(id, LockType.ATTACK,   "kit_select");

// зняти одну з двох причин — MOVEMENT лишиться заблокованим
lib.interactionLockModule().unlock(id, LockType.MOVEMENT, "kit_select");

lib.interactionLockModule().unlockAll(id, "kit_select");   // усі типи за однією причиною
lib.interactionLockModule().clearAll(id);                  // прибрати всі причини геть
boolean locked = lib.interactionLockModule().isLocked(id, LockType.MOVEMENT);
```

Хуки завжди підписані, але бездіяльні, доки немає активних замків.

---

### 3.6. `withDamageGuard()` — блокування урону

Класи: `dev.shaurmalib.common.damage.DamageInterceptorRegistry` (статичний),
`DamageGuardHooks`, `GunDamageBridge`.

Замість одного `if-else`-монстра — іменовані перехоплювачі:

```java
DamageInterceptorRegistry.register("spawn_protection", (victim, source, amount, ctx) -> {
    return victim.level().getGameTime() < protectUntil(victim);  // true = скасувати урон
});

DamageInterceptorRegistry.unregister("spawn_protection");
boolean blocked = DamageInterceptorRegistry.isBlocked(victim, source, amount, ctx);
```

`GunDamageBridge.tryBlock(...)` — окремий шлях для сторонніх джерел урону
(TACZ `EntityHurtByGunEvent`), які обходять `LivingHurtEvent`. Використовує
той самий реєстр, тож дублювати логіку не потрібно.

---

### 3.7. `withAnimatedItems()` + `withPhantomSlotBridge(...)` — анімовані предмети

Класи: `ItemAnimationEngine` (сесії/тік/consume/anti-dupe),
`ItemLeftArmHideEngine` (приховати реальну руку),
`ItemCameraController` (камера від предмета), реєстр —
`dev.shaurmalib.common.item.ItemDefinitionRegistry`,
опис — `ItemDefinition` / `ArmOverride` / `HandSlot` / `ItemCameraTrack`.

#### 3.7.1. Реєстрація предмета

```java
ItemDefinitionRegistry.register(
        MyMedkitItem.class,
        ItemDefinition.<ServerPlayer, InteractionHand>builder()
                .animationName("use")     // ім'я анімації в GeckoLib-моделі
                .activateTick(50)         // тік, на якому викликається onActivate
                .consumeTick(61)          // тік, на якому зменшується стек
                .useCooldown(true)        // false — керуємо кулдауном самі
                .onActivate((player, hand) -> { /* ефект */ })
                .build());
```

Контролер анімації у предмета має зватись **`mainCtrl`**, а базова
анімація — **`idle`** (ці рядки хардкодяться лише як константи
`AnimatedGeoItem.MAIN_CONTROLLER` / `IDLE_ANIM_NAME`).

#### 3.7.2. Приховування реальної руки

Потрібно, коли модель предмета має власну кістку руки — інакше видно «дві
ліві руки» (анімована з моделі + реальна рука гравця).

```java
.armOverride(HandSlot.OFF_HAND, "LeftArm")   // короткий варіант: ховати реальну руку
.armOverride(ArmOverride.of(HandSlot.MAIN_HAND, "RightArm"))
.armOverride(ArmOverride.skinOnly(HandSlot.OFF_HAND, "LeftArm"))     // підмінити на скін, не ховати
.armOverride(ArmOverride.disabled(HandSlot.OFF_HAND, "LeftArm"))     // вимкнути дефолт
```

Рушій перевіряє **обидві руки**: достатньо тримати предмет в одній руці, а
`ArmOverride` вказати для тієї руки, яку треба приховати.

#### 3.7.3. Камера від предмета (необов'язково)

```java
.cameraTrack(ItemCameraTrack.builder()
        .keyframe(0.0f,   0f,   0f)
        .keyframe(0.5f,  12f,  -8f)
        .keyframe(1.2f,   0f,   0f)
        .build())
```

Виклики в рантаймі: `ItemCameraController.trigger(...)`,
`.cancelActive()`, `.isActive()`.

#### 3.7.4. Мост до «фантомних» слотів (anti-dupe)

```java
.withPhantomSlotBridge(new MyPhantomSlotBridge())   // реалізує PhantomSlotBridge<ServerPlayer>
```

Потрібно лише якщо у вашому моді є власні синтетичні інвентарні слоти, які
можуть «з'їсти» предмет під час анімації.

---

### 3.8. `withAnimatedBlocks()` — анімовані блоки (кейси)

`AnimatedBlockEntityBase` (GeckoLib), lifecycle
`IDLE → OPENING → OPEN → CLOSING → REFILLING`.

Ваш мод сам реєструє `BlockEntityType` і передає лише `AnimatedBlockClipSet` —
`record AnimatedBlockClipSet(String idle, String open, String close, String refill)`:

```java
public class MyCaseBlockEntity extends AnimatedBlockEntityBase {
    @Override protected AnimatedBlockClipSet clips() {
        return new AnimatedBlockClipSet("idle", "open", "close", "refill");
        // або: AnimatedBlockClipSet.standard()
        // або: AnimatedBlockClipSet.withoutRefill("idle", "open", "close")
    }
}
```

Корисне в рантаймі: `onMenuOpened()`, `onMenuClosed()`,
`triggerRefill()`, `getOpenersCount()`. Лічильник `openersCount` ніколи не
скидається примусово ззовні, а зміна типу блоку дозволена лише за
`openersCount == 0`.

---

### 3.9. `withSkinnableEntities()` — сутності з рантайм-скіном

`SkinnableEntityBase`: скін-UUID, синхронізація броні, anti-kick fade-out,
knockback від вибухів.

```java
public class MyCorpse extends SkinnableEntityBase {
    public void setOwner(ServerPlayer p) {
        setSkinUUID(p.getUUID());
        setArmor(p.getInventory());
    }
}
```

Head-tracking камери в модуль **не входить** — це продуктова логіка вашого мода.

---

### 3.10. `withInvisibleZones()` — зона без хітбоксу

`InvisibleZoneEntityBase` — найризикованіший модуль (власний `tick()` з
ручним керуванням AABB/`xOld`).

```java
public class MyDome extends InvisibleZoneEntityBase {
    // у конструкторі або відразу після спавну:
    // init(ownerTag, teamId, maxLife, shape)
}
```

Є опційний адаптер `ITargetEntity` для TACZ (soft-dependency) — щоб по зоні
можна було вести вогонь. **Обов'язково прогоніть regression-чеклист з
Javadoc класу** після зміни логіки.

---

### 3.11. `withSpectator()` — режим спостереження (вид B)

Вимагає `withTeleport()`. Клас `SpectatorSessionController` (статичний).

```java
SpectatorSessionController.start(viewer, target);          // camera-lock + запам'ятати позицію
SpectatorSessionController.startByName(server, viewer, "Nick", candidates);
SpectatorSessionController.cycleNext(viewer);
SpectatorSessionController.cyclePrevious(viewer);
SpectatorSessionController.stop(viewer);

// автоматичне переселення при смерті/виході цілі:
SpectatorSessionController.onTargetKilled(targetUuid, killerUuid);
SpectatorSessionController.tick(server, allCandidates, allowedTargets, policy, onRetarget);
```

Ванільний `GameType.SPECTATOR` (вид A) окремого класу не має — просто
викликайте `player.setGameMode(GameType.SPECTATOR)`.

---

### 3.12. `withAnimationRecording()` — запис keyframe-анімацій

`AnimationRecordFacade` (статичний) — замінює три окремі `*AnimRecordManager`.

```java
// countdownTicks: 0 — почати одразу; >0 — спершу фаза відліку
// recordTicks: тривалість запису
AnimationRecordFacade.startAbsolute(player, 0, 200, saveTarget, callbacks);
AnimationRecordFacade.startDelta(player, 60, 200, baseYaw, basePitch, saveTarget, callbacks);

AnimationRecordFacade.stop(player);
boolean recording = AnimationRecordFacade.isRecording(player.getUUID());
AnimationRecordFacade.onServerTick(server);   // викликати зі свого ServerTickEvent (End-фаза)
```

`startAbsolute`/`startDelta` повертають `false`, якщо в цього гравця вже є
активна сесія запису.

---

### 3.13. `withScreenEffects()` — post-chain ефекти

`ScreenEffectPostChain` (статичний) + `PostChainEffectCauses`.

```java
// 1) один раз зареєструвати ефект (client-setup):
ScreenEffectPostChain.register(new PostChainEffectSpec("mono", "mymod", "shaders/post/mono.json"));

// 2) один раз підключити спільний рендер-хук:
MinecraftForge.EVENT_BUS.addListener(ScreenEffectPostChain::onRenderGuiPre);

// 3) увімкнути/вимкнути з причиною — замки як в InteractionLockRegistry:
PostChainEffectCauses.activate("mono", "death_cam");
PostChainEffectCauses.activate("mono", "endgame");
PostChainEffectCauses.deactivate("mono", "death_cam");   // ефект ще живий: "endgame"
PostChainEffectCauses.isActive("mono");
PostChainEffectCauses.clearAll("mono");                  // прибрати всі причини
```

Шейдер-ресурси лишаються у namespace вашого мода (`namespace` + `path` у
`PostChainEffectSpec`).

---

### 3.14. `withFreeCamera()` — вільна камера

Класи: `CameraOwnershipRegistry` (єдиний стек власників — прибирає гонки між
кількома менеджерами камери), `FreeCameraEntity` + готові контролери
`StationaryCameraController`, `EntitySpectateController`,
`CinematicPathController`, `FreeFlyCameraController` (WASD+миша).

```java
CameraOwnershipRegistry.OwnershipToken token = CameraOwnershipRegistry.acquire(CameraOwner.CINEMATIC);
// ... у тіку:
controller.enable(originPlayer, x, y, z, yaw, pitch);

CameraOwnershipRegistry.release(token);            // повернути камеру гравцю
CameraOwnershipRegistry.discardSilently(token);    // якщо власника вже знято іншим кодом
boolean mine = CameraOwnershipRegistry.isActiveOwner(CameraOwner.CINEMATIC);
```

Рушії руху: `PathMotion`, `LoopPathMotion`, `BezierApproachMotion`,
`FollowEntityMotion` — композиціонуються напряму.

---

### 3.15. `withGraffiti(namespace)` — графіті

Серверне ядро: `GraffitiBlockBase`, `GraffitiBlockEntityBase`,
`GraffitiFileStore`, `GraffitiTransferManager`. Дані блоку, NBT та мережева
серіалізація, PNG-сховище на диску, chunked-передача.

**Клієнтська частина залишається у вашому моді** (кеш, world-renderer,
canvas-редактор) і підключається через
`GraffitiClientCacheBridge.register(...)`.

---

### 3.16. `withMixins(MixinToggle...)` — перемикання миксинів

```java
.withMixins(
    ShaurmaLib.MixinToggle.disable(MixinId.SOUND_ENGINE_DUCK_ON_START),
    ShaurmaLib.MixinToggle.enable(MixinId.SPECTATOR_HAND_VISIBILITY))
```

Доступні `MixinId`: `ZIPLINE_GAME_RENDERER`, `ZIPLINE_HUMANOID_MODEL`,
`ZIPLINE_ITEM_IN_HAND_RENDERER`, `SPECTATOR_HAND_VISIBILITY`,
`SOUND_ENGINE_DUCK_ON_START`, `VANILLA_HUD_CANCEL`.

Викликати **до** того, як FML торкнеться цільових класів (тобто в
конструкторі `@Mod`). Миксин, для якого toggle не передано, увімкнений.

Корисно, якщо у вашому моді вже є власний миксин на той самий клас і ви не
хочете подвійної обробки.

---

### 3.17. `withConfig` → `withModeContract(modeStateFileName)` — режими гри

`ModeContractModule`, `GameModeRegistry`, `GameModeContract`.

Персистенція вибраного режиму (файл-стан, типово `mode.yml`), хуки
`onModeActivated` / `onModeDeactivated` (з `Objects.requireNonNull` на
сервері), плюс `menuIcon()` / `accentColor()` для меню налаштувань.

```java
GameModeRegistry registry = lib.modeContractModule().registry();
registry.register(new MyMode());
registry.initPersistence();                 // читає mode.yml
registry.setActive("br", server);            // персистенція + хуки onModeActivated/Deactivated
registry.setActiveUsingCurrentServer("br");
registry.current().id();
registry.all();
registry.byId("br");
registry.saveCurrentMode();
registry.onDeactivation(mode -> { /* ... */ });
registry.reloadCurrentModeConfig();
```

---

### 3.18. `withLifecycle(minPlayersToStart)` — життєвий цикл матчу

`LifecycleModule`, `MatchLifecycleBus`, `MatchLifecycleState`
(`IDLE → WAITING_FOR_PLAYERS → COUNTDOWN → ACTIVE → ENDING`), `IdleBehavior`.

```java
lib.lifecycleModule().lifecycleBus().subscribe(state -> { /* реакція на зміну фази */ });
lib.lifecycleModule().idleBehavior();
```

Блокування редагування налаштувань під час гри використовується всередині
`withModeSettings(...)` — окремо його кликати не треба.

### 3.18a. `withModeSettings(style, bridge, showPlayersTab)` — меню режиму

Метод створює готовий клієнтський `ModeSettingsScreen` для режимів,
зареєстрованих через `withModeContract(...)`. Він обов'язково вимагає
`withLifecycle(...)`, щоб меню знало, коли редагування треба заблокувати.

```java
StyleTheme style = StyleTheme.builder()
        .backgroundColor(0x090A0F)
        .borderColor(0x667788)
        .borderWidth(1)
        .build();

SettingsSyncBridge bridge = new SettingsSyncBridge() {
    @Override
    public List<PlayerModeRow> playersList() { return currentPlayers; }
    @Override
    public void requestPlayerList() { /* власний packet */ }
    @Override
    public void togglePlayerSpectator(String name, boolean spectator) { /* packet */ }
    @Override
    public void saveSettings(Map<String, Integer> values) { /* packet */ }
    @Override
    public void requestModeChange(String modeId) { /* packet */ }
};

ShaurmaLib.Handle lib = ShaurmaLib.init("mymod", modEventBus)
        .withConfig(worldRoot, "mymod", MyMod.class::getResourceAsStream)
        .withModeContract("mode.yml")
        .withLifecycle(2)
        .withModeSettings(style, bridge, true)
        .build();
```

Після `build()` мод може відкрити екран через
`lib.modeSettingsModule().newScreen(Component.literal("Settings"))`.
`showPlayersTab=false` приховує вкладку призначення режимів гравцям.
Мережеве застосування значень навмисно залишається в `SettingsSyncBridge`:
бібліотека не вигадує packet-формат конкретної гри.

---

### 3.19. `withPlayerLifecycle(...)` — join/return політика

`PlayerLifecycleModule`, `PlayerReturnFlow`, `DisconnectHandler`,
`JoinPolicy` (`SPECTATE`/`QUEUE`/`REJECT`), `DisconnectPolicy`
(`PRESERVE_STATE`/`RESET_TO_SPECTATOR`/`KICK_FROM_MATCH`).

```java
.withPlayerLifecycle(JoinPolicy.SPECTATE, null,
        DisconnectPolicy.PRESERVE_STATE, myStatePreserver)
```

`myStatePreserver` — ваша реалізація `PlayerReturnFlow.StatePreserver`
(що саме відновлювати: інвентар, гроші, прогрес раунду).

---

### 3.20. `withLicense(provider, licenseDir)` — ліцензійний гейт

`LicenseModule`, `LicenseGate`. Бібліотека дає **каркас**; ключ і спосіб
перевірки — ваша реалізація `LicenseProvider` (бібліотека не містить
жодного вбудованого секрету).

```java
.withLicense(new MyLicenseProvider(), licenseDir)

// далі:
lib.licenseModule().gate().requireActivated();   // кинеться, якщо не активовано
```

---

### 3.21. `withLobby(teamId, spawnProvider)` — лобі

`LobbyModule`: спільна точка спавну + приховування ніків через
scoreboard-команду.

```java
.withLobby("sc_nametag_hide", () -> currentLobbySpawn())

// далі:
lib.lobbyModule().sendToLobby(player);
lib.lobbyModule().hideNameTag(player);
```

**Бібліотека не вирішує за вас, КОЛИ** відправляти в лобі (реконект vs
новий глядач vs старт) — це ваша продуктова логіка.

---

### 3.22. `withOverlays()` — overlay-рушій

Клас `OverlayEngine` — **один** `IGuiOverlay`-хук замість 40+ окремих
`registerAboveAll(...)`.

Реально підключити до Forge треба окремим викликом у своєму
`RegisterGuiOverlaysEvent`:

```java
@SubscribeEvent
static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
    dev.shaurmalib.forge.ShaurmaLib.attachOverlayEngine(event);
}
```

> Якщо ви підключаєте модуль, який малює щось через `OverlayEngine`
> (наприклад `withRadio`), **без** цього виклику нічого не намалюється.

Підмодулі, кожен вимагає `withOverlays()`:

| Метод | Клас | Що робить |
|---|---|---|
| `withActionBarMessages()` | `ActionBarMessageSystem` | повідомлення SUCCESS/ERROR/COOLDOWN/INFO над хотбаром |
| `withAnimatedCountdown()` | `AnimatedCountdownSystem` | pop-in/pop-out каркас цифр відліку |
| `withWorldTint("id")` | `WorldTintOverlay` | fade екрана кольором (можна кілька каналів) |
| `withChat(ctx, feedId)` | `ChatModule` | неванільний чат (див. 3.23) |
| `withRadio(...)` | `RadioDialogManager` + `RadioDialogOverlay` | радіо-диктор (див. 3.24) |

---

### 3.23. `withChat(teamContext, feedId)` + `withChatDisplaySink(...)` — чат

Клас `dev.shaurmalib.forge.chat.ChatModule`, дані — `TeamChatContext`,
`ChatHistoryStore` (lib-common).

```java
.withChat(myTeamContext, "killfeed")   // спільний чат + команди з префіксом '/'
```

Виправлений баг оригіналу: подвійний провідний `//` у командах більше не
ламає виконання (на сервері й на клієнті одночасно).

Якщо у вас **уже є свій** notification-overlay і ви не хочете переходити на
бібліотечний рендер — підключіть свій sink:

```java
.withChatDisplaySink(new MyChatDisplaySink())   // ChatModule.ChatDisplaySink
```

Тоді `withOverlays()` для `withChat(...)` не потрібен, а дані
(форматування на сервері, історія для T-екрана, командний routing)
лишаються бібліотечними.

---

### 3.24. `withRadio(volumeProvider, startSound, noiseSound, endSound, overlayVisible)` — радіо-диктор

Вимагає `withOverlays()`. Класи:
`dev.shaurmalib.forge.radio.RadioDialogManager` (серверний trigger-API),
`RadioDialogOverlay`, `RadioAudioDucking`, `RadioDialogConfigLoader`,
`RadioVoiceVolumeProvider`;
дані — `RadioDialogRegistry`, `RadioDialogEntry`, `RadioLangEntry`,
`RadioTriggerContext` (lib-common).

```java
.withRadio(
        () -> ModSoundCategory.ANNOUNCER.getVolume(),   // 0..1, null → повна гучність
        ModSounds.RADIO_START.get(),                    // «клац» на старті (може бути null)
        ModSounds.RADIO_NOISE.get(),                    // фоновий шум рації (може бути null)
        ModSounds.RADIO_END.get(),                      // звук завершення (може бути null)
        () -> !DeathCameraHandler.isActive())           // умова показу оверлея
```

> **Увага з порядком ініціалізації:** `RegistryObject.get()` у конструкторі
> `@Mod` кине виняток (реєстри Forge ще не заповнені). Або передайте `null`
> тут і викличте `RadioAudioDucking.configure(...)` пізніше
> (`FMLClientSetupEvent.enqueueWork` — рекомендоване місце), або реєструйте
> звуки раніше. Саме так зроблено в `snipers_shaurma`.

#### Конфіг реплік

`radio_dialogs.yml` (спільний файл namespace):

```yaml
settings:
  default_language: uk_ua
  duck_ratio: 0.30
  model_item: mymod:commander_radio

dialogs:
  RADIO_START:
    uk_ua:
      text: "Увага всім підрозділам!"
      sound: mymod:radio.start.uk
      duration_ticks: 80
      hold_ticks: 40
    en_us:
      text: "Attention all units!"
      sound: mymod:radio.start.en
      duration_ticks: 80
      hold_ticks: 40
```

Завантаження — окремим викликом після `build()`:

```java
RadioDialogConfigLoader.load(lib.configModule().configTree());
```

(типово підписати на `lib.configModule().reloadBus()`).

#### Серверний trigger-API

```java
RadioDialogManager.broadcast(server, "RADIO_START");                 // усім
RadioDialogManager.trigger(player, "RADIO_START");                   // одному

// з пріоритетом і анти-спам групою:
//   RadioTriggerContext(priority, cooldownGroup)
//   готові: RadioTriggerContext.normal() / .high()
RadioDialogManager.trigger(player, "RADIO_ALERT",
        new RadioTriggerContext(RadioTriggerContext.PRIORITY_HIGH, "combat_alerts"), 200);

RadioDialogManager.stop(player);       // миттєво сховати
RadioDialogManager.stopAll(server);
RadioDialogManager.releasePrioritySlot(player);
```

Пріоритет: репліка з вищим пріоритетом **перериває** поточну; з нижчим або
рівним — ігнорується (не стає в чергу). `cooldownGroup` глушить повтори тієї
самої групи протягом `cooldownTicks`.

#### Модель-консюлер, що «говорить»

Якщо у вас є 3D-модель рації з анімаціями `idle`/`talking`:

```java
RadioDialogOverlay.setTalkingListener(MyRadioState::setTalking);
```

---

### 3.25. `withSound()` — звуковий центр

Клас `SoundCenter` (статичний) + реєстри даних у lib-common:
`SoundCategoryRegistry` (ваші category-слайдери), `SoundCueRegistry`
(прив'язка `SoundEvent` → категорія + стадія), `SoundCue`.

**Правило: консюмер НІКОЛИ не будує `SimpleSoundInstance` сам.**

```java
SoundCenter.play(soundId, SoundSource.PLAYERS, volume, pitch);
SoundCenter.play(soundId, SoundSource.PLAYERS, volume, pitch, () -> suppressPredicate());

SoundCenter.playAt(soundId, SoundSource.BLOCKS, volume, pitch, x, y, z);
SoundCenter.playUi(soundId, volume, pitch);                 // працює і без mc.player
SoundCenter.trackMovingSource(soundId, SoundSource.PLAYERS,
        x, y, z, maxVolume, maxHearDistance, pitch);        // літак аірдропу тощо
```

З сервера — пакетами бібліотеки (вони в її власному каналі `shaurma_lib`):
`PlaySoundPacket` / `StopSoundPacket`.

### 3.26. `attachChatScreenIntercept(...)` та `attachVanillaHudCancel(...)`

Два додаткові статичні хуки на `ShaurmaLib` (не на `Builder`, бо їхні Forge-події
приходять в іншій точці життєвого циклу):

```java
ShaurmaLib.attachChatScreenIntercept(event, ...);
ShaurmaLib.attachVanillaHudCancel(() -> chatActive);
```

### 3.27. `InventorySlotAllocation` — персональні слоти гравця

Спочатку підключіть модуль у builder. Це лише додає можливість API і не
обмежує жодного гравця автоматично:

```java
ShaurmaLib.Handle lib = ShaurmaLib.init("mymod", modEventBus)
        .withInventorySlotAllocation()
        .build();
```

Модуль обмежує інвентар **окремого** гравця. Сервер відхиляє кліки по
недозволених слотах, забороняє небезпечні mass-transfer операції
(`shift-click`, quick-craft, double-click pickup), а клієнтський mixin
блокує викидання предметів до відправлення пакета, тому предмет не зникає
візуально. HUD показує лише виділені hotbar-слоти та не дозволяє вибрати
заблокований слот.

```java
// 0 — жодного hotbar-слоту; залишається лише рука без видимого hotbar.
InventorySlotAllocation.setHotbarSlotCount(player, 0);

// Доступні слоти 0..3, решта інвентаря недоступна.
InventorySlotAllocation.setHotbarSlotCount(player, 4);

// Довільна розкладка: hotbar 0, 2 і offhand 40.
InventorySlotAllocation.setAllowedSlots(player, Set.of(0, 2, 40));

// Зняти обмеження.
InventorySlotAllocation.clear(player);
```

Індекси vanilla inventory: `0..8` — hotbar, `9..35` — основний інвентар,
`36..39` — броня, `40` — offhand. Вміст недозволених слотів не видаляється.
За замовчуванням стандартний екран інвентаря заблокований; якщо він потрібен
для конкретного режиму, після розподілення викличте. Для `0..9` hotbar-слотів
це означає "доступна лише рука/hotbar", а весь vanilla inventory screen
залишається заблокованим, доки його явно не розблокувати:

```java
InventorySlotAllocation.setInventoryScreenBlocked(player, false);
```

Викидання також заблоковане за замовчуванням. Його можна дозволити окремо:

```java
InventorySlotAllocation.setItemDropBlocked(player, false);
```

Якщо замість повного блокування потрібен власний екран, на клієнті один раз
зареєструйте фабрику:

```java
ShaurmaLib.setInventoryScreenFactory(MyInventoryScreen::new);
```

Передача `null` повертає режим повного блокування vanilla-екрана.

Повністю власний вигляд hotbar можна підключити на клієнті:

```java
InventorySlotAllocationClientHooks.setCustomHotbarRenderer(
        (graphics, minecraft, allowedSlots, partialTick, width, height) -> {
            // allowedSlots містить фізичні слоти vanilla: 0..8.
            // Тут мод малює власні текстури, предмети, анімації та selection.
        });
```

Якщо renderer не зареєстрований, використовується стандартний renderer
бібліотеки. Переданий список слотів незмінний; серверне обмеження все одно
перевіряється незалежно від клієнтського рендера.

Для контейнерів типу скрині `shift-click` навмисно заблокований повністю:
звичайне переміщення предметів мишкою в дозволені слоти залишається доступним.

### 3.28. `Stamina` — витрата та відновлення енергії

`Stamina` — серверний opt-in модуль. Він не активує витрату stamina для
гравців лише через наявність бібліотеки. Подієві hooks підключені в jar-і,
але залишаються бездіяльними, доки мод не викличе `withStamina(...)`.

#### Підключення

```java
import dev.shaurmalib.forge.ShaurmaLib;
import dev.shaurmalib.forge.stamina.StaminaRules;
import dev.shaurmalib.forge.stamina.StaminaService;

ShaurmaLib.Handle lib = ShaurmaLib.init("mymod", modEventBus)
        .withStamina()
        .build();
```

`withStamina()` вмикає стандартні правила. Якщо потрібні інші дефолти для
всіх гравців, передайте їх одразу:

```java
ShaurmaLib.Handle lib = ShaurmaLib.init("mymod", modEventBus)
        .withStamina(StaminaRules.builder()
                .maxStamina(100)
                .drainPerSecond(20)
                .recoveryPerSecond(25)
                .emptyRecoveryDelaySeconds(3)
                .recoveryDelaySeconds(2)
                .build())
        .build();
```

#### Правила `StaminaRules`

`StaminaRules` — immutable record із builder-ом. Усі числові значення
перевіряються: `maxStamina` має бути більше `0`, інші параметри не можуть
бути від’ємними або `NaN`/Infinity.

| Параметр | Одиниця | Поведінка |
|---|---:|---|
| `active` | boolean | Чи працює stamina для гравця. `false` вимикає drain і regen, але не змінює ванільний sprint/hunger. |
| `maxStamina` | stamina | Верхня межа значення. |
| `drainPerSecond` | stamina/сек | Витрата під час sprint. `0` повністю вимикає витрату. |
| `recoveryPerSecond` | stamina/сек | Швидкість відновлення після затримки. |
| `emptyRecoveryDelaySeconds` | сек | Затримка після досягнення `0`. |
| `recoveryDelaySeconds` | сек | Затримка після використання, якщо залишилась stamina. |
| `recoveryEnabled` | boolean | Дозволяє або повністю забороняє regen. |
| `forceFullHungerWhileActive` | boolean | Кожен тик підтримує hunger `20` і saturation `5`, коли правила active. Це замінює hunger-обмеження sprint. |
| `blockJumpWhenDepleted` | boolean | Серверно блокує стрибок, коли правила active і stamina дорівнює `0`. За замовчуванням `false`. |

Приклад повних правил:

```java
StaminaRules gameRules = StaminaRules.builder()
        .active(true)
        .maxStamina(100)
        .drainPerSecond(20)
        .recoveryPerSecond(25)
        .emptyRecoveryDelaySeconds(3)
        .recoveryDelaySeconds(2)
        .recoveryEnabled(true)
        .forceFullHungerWhileActive(true)
        .blockJumpWhenDepleted(true)
        .build();
```

#### Правила для конкретного гравця

`setRules` призначає правила конкретному `ServerPlayer` і синхронізує
оновлений стан. Це потрібно викликати при вході гравця в lobby/game:

```java
StaminaService.setRules(player, gameRules);

StaminaRules lobbyRules = StaminaRules.builder()
        .active(false)
        .build();
StaminaService.setRules(player, lobbyRules);
```

Правила **не зберігаються в NBT**: це навмисно, бо правила належать режиму,
а не гравцю. Після relog/restart мод має повторно призначити правила режиму.
Поточне числове значення stamina зберігається в persistent player data.

#### Значення stamina

```java
float current = StaminaService.getStamina(player);
float maximum = StaminaService.getMaxStamina(player);
boolean active = StaminaService.isActive(player);

StaminaService.setStamina(player, 50.0f);
StaminaService.setStaminaPercent(player, 0.5f);
StaminaService.setStamina(player, 0.0f);
```

`setStamina` автоматично обмежує значення діапазоном `0..maxStamina`.
Від’ємні значення не падають із помилкою — вони стають `0`; значення вище
максимуму стає `maxStamina`. `NaN`/Infinity відхиляються exception-ом.

За замовчуванням зменшення stamina вручну починає нову recovery-затримку.
Для адміністративного/синхронізаційного встановлення без скидання таймера:

```java
StaminaService.setStamina(player, value, false);
```

`syncNow(player)` примусово повторно відправляє стан клієнту. `clear(player)`
видаляє персональні правила, runtime-значення та persistent stamina і
вимикає HUD для цього клієнта.

#### Серверна механіка по тиках

На `PlayerTickEvent.END` сервер:

1. пропускає creative/spectator без drain/regen і вимикає їхній HUD;
2. якщо `active=false`, не торкається stamina або vanilla hunger;
3. якщо гравець спринтує не на vehicle і stamina `>0`, віднімає
   `drainPerSecond / 20`;
4. при досягненні `0` примусово вимикає sprint;
5. якщо sprint-клавіша залишилась затиснута, сервер все одно не вважає
   гравця спринтером при `0`, тому recovery запускається;
6. після відповідної затримки додає `recoveryPerSecond / 20`;
7. не дозволяє значенню вийти за межі `0..maxStamina`;
8. синхронізує тільки змінений стан, а при login робить forced sync.

Коли `drainPerSecond=0`, sprint не створює drain і не блокує regen.
Коли `recoveryEnabled=false`, recovery не відбувається навіть після
повного виснаження, але drain продовжується.

Якщо `blockJumpWhenDepleted(true)`, клієнтський і серверний mixin скасовують
`jumpFromGround` для гравця при `0` stamina. Клієнт не починає стрибок
візуально, а сервер додатково перевіряє правило. Це не змінює витрату stamina:
стрибки не списують stamina автоматично, а лише можуть бути заборонені після
виснаження. При `false` стрибки працюють як у vanilla.

У поточному API drain підключений тільки до sprint. Стрибки, плавання,
атаки, mining і використання предметів не витрачають stamina автоматично:
це навмисно не вигадується бібліотекою без правил конкретної гри. Для таких
дій мод може сам зменшити значення через `setStamina(...)`, після чого
бібліотека застосує звичайну recovery-затримку.

#### Hunger і sprint

`forceFullHungerWhileActive(true)` кожен серверний тик встановлює hunger
`20` і saturation `5`. Це потрібно для режимів, де stamina повністю замінює
vanilla sprint hunger gate. Після переходу на `active(false)` бібліотека
не відновлює попередній hunger автоматично — мод режиму має сам задати
потрібний рівень через `player.getFoodData()`.

При `forceFullHungerWhileActive(false)` hunger не змінюється бібліотекою:
Minecraft сам забороняє sprint при hunger нижче ванільного порога.

#### Клієнтська синхронізація і HUD

Сервер відправляє `StaminaSyncPacket` із `active`, `stamina` та
`maxStamina`. Клієнтський стан доступний через:

```java
StaminaSyncPacket.ClientState.isActive();
StaminaSyncPacket.ClientState.getStamina();
StaminaSyncPacket.ClientState.getMaxStamina();
```

Пакет не приймає команди від клієнта: клієнт не може змінити stamina.
При logout client state очищається.

За замовчуванням бібліотека показує просту stamina-панель над hotbar. Її
можна повністю замінити:

```java
StaminaClientHooks.setRenderer(
        (graphics, minecraft, stamina, maxStamina, partialTick, width, height) -> {
            // Власний bar, текстури та анімації.
        });
```

Renderer викликається після vanilla hotbar лише коли stamina active:

```java
StaminaClientHooks.setRenderer(
        (graphics, minecraft, stamina, maxStamina, partialTick, width, height) -> {
            if (maxStamina <= 0) return;
            float progress = stamina / maxStamina;
            // Власні texture, animation, colors і позиція.
        });
```

Передані `stamina` та `maxStamina` приходять із сервера. Renderer не є
механікою stamina і не може дозволити sprint або змінити значення.
Передача `null` повертає стандартний renderer бібліотеки.

#### Lifecycle і очищення

`StaminaHooks` обробляє:

| Подія | Дія |
|---|---|
| `PlayerTickEvent.END` | drain/regen на сервері |
| `PlayerLoggedInEvent` | forced sync клієнта |
| `PlayerLoggedOutEvent` | очищення runtime maps без видалення persistent stamina |
| client `LoggingOut` | очищення клієнтського HUD state |

`clear(player)` використовуйте при остаточному виході з режиму, якщо stamina
не повинна переноситись у наступну сесію. `setRules(active(false))`
використовуйте для lobby, якщо значення потрібно зберегти до повернення в
гру.

---

## 4. Модулі поза `Builder` (module hooks)

Це статичні сервіси без стану ініціалізації — `withXxx()` для них додав би
рядок без реальної користі. Кожен має свій `XxxModuleHook`-маркер у
`dev.shaurmalib.forge.module` з Javadoc і прикладом.

| Хук | Реальні класи | Як користуватись |
|---|---|---|
| `CommandModuleHook` | `ShaurmaCommandRoot` | створити в `@SubscribeEvent` на `RegisterCommandsEvent` |
| `MatchHistoryModuleHook` | `MatchHistoryStore` | `MatchHistoryStore.get(level, namespace).addRecord(record)` |
| `LeaderboardModuleHook` | `LeaderboardEngine`, `LeaderboardType`, `HologramLocationStore` | статичні методи напряму, реєстрація типів топів |
| `MarkersModuleHook` | `MarkerRenderRules`, `WorldBillboardPrimitives` | викликати зі свого `RenderLevelStageEvent` |
| `StyleModuleHook` | `StyleTheme`, `StylePalette` | `static final StyleTheme THEME = new StyleTheme(StylePalette.DEFAULT)` |
| `CinematicModuleHook` | `CineTimeline`, `CinePresets` | створити екземпляр на старті матчу й тікати самому |
| `AnimatedItemModuleHook` | — | маркер-документація для `withAnimatedItems()` |
| `SpectatorModuleHook` | — | маркер-документація для `withSpectator()` |

Таблиця стилів (TAB-overlay): `TabListStyle`, `TabColumn`, `TabCell`,
`TabListLineProvider`.

---

## 5. Статичні сервіси, доступні одразу

Ці класи не потребують `withXxx()` для **читання** API (але відповідний
`withXxx()` документує свідоме підключення й може вмикати їхні подієві хуки):

`TeleportService`, `PlayerFreezeService`, `PlayerPoseController`,
`ItemAnimationEngine`, `ItemLeftArmHideEngine`, `ItemCameraController`,
`SpectatorSessionController`, `AnimationRecordFacade`,
`ScreenEffectPostChain`, `ChatModule`, `RadioDialogManager`, `SoundCenter`.

`SoundCategoryDucker` тикає **завжди** — на нього спирається і радіо-модуль.

---

## 6. Мінімальний чеклист інтеграції

1. [ ] JDK 17 стоїть і Gradle-демон на ньому (інакше на JDK 21+ буває
   `Unsupported class file major version`).
2. [ ] `mappings channel: 'official', version: '1.20.1'`.
3. [ ] Токен у `~/.gradle/gradle.properties` (`gpr.user` / `gpr.key`).
4. [ ] Репозиторій `https://maven.pkg.github.com/B23101/shaurma-lib` +
   `implementation 'dev.shaurmalib:shaurma-lib-forge:0.1.0-SNAPSHOT'`.
5. [ ] `mods.toml` оголошує залежність від `shaurma_lib`.
6. [ ] Jar бібліотеки лежить у `/mods` (або вшитий у ваш артефакт).
7. [ ] `ShaurmaLib.init("ваш_mod_id", modEventBus)` у конструкторі `@Mod`.
8. [ ] Якщо використовуєте overlay-модулі — `ShaurmaLib.attachOverlayEngine(event)`.
9. [ ] Опційні залежності (GeckoLib / PlayerAnimationLib) додані, якщо їхні
   модулі підключені.

---

## 7. Що не перевіряється компіляцією (перевіряйте запуском гри)

Збірка доводить, що код **компілюється й пакується**. Вона НЕ доводить
рантайм-поведінку. Перед релізом прогнати:

1. **Дублікати миксинів на один клас.** Якщо у вашому моді вже є власний
   миксин на `ItemInHandRenderer` / `SoundEngine` / `ChatComponent` / `SoundManager`
   / `PostChain`, то разом із лібовим їх буде ДВА на тому самому методі.
   Зазвичай це безпечно (різні `@Inject` зі своїми умовами скасування), але
   перевірте лог Mixin на `Mixin apply ... failed` і вимкніть зайвий через
   `withMixins(MixinToggle.disable(MixinId.XXX))`.
2. **Радіо-ducking.** Якщо у вашому моді є ВЛАСНА система ducking
   (свій `SoundCategoryDucker` + свій миксин на `SoundEngine`), вона може
   множитися з лібовою на звуках, що стартують під час репліки. Рішення:
   або лишити лише одну, або вимкнути лібовий миксин:
   `withMixins(ShaurmaLib.MixinToggle.disable(MixinId.SOUND_ENGINE_DUCK_ON_START))`.
3. **GeckoLib-рендер руки** (`armOverride`) — перевірити в першій особі, що
   немає «двох рук» на кожному предметі з `LeftArm`/`RightArm` у моделі.
4. **Пози гравця** (`PlayerPoseController`) — перевірити саме після
   виходу+входу в гру (relog) і після respawn: поза має або продовжуватись
   на новому екземплярі гравця, або коректно зніматись.
5. **Вільна камера** — переконатись, що два різні менеджери камери не
   тримають ownership одночасно (`CameraOwnershipRegistry.isActiveOwner`).
6. **InvisibleZone / AnimatedBlocks** — прогнати regression-чеклист з
   Javadoc цих класів (вони найризикованіші: ручний `tick()`/AABB).
7. **Класи `@OnlyIn(CLIENT)` у спільному (server+client) коді.** Якщо метод
   викликається і на виділеному сервері, а всередині торкається
   `@OnlyIn(CLIENT)`-класу, Forge кидає
   `Attempted to load class ... for invalid dist DEDICATED_SERVER` — і весь
   виклик падає цілком (так валився розбір `radio_dialogs.yml`, поки
   duck-ratio не обгорнули в `FMLEnvironment.dist == Dist.CLIENT`).
   Обгортайте такі гілки в `FMLEnvironment.dist` або `DistExecutor`.
8. **Сторонні бібліотеки на classpath** — див. §1.3: у dev-запуску вони невидні
   кодам мода, тому потрібні в рантаймі класи мають їхати в mod-jar'і.

### Станом на 2026-09-12 перевірено запуском

`./gradlew runServer` для пари `snipers_shaurma` + `shaurma-lib 0.1.0-SNAPSHOT`
(Forge 1.20.1-47.3.0, ForgeGradle 6, JDK 17): сервер стартує (`Done (3.8s)`),
усі YAML-конфіги читаються, `radio_dialogs.yml` розбирається (50 реплік),
жодного `NoClassDefFoundError` і жодного `RuntimeDistCleaner`-помилки про
client-only класи на `DEDICATED_SERVER`, UDP-потік голосового чату не вмирає
на побитому пакеті. **Клієнтські шляхи (рендер, overlay-рушій, arm-hiding,
камера, пози гравця) цим запуском НЕ покриті** — їх треба прогнати окремо
через `runClient`.
