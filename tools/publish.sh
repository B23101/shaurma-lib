#!/usr/bin/env bash
# Збирає всі dist/* і публікує їх у Maven-репозиторій гілки `maven` на GitHub.
# Моди підключають:  maven { url = 'https://raw.githubusercontent.com/B23101/shaurma-lib/maven' }
#   ./tools/publish.sh            — зібрати + опублікувати в гілку maven (git push)
#   ./tools/publish.sh --local    — лише зібрати в build/maven-repo і ~/.m2 (без push)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$ROOT/build/maven-repo"
VER="$(grep '^libVersion=' "$ROOT/version.properties" | cut -d= -f2 | tr -d '\r')"
echo "ShaurmaLib $VER"

# Усі збірки, що публікуються (нова версія: tools/new-version.sh, потім додати сюди)
DISTS="forge-1.20.1 fabric-1.20.1-meta fabric-1.20.1"

for d in $DISTS; do
  echo "== dist/$d"
  (cd "$ROOT/dist/$d" && ./gradlew publishAllPublicationsToShaurmaRepoRepository publishToMavenLocal --console=plain -q)
done

[ "${1:-}" = "--local" ] && { echo "OK: $REPO"; exit 0; }

WT="$ROOT/build/maven-branch"
cd "$ROOT"
git fetch -q origin maven 2>/dev/null || true
rm -rf "$WT"; git worktree prune
if git show-ref -q refs/remotes/origin/maven; then
  git worktree add -q -B maven "$WT" origin/maven
else
  git worktree add -q --orphan -b maven "$WT"
fi
cp -r "$REPO/." "$WT/"
cat > "$WT/README.md" <<MD
# ShaurmaLib Maven

\`\`\`groovy
repositories { maven { url = 'https://raw.githubusercontent.com/B23101/shaurma-lib/maven' } }
\`\`\`
Артефакти \`dev.shaurmalib\`: \`shaurma-lib-forge-1.20.1\`, \`shaurma-lib-meta-fabric-1.20.1\`, \`shaurma-lib-anim-fabric-1.20.1\`.
MD
cd "$WT"
git add -A
git commit -q -m "ShaurmaLib $VER" || echo "(без змін)"
git push -q origin maven
cd "$ROOT"; git worktree remove --force "$WT"
echo "Опубліковано $VER у гілку maven"
