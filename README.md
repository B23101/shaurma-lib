# ShaurmaLib

Спільна бібліотека систем для Forge-модів **Minecraft 1.20.1**.
Публікується у **GitHub Packages**, тому підключається однією залежністю —
і **ніколи не вимагає правити версію**.

```
dev.shaurmalib:shaurma-lib-forge:0.1.0-SNAPSHOT
```

* Повний довідник по кожному модулю → **[docs/USAGE.md](docs/USAGE.md)**
* Архітектура та принципи → **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**

---

## Що це

* `lib-common` — чиста Java-частина (контракти, дані, обчислення) без жодного
  `net.minecraftforge.*` імпорту (це перевіряє Gradle-таска
  `architectureSniffer`) — основа для майбутнього Fabric-порту.
* `lib-forge` — Forge-специфічна частина (Forge 1.20.1): event-хуки, мережевий
  канал `shaurma_lib`, миксини.
* SnakeYAML вшитий (релокований) у `lib-forge` — окремо додавати не треба.
  Імпортуйте його як `dev.shaurmalib.libs.snakeyaml.*` і **не додавайте власну
  `org.yaml:snakeyaml`**: у dev-запусках Forge звичайні classpath-бібліотеки не
  видні кодам мода й падають з `NoClassDefFoundError` (див. USAGE.md, §1.3).
* Ініціалізація — **Builder-ланцюжок** у конструкторі `@Mod`.
* Миксини бібліотеки декларуються в її власному
  `shaurma_lib.mixins.json` (зареєстрований у маніфесті jar) — вашому моду
  не треба нічого додавати.

---

## Підключення

### 1. Токен (один раз на машину)

GitHub Packages вимагає автентифікації **навіть для публічних пакетів** —
потрібен Personal Access Token (classic) зі scope `read:packages`.

`~/.gradle/gradle.properties` (Windows: `C:\Users\<ти>\.gradle\gradle.properties`):

```properties
gpr.user=ВАШ_НІК_НА_GITHUB
gpr.key=ghp_ВАШ_ТОКЕН
```

### 2. `build.gradle` вашого мода

```gradle
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/B23101/shaurma-lib')
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = findProperty('gpr.key')  ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

// SNAPSHOT кешується 24 год за замовчуванням — скидаємо, щоб брати свіжий
configurations.all {
    resolutionStrategy { cacheChangingModulesFor 0, 'seconds' }
}

dependencies {
    implementation 'dev.shaurmalib:shaurma-lib-forge:0.1.0-SNAPSHOT'
}
```

Обов'язково ті самі `mappings channel: 'official', version: '1.20.1'` і
Java 17, що й у бібліотеки.

### 3. `mods.toml`

```toml
[[dependencies."ваш_mod_id"]]
modId = "shaurma_lib"
mandatory = true
versionRange = "[0.1,)"
ordering = "AFTER"
side = "BOTH"
```

`shaurma-lib-forge` — це **окремий Forge-мод** (modId `shaurma_lib`), тому
його jar має бути в `/mods` поруч з вашим (або вшитий у ваш артефакт).

### 4. Ініціалізація

```java
@Mod("mymod")
public class MyMod {
    public MyMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        dev.shaurmalib.forge.ShaurmaLib.Handle lib =
            dev.shaurmalib.forge.ShaurmaLib.init("mymod", bus)
                .withTeleport()
                .withPlayerFreeze()
                .withAnimatedItems()
                .build();
    }
}
```

Деталі, залежності між модулями та приклади на кожен модуль —
у **[docs/USAGE.md](docs/USAGE.md)**.

---

## Чому версію не треба правити

`0.1.0-SNAPSHOT` — це канал, а не реліз:

1. кожен `git push` у `main` → GitHub Actions (`.github/workflows/publish.yml`)
   збирає та публікує новий SNAPSHOT у GitHub Packages;
2. Gradle бачить changing module і при кожній збірці тягне найсвіжіший;
3. ви просто робите `./gradlew build` — і отримуєте актуальну бібліотеку.

Змінити версію можна в одному місці — `gradle.properties` (`libVersion`).

---

## Публікація (для автора бібліотеки)

```bash
./gradlew build publish
```

Координати та репозиторій беруться з `gradle.properties`
(`githubOwner` / `githubRepo`), credentials — з `gpr.user` / `gpr.key` або з
`GITHUB_ACTOR` / `GITHUB_TOKEN`.

---

## Модулі — коротко

| Модуль | `withXxx(...)` |
|---|---|
| Конфіги | `withConfig(worldRoot, namespace, resourceLoader)` |
| Телепорт | `withTeleport()` |
| Заморозка гравця | `withPlayerFreeze()` |
| Пози гравця (PAL) | `withPlayerAnim()` |
| Блокування дій | `withInteractionLock()` |
| Захист від урону | `withDamageGuard()` |
| Анімовані предмети | `withAnimatedItems()` (+ `withPhantomSlotBridge`) |
| Анімовані блоки | `withAnimatedBlocks()` |
| Сутності зі скіном | `withSkinnableEntities()` |
| Зони без хітбоксу | `withInvisibleZones()` |
| Спостерігач (вид B) | `withSpectator()` |
| Запис анімацій | `withAnimationRecording()` |
| Екранні ефекти | `withScreenEffects()` |
| Вільна камера | `withFreeCamera()` |
| Графіті (сервер) | `withGraffiti(namespace)` |
| Перемикання миксинів | `withMixins(toggles...)` |
| Режими гри | `withModeContract(modeStateFileName)` |
| Lifecycle матчу | `withLifecycle(minPlayersToStart)` |
| Join/return-політика | `withPlayerLifecycle(...)` |
| Ліцензія | `withLicense(provider, dir)` |
| Лобі | `withLobby(teamId, spawnProvider)` |
| Overlay-рушій | `withOverlays()` |
| Action-bar повідомлення | `withActionBarMessages()` |
| Анімований відлік | `withAnimatedCountdown()` |
| Звуковий центр | `withSound()` |
| Тінт світу | `withWorldTint(tintId)` |
| Чат | `withChat(ctx, feedId)` / `withChatDisplaySink(sink)` |
| Радіо-диктор | `withRadio(volume, start, noise, end, visible)` |
| Розподіл слотів гравця | `withInventorySlotAllocation()` + `InventorySlotAllocation.setHotbarSlotCount(player, count)` |
| Stamina | `withStamina(...)` + `StaminaService.setRules(player, rules)` |

Статичні сервіси (підключаються прапорцем лише як підтвердження наміру):
`TeleportService`, `PlayerFreezeService`, `PlayerPoseController`,
`ItemAnimationEngine`, `ItemLeftArmHideEngine`, `ItemCameraController`,
`RadioDialogManager`, `SoundCenter`, `SpectatorSessionController`,
`AnimationRecordFacade`, `ScreenEffectPostChain`.

Модулі-«хуки» без `Builder` (команди, історія матчів, лідерборди, маркери,
стилі, кінематика) описані в `docs/USAGE.md`, розділ 4.

---

## Вимоги

| Що | Значення |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 1.20.1-47.3.0 |
| Mappings | official 1.20.1 |
| Java | 17 (і Gradle-демон на 17) |
| ForgeGradle | 6.x |
| Опційно | GeckoLib 4.7.2, PlayerAnimationLib 1.0.2-rc1+1.20 |
