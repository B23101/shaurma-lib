# shaurma-lib: мульти-лоадер × мульти-версія

## 1. Статус (чесно)

| Що | Стан |
|---|---|
| `core` — чиста Java 17, 0 імпортів Minecraft/лоадерів (135 файлів + SPI + render) | ✅ зібрано `javac --release 17`, 16 тестів проходять (`tools/verify-core.sh`) |
| 14 файлів `lib-common`, що тягли `net.minecraft.*`, переписані на SPI | ✅ |
| Рендер-абстракція `GuiCanvas` + приклад реального порту (`CountdownOverlay`) | ✅ тестується без гри |
| Gradle-файли (`core/build.gradle`, sniffer v2) | ⚠️ написані, **Gradle тут не запускався** |
| Події (`EventBus`, `PlatformEvents`, `DamageGuard`), мережа (`PacketCodec`, `PacketRegistry`, явні wire-id 0–17), 2 реальні пакети перенесено | ✅ тести проходять (28 перевірок) |
| **Вертикальний зріз Stamina** (фаза 2, еталон): `StaminaEngine` (чиста state-machine) → `StaminaService` (SPI-залежності в конструкторі) → `StaminaModule` (шина) → `StaminaHud` (`GuiCanvas`) → `StaminaGates` (для міксинів). Модульна система `ShaurmaModule`/`ModuleHost`/`ShaurmaCore` замінює `FMLModuleContext` і 2000-рядковий Builder | ✅ у `core`, 50 перевірок проходять |
| `mc/v1_20_1` і `loader/forge` для stamina (`McPlayerRef`, `PlayerOpsImpl`, 2 міксини, `ForgeEventBridge`, `ForgePlayerPersistence`) | ⚠️ **написано, не скомпільовано** — немає доступу до Minecraft/Forge |
| **Рушій анімацій**: ядро (104 тести) + шар 1.20.1 + Forge (запущено в грі) + Fabric 1.20.1 (зібрано Loom) | ✅ див. `docs/ANIMATION.md` |
| Решта `mc:*` адаптерів, `loader:*`, 9 dist-jar-ів, порт ~195 файлів `lib-forge` | ⏳ фази 2–6 нижче. У пісочниці немає доступу до maven Forge/Fabric/NeoForge/Mojang, тож код проти гри я компілювати не міг і не вдаю, що міг |

`legacy/lib-forge` — оригінал без змін. **Він поки не збирається проти нового `core`**: змінилися сигнатури
14 типів (`TeleportService` — 15 файлів-споживачів, `GameModeContract` — 6, `ChatEntry`/`ChatFormatEngine` — по 5,
`DamageInterceptor` — 3, решта — по 1–2). Це перший крок фази 2 (mechanical, ~40 місць).

## 2. Ключова ідея: дві осі, а не одна

Для даної версії гри `net.minecraft.*` **однаковий** на Fabric, Forge і NeoForge (Mojang-імена). Тож розбіжності дві й незалежні:

* **вісь версії** (1.20.1 / 1.21.1 / 26.3): класи й сигнатури ванілі, рендер, пакети, компоненти предметів, шейдери, Java 17/21/25;
* **вісь лоадера** (Fabric / Forge / NeoForge): події, реєстри, ентрипоінт, мережева реєстрація, мод-метадані.

Тому не 9 копій коду, а шари:

```
                    ┌──────────────────────────── core (Java 17, 0 MC) ───────────────────────────┐
                    │ логіка, стан-машини, конфіги, математика, чат, рейтинг, оверлеї → GuiCanvas │
                    │ platform SPI: Text, DataTag, ResourceId, *Ref, TeleportBackend, LootDropper…│
                    └───────────────▲──────────────────────────────────────────▲──────────────────┘
        вісь версії                 │ реалізує SPI                              │ реалізує PlatformEvents/Net
   mc:v1_20_1  mc:v1_21_1  mc:v26_3 │  (ванілль, БЕЗ лоадера; Mixin тут)        │  loader:fabric / forge / neoforge
                    └───────────────┴──────────────┬────────────────────────────┘  (БЕЗ версійних класів)
                                         dist:<loader>-<mc>  = core + mc:<ver> + loader:<l> + shade/relocate
                                         → 9 jar-ів (8, якщо без NeoForge 1.20.1)
```

Правило залежностей (sniffer це перевіряє): `core` ← нічого. `mc:*` ← core + ваніль. `loader:*` ← core + API лоадера
(**виправлення**: типи ПОДІЙ лоадера теж різні між версіями — Forge 1.20.1 `TickEvent.PlayerTickEvent` vs NeoForge 1.21 `PlayerTickEvent.Post` — тому клей живе в `loader/<l>/<mc-версія>/`; лише тонкий міст події → `PlatformEvents`, без логіки; обгортки гравців дає `RefFactory`). `dist:*` лише склеює. Єдиний jar на всі версії неможливий (різні Java 17/21/25,
різні імена класів), тож «підтримка всіх версій» = **одна кодова база → N jar-ів**.

## 3. Що розходиться між версіями і де воно живе

Позначка «?» — моє очікування, яке треба підтвердити при порті проти реальних jar-ів.

| Тема | 1.20.1 | 1.21.1 | 26.3 | Де ховається |
|---|---|---|---|---|
| Id ресурсу | `ResourceLocation` | `ResourceLocation` | `Identifier` (?) | `ResourceId` у core, конвертер в `mc:*` |
| Мапінги | official | official | без обфускації (підтверджено) | build-плагіни `mc:*` |
| Java | 17 | 21 | 25 (підтверджено) | toolchain `dist:*`; core = release 17 |
| Телепорт | `teleportTo(level,x,y,z,yRot,xRot)` | з `Set<RelativeMovement>` (?) | `TeleportTransition` (?) | `TeleportBackend` |
| Предмети | NBT | DataComponents | DataComponents | `ItemRef`/`DataTag` |
| Мережа | SimpleChannel + `FriendlyByteBuf` | `CustomPacketPayload` + `StreamCodec` (?) | payload-система (?) | `PacketCodec` у core + реєстрація в `mc:*`/`loader:*` |
| HUD/GUI | `GuiGraphics` | `GuiGraphics` | новий render-pipeline (?) | **`GuiCanvas`** |
| Post-шейдери (DarkZone, ScreenEffect) | `PostChain` json | змінюється (?) | новий pipeline (?) | `PostEffectBackend` + окремі assets на версію |
| GeckoLib | 4.x | 4.x/5.x (?) | 5.5.7 (є для всіх 3 лоадерів) | `GeckoBridge` в `mc:*` |
| PlayerAnimator (KosmX) | є | є | **upstream не знайдено** | див. §6 |

## 4. Рендер без дублювання

* `GuiCanvas` — єдиний контакт HUD/екранів з рендером. Реалізація: по одній на версію (обгортка над `GuiGraphics`/pipeline).
* Уся логіка оверлеїв/віджетів/екранів (IntroOverlay, Alert, ActionBar, Radio, TabList, ModeSettingsScreen, ChatHistory…) пишеться
  проти `GuiCanvas` **один раз**. Час — через інжектований годинник (детермінізм).
* `RecordingCanvas` записує кадр у `RenderCommand`-и → юніт-тести «що намалював оверлей» без гри; `replay` дозволяє кешувати статичні панелі.
* Команди — поліморфні (`RenderCommand.applyTo`), без `switch`.
* Для 3D (маркери, білборди, графіті) — аналогічний `WorldCanvas` (billboard/line/box у координатах відносно камери): **фаза 4**.
* Скрінам (Forge `Screen` ↔ Fabric `Screen` однакові в ванілі) — один тонкий `ScreenHost` у `mc:*`, що делегує `core`-віджету.

## 5. Міксини, події, мережа

* **Міксини** — у `mc:<ver>`, не в лоадері: Mixin однаково працює на всіх трьох. Кожен міксин ≤ ~10 рядків і лише кличе
  `ShaurmaHooks.xxx(...)` (інтерфейс у core). 15 наявних міксинів (664 LOC) — переважно перенос 1:1 з перевіркою імен під версію.
* **Події**: core оголошує `PlatformEvents` (server tick, player join/leave, damage, chat, HUD render…). `loader:*` підписує
  рідні події й передає в core. Це заміна `FMLModuleContext`/`IEventBus` (зараз прибиті до Forge).
* **Мережа**: пакет = `record` у core + `PacketCodec` (читає/пише через абстрактні `ByteSource/ByteSink`), id фіксовані
  (як і зараз «packet IDs фіксовані між релізами» — wire-сумісність між версіями збережеться).

## 6. Ризики, які треба вирішити

1. **`playeranim` → власний рушій `dev.shaurmalib.anim`** (див. `docs/ANIMATION.md`): ядро в core, шар версії `mc/v1_20_1/…/anim/mc`,
   клей `loader/forge` + `loader/fabric`. Старий PAL-модуль лишився в legacy лише для snipers_shaurma (вимикається без PAL).
2. **NeoForge 1.20.1** — застарілий бранч; **Forge 26.3** існує (GeckoLib для нього є). Рекомендую: Forge 1.20.1 + Fabric 1.20.1,
   усі три лоадери для 1.21.1 і 26.3.
3. Дублікати в оригіналі: `GameModeContract`, `TeleportService`, `TeleportedEvent` існують і в common, і в forge; у common
   `TeleportService.ensureChunkLoaded` був заглушкою, а `dispatch()` — порожнім. Тепер у core єдина оркестрація; реальні
   чанк-тікети мають жити в `TeleportBackend` кожної версії.
4. Сумісність з `snipers_shaurma`/maniac-mode: пакети `dev.shaurmalib.common.*` збережені, але підписи з `ServerPlayer`→`PlayerRef`
   тощо ламають бінарну сумісність — потрібен один «bump» споживачів (або тонкі `forge-compat` перевантаження).

## 7. План (оцінки за `docs/MIGRATION_MAP.csv`, евристика за імпортами)

| Фаза | Зміст | Обсяг |
|---|---|---|
| 1 ✅ | core + SPI + render-основа + sniffer | зроблено |
| 2 | `mc:v1_20_1` (де зараз код): бекенди, `TextBridge`, `GuiCanvasImpl`, пакети; legacy → нові сигнатури; Forge-dist 1.20.1 | MC-LOGIC 41 файл/5.9k, PACKET 18/1.3k, MIXIN 15/0.7k |
| 3 | `loader:fabric` + `loader:neoforge` на 1.20.1-коді/1.21.1; `PlatformEvents` | EVENTS/GLUE 26/5.7k |
| 4 | Рендер: усі оверлеї/екрани → `GuiCanvas`; `WorldCanvas`; post-ефекти | RENDER 42/8.2k |
| 5 | `mc:v1_21_1` (components, payloads, GeckoLib) | дельта до фази 2 |
| 6 | `mc:v26_3` (Identifier, pipeline, GeckoLib 5, Java 25) | найбільша дельта (рендер) |

Гейт кожної фази: sniffer + `smokeTest` у core, `runClient/runServer` на реальній версії. Для кожного нового бекенду —
контракт-тест (однаковий набір перевірок проти `TeleportBackend`/`GuiCanvas` на всіх версіях).

## 8. Рецепт порту одного модуля (на прикладі Stamina)

1. Правила/значення → `record` у `core` (валідація в конструкторі).
2. Алгоритм → чиста функція/state-machine без `Player` (`StaminaEngine.step(rules, state, input) → Result`).
   Зовнішній ефект повертається як результат (`stopSprinting`), а не виконується.
3. Оркестратор → клас у `core`, залежності ігрового світу — інтерфейси SPI в конструкторі (`PlayerOps`, `PlayerPersistence`, `NetworkSender`). Жодних `static`-мап.
4. Нова потреба від гри = новий інтерфейс + `Platform.register(Class, impl)` (без зміни `install`).
5. HUD/екран → `Overlay`/`GuiCanvas`; тест через `RecordingCanvas`.
6. Підключення → `ShaurmaModule` (`dependsOn`, `attach`, `detach`); код лоадера не знає, що таке stamina.
7. Міксин на версію ≤ 6 рядків: обгорнути об'єкт і викликати `*Gates` у core.
8. Рівно один клас на лоадер×версію постить рідну подію в `PlatformEvents.BUS`.

Що в цьому зрізі стало явним і було прихованим у legacy: persistence — це **вісь лоадера** (у Fabric немає `getPersistentData`),
`ClientAccess` потрібен лише на клієнті, а клієнтський блок стрибка тепер діє лише на локального гравця
(legacy гасив стрибки всіх `Player` на клієнті).
