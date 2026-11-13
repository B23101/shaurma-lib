# Рушій анімацій ShaurmaLib (`dev.shaurmalib.anim`)

Власна система анімацій замість PlayerAnimator (KosmX). Одна система для **ванільного гравця**
і для **GeckoLib-моделей** (маньяк, моби): шари, маски кісток, плавні переходи, погляд, пропси,
перша особа, мережа. Написана під мульти-лоадер з першого рядка.

## 1. Де що лежить

| Шар | Шлях | Що містить | Залежності |
|---|---|---|---|
| **ядро** | `core/src/main/java/dev/shaurmalib/anim/**` | кліпи, парсер, Molang, шари, маски, локомоція, правила, пропси (запікання), мережеве повідомлення | **лише JDK 17** (0 імпортів гри) |
| **версія** | `mc/v1_20_1/src/main/java/dev/shaurmalib/anim/mc/**` | `PlayerModel`/`ModelPart`, GeckoLib-міст, рендер пропсів, перша особа, 5 міксинів | ваніль 1.20.1 (+GeckoLib опційно) |
| **лоадер** | `loader/forge/…/anim/forge/**`, `loader/fabric/…/anim/fabric/**` | реєстрація подій, ресурсів, мережевого каналу, команда `/sanim` | API лоадера |
| **плагін** | `tools/blockbench/shaurma_anim.js` | експорт анімацій, пропсів, масок з Blockbench | Blockbench ≥ 4.8 |

Порт на нову версію/лоадер — це **лише** шар версії (≈900 рядків: ті самі класи з іншими сигнатурами
ванілі) і/або клей лоадера (≈150 рядків). Логіка, формат, тести — не змінюються.
Імена пакетів шару версії **однакові** для всіх версій (`dev.shaurmalib.anim.mc`) — код модів-споживачів
не змінюється при переході 1.20.1 → 1.21.1.

Зараз:
* Forge 1.20.1 — зібрано, запущено в грі (див. §9);
* Fabric 1.20.1 — той самий шар версії + `loader/fabric`, зібрано Loom-ом (`dist/fabric-1.20.1`);
* NeoForge 1.21.1 / Fabric 1.21.1 — потрібен шар `mc/v1_21_1` (зміни: `VertexConsumer.addVertex`,
  `ResourceLocation.fromNamespaceAndPath`, payload-мережа) — ще не написано.

## 2. Як це працює (коротко)

```
кожен тік:    Animator.tick    → події кліпів (звук/частинки/мітки), правила, згладжування локомоції
кожен кадр:   Animator.evaluate(partialTick, базова_поза) → Pose
              база = ванільна поза моделі (гравець) або спокій (geo)
              для шарів знизу вгору: LOCOMOTION → IDLE → HOLD → ACTION → GESTURE → POSE → CINEMATIC
                  вага = fade × маска(кістка) × вага_запиту
                  значення = lerp(нижче, кліп, вага)   (або += для additive)
              погляд: голова за камерою з силою look (0..1) поверх усього
адаптер:      Pose → ModelPart (гравець) або GeoBone (GeckoLib) — лише кістки, які рушій змінив
```

Кістка, якої кліп не має, **прозора** — її веде нижчий шар. Звідси головне правило:
**аптечка з маскою `upper_body` у шарі ACTION + ходьба в LOCOMOTION = ноги йдуть, руки бинтують.**

## 3. Маски

`upper_body`, `legs`, `arms`, `right_arm`, `left_arm`, `head`, `spine`, `full`, `root` — групи виводяться
з ригу (для geo-моделей — з імен і ієрархії кісток). Ім'я кістки = кістка + нащадки.

```
right_arm+left_arm        об'єднання
full-legs   (або -legs)   усе, крім ніг
upper_body+torso*0.5      тулуб лише наполовину
body!                     лише сама кістка, без дітей
```

## 3б. Типи анімацій (Blockbench → Animation → Shaurma: налаштування анімації → «Тип»)

| Тип | Приклад | Як з іншими анімаціями | У файлі |
|---|---|---|---|
| **Повна** | лежати, катсцена | усе інше вимкнено: кістки без ключів стоять у спокої, голова не за камерою | `slot: pose`, `mask: full`, `look: 0`, ключі спокою для всіх кісток |
| **Часткова** | підняти руку | разово, лише свої кістки; ходьба/біг/idle живуть на решті | `slot: action` |
| **Синхронна** | аптечка на ходу | гравець рухається; вибрані частини (ноги, тулуб, голова, руки) лишаються за рухом, решту веде анімація | `slot: action`, `mask: full-legs-body!-head` (з галочок) |
| **Стан** | ліхтарик, каністра | триває; **поза + сила × рух**: при бігу рука трохи махає, на шифті нахиляється з тілом, при ударі — ванільний змах з предметом | `slot: hold`, `state: {follow_move, follow_sneak, follow_idle, yield_swing, yield_use}`, `hold: {item, hand}` |

Стан з «предметом у руці» вмикається **сам**, коли гравець бере предмет, і плавно гасне, коли прибирає —
без Java-коду (правило будується з файлу при F3+T). Без предмета — з коду: `AnimBinding.when(...)`.
Ванільний предмет у руці ховає пропс (`hide_held`), а ванільну позу «тримаю предмет» (рука на 18° вперед)
адаптер прибирає сам, поки стан веде руку, — інакше поза стояла б вище, ніж у Blockbench.
Поки грає дія вищого шару (часткова/синхронна аптечка, емоція), вона перекриває стан на своїх кістках;
скінчилась — стан повертається з кросфейдом.

## 4. Формат кліпа (`assets/<ns>/shaurma_anim/**.json`)

Надмножина Bedrock: Blockbench імпортує такі файли назад без плагіна.

```json
{
  "format": "shaurma_anim", "rig": "player",
  "animations": {
    "medkit_use": {
      "length": 2.0, "animation_length": 2.0, "loop": "once",        // once | loop | hold
      "slot": "action", "mask": "upper_body",
      "blend_in": 0.15, "blend_out": 0.25,
      "look": 0.55,                       // голова за камерою на 55%
      "first_person": "model",            // vanilla | model | hide
      "bones": { "right_arm": { "rotation": { "0.0": [0,0,0], "0.3": {"post": [-52,-24,0], "lerp_mode": "catmullrom"} } } },
      "props": { "bandage": { "parent": "left_item", "model": "maniacmod:props/bandage",
                              "texture": "maniacmod:textures/shaurma_anim/bandage.png",
                              "visible": {"0.0": false, "0.62": true, "1.45": false}, "hide_held": "right" },
                 "aura": { "parent": "world", "model": "maniacmod:props/aura",       // вільна модель навколо гравця
                           "texture": "maniacmod:textures/shaurma_anim/aura.png", "translucent": true, "emissive": true } },
      "events": [ { "time": 0.45, "type": "sound", "value": "minecraft:item.armor.equip_leather", "volume": 0.8 },
                  { "time": 0.42, "type": "custom", "value": "maniacmod:geo_item/medkit/use" } ]
    }
  }
}
```

* Значення — як у Blockbench UI (градуси, пікселі). Рушій сам переводить у простір моделі.
* Пропс: `parent` — кістка ригу або `world` (простір сутності від ступнів, не йде за коренем `body`);
  кістки пропса — треки кліпа з тими ж іменами; масштаб 0 (ступінчасто) ховає кістку з дітьми, також для `item`.
* Ключ може бути Molang: `"-82 + query.head_x_rotation * 0.9"` (рука ліхтарика цілиться за поглядом).
  Доступно: `query.anim_time`, `life_time`, `ground_speed`, `head_x_rotation`, `head_y_rotation`,
  `is_on_ground`, `is_sneaking`, `modified_move_speed`, `math.*`, змінні `v.*`.
* Інтерполяція: linear, catmullrom, step, + easing (`easeInOutSine`…), pre/post на одному кадрі.
* Старі файли читаються без змін: `player_animation(s)/*.json` (PlayerAnimator, риг гравця),
  `animations/**.animation.json` (GeckoLib, id `ns:файл/ім'я`).

Риг гравця — `player-animation_model.bbmodel`: `body` — корінь усього тіла (лягти, нахилитись),
`torso` — тулуб, `head`, `right_arm`/`left_arm` (+ `right_item`/`left_item` — предмет у руці),
`right_leg`/`left_leg`. Якір `right_item` збігається з ванільним рівно — предмет стоїть там, де в Blockbench.

## 4б. Камера за головою (перша особа)

`"camera": 1, "camera_rotation": 0.6` у кліпі (або `.camera(1f, 0.6f)` у `PlayRequest`/`AnimBinding`):
камера першої особи стає в точку ока анімованої голови (з урахуванням кореня `body` — упав, ліг, нахилився)
і повертається разом з головою на вказану частку. Мишка працює поверх, вмикання/вимикання — з fade кліпа.
Рахує `CameraSolver` (ядро), ставить міксин `Camera.setup` (крен — `GameRenderer.bobHurt`), тобто
однаково на всіх лоадерах. Перевірено в грі: лежачий — камера з 1.62 опускається до 0.26 над ступнями.

### Перемикачі першої особи

```java
ShaurmaAnim.setFirstPersonArms(false); // руки рушія від першої особи (first_person: model/hide) — вимкнути
ShaurmaAnim.setHeadCamera(true);       // камера за головою — окрема опція
```
За замовчуванням обидва увімкнені. Клей лоадера сам шукає моди першої особи (`FirstPersonCompat`):
* `firstperson` (First-person Model) малює справжнє тіло → руки рушія вимикаються, **камера лишається**:
  тіло, яке видно, анімує той самий рушій, і камера в оці цього тіла — саме те, що треба;
* `realcamera` (Real Camera) і малює тіло, і сам ставить камеру в голову → вимикаються обидва
  (два міксини на `Camera.setup` смикали б камеру).

Явний виклик мода перекриває автодетект (`resetFirstPersonOverrides()` — повернути автоматику).
Стан видно в `/sanim info`.

## 4в. Емоції (колесо на B)

```java
// спільний код (клієнт показує, сервер перевіряє id); кліп — звичайний кліп рушія
ShaurmaEmotes.register(Emote.builder("maniacmod:wave", "maniacmod:emote_wave").name("Помахати")
    .category("social").upperBody().build());                   // upper_body — можна йти
ShaurmaEmotes.register(Emote.builder("maniacmod:sit", "maniacmod:emote_sit").name("Сісти")
    .fullBody().loop(true).build());                            // full — рух зупиняє (interruptOnMove)

ShaurmaEmotes.setServerGate((player, emote) -> canEmote((ServerPlayer) player)); // «чи можна ЗАРАЗ», і щотіка
ShaurmaEmotes.setClientGate(player -> canEmoteClient((Player) player));         // чи відкривати колесо
ShaurmaEmotes.setFilter(e -> e.category().equals("social"));                    // лише частина для режиму
ShaurmaEmotes.setWheelEnabled(false);                                           // вимкнути зовсім
```

* Утримати **B** — колесо з 8 слотів; у центрі — **3D-модель самого гравця** (його скін, броня, предмет),
  яка вже грає наведену емоцію. Окремий `Animator` превʼю, рендер — ванільний `renderEntityInInventory`
  через `McAnim.withPreview` (весь рендер-ланцюг рушія: маски, пропси). Гравець у світі в цей час не змінюється.
* Відпустити на сегменті → `EmoteMessage` на сервер → хук мода → `ServerAnimations.play` (шар ACTION) усім.
  У центрі — скасувати; коротке натискання B, поки емоція грає, або повторний вибір тієї ж — зупинити.
* ПКМ у колесі або `/sanim emotes` — налаштування: список емоцій за категоріями, 3D-превʼю, перетягування
  в слот (слот у слот — обмін, за колесо — прибрати), ПКМ по слоту — очистити. Зберігається в
  `config/shaurma_lib/emote_wheel.txt`; поки гравець нічого не налаштував — слоти заповнюються емоціями мода.
* Переклади: `emote.<ns>.<id>`, `emote.category.<категорія>` (інакше — назва з реєстру).
* Ядро (`dev.shaurmalib.anim.emote`): `Emote`, `EmoteRegistry`, `EmoteWheel` (слоти + файл), `EmoteSessions`
  (серверний облік: рух/заборона → стоп), `EmoteMessage`, `EmoteNetwork` (SPI клієнт → сервер, реалізує лоадер).
  Шар версії (`dev.shaurmalib.anim.mc.emote`): `EmoteClient` (клавіша), `EmoteWheelScreen`, `EmoteSettingsScreen`,
  `EmotePreview`, `ServerEmotes`.

## 5. Код: три способи запустити анімацію

```java
// 1) Сервер: побачать усі, хто бачить гравця (і він сам), навіть ті, хто підключився посеред дії
ServerAnimations.play(player, "maniacmod:medkit_use");

// 2) Правило «поки X — грай Y» (клієнт, один раз при старті). Само вмикає/гасить з кросфейдом,
//    не перебиває ручні дії, з кількох правил шару бере вищий priority.
ShaurmaAnim.bind(AnimBinding.when("maniacmod:downed", s -> ClientMatchState.isFallen(s.uuid()))
    .play("maniacmod:downed_lie").slot(Slot.POSE).priority(50).rig("player"));
ShaurmaAnim.bind(AnimBinding.holding("maniacmod:flashlight", "maniacmod:flashlight_hold").mask("right_arm"));

// 3) Напряму на клієнті
ShaurmaAnim.play(uuid, PlayRequest.of("ns:clip").slot(Slot.GESTURE).mask("head").fade(0.1f, 0.2f));
```

Локомоція (стояння/ходьба/біг/присід/стрибок/падіння) — `LocomotionSet` на риг. Цикли кроку йдуть
від **пройденої відстані**, а не від часу — ноги не ковзають. Відсутній стан падає на запасний
(біг → ходьба → стояння), а плавання/їзда/елітри без кліпа лишаються ванільними.
`ShaurmaAnim.setPlayerLocomotion(true)` замінює ванільні рухи гравця кліпами `shaurma_lib:player/*`.
Поки гравець використовує предмет (лук, щит), руки локомоції поступаються ванільній позі.

GeckoLib-модель: прибрати контролери з `GeoAnimatable`, у рендерері —
`GeoAnimBridge.apply(bone, pose)` / `restore(bone, saved)` навколо `renderRecursively`
(приклад — `ManiacGeoRenderer`). Кадр рахується з partialTick — без ривків GeckoLib на 20 Гц.

## 6. Blockbench

Анімація гравця — це сцена: риг гравця + окремі моделі, що існують лише поки грає кліп (аптечка, бинт,
шприц, 3D-ефект). Це НЕ предмети слотів — у грі вони малюються рівно так, як у Blockbench.

1. File → Plugins → Load Plugin from File → `tools/blockbench/shaurma_anim.js`.
2. Новий проєкт **Shaurma: модель гравця** (стартовий екран) або Animation → **Shaurma: створити риг гравця**.
   Animation → **Shaurma: скін гравця (PNG)** — текстура лише для кубиків ригу.
3. **File → Import → Shaurma: модель у анімацію** — `.bbmodel` або `.geo.json`:
   * текстури: вбудовані в .bbmodel / поруч з .geo.json / `../textures`; інакше попросить PNG.
     Кілька текстур зшиваються в одну (рушій малює пропс однією текстурою);
   * **Прив'язати до**: права/ліва рука (`right_item`/`left_item`), голова, тулуб… або **Вільно** —
     модель у просторі гравця, не за кісткою (ефекти навколо, предмет на землі);
   * масштаб, зсув, «ховати ванільний предмет», напівпрозорість, світіння;
   * **Як предмет у руці** (для `right_item`/`left_item`, якщо в .bbmodel є Display «thirdperson_righthand»):
     модель стає рівно так, як ванільний предмет у руці в грі — поворот, зсув і масштаб з Display
     (ліва рука — дзеркально, як у грі). Без цього модель ставала б неповернутою й у повний розмір;
   * якщо в .bbmodel є анімації — вставити вибрану в поточну анімацію з поточного кадру (кришка відкривається).
   Одразу після імпорту відкривається **Shaurma: підігнати пропс** — масштаб, зсув і поворот змінюються
   наживо в сцені (ключі позиції кришки/бинта масштабуються разом); «Скасувати» повертає як було.
   Те саме будь-коли пізніше: ПКМ на групі пропса → **Shaurma: підігнати пропс**.
   Імена груп стають унікальними з префіксом пропса (група `root` моделі не стане кісткою `body` гравця).
4. Пропс вручну: група ПІД кісткою ригу або в корені сцени поза ригом (вільний). Підгрупи анімуються в тій самій анімації.
5. Animation → **Shaurma: показати / сховати пропс з цього кадру** — ступінчасті ключі масштабу 1/0.
6. Animation → **Shaurma: передати пропс в іншу кістку** — бинт з аптечки (права рука) → ліва рука з кадру 1.2 с:
   копія під `left_item` (та сама відстань від кістки), оригінал ховається, копія з'являється. Далі — ключі копії.
7. Animation → **Shaurma: налаштування анімації** — **Тип** (повна / часткова / синхронна / стан, див. 3б), fade, погляд, перша особа; для стану — сила руху й предмет у руці.
8. File → Export → **Export Shaurma Animation**, «Куди»:
   * **у папку resources мода** — `src/main/resources` (знаходиться сама поруч з .bbmodel); потрапить у збірку мода;
   * **одразу в гру** — `<гра>/shaurma_anim/<файл>/` (`.minecraft` або `run/` дев-середовища знаходяться самі); у грі F3+T;
   * **.zip** (веб-версія) — **не розпаковувати**: покласти в `<гра>/shaurma_anim/` (рушій читає архів сам, ресурс-пак
     вмикати не треба) або в `resourcepacks/` як звичайний ресурс-пак (у ньому є `pack.mcmeta`).
   Налаштування запам'ятовуються в проєкті. **Автоекспорт при Ctrl+S** — галочка в цьому ж вікні.
   **Shaurma: швидкий експорт** (Ctrl+Alt+E) — повтор без діалогу.
   Пишуться кліпи, `.geo.json` пропсів і їхні текстури (з `<гра>/shaurma_anim/` PNG реєструються як текстури гри); після експорту — список файлів і попередження
   (дві групи з однаковим ім'ям, пропс без текстури, кілька текстур).
9. У грі: F3+T перезавантажує кліпи; `/sanim play <кліп>` — показати на собі; `/sanim list`, `/sanim info`.

### Приклад: аптечка → бинт у ліву руку
1. Імпорт `medkit.bbmodel` → прив'язати до `right_item`, ховати предмет у правій; вставити його анімацію «open» з 0.4 с.
2. Ключі `right_arm`: піднята перед собою. Вибрати групу бинта всередині аптечки, курсор на 1.2 с →
   **передати пропс** → `left_item`. Ключі `left_arm` + копії бинта — тягне до себе й намотує.
3. Налаштування анімації: шар `action`, маска `upper_body` (ноги ходять), перша особа — `model`. Експорт.

## 7. Мережа

`AnimMessage` (ядро) — формат дротів незалежний від лоадера; `AnimNetwork` — SPI, який реалізує лоадер
(Forge: пакет у каналі `shaurma_lib:main`, доданий у кінець — старі id не змінились;
Fabric: канал `shaurma_lib:anim`). Емоції — зворотний напрямок: `EmotePacket` (Forge, наступний id після
анімаційного) / канал `shaurma_lib:emote` (Fabric). `AnimMessage` дописує в кінець байт режиму повтору
(емоція «зациклена» з реєстру); старе повідомлення без нього читається як «режим з кліпа». Пакет, що прийшов раніше за сутність, чекає її до 3 с.

## 8. Тести

```bash
# ядро: 157 перевірок (зокрема реєстр/слоти/сесії емоцій, перемикачі першої особи) + парсинг УСІХ анімацій маніяка (без гри, лише JDK)
javac --release 17 -d out $(find core/src/main/java/dev/shaurmalib/anim core/src/test/java/dev/shaurmalib/anim -name '*.java')
java -cp out dev.shaurmalib.anim.AnimSmokeTest ../maniac_shaurma/src/main/resources

# плагін Blockbench (Node) + перевірка його виходу тим самим рушієм
node tools/blockbench/test/export.test.js out/bb
java -cp out dev.shaurmalib.anim.PluginExportCheck out/bb
```

## 9. Перевірка в грі (маніяк)

`maniac_shaurma`: `gradlew runClient -PdevLib=true -Pselftest=true` — `AnimSelfTest` сам створює світ,
ходить/біжить/присідає, грає аптечку (3-тя і 1-ша особа), адреналін, ліхтарик, лежання, несення,
ремонт, потім робить гравця маньяком (geo-модель) і перевіряє ходьбу, удар на ходу (верх тіла), біг,
несення однією рукою, погляд. Емоції: відкриває колесо, наводить сегменти (скріншоти з 3D-моделлю
в центрі й перевірка, що поза превʼю змінюється), вибирає — емоція приходить із сервера; «сісти» гасне
від руху, повторний вибір зупиняє танець; екран налаштувань з превʼю і призначенням слота. Скріншоти — `run/screenshots/animtest_*.png`, підсумок — у лозі.
