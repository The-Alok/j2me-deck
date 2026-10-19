#!/usr/bin/env bash
# OpenGL ES output checks against a real EGL surface (pixel-exact output per scaling mode, LCD grid, overlays, resize, window re-creation).
# Needs: a JDK 21+, xvfb-run (or a real display), and Mesa's EGL + GLES2 libraries (software rendering through llvmpipe is fine).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/build.sh" >/dev/null
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
CP="$ROOT/build/classes"
FEATURE="$(javac -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')"
FLAGS=(--release "$FEATURE")
FLAGS_RUN=()
if (( FEATURE == 21 )); then FLAGS+=(--enable-preview); FLAGS_RUN+=(--enable-preview); fi
FLAGS_RUN+=(--enable-native-access=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED)
javac "${FLAGS[@]}" -nowarn -cp "$CP" -d "$OUT" "$ROOT/src/test/java/GlTest.java"
RUN=(java "${FLAGS_RUN[@]}" -cp "$CP:$OUT" GlTest)
if [[ -n "${DISPLAY:-}" ]]; then "${RUN[@]}" 2>&1 | grep -v 'libEGL warning'
else xvfb-run -a -s "-screen 0 1280x800x24" "${RUN[@]}" 2>&1 | grep -v 'libEGL warning'; fi
