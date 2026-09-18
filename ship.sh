#!/bin/bash
# ship.sh <Dir> <AppName> <version> <notes-file>   — build signed APK+AAB, commit, push, GitHub release
set -e
DIR=/root/claude/$1; APP="$2"; VER="$3"; NOTES="$4"
cd "$DIR"
sed -i 's/-Xmx3g/-Xmx2g/' gradle.properties
./gradlew --stop >/dev/null 2>&1 || true
./gradlew :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest -q 2>&1 | grep -E "^e:|FAILED|error:" && { echo "BUILD FAILED"; exit 1; }
mkdir -p dist; rm -f dist/*
cp app/build/outputs/apk/release/app-release.apk "dist/$1-v$VER.apk"
cp app/build/outputs/bundle/release/app-release.aab "dist/$1-v$VER.aab"
grep -q "^dist/" .gitignore || echo "dist/" >> .gitignore
export GIT_COMMITTER_NAME=Mohithash GIT_COMMITTER_EMAIL=17986082+Mohithash@users.noreply.github.com
[ -d .git ] || git init -q -b main
git add -A
git commit -q --author="Mohithash <17986082+Mohithash@users.noreply.github.com>" -m "$APP v$VER" || true
if ! git remote get-url origin >/dev/null 2>&1; then
  gh repo create "Mohithash/$1" --private --source=. --remote=origin --push >/dev/null
else
  git push -q origin main
fi
git tag -a "v$VER" -m "$APP v$VER" && git push -q origin "v$VER"
gh release create "v$VER" "dist/$1-v$VER.apk" "dist/$1-v$VER.aab" --title "$APP v$VER" --notes-file "$NOTES" >/dev/null
./gradlew --stop >/dev/null 2>&1 || true
echo "SHIPPED https://github.com/Mohithash/$1/releases/tag/v$VER  apk=$(du -h dist/$1-v$VER.apk | cut -f1) aab=$(du -h dist/$1-v$VER.aab | cut -f1)"
