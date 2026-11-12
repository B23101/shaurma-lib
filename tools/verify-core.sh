#!/usr/bin/env bash
# Перевірка ядра без Gradle: javac --release 17 + sniffer + smoke-тести.
# Потрібно: JDK 17+ і snakeyaml.jar (SNK=/шлях/до/snakeyaml.jar).
set -euo pipefail
cd "$(dirname "$0")/../core"
SNK="${SNK:?вкажіть SNK=/шлях/до/snakeyaml.jar}"
OUT="$(mktemp -d)"
if grep -rnE '^\s*import\s+(static\s+)?(net\.minecraft|com\.mojang|net\.minecraftforge|net\.neoforged|net\.fabricmc|org\.spongepowered|cpw\.mods|software\.bernie|dev\.kosmx)\.' src/main/java; then
  echo "SNIFFER FAIL"; exit 1; fi
echo "sniffer: OK"
javac --release 17 -encoding UTF-8 -cp "$SNK" -d "$OUT" $(find src/main/java -name '*.java')
javac --release 17 -encoding UTF-8 -cp "$OUT:$SNK" -d "$OUT" $(find src/test/java -name '*.java')
java -cp "$OUT:$SNK" dev.shaurmalib.CoreSmokeTest
