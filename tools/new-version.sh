#!/usr/bin/env bash
# Заготовка під нову пару «лоадер × версія гри».
#
#   ./tools/new-version.sh fabric 1.21.1
#   ./tools/new-version.sh forge  1.21.1
#
# Робить:
#   1. mc/v<версія>      — копія найближчого наявного шару версії (адаптери Mc*, anim/mc, meta/mc, міксини).
#                          Пакети НЕ перейменовуються: dev.shaurmalib.mc.* однаковий у всіх версіях,
#                          збірка просто бере іншу теку. Далі правиш лише те, що не компілюється.
#   2. dist/<лоадер>-<версія> — копія наявної збірки цього лоадера; у gradle.properties
#                          підставлені mc_version / mc_layer / java_version.
# Потім: вписати версії лоадера/API/GeckoLib у dist/<…>/gradle.properties і зібрати (див. docs/NEW_VERSION.md).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOADER="${1:?лоадер: fabric | forge | neoforge}"
MC="${2:?версія гри, напр. 1.21.1}"
LAYER="v${MC//./_}"

case "$MC" in
  1.20*) JAVA=17 ;;
  1.21*) JAVA=21 ;;
  *)     JAVA=25 ;;   # 26.x і новіші
esac

cd "$ROOT"
# ── 1. шар версії ──────────────────────────────────────────────────────────
if [ -d "mc/$LAYER" ]; then
  echo "mc/$LAYER уже є — не чіпаю"
else
  SRC="$(ls -d mc/v* | sort -V | tail -1)"
  cp -r "$SRC" "mc/$LAYER"
  rm -rf "mc/$LAYER/build" "mc/$LAYER/out"
  echo "mc/$LAYER ← $SRC (правити лише те, що не компілюється під $MC)"
fi

# ── 2. збірка ──────────────────────────────────────────────────────────────
DIST="dist/$LOADER-$MC"
if [ -d "$DIST" ]; then
  echo "$DIST уже є — не чіпаю"; exit 0
fi
case "$LOADER" in
  fabric)   TEMPLATE="dist/fabric-1.20.1" ;;
  forge)    TEMPLATE="dist/forge-1.20.1" ;;
  neoforge) echo "Шаблону NeoForge ще немає: створи dist/neoforge-$MC за зразком dist/fabric-1.20.1 (плагін net.neoforged.moddev)."; exit 1 ;;
  *) echo "невідомий лоадер: $LOADER"; exit 1 ;;
esac
mkdir -p "$DIST"
(cd "$TEMPLATE" && tar --exclude=build --exclude=.gradle --exclude=run -cf - .) | (cd "$DIST" && tar -xf -)
P="$DIST/gradle.properties"
sed -i -e "s/^mc_version=.*/mc_version=$MC/" -e "s/^mc_layer=.*/mc_layer=$LAYER/" -e "s/^java_version=.*/java_version=$JAVA/" "$P"
sed -i "s/^rootProject.name = .*/rootProject.name = 'shaurma-lib-$LOADER-$MC'/" "$DIST/settings.gradle"
[ -f "$DIST/gradle/gradle-daemon-jvm.properties" ] && sed -i "s/^toolchainVersion=.*/toolchainVersion=$JAVA/" "$DIST/gradle/gradle-daemon-jvm.properties"

cat <<MSG

Готово: mc/$LAYER і $DIST (Java $JAVA).
Далі:
  1. $P — версії лоадера / API / GeckoLib для $MC (сайти: fabricmc.net/develop, files.minecraftforge.net, geckolib).
  2. (cd $DIST && ./gradlew build) — і правити помилки ЛИШЕ в mc/$LAYER (ядро core не чіпати).
  3. Додати "$LOADER-$MC" у список DISTS в tools/publish.sh.
MSG
[ "$LOADER" = forge ] && echo "  ! Forge-збірка тягне legacy/lib-forge (модулі, ще не перенесені в core) — вони написані під Forge 1.20.1."
exit 0
