# Нова версія гри / новий лоадер

Уся логіка бібліотеки живе в `core` (чиста Java 17, без Minecraft) і **не змінюється** між версіями.
Під кожну версію гри пишеться лише тонкий шар `mc/<версія>`, під кожен лоадер — `loader/<лоадер>`.

```
core/                       ← логіка (однакова для всіх)            dev.shaurmalib.common.*, anim.*, meta.*, platform.*
mc/v1_20_1/                 ← адаптери гри 1.20.1                    dev.shaurmalib.mc.*  (Mc, McText, McTeleportBackend…)
mc/v1_21_1/  (майбутнє)     ← ТІ САМІ класи й пакети, інший код       dev.shaurmalib.mc.*
loader/forge, loader/fabric ← події, мережа, встановлення платформи  dev.shaurmalib.loader.*
dist/<лоадер>-<версія>/     ← збірка jar: core + mc/<mc_layer> + loader/<лоадер>
```

Пакети в `mc/*` **не містять версії** (`dev.shaurmalib.mc.Mc`, а не `mc.v1_20_1.Mc`), тому код лоадера й модів
пише `Mc.player(sp)` / `Mc.component(text)` однаково для будь-якої версії — збірка лише підставляє іншу теку.

## Покроково

```bash
./tools/new-version.sh fabric 1.21.1
```

1. Скрипт копіює найновіший `mc/v*` у `mc/v1_21_1` і шаблон збірки в `dist/fabric-1.21.1`
   (у `gradle.properties` уже стоять `mc_version`, `mc_layer`, `java_version`).
2. Вписати в `dist/fabric-1.21.1/gradle.properties` версії `fabric_loader`, `fabric_api`, `geckolib` (для Forge — `forge_version`).
3. `cd dist/fabric-1.21.1 && ./gradlew build` і правити помилки **лише в `mc/v1_21_1`**.
4. Додати збірку в `DISTS` у `tools/publish.sh`, підняти `version.properties`, `./tools/publish.sh`.

## Що зазвичай ламається між версіями (і де це правиться)

| Тема | Клас у `mc/<версія>` |
|---|---|
| телепорт (`teleportTo` / `TeleportTransition` у 26.x) | `McTeleportBackend` |
| предмети: NBT (1.20) → DataComponents (1.21+) | `McItemRef`, `McData` |
| текст / JSON компонентів | `McText` |
| id ресурсу (`ResourceLocation` → `Identifier`) | `McLevelRef` |
| урон (`DamageSource`, теги) | `McDamageRef` |
| HUD / екрани (`GuiGraphics`, render pipeline) | `anim/mc`, `meta/mc` |
| міксини (імена методів) | `mc/mixin` |

Контракт кожного адаптера — інтерфейс у `core/.../platform` (`TeleportBackend`, `TextBridge`, `LootDropper`, `RefFactory`, `PlayerOps`…).
Лоадер реєструє реалізації при старті: Forge — `loader/forge/.../ForgePlatform.install()`.

## Що ще прив'язане до Forge 1.20.1

`legacy/lib-forge` — модулі, ще не перенесені в `core` + `mc/*` (оверлеї, камера, графіті, радіо, офлайн-аватари тощо).
Їхня логіка вже в `core`, але Forge-частина (події, рендер, пакети) — лише під Forge 1.20.1. Перенесення модуля:
рецепт у `ARCHITECTURE.md` §8 (еталон — stamina). Після перенесення модуль автоматично є в усіх `dist/*`.
