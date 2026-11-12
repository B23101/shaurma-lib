# Темна зона (darkzone)

Реалізація за принципом еталонного `darkness-1.19` (mixin на lightmap-текстуру,
**не** `WorldTintOverlay`) — темніє реальне світло сцени, а не малюється
чорний прямокутник поверх кадру.

## Як це працює

1. **Геометрія** — `DarkZoneShape` (у `common`): X/Z-полігон, `Y` завжди
   ігнорується (зона діє по всій висоті стовпця). Правило точок:
   - 1 точка — зона ще не замкнена;
   - 2 точки — трактуються як протилежні кути прямокутника (діагональ);
   - 3+ точок — довільний багатокутник у порядку додавання.
   Перевірка належності — стандартний ray-casting (PNPOLY).

2. **Плавна межа** — `DarkZoneEdgeSmoothing`: сила затемнення наростає
   лінійно від 0 на межі до повної на відстані 3 блоків углиб зони,
   щоб не було стрибка яскравості при ході вздовж кордону.

3. **Сервер** — `DarkZoneManager` тримає всі зони, `effectiveStrengthAt(x, z)`
   рахує максимальну ефективну силу серед усіх зон, у яких перебуває точка
   (зони можуть перекриватись — виграє найтемніша).

4. **Мережа** — `DarkZoneStrengthPacket` (сервер → клієнт), дедуплікація
   "надсилати лише при зміні" — у `DarkZoneServerTicker`.

5. **Клієнт** — `DarkZoneLightmap.darken(pixel, strength)` мутує пікселі
   16×16 lightmap-текстури **тим самим способом**, що `Darkness.darken`
   у `darkness-1.19`: пропорційне зменшення яскравості R/G/B, не альфи.
   Підключається двома міксинами:
   - `MixinLightTextureDarkZone` — тегує потрібний `DynamicTexture`
     одразу після конструктора `LightTexture` (роль `MixinLightTexture`
     оригіналу);
   - `MixinDynamicTextureDarkZone` — перед кожним `upload()` темнить усі
     256 пікселів лайтмапи (роль `MixinDynamicTexture` оригіналу).

## Підключення в консюмер-моді

### 1. Команди (`RegisterCommandsEvent`)

```java
@SubscribeEvent
static void onRegisterCommands(RegisterCommandsEvent event) {
    DarkZoneManager darkZoneManager = lib.darkZoneModule().manager();
    event.getDispatcher().register(
        DarkZoneCommand.build("darkzone", darkZoneManager)
    );
}
```

Команди:
- `/darkzone create <id>` — нова порожня зона.
- `/darkzone point add <id>` — додає точку в позиції виконавця (X/Z, Y ігнорується).
- `/darkzone point add <id> <x> <z>` — додає точку за явними координатами.
- `/darkzone point remove <id> <index>` — видаляє точку за індексом.
- `/darkzone strength <id> <0..1>` — сила затемнення зони.
- `/darkzone remove <id>` — видаляє зону повністю.
- `/darkzone list` — список усіх зон і їх стан.

### 2. Серверний тік

```java
@SubscribeEvent
static void onServerTick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    for (ServerLevel level : server.getAllLevels()) {
        darkZoneTicker.tick(level, (player, pkt) ->
            NetworkHandler.CHANNEL.sendToPlayer(player, pkt));
    }
}

@SubscribeEvent
static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    darkZoneTicker.clearPlayer(event.getEntity().getUUID());
}
```

### 3. Мережа

Зареєструвати `DarkZoneStrengthPacket` у консюмерському `NetworkHandler`
так само, як інші sync-пакети бібліотеки (`StaminaSyncPacket` і т.п.):
`encode`/`decode`/`handle` вже готові, лишається лише `registerMessage(...)`
з наступним вільним id.

### 4. Мікси

`shaurma_lib.mixins.json` уже оновлено — `MixinLightTextureDarkZone` і
`MixinDynamicTextureDarkZone` додані в секцію `client`. Нічого додатково
реєструвати не треба.

## Що НЕ зроблено навмисно

- Жодного UI для розмітки зон у грі (лише команди, як просив консюмер).
- Персистентність зон (збереження між рестартами сервера) — консюмер сам
  вирішує, чи серіалізувати `DarkZoneManager` (наприклад у world data),
  бібліотека тримає лише in-memory реєстр на час роботи сервера.
