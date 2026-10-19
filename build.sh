#!/usr/bin/env bash
# Builds build/J2ME-Deck.jar (needs a JDK 17+; the release archives already contain a prebuilt jar).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
if ! command -v javac >/dev/null 2>&1 || ! command -v jar >/dev/null 2>&1; then
  echo "A JDK 17 or newer is required to build. The release archives contain a prebuilt jar." >&2
  exit 127
fi
rm -rf "$BUILD"
mkdir -p "$BUILD/classes"
# --release needs the JDK's ct.sym; fall back to -source/-target on stripped-down JDKs.
RELEASE_FLAGS=(--release 17)
if ! javac --release 17 -version >/dev/null 2>&1; then RELEASE_FLAGS=(-source 17 -target 17); fi
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac "${RELEASE_FLAGS[@]}" -nowarn -d "$BUILD/classes"
# OpenGL ES output (src/gl): uses the foreign-function API, a preview feature on Java 21 and final from 22. Built into the same jar; a JVM that
# cannot load these classes simply keeps the Swing renderer. Skipped on JDKs older than 21.
JDK_FEATURE="$(javac -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')"
if [[ "$JDK_FEATURE" =~ ^[0-9]+$ ]] && (( JDK_FEATURE >= 21 )); then
  GL_FLAGS=(--release "$JDK_FEATURE")
  (( JDK_FEATURE == 21 )) && GL_FLAGS+=(--enable-preview)
  if find "$ROOT/src/gl/java" -name '*.java' -print0 | xargs -0 javac "${GL_FLAGS[@]}" -nowarn -cp "$BUILD/classes" -d "$BUILD/classes" 2>"$BUILD/gl-build.log"; then
    echo "OpenGL ES output built for Java $JDK_FEATURE"
  else
    echo "WARNING: OpenGL ES output not built (see $BUILD/gl-build.log); the Swing renderer still works." >&2
    rm -rf "$BUILD/classes/j2medeck/gl"
  fi
else
  echo "Note: JDK ${JDK_FEATURE:-?} is older than 21, building without the OpenGL ES output." >&2
fi
VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION" 2>/dev/null || echo dev)"
printf 'Implementation-Title: J2ME Deck\nImplementation-Version: %s\n' "$VERSION" > "$BUILD/manifest.txt"
jar --create --file "$BUILD/J2ME-Deck.jar" --manifest "$BUILD/manifest.txt" --main-class j2medeck.LinuxLoader -C "$BUILD/classes" .
echo "Built: $BUILD/J2ME-Deck.jar (version $VERSION)"
