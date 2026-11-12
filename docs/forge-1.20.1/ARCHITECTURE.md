# shaurma-lib — архітектура бібліотеки для Minecraft Forge модів

## Огляд

`shaurma-lib` — це спільна бібліотека для створення Minecraft модів на
Forge 1.20.1. Вона містить:

- **TeleportService** — служба телепортації з підтримкою chunk safety
  і TeleportedEvent.
- **AnimatedItemSystem** — система анімованих предметів з GeckoLib.
- **OverlayEngine** — клієнтські оверлеї (HUD, actionbar, alert, worldtint).
- **ConfigTree** — конфігураційне дерево (YAML, namespace, mode configs).
- **GameModeContract** — контракт режиму гри (GameModeContract interface).
- **MatchHistoryStore** — зберігання історії матчів.
- **LeaderboardEngine** — обчислення топів.
- **RadioDialogManager** — система радіо-диктора.
- **InteractionLockRegistry** — блокування взаємодії.
- **DamageInterceptor** — власний інтерцептор урону.
- **GraffitiRegistrationHook** — движок графіті.
- **FreecamController** — вільна камера.
- **SkinnableEntityBase** — сутності зі скінами.
- **ScreenEffectPostChain** — пост-обробка екранних ефектів.
- **ModeSettingsScreen** — меню налаштування режимів.
- **LifecycleModule** — lifecycle (MatchLifecycleState, PlayerJoinFlow, PlayerReturnFlow).
- **StyleModule** — стилі (StyleTheme, HotbarPanelRenderer, TabListStyle, MenuStyleTheme).
- **ChatFormatEngine** — форматування чату.
- **AnimationRecordFacade** — єдиний API запису анімацій.
- **TurnAnimationEngine** — анімації поворотів.
- **PlayerPoseController** — кастомні позі.
- **ArmOverride** — перевизначення руки (яка кістка замінює яку руку гравця).
- **ItemCameraTrack** — трек камери предмета.
- **InvisibleZoneEntityBase** — сутність-зона без хітбоксу.
- **AnimatedBlockEntityBase** — анімований блок.
- **HologramLeaderboardRenderer** — голограма лідерборду.
- **SpectatorSessionController** — режим спостереження.

## Архітектура

```
shaurma-lib/
├── lib-common/             # чиста Java, без net.minecraftforge.*
│   ├── config/
│   │   └── ShaurmaConfigTree.java       # дерево конфігів
│   ├── teleport/
│   │   ├── TeleportService.java        # служба телепортації
│   │   └── TeleportedEvent.java        # подія телепортації
│   ├── item/animated/
│   │   ├── ItemDefinition.java         # білдер предмета
│   │   ├── ItemActivation.java        # інтерфейс активації
│   │   ├── ArmOverride.java           # перевизначення руки
│   │   ├── ItemCameraTrack.java       # трек камери
│   │   ├── AnimationName.java
│   │   ├── HandSlot.java
│   │   ├── ActivateTick.java
│   │   └── ConsumeTick.java
│   └── ...
├── lib-forge/              # Forge 1.20.1 mod-jar
│   ├── src/main/java/dev/shaurmalib/forge/
│   │   ├── ShaurmaLib.java            # головний клас ініціалізації
│   │   ├── module/
│   │   │   ├── ArchitectureSniffer.java  # перевірка архітектури
│   │   │   ├── FMLModuleContext.java     # контекст модуля
│   │   │   ├── ConfigTreeHook.java       # хук конфігурації
│   │   │   └── TeleportModuleHook.java   # хук телепортації
│   │   └── mixins/
│   │       └── shaurma_lib.mixins.json
│   └── src/main/resources/
│       └── META-INF/mods.toml
└── docs/
    └── ARCHITECTURE.md
```

## Модулі

### ConfigTree

Дерево конфігів підтримує namespace (наприклад "snipers_shaurma") з
спільною папкою (game.yml, sounds.yml) і під-папками для кожного режиму.

#### API

```java
ShaurmaConfigTree configTree = ShaurmaConfigTree.builder(worldRoot, "snipers_shaurma")
    .build();

Path modeConfigFolder = configTree.modeConfigFolder("sniper_contract");
Map<String, Object> data = configTree.readYaml(modeConfigFolder.resolve("game.yml"));
configTree.saveYaml(modeConfigFolder.resolve("game.yml"), config);
```

### TeleportService

Служба телепортації з підтримкою chunk safety, fall-distance reset,
cross-dimension safety, passenger cascade і TeleportedEvent.

#### Використання

```java
TeleportService.teleport(player, target, x, y, z, yaw, pitch, TeleportReason.LOBBY_JOIN);
```

### AnimatedItemSystem

Система анімованих предметів з GeckoLib.

#### Реєстрація предмета

```java
ItemDefinition item = ItemDefinition.builder()
    .animationName("use")
    .activateTick(50)
    .consumeTick(61)
    .armOverride(HandSlot.OFF_HAND, "LeftArm")
    .armOverride(HandSlot.MAIN_HAND, "RightArm")
    .onActivate((player, hand) -> {
        // effect
    })
    .cameraTrack(ItemCameraTrack.builder()
        .keyframe(0.00f, 0.0f, 0.0f)
        .keyframe(0.35f, 0.0f, 2.2f)
        .build())
    .build();
```

### OverlayEngine

Клієнтські оверлеї (HUD, actionbar, alert, worldtint).

#### Відправлення сповіщення

```java
AlertNotificationSystem.push(new AlertSpec(
    "Title", "Description", icon, sound, color, 3
));
```

### GameModeContract

Контракт режиму гри.

#### Реалізація

```java
public class MyMode implements GameModeContract {
    @Override
    public String id() {
        return "my_mode";
    }

    @Override
    public String displayName() {
        return "My Mode";
    }

    @Override
    public String configFolder() {
        return "my_config";
    }

    // ...
}
```

### MatchHistoryStore

Зберігання історії матчів.

#### Використання

```java
MatchHistoryStore store = MatchHistoryStore.instance();
store.currentSession().put("key", "value");
```

### LeaderboardEngine

Обчислення топів.

#### Використання

```java
LeaderboardEngine engine = LeaderboardEngine.instance();
List<PlayerRanking> rankings = engine.computeTop(
    statSource, Comparator.comparingLong(PlayerRanking::score).reversed(), 10
);
```

### RadioDialogManager

Система радіо-диктора.

#### Використання

```java
RadioDialogManager.trigger(server, "RADIO_PLAYER_TARGET_REACHED_TEAM",
    TriggerContext.builder().player(player).teamId(teamId).build());
```

### InteractionLockRegistry

Блокування взаємодії.

#### Використання

```java
InteractionLockRegistry.lock(player, LockType.MOVEMENT, "kit_select_phase");
InteractionLockRegistry.unlock(player, LockType.MOVEMENT, "kit_select_phase");
```

### DamageInterceptor

Власний інтерцептор урону.

#### Реєстрація

```java
DamageInterceptor interceptor = (victim, source, amount, ctx) -> {
    // ?
    return true;
};
DamageInterceptorRegistry.register(interceptor, Priority.HIGH);
```

### GraffitiRegistrationHook

Движок графіті.

#### Реєстрація

```java
GraffitiRegistrationHook.register(item, recipe);
```

### FreecamController

Вільна камера.

#### Використання

```java
FreecamController.enterFreecam(player);
FreecamController.exitFreecam(player);
```

### SkinnableEntityBase

Сутності зі скінами.

#### Використання

```java
SkinnableEntityBase entity = new SkinnableEntityBase(player);
entity.setSkin(player.getSkin());
```

### ScreenEffectPostChain

Пост-обробка екранних ефектів.

#### Використання

```java
ScreenEffectPostChain.enable(ScreenEffect.MONOCHROME);
ScreenEffectPostChain.disable(ScreenEffect.MONOCHROME);
```

### ModeSettingsScreen

Меню налаштування режимів.

#### Реєстрація режиму

```java
GameModeRegistry.register(new MyMode());
```

### LifecycleModule

Lifecycle (MatchLifecycleState, PlayerJoinFlow, PlayerReturnFlow).

#### Використання

```java
LifecycleModule.setState(MatchLifecycleState.ACTIVE);
```

### StyleModule

Стилі (StyleTheme, HotbarPanelRenderer, TabListStyle, MenuStyleTheme).

#### Використання

```java
StyleTheme theme = StyleTheme.builder()
    .backgroundColor(0x000000)
    .borderColor(0x00FF00)
    .borderWidth(2)
    .buttonStyle(ButtonStyle.ROUNDED_RECTANGLE)
    .font(StyleTheme.Font.DIALOG)
    .build();

StyleModule.setTheme(theme);
```

### ChatFormatEngine

Форматування чату.

#### Використання

```java
ChatFormatEngine.format("{team_color}[{rank}] {name}: {message}");
```

### AnimationRecordFacade

Єдиний API запису анімацій.

#### Використання

```java
AnimationRecordFacade facade = AnimationRecordFacade.instance();
facade.record(session);
```

### TurnAnimationEngine

Анімації поворотів.

#### Використання

```java
TurnAnimationEngine engine = TurnAnimationEngine.instance();
engine.play(animation);
```

### PlayerPoseController

Кастомні позі.

#### Використання

```java
PlayerPoseController.applyPose(player, PoseKey.SITTING);
```

### ArmOverride

Перевизначення руки.

#### Використання

```java
ArmOverride override = ArmOverride.of(HandSlot.OFF_HAND, "LeftArm");
ItemDefinition.builder()
    .armOverride(HandSlot.OFF_HAND, override.boneName())
    .build();
```

### ItemCameraTrack

Трек камери предмета.

#### Використання

```java
ItemCameraTrack track = ItemCameraTrack.builder()
    .keyframe(0.00f, 0.0f, 0.0f)
    .keyframe(0.35f, 0.0f, 2.2f)
    .build();

ItemDefinition.builder()
    .cameraTrack(track)
    .build();
```

### InvisibleZoneEntityBase

Сутність-зона без хітбоксу.

#### Використання

```java
InvisibleZoneEntityBase zone = new InvisibleZoneEntityBase(
    new ZoneShapeDescriptor(halfX, halfY, halfZ, origin)
);
```

### AnimatedBlockEntityBase

Анімований блок.

#### Використання

```java
AnimatedBlockEntityBase block = new AnimatedBlockEntityBase(
    new RawAnimation("idle"), new RawAnimation("open"),
    new NonNullList<ItemStack>()
);
```

### HologramLeaderboardRenderer

Голограма лідерборду.

#### Використання

```java
HologramLeaderboardRenderer renderer = HologramLeaderboardRenderer.instance();
renderer.render(leaderboard, hologramDisplayManager);
```

### SpectatorSessionController

Режим спостереження.

#### Використання

```java
SpectatorSessionController.enterSpectator(player, target);
SpectatorSessionController.exitSpectator(player);
```

## Безпека та перевірка

Дозволено використовувати `net.minecraftforge.*` лише в `lib-forge`.

У `lib-common` використовується `ArchitectureSniffer` для перевірки,
що `net.minecraftforge.*` не імпортується.

### Gradle task

```bash
./gradlew :lib-common:sniffer --args="strict=true"
```

## Ліцензія

LGPL-3.0-only

## Зв'язок

- [shaurma-lib](shaurma-lib/README.md)
- [Snipers Shaurma](snipers_shaurma/README.md)
- [Forge 1.20.1](https://mcforge.readthedocs.io/en/1.20.x/)
- [SpongePowered Mixins](https://github.com/SpongePowered/Mixin)
