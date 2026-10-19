#!/usr/bin/env bash
# Headless self-tests: MIDP graphics/sprites/key states (SelfTest), features such as networking, files, tone
# sequences, Nokia API, scanner, JAD (FeatureTest) and the LCDUI screens (UiShots, writes /tmp/ui_*.png).
# Needs a full JDK (javac + jdk.compiler).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/build.sh" >/dev/null || { echo "build failed"; exit 1; }
OUT="$(mktemp -d)"; HOME_TMP="$(mktemp -d)"; trap 'rm -rf "$OUT" "$HOME_TMP" "${RESULTS:-}"' EXIT
CP="$ROOT/build/classes"
# GlTest is built and run separately (tools/gltest.sh): it links against the preview-feature GL classes
javac -nowarn -cp "$CP" -d "$OUT" $(ls "$ROOT"/src/test/java/*.java | grep -v '/GlTest\.java$') || { echo "tests failed to compile"; exit 1; }
RESULTS="$(mktemp)"
for t in SelfTest FeatureTest UiShots PadTest CompatTest RewriterTest MascotTest M3GTest M3GSpriteSkinTest GlobalConfigTest MidiLevelerTest; do
  echo "== $t"
  java -Djava.awt.headless=true -Duser.home="$HOME_TMP" -cp "$CP:$OUT" "$t" 2>&1 | grep -v '^\[j2me\]\|prefs\|^INFO' | tee -a "$RESULTS"
done

# OpenGL ES output (needs xvfb-run or a display, Mesa EGL/GLES2 and a JDK 21+); skipped when those are missing.
if command -v xvfb-run >/dev/null 2>&1 || [[ -n "${DISPLAY:-}" ]]; then
  if [[ "$(javac -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')" -ge 21 ]] && ldconfig -p 2>/dev/null | grep -q 'libEGL.so.1'; then
    echo "== GlTest"; "$ROOT/tools/gltest.sh" 2>&1 | tail -25 | tee -a "$RESULTS"
  else echo "== GlTest skipped (needs JDK 21+ and libEGL)"; fi
else echo "== GlTest skipped (no xvfb-run / display)"; fi

# Fail the script (and CI) when any check failed or a test crashed. (The pipelines above only report the exit status of grep/tee.)
if grep -qE '^FAIL|^[0-9]+ FAILED|Exception in thread|^Exception|^Error:' "$RESULTS"; then
  echo; echo "SELFTEST FAILED"; exit 1
fi
echo; echo "SELFTEST OK"
