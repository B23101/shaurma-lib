# ShaurmaLib

Спільна бібліотека модів Shaurma: модулі режимів (конфіги, телепорт, оверлеї, чат, радіо, камера, графіті…),
власний рушій анімацій гравця (`dev.shaurmalib.anim`) і мета-гра (`dev.shaurmalib.meta`: профіль, рівні, валюта,
косметика, магазин, пропуск, лідерборди, екран підсумків).

Одна кодова база → окремий jar на кожну пару «лоадер × версія гри». Мод **вшиває** потрібний jar у себе
(Forge — `jarJar`, Fabric — `include`), гравцям нічого окремо ставити не треба.

## Підключення в мод

```groovy
repositories {
    maven { url = 'https://raw.githubusercontent.com/B23101/shaurma-lib/maven'; content { includeGroup 'dev.shaurmalib' } }
}
```

| Артефакт `dev.shaurmalib:` | Лоадер / гра | Що всередині | Хто використовує |
|---|---|---|---|
| `shaurma-lib-forge-1.20.1:0.5.0` | Forge 1.20.1 | усе (усі модулі + анімації + мета) | maniac_shaurma, snipers_shaurma |
| `shaurma-lib-meta-fabric-1.20.1:0.5.0` | Fabric 1.20.1 | лише мета-гра | mafia |
| `shaurma-lib-anim-fabric-1.20.1:0.5.0` | Fabric 1.20.1 | анімації + мета | — |

Forge:
```groovy
jarJar(implementation("dev.shaurmalib:shaurma-lib-forge-1.20.1:${shaurma_lib_version}")) { jarJar.ranged(it, '[0.5.0,0.6.0)') }
```
Fabric:
```groovy
modImplementation("dev.shaurmalib:shaurma-lib-meta-fabric-1.20.1:${shaurma_lib_version}") { transitive = false }
include("dev.shaurmalib:shaurma-lib-meta-fabric-1.20.1:${shaurma_lib_version}") { transitive = false }
```

## Структура

| Тека | Що |
|---|---|
| `core/` | уся логіка — чиста Java 17 без Minecraft: модулі `common`, SPI `platform`, `render`, `anim`, `meta` |
| `mc/v1_20_1/` | шар версії гри 1.20.1: адаптери `dev.shaurmalib.mc.*` (`Mc`, `McText`, `McTeleportBackend`…), міксини, екрани |
| `loader/forge`, `loader/fabric` | клей лоадерів: події, мережа, `ForgePlatform.install()` |
| `legacy/lib-forge` | Forge-частина модулів колишнього `shaurma-lib` (логіка вже в `core`, події/рендер — ще під Forge 1.20.1) |
| `dist/<лоадер>-<версія>` | збірки jar; усе, що відрізняє версію, — у їхньому `gradle.properties` |
| `docs/` | `ARCHITECTURE.md` (план мульти-версії), `ANIMATION.md`, `META.md`, `forge-1.20.1/` (документація модулів) |

## Збірка і реліз

Версія — лише в `version.properties`.

```bash
./tools/publish.sh --local   # зібрати всі dist/* у build/maven-repo і ~/.m2 (перевірка модів: -PuseLocalLib=true)
./tools/publish.sh           # те саме + опублікувати в гілку maven на GitHub
```

Потрібні JDK 17 (Forge-збірка, Gradle 8.14) і JDK 21+ (Fabric-збірки, Gradle 9.5).

## Нова версія гри

```bash
./tools/new-version.sh fabric 1.21.1   # заготовка mc/v1_21_1 + dist/fabric-1.21.1
```
Покроково — `docs/NEW_VERSION.md`.

Smoke-тест Forge (сервер вантажить бібліотеку й виходить): `cd dist/forge-1.20.1 && ./gradlew :lib-forge:runGameTestServer`.

## Стан мульти-версії

Готово: Forge 1.20.1 (повна) і Fabric 1.20.1 (анімації + мета). Уся логіка вже в `core` і спільна для версій;
Forge-збірка компілює `core` замість старого `lib-common`. Далі: перенести Forge-частину модулів із `legacy/lib-forge`
у `loader/*` + `mc/*` (рецепт — `docs/ARCHITECTURE.md` §8), потім `mc/v1_21_1` і `mc/v26_3`.
