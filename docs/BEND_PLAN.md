# План: згинання рук і ніг (лікті / коліна) у рушії ShaurmaLib

> Статус: план, код ще не писався. Ціль — `mc/v1_20_1` (Forge + Fabric), ядро `core` без змін у принципах.

## 0. Висновок

**Так, можна, і краще, ніж у PlayerAnimationLibraryMoreRotation (PALMore).**
Але **не підключати** PALMore і не копіювати його код:

| | PALMore | Що беремо |
|---|---|---|
| Ліцензія | LGPL-3 (сам мод), Bendable Cuboids — MIT (KosmX) | лише **ідею**; код пишемо свій |
| Версії MC | 1.21.1+ / 26.x, жорстко прив'язаний до PAL + Bendable Cuboids + MixinSquared | у нас 1.20.1 і свій рушій — їхні хуки (`PlayerAnimBone`, `AvatarAnimManager`) нам не підходять |
| Модель згину | нативно лише X (скаляр `bend`), Y/Z — «латка» поверх вершин; `bend` — окреме поле поза звичайними каналами | згин = **звичайна кістка** (`right_forearm`), повне 3D-обертання (swing + twist) |

Суть технології (вона ж у bendy-lib / Bendable Cuboids): **кубоїд руки/ноги малюється не 6 гранями, а
сіткою, розрізаною посередині**. Верхня половина стоїть, нижня обертається навколо шарніра (лікоть/коліно),
кільце вершин на шарнірі ставиться «на ус» (miter) — без щілин і без злому текстури.

Головна архітектурна ідея для ShaurmaLib: **нижній сегмент — це кістка ригу**, а не спецполе. Тоді шари,
маски, кросфейди, additive, Molang, мережа, емоції, Blockbench-експорт працюють **без жодних змін** —
змінюється лише те, як адаптер *малює* руку.

---

## 1. Модель даних (ядро, `core`)

### 1.1 Нові кістки в `Rig.PLAYER` (`core/.../anim/rig/Rig.java`)

```java
.bone("right_forearm", "right_arm",  6, 18, 0)   // лікоть: середина руки (BB, Y вгору)
.bone("left_forearm",  "left_arm",  -6, 18, 0)
.bone("right_shin",    "right_leg",  1.9f, 6, 0) // коліно: середина ноги
.bone("left_shin",     "left_leg",  -1.9f, 6, 0)
```

* `right_item` / `left_item` **переносимо під `*_forearm`** (предмет іде за передпліччям). Старі кліпи
  не ламаються: коли передпліччя в нулі, ланцюжок ідентичний.
* Маски будуються з імен (`Rig.buildDefaultGroups`): `forearm` містить `arm` + `right/left`, `shin` — у
  групі `legs`. Тобто `arms`, `right_arm`, `legs`, `upper_body`, `full-legs` **автоматично** включають
  нові кістки. Перевірити тестом (див. §6). Увага: `right_arm!` (лише кістка без дітей) тепер **не**
  включає лікоть — це правильна семантика, але відзначити в `ANIMATION.md`.
* Півот у ригу — для Blockbench/ланцюжка пропсів. **Реальний шарнір у рендері береться з геометрії
  кубоїда** (центр по довгій осі), тому slim-руки (3 px) працюють без окремого ригу.

### 1.2 Синоніми (`BoneNames.PLAYER_ALIASES` + дзеркально в `shaurma_anim.js`)

```
right_arm_lower, right_lower_arm, lower_arm_right, forearm_right, right_elbow → right_forearm
right_leg_lower, right_lower_leg, lower_leg_right, shin_right,   right_knee  → right_shin
(аналогічно left_*)
```
`Right_Arm_Lower` (DragonCore-риги) після `snake()` дає `right_arm_lower` → працює.

### 1.3 Імпорт чужого формату (`ClipParser`)

PlayerAnimator/PAL-кліпи мають у кістки руки канал `bend` (скаляр, градуси) і інколи `bend_direction`/`axis`;
PALMore — `bend: { rotation:[x,y,z], position, scale }`. Парсер **перекладає** це у трек кістки-нащадка:

| Вхід | Вихід |
|---|---|
| `right_arm.bend: 35` або `[35,0,0]` | `right_forearm.rotation = [-35, 0, 0]`* |
| `bend` + `bend_direction: α` | swing навколо осі X, поверненої на α навколо Y |
| `bend.rotation [x,y,z]` (PALMore) | `right_forearm.rotation = [x,y,z]`* |
| `bend.position/scale` | `right_forearm.position/scale` (етап 4, опційно) |
| `torso.bend` | поки ігнорувати з попередженням у лозі (етап 5) |

\* знак перевірити в грі на еталонній емоції PlayerAnimator, зафіксувати тестом.

### 1.4 Чиста математика згину — у `core` (тестується без гри)

`core/.../anim/bend/BendMath.java` (лише JDK, `float[]`):

```
input : q        — обертання нижнього сегмента відносно верхнього (з BoneTransform, ZYX градуси)
        hinge    — точка шарніра в просторі частини
        axisLong — довга вісь кінцівки (Y у ModelPart)
output: 3 матриці/функції: upper (identity), mid (miter), lower (q)
```

1. **Swing-twist розклад** `q = swing · twist` відносно довгої осі.
   * `twist` (поворот передпліччя навколо себе) — нижнє кільце повний, кільце шарніра половина.
   * `swing` (власне згин) — нижнє кільце повний, шарнір — **miter**.
2. **Miter-кільце**: вершину шарніра з відступом `d` повертаємо на `swing½` і розтягуємо компоненту
   в площині згину на `1/cos(θ/2)` (точка перетину бічного ребра з бісекторною площиною). Товщина
   ліктя зберігається, щілин нема.
3. **Обмеження** `θ ≤ 165°` (далі внутрішній бік вивертається), м'яке — через `clamp` з попередженням
   у debug-режимі.
4. `q == identity` → швидкий шлях: повертаємо «не згинати», рендер ванільний байт-в-байт.

---

## 2. Рендер (шар версії, `mc/v1_20_1`)

### 2.1 Стан згину на `ModelPart`

`MixinModelPartBend` (новий) додає в `ModelPart`:
```java
@Unique BendState shaurma$bend;   // null = ванільний рендер
```
`BendState` = кватерніон `q` + прапор + (кеш) розкладу swing/twist. Інтерфейс `BendablePart`
(`shaurma$getBend/setBend`) — щоб звертатись без рефлексії.

### 2.2 Перехоплення малювання

`ModelPart#compile(PoseStack.Pose, VertexConsumer, int, int, float, float, float, float)` — `@Inject HEAD,
cancellable`: якщо `shaurma$bend` активний → `BendMeshRenderer.render(this.cubes, bend, …)` і `ci.cancel()`.
Інакше нічого не робимо (нуль вартості для всіх інших сутностей).

### 2.3 `BendMeshRenderer` + кеш шаблонів

* Для кожного `ModelPart.Cube` один раз будуємо `BendTemplate` (кеш `WeakHashMap<Cube, BendTemplate>`
  або `@Unique` поле на `Cube`): беремо `polygons` (`@Accessor`), бічні 4 грані **ріжемо по середині
  довгої осі** з інтерпольованими UV → 10 квадів (верх, низ, 4×2 боки), 3 кільця вершин: upper / mid / lower.
* Кожен кадр: трансформуємо 3 кільця (`BendMath`), нормалі — від сегмента (верхні квади — без змін,
  нижні — `q·n`, у шарнірних квадах — нормаль своєї половини). Пишемо в `VertexConsumer` тим самим
  форматом, що ваніль (`vertex(x,y,z,r,g,b,a,u,v,overlay,light,nx,ny,nz)`).
* Нуль алокацій у кадрі: робочі `float[]`/`Vector3f` у `ThreadLocal`.
* Опція якості `bend_segments` (1 = miter, 2–4 = заокруглений лікоть) — у конфіг клієнта, за замовчуванням 1
  (піксельний стиль скінів).

### 2.4 Запис пози → модель (`PlayerModelBridge`)

* `apply(...)`: для `RIGHT_ARM` і т.д. — після `write(...)` пишемо `BendState` з `pose.bone(RIGHT_FOREARM)`,
  якщо кістка `touched`; інакше `bend = null`.
* `syncOverlays(...)`: `copyFrom` копіює лише позу → **додатково копіюємо bend** на `rightSleeve`,
  `leftSleeve`, `rightPants`, `leftPants` (інфльовані кубоїди мають той самий центр — шарнір збігається).
* `reset(...)`: обнуляє bend на всіх 10 частинах (інакше «залипання» згину після кінця кліпа —
  саме цей баг описаний у документації PALMore).
* `applyArms(...)` (перша особа) — так само пише bend → лікоть видно і від першої особи.

### 2.5 Усе, що «висить» на руці/нозі

| Що | Де | Зміна |
|---|---|---|
| Предмет у руці | `MixinItemInHandLayerAnim` | нова ін'єкція **після** `translateToHand` (`shift = AFTER`): `translate(hinge) · rotate(q) · translate(-hinge)`. Порядок важливий: до ванільних `XP(-90)·YP(180)` і до кістки `right_item` |
| Броня | `HumanoidModel#copyPropertiesTo` | `@Inject TAIL`: копіювати bend у модель броні → наручі/поножі/чоботи згинаються разом |
| Пропси | `PropRenderer` (гілка `parentIdx`) | новий випадок `parent = *_forearm/*_shin`: частина → `translateAndRotate` → трансформ нижнього сегмента; пропси під `right_item` автоматично через п.1 |
| Косметика | `meta/mc/render/CosmeticRenderer` | хелпер `BendTransforms.applyLower(ps, part)` для слотів, що сидять нижче шарніра (рукавиці, чоботи); вище — без змін |
| Перша особа | `FirstPersonRenderer` | працює через `applyArms` + хук `compile`; перевірити кут камери |
| GeckoLib | `GeoAnimBridge` | **нічого** — у geo-моделей справжні кістки; кліп з `right_forearm` на geo-гуманоїді знайде кістку за ім'ям, якщо вона є |

---

## 3. Blockbench (`tools/blockbench/shaurma_anim.js`)

1. `PLAYER_RIG`, півоти, `canonicalPlayerBone` — додати 4 кістки й ті самі синоніми, що в ядрі.
2. Шаблон `player-animation_model.bbmodel`: кожна рука/нога — **дві групи**: верхній куб 4×6×4 під
   `right_arm`, нижній 4×6×4 під `right_forearm` (UV розрізані навпіл, щоб превʼю виглядало як скін).
   `right_item` — під `right_forearm`.
3. Експорт: нові кістки — звичайні треки, нічого спеціального.
4. (Бонус) Якщо версія Blockbench має IK-ланцюжки з «запіканням» у ключі — кнопка «Bake IK» перед
   експортом: аніматор тягне кисть, лікоть рахується сам.
5. `test/export.test.js` — кейс: кліп із `right_forearm` експортується з правильною ієрархією та півотом.

---

## 4. Сумісність і ризики

| Ризик | Що робимо |
|---|---|
| **Embeddium/Sodium 1.20.1** мають fast-path рендеру `ModelPart` і можуть перехопити `compile` раніше | перевірити порядок ін'єкцій; за потреби — пріоритет міксина та/або перехоплення рівнем вище (`ModelPart#render`). Обов'язковий ручний тест з Embeddium (Forge) і Sodium (Fabric) |
| OptiFine / EMF (CEM-моделі) замінюють геометрію | якщо `cubes` не стандартні (не 6 полігонів) — тихо падаємо в ванільний рендер без згину |
| PlayerAnimator + bendy-lib у тій самій збірці | bend вмикаємо лише коли **наш** `Pose` торкнувся `*_forearm`; інакше не втручаємось. Тест співіснування |
| Iris (шейдери) | пишемо в `VertexConsumer` у ванільному форматі з нормалями — має працювати; тест |
| Кути > 150° | clamp у `BendMath` |
| Продуктивність | 40 вершин замість 24 лише для зігнутих частин гравців; шаблони кешовані; без алокацій |

---

## 5. Етапи (кожен — окремий коміт, що збирається й проходить тести)

| # | Етап | Обсяг | Готово, коли |
|---|---|---|---|
| 1 | **Ядро**: кістки в `Rig.PLAYER`, синоніми, `BendMath` (swing-twist, miter), тести | ~250 рядків | `gradle :core:test` зелений, маски містять нові кістки |
| 2 | **Рендер**: `BendState`, `MixinModelPartBend`, `BendMeshRenderer`, `PlayerModelBridge` (apply/sync/reset/firstperson) | ~400 | `/sanim play` тестового кліпа: лікоть і коліно гнуться, без щілин, рукави/штани разом, згин зникає після кліпа |
| 3 | **Навісне**: предмет у руці, броня, пропси, косметика | ~150 | меч/ліхтарик ідуть за передпліччям; поножі гнуться |
| 4 | **Імпорт**: `bend` PlayerAnimator/PAL/PALMore у `ClipParser` (+ `position/scale` нижнього сегмента) | ~120 | еталонна емоція PlayerAnimator виглядає так само, знак зафіксовано тестом |
| 5 | **Blockbench**: риг, шаблон, синоніми, тест експорту; (опц.) згин тулуба `torso_upper` | ~100 + .bbmodel | анімацію з ліктями зроблено в BB і без змін відтворено в грі |
| 6 | **Сумісність і документація**: Embeddium/Sodium/Iris/PlayerAnimator, розділ у `ANIMATION.md`, конфіг `bend_segments` | — | чек-лист §6 пройдено |

Порядок 1 → 2 обов'язковий; 3, 4, 5 після 2 незалежні.

---

## 6. Перевірка

**Автотести (core, без гри):**
* `BendMath`: `θ=0` → вершини незмінні точно; спільні ребра upper/mid і mid/lower збігаються (нема щілин)
  для сітки кутів −165…165° по X, Z і довільних осях; товщина в шарнірі = вихідна; twist не змінює довжину.
* `Rig`: `mask("arms")`, `mask("full-legs")`, `mask("right_arm")` містять/не містять нових кісток як очікується;
  `right_item` — нащадок `right_forearm`.
* `ClipParser`: `bend: 35`, `bend: [x,y,z]`, PALMore-контейнер, keyframe-таймлайн `bend` → правильний трек.
* `PluginExportCheck` / `export.test.js`: експорт кліпа з передпліччям.

**Ручний чек-лист у грі (Forge і Fabric 1.20.1):**
1. Широкі й slim-скіни, з/без зовнішнього шару.
2. Згин 0 / 45 / 90 / 150° по X, Z, twist; кросфейд між кліпами; additive-шар поверх.
3. Маска `upper_body` + ходьба: руки гнуться, ноги йдуть.
4. Предмет у руці, щит, лук; броня шкіряна/незеритова; пропс на `right_item`.
5. Перша особа, F5, інвентар-превʼю (`Preview`/`WardrobeScreen`), інші гравці в мультиплеєрі.
6. Embeddium/Sodium, Iris, разом із PlayerAnimator.
7. Після закінчення кліпа — модель ідентична ванілі (нема «залипання»).
