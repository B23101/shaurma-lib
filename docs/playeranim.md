# Рушій анімацій гравця (`withPlayerAnim()`)

Клієнтський рушій поз поверх PlayerAnimator (PAL 1.0.2-rc1, MC 1.20.1).
Показує позу гравця **всім**, хто його бачить, без власного мережевого пакета:
`PlayerModelMixin` PAL застосовує `AnimationStack` кожного `AbstractClientPlayer`.
Умова — shaurma-lib + PAL встановлені в усіх клієнтів сервера.

## Швидкий старт

```java
// 1. Один раз при старті (клієнт): іменований пресет.
PoseActionRegistry.register(PoseAction.of("maniac:flashlight",
        PoseSource.hold("maniac", "flashlight").withFade(4, 4),
        PoseLayerId.ITEM_ACTION));
PoseActionRegistry.register(PoseAction.of("maniac:throw",
        PoseSource.oneShot("maniac", "throw").withFade(2, 3).withEase(PoseEase.OUTCUBIC),
        PoseLayerId.ITEM_ACTION));

// 2. Коли потрібно:
PlayerPoseController.playAction(player, "maniac:flashlight");   // false → PAL ще не готовий, ретраїти
PlayerPoseController.stopAction(player, "maniac:flashlight");   // плавно, за fadeOutTicks
PlayerPoseController.playAction(player, "maniac:throw");        // більше нічого: сам завершиться
```

Без пресетів — те саме через `trigger(player, layerId, source)` / `stop(player, layerId)`.
Старий API (`PoseSource.jsonAnimation(ns, id, looping)`, 3-арг конструктор) працює як раніше, без fade.

## Hold чи one-shot

Це визначає **сам `.json`** (`looping` → `KeyframeAnimation.isInfinite`), а не код:

| `.json` | Поведінка | Хто знімає |
|---|---|---|
| `looping: true` | HOLD | консюмер: `stop()` / `stopAction()` |
| `looping: false` | ONE_SHOT | **рушій сам**, за `fadeOutTicks` до кінця кліпа |

Поле `PoseSource.looping` — лише документація наміру в місці виклику; розсинхрон із файлом безпечний.
Довжина береться з файлу (`stopTick`), `durationTicks` не існує.

`isActive()` під час fade-out вже `false` — наступну позу можна запускати одразу; `isVisible()` — чи щось ще видно.

## Шари

| `PoseLayerId` | Пріоритет | Призначення |
|---|---|---|
| `DEATH_LIE` | 1100 | лежача смерть |
| `DROP_FALL` | 1000 | падіння при дропі |
| `ZIPLINE_RIDE` | 950 | зіплайн |
| `AIRPLANE_SEAT` | 900 | сидіння в літаку |
| **`ITEM_ACTION`** | **700** | **ліхтарик, каністра, ремонт, кидок** |
| `CUSTOM` | 500 | будь-що інше |
| **`LOCOMOTION_OVERRIDE`** | **100** | **кастомна ходьба/біг/шифт — лише за явним `trigger`** |

Вищий шар **перекриває** нижчі на кістках, де має keyframe-дані; кістка без даних прозора.
Override/additive вирішує природа шару, окремого enum немає.

## Що малює художник у `.json` (коду не потрібно)

* Кістки: `head`, `torso` (= ванільна `body`), `rightArm`, `leftArm`, `rightLeg`, `leftLeg`;
  синтетична `body` рухає **все** тіло, `torso` — лише корпус (для нахилу під час ремонту).
* **`rightItem` / `leftItem`** — положення самого ліхтарика/каністри відносно руки
  (читає `HeldItemMixin` PAL: `ROTATION` + `POSITION`/16).
* Кути в keyframe-ах **загортаються за ±π** (значення 5.0 рад читається як 5.0−2π). Пишіть у межах ±π.
* Для ніг використовуйте `ROTATION`/`BEND`; великий `POSITION` дає косметично дивний результат
  (візуальна модель і хітбокс незалежні).

## Динамічна правка кісток (опційно)

Лише для значень, що залежать від стану гри (напр. нахил за `getXRot()`); статичне малюйте в `.json`.

```java
PlayerPoseController.applyBoneAdjustment(player, PoseLayerId.ITEM_ACTION,
        bone -> switch (bone) {
            case "rightArm", "leftArm" -> Optional.of(BoneAdjustment.Part.pitchDegrees(player.getXRot() / 2));
            default -> Optional.empty();          // прозора кістка = «маска»
        });
```

* **Додається** до нижніх шарів (на відміну від keyframe-кістки, що замінює).
* Провайдер викликається **щокадру для кожної кістки** — тримайте його дешевим.
* Повторний виклик із тим самим провайдером — no-op. Для плавної появи викликайте **до** `trigger`.
* Живе одну «сесію» пози: скидається на завершенні / `stopAll(player, true)`.
* Через `PoseAction.withAdjustment(...)` ставиться автоматично в `playAction`.

## Що рушій виправляє відносно «голого» PAL 1.0.2-rc1

Усе нижче відтворено запуском реального коду, не прочитано з вихідників.

1. **Fade постфактум неможливий.** `replaceAnimationWithFade` пропускає fade, якщо кліп уже неактивний,
   а `AnimationStack` пропускає неактивні шари → one-shot зникав би миттєво. Тому fade-out **пре-емптивний**:
   стартує за `fadeOutTicks` до `stopTick`, поки кліп ще грає (PAL гарантує `stopTick ≥ endTick+3`).
   Довший за залишок fade скорочується до залишку.
2. **Висячі fade-и.** Якщо обидві анімації fade-у неактивні, `canRemove()` не перевіряється ніколи —
   модифікатор висить вічно і накопичується. Рушій тікає шари **ззовні** і явно чистить.
3. **`NaN` в `AdjustmentModifier`.** `0f/0f` при `beginTick==0` на першому кадрі → мерехтіння руки на один кадр.
   `SafeAdjustmentModifier` виправляє; плюс захист від `NaN/Inf` і винятків провайдера.
4. **Лаг fade-out правки.** `AdjustmentModifier.fadeOut` PAL відстає на 1 тік і лишає `1/n` правки, яку різко
   знімає завершення. Замінено на точне лінійне згасання.
5. **`Ease.OUTBOUNCE` = `outBack`** (баг PAL) і `CONSTANT` (завжди 0) — у `PoseEase` **свідомо відсутні**.
6. **Fade-in «з нічого»** PAL пропускає за замовчуванням — рушій створює його явно.
7. **Пауза одиночної гри**: PAL не тікає стек, тому рушій теж не тікає (інакше one-shot «з'їдається»).

Плюс збережено relog/respawn-фікс (звірка `AnimationStack` + `WeakReference` власника): поза переноситься на новий
екземпляр гравця **без** fade-in.

## Обмеження (чесно)

* Авто-завершення працює лише для `KeyframeAnimationPlayer`. Процедурна `IAnimation` у шарі — тільки HOLD.
* Fade-out one-shot **множиться** на власний хвіст кліпа (`stopTick−endTick`, ≥3 тіки) — кінцева крива
  крутіша за лінійну. Це властивість PAL, а не помилка.
* Смерть/зміну фази рушій не ловить сам (нема надійної клієнтської події) — викликайте
  `stopAll(player, true)` зі своєї точки.
* Не існує жодного `.json` для `LOCOMOTION_OVERRIDE` — шар додано як примітив за запитом, без реального споживача.

## Тести

`tools/playeranim-harness/run.sh <шлях до minecraftPlayerAnimator-port-1.20>` — 345 перевірок проти **справжнього**
coreLib PAL (без Minecraft; JDK 17+, python3, Maven Central не потрібен). **Не покриває:** `PlayerPoseEvents`,
реальні міксини PAL, рендер, JSON-завантаження кліпів, збірку Gradle/ForgeGradle — це перевіряється лише
збіркою й запуском у грі.

Одноразова мутаційна перевірка (навмисно внесені баги в рантайм): з 11 значущих мутацій вбито всі, окрім
надлишкової третьої лінії NaN-захисту (`getFadeIn`), яку дублюють дві інші.
