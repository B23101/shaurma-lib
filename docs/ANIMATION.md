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
                              "visible": {"0.0": false, "0.62": true, "1.45": false}, "hide_held": "right" } },
      "events": [ { "time": 0.45, "type": "sound", "value": "minecraft:item.armor.equip_leather", "volume": 0.8 },
                  { "time": 0.42, "type": "custom", "value": "maniacmod:geo_item/medkit/use" } ]
    }
  }
}
```

* Значення — як у Blockbench UI (градуси, пікселі). Рушій сам переводить у простір моделі.
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

1. File → Plugins → Load Plugin from File → `tools/blockbench/shaurma_anim.js`.
2. Відкрити `player-animation_model.bbmodel` (або Animation → **Shaurma: створити риг гравця**).
3. Пропс: додати групу ПІД кістку ригу (наприклад під `right_item`) з власними кубами й текстурою.
   Підгрупи пропса (кришка) анімуються в тій самій анімації. Сховати — масштаб 0.
   ПКМ на групі → **Shaurma: налаштування пропса** (ховати ванільний предмет, світіння).
4. Animation → **Shaurma: налаштування анімації** — шар, маска, fade, погляд, перша особа.
5. File → Export → **Export Shaurma Animation** → вибрати папку `src/main/resources`.
   Пишуться кліпи, `.geo.json` пропсів і їхні текстури.
6. У грі: F3+T перезавантажує кліпи; `/sanim play <кліп>` — показати на собі; `/sanim list`, `/sanim info`.

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
