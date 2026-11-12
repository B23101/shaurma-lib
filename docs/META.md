# Мета-двигун ShaurmaLib (`dev.shaurmalib.meta`)

Спільний для всіх режимів рушій: профіль, статистика, рівні й ранги, економіка, косметика, крафт, магазин,
скрині, сезонний пропуск, покращення, лідерборди, екран підсумків, компас і 3D-плюсики хілу.
**Бібліотека — рушій, режим — дані**: у кожного режиму своя валюта, косметика, статистика, ранги й стиль меню.

## Шари

| Шар | Що | Де |
|---|---|---|
| `core` (чиста Java) | `MetaMode` (опис режиму), `MetaServer` (усі зміни профілю), `MetaClient` (стан клієнта), `MetaProfile` + `ProfileStore` (JSON, атомарний запис), `MatchReport` → `MatchResult`, `ShopSection` (ротація зі знижками), `ChestDef` (гарант), `RewardTrack` (сезон, щоденний подарунок), `WaypointCompass`, `PlusBurst`, `MetaMessage` (дріт) | `core/src/main/java/dev/shaurmalib/meta` |
| `mc/v1_20_1` | екрани (`WardrobeScreen`, `ProfileScreen`, `ResultScreen`, `ShopScreen`, `TrackScreen`, `LeaderboardScreen`), `CosmeticRenderer`/`AuraRenderer`, `HealPlusRenderer`, `CompassHud`, `MetaUi` (стиль меню мафії), `/meta` | `mc/v1_20_1/.../meta/mc` |
| `loader/forge`, `loader/fabric` | канал `shaurma_lib:meta`, події, шар косметики, відтінок предметів | `loader/*/.../meta` |

Тести: `MetaSmokeTest` (39 перевірок, запускається з `CoreSmokeTest`).

## Збірки
* **Forge 1.20.1** (маніяк): `../shaurma-lib` синхронізує `dev/shaurmalib/meta/**` разом з анімаціями → `publishToMavenLocal`.
* **Fabric 1.20.1, лише мета** (мафія): `dist/fabric-1.20.1-meta` → `./gradlew publishToMavenLocal`. Без рушія анімацій, міксинів і клавіш — у мод не потрапляє нічого, крім мета-технології. Мафія вшиває його (`include`).
* **Fabric 1.20.1, повна** (`dist/fabric-1.20.1`): анімації + мета. Loom оновлено до 1.17.19 / Gradle 9.5.1 (1.11-SNAPSHOT офлайн не резолвився).

## Підключення режиму
```java
MetaModes.register(MetaMode.builder("maniac", "Маніяк").theme(MetaTheme.halloween())
    .currency(new Currency("darkcoin", "DarkCoins", 0xE5383B, "blood"))
    .shards(new Currency("shard", "Уламки", 0x6FB8FF, "gem"))
    .ladder(...).stat(...).cosmetics(catalog).chest(...).section(...).offer(...).track(track).rule(...).build());
MetaServer.whenStarted(s -> s.onProfileLoad(p -> /* міграції */));
// кінець матчу:
MetaServer.get().finishMatch(List.of(MatchReport.of(uuid, name).track("survivor").won(true).action("win", 1)...));
```
Решту (вхід/вихід, збереження, мережа, екрани) бібліотека робить сама.

## Компас і хіл
* `MetaServer.setWaypoints(uuid, List<Waypoint>)` — плашка вгорі («Галявина · 211 м · вище»), стрілка крутиться з камерою, решта цілей — маркерами на шкалі.
* `MetaServer.healFx(uuid, x, y, z, count, color, seconds)` — 3D-плюсики, що злітають і крутяться; бачать усі в радіусі 48.

## Адмін
`/meta give <гравець> coins|cosmetic|material|points|xp ...`, `/meta open <екран>`, `/meta reset <гравець>`.
