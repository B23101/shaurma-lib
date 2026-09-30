# camera/replay — Replay-камера FreeCamera

Дані (без Minecraft, тестуються окремо) — `lib-common/.../camera/replay`:
`ReplayPoint` → `ReplayClip` (скільки завгодно точок, спільний інтервал АБО свій на відрізок,
`cutAtMs` — примусове завершення) → `ReplayScript` (скільки завгодно клипів + загальна стеля
`totalCutMs`: у цей момент усе обривається, навіть посеред руху; якщо клипи скінчились раніше —
камера тримає останній кадр).

Сервер: `ReplayDirector.play(player, script, delayMs)` / `.stop(player, restore, notifyClient)`.
Клієнт: `ReplayPlayer` (сам стартує з пакета; `dipAlpha()`, `isAreaLoaded()`, `Listener`).

## Виправлення «камера далеко від гравця — сцени нема»

Сервер шле клієнту чанки лише навколо ГРАВЦЯ, а `FreeCameraEntity` існує тільки на клієнті.
`FreeCameraChunkService` на час Replay «прив'язує» стеження чанків до камери: тіло гравця стоїть
над світом над чанком камери (невразливе, без гравітації), а після Replay повертається на місце.
`ReplayDirector` робить це сам і веде якір на 1.2 с ПОПЕРЕДУ камери. Для решти контролерів
(`Stationary`, `CinematicPath`, `FreeFly`) клієнт шле `FreeCameraChunkRequestPacket`, але сервер
виконує його лише після `FreeCameraChunkService.authorize(player)` — інакше це був би клієнтський телепорт.

PROTOCOL_VERSION каналу: 2.
