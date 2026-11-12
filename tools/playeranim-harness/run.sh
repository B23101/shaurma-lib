#!/usr/bin/env bash
# Харнес рушія анімацій shaurma-lib: справжній PlayerAnimator coreLib + справжній рантайм, без Minecraft.
#
# Використання:   tools/playeranim-harness/run.sh /шлях/до/minecraftPlayerAnimator-port-1.20
# Потрібно: JDK 17+ (javac), python3. Maven Central НЕ потрібен.
#
# Що перевіряється — і що НІ (чесно):
#   ПЕРЕВІРЯЄТЬСЯ:  PoseLifecycle, PoseSource, PoseEase↔Ease, PoseLayerRuntime, SafeAdjustmentModifier,
#                   PlayerPoseController (проти заглушок MC зі справжніми сигнатурами).
#   НЕ ПЕРЕВІРЯЄТЬСЯ: PlayerPoseEvents (Forge-події), реальні міксини PAL, рендер, JSON-завантаження
#                   (gson-частина coreLib виключена), збірка Gradle/ForgeGradle.
set -euo pipefail
PAL="${1:?вкажіть шлях до minecraftPlayerAnimator-port-1.20}"
HERE="$(cd "$(dirname "$0")" && pwd)"; REPO="$(cd "$HERE/../.." && pwd)"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
mkdir -p "$W/pal" "$W/palout" "$W/common" "$W/forge" "$W/h"

cp -r "$PAL/coreLib/src/main/java/." "$W/pal/"
rm -rf "$W/pal/dev/kosmx/playerAnim/core/data/gson"                        # gson недоступний; JSON-завантаження не тестуємо
rm -f  "$W/pal/dev/kosmx/playerAnim/api/layered/modifier/"{SpeedModifier,MirrorModifier}.java   # рушій їх не використовує
python3 "$HERE/scripts/delombok.py" "$W/pal"
javac -nowarn -d "$W/palout" $(find "$W/pal" "$HERE/stubs/org/jetbrains" "$HERE/stubs/javax" -name '*.java') 2>&1 | grep -v '^Note' || true

P="$REPO/lib-common/src/main/java/dev/shaurmalib/common/playeranim"
javac --release 17 -nowarn -d "$W/common" "$P"/*.java

F="$REPO/lib-forge/src/main/java/dev/shaurmalib/forge/playeranim"
javac --release 17 -nowarn -cp "$W/palout:$W/common" -d "$W/forge" \
  $(find "$HERE/stubs/net" "$HERE/stubs/dev" "$HERE/stubs/org/apache" -name '*.java') \
  "$F/PoseEaseMapper.java" "$F/SafeAdjustmentModifier.java" "$F/PoseLayerRuntime.java" "$F/PlayerPoseController.java"

javac --release 17 -nowarn -cp "$W/palout:$W/common:$W/forge" -d "$W/h" $(find "$HERE/src" -name '*.java')
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$W/palout:$W/common:$W/forge:$W/h" Main
