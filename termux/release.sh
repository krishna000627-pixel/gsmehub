#!/data/data/com.termux/files/usr/bin/bash
# Commit, push and let GitHub Actions build the signed APK.
# Usage: bash termux/release.sh "v1: message"
set -euo pipefail
cd "$(dirname "$0")/.."

MSG="${1:-}"
[ -n "$MSG" ] || { echo 'Usage: bash termux/release.sh "v1: message"'; exit 1; }
[ -d .git ] || { echo "Run bash termux/setup.sh first"; exit 1; }

VN="$(grep -m1 'versionName' app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')"
VC="$(grep -m1 'versionCode' app/build.gradle.kts | sed -E 's/[^0-9]*([0-9]+).*/\1/')"
echo "==> Gamehub $VN (versionCode $VC)"

git add -A
if git diff --cached --quiet; then
  echo "Nothing new to commit"
else
  git commit -q -m "$MSG"
fi
git branch -M main
git push -u origin main || { git pull --rebase origin main && git push -u origin main; }

SLUG="$(git remote get-url origin | sed -E 's#.*github.com[:/]##; s#\.git$##')"
echo "Build:   https://github.com/$SLUG/actions"
echo "Release: https://github.com/$SLUG/releases/latest"
