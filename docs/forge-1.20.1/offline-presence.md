# Офлайн-присутність (`withOfflinePresence`)

Коли гравець виходить із сервера під час матчу, у світі лишається його фізичне тіло
(скін, броня, предмет, поза; правила урону як у гравця), а при вході гравець
повертається в це тіло. Бібліотека не знає про ваш режим: усе доменне приходить
через `OfflinePresenceConfig`.

## Принципи

- **Правда в реєстрі**, а не в сутності. `OfflineRegistry` (lib-common, без Minecraft-коду) — автомат станів
  `STANDING → KILLED | EXPIRED → RETURNED` і `CLOSED`. Сутність без запису сама себе знищує.
- **Scope** визначає, коли тіла взагалі можуть існувати. Поки scope закрито, тіла не створюються.
  Закриття (кінець матчу, деактивація режиму, зупинка сервера) знімає всі тіла **безшумно**:
  без дропу, без спавну, без подій «вбито».
- **Один спосіб отримати актора:** `OfflineActors.resolve(server, uuid)` повертає `LivingEntity`
  (живого гравця або тіло). Режим працює з UUID і не має «гілок для офлайну».
- Лут випадає **один раз** (`markKilled` атомарний). Знімок інвентаря живе в пам'яті модуля, не в NBT сутності.

## Підключення (консюмер)

```java
// 1. Свій тип тіла (підклас може бути порожнім).
public class ManiacAvatar extends OfflineAvatarBase {
    public ManiacAvatar(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }
}

// 2. Реєстрація типу: MobCategory.MISC (НЕ MONSTER), sized(0.6F, 1.8F).
public static final RegistryObject<EntityType<ManiacAvatar>> OFFLINE_AVATAR = ENTITY_TYPES.register("offline_avatar",
    () -> EntityType.Builder.<ManiacAvatar>of(ManiacAvatar::new, MobCategory.MISC)
            .sized(0.6F, 1.8F).clientTrackingRange(10).build("offline_avatar"));

// 3. Атрибути (mod-bus) і рендерер (клієнт, mod-bus).
event.put(OFFLINE_AVATAR.get(), OfflineAvatarBase.createAttributes().build());      // EntityAttributeCreationEvent
event.registerEntityRenderer(OFFLINE_AVATAR.get(), OfflineAvatarRendererBase::new); // RegisterRenderers

// 4. У ShaurmaLib.init(...): обов'язково поруч із withTeleport().
.withTeleport()
.withOfflinePresence(OfflinePresenceConfig.builder()
    .entityType(OFFLINE_AVATAR::get)
    .spawnWhen(player -> /* OfflineSpec.avatar() або OfflineSpec.none() */)
    .damagePolicy(OfflineDamagePolicy.mirrorPlayerRules((record, source, amount) -> /* true, якщо власник «мертвий» */))
    .lootPolicy(/* за замовчуванням ванільні ItemEntity */)
    .onReturn((player, ret) -> { /* відновити заморозку, пози, синхронізацію */ })
    .bindScopeToLifecycle(lifecycleBus, Set.of(MatchLifecycleState.ACTIVE),
            Set.of(MatchLifecycleState.ENDING, MatchLifecycleState.IDLE))
    .nameTagHideTeamId("my_nametag_hide")
    .build())
```

## Що робити в обробниках режиму

| Місце | Дія |
|---|---|
| Вихід гравця | `if (!lib.offlinePresenceModule().hasAvatar(uuid)) { стара логіка виходу з матчу }` |
| Вхід гравця (NORMAL) | `lib.offlinePresenceModule().takeReturn(uuid)` — присутнє, якщо це повернення. `finalStatus` = `STANDING` / `KILLED` / `EXPIRED` |
| Пошук цілі удару, пастки, дроти | `OfflineActors.candidates(server, filter)` / `resolve(server, uuid)` замість `getPlayers()` |
| Цикли для UI, чату, звуку | `OfflineActors.onlinePlayers(server)` (тіла їм нічого не дають) |
| Умова перемоги «усі живі офлайн» | `standingCount(isSurvivor)` порівняти з кількістю живих |
| Правила урону, що мають діяти й на тіло | `DamageInterceptorRegistry.registerOwnerRule(id, (uuid, source, amount, ctx) -> ...)` |

`DamageInterceptor` (за `ServerPlayer`) діють лише на онлайн-гравців; правила за UUID — на обох.

## Життєвий цикл

- **Вихід:** перевірки (сервер зупиняється, singleplayer, creative/spectator, scope закрито) → `spawnWhen` →
  знімок → запис у реєстрі → тіло у світі → чанк тримається ticking-тікетом.
- **Удар по тілу:** `OfflineAvatarBase#hurt` повністю віддає удар модулю → `OfflineDamagePolicy`.
  `BYPASSES_INVULNERABILITY` (порожнеча, `/kill`) вбиває тіло напряму.
- **Смерть:** лут за `OfflineLootPolicy`, анімація смерті, тікет знімається. Запис лишається `KILLED`,
  щоб при вході режим знав, що власник «помер», поки був офлайн.
- **Вхід:** запис забирається, тіло знімається без дропу, гравець телепортується на місце тіла
  (`TeleportReason.OFFLINE_RETURN`). Для `KILLED`/`EXPIRED` інвентар гравця очищається ДО колбеків режиму (захист від дюпа).
- **Закриття scope / рестарт:** тіла зникають безшумно; осиротілі (старий scope, немає запису) не входять у світ
  (`EntityJoinLevelEvent`), чанк-тікети після рестарту знімає валідаційний колбек.
- `maxAbsenceTicks` (`-1` = без ліміту): по завершенні тіло переходить у `EXPIRED`, лут випадає, тіло зникає.

## Налагодження

`/<root> offline list | spawn <player> | kill <name> | discard <name> | purge | scope open|close`.
`spawn` створює тіло з поточного гравця, не виходячи; воно нічого не дропає.

## Перевірка

- `lib-common`: `OfflineRegistryTest` (переходи станів, «лут один раз», закриття scope).
- `lib-forge` не має автотестів; чек-лист перевірки в грі — у плані, розділ 10.
