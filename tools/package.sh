#!/usr/bin/env bash
# Creates the release archives in dist/: j2me-deck-<version>.tar.gz (+ .zip when `zip` exists) and SHA256SUMS.
# Needs: a JDK (21 recommended, so the OpenGL ES output is included) and gcc (for the static Steam launcher).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
VERSION="$(tr -d '[:space:]' < VERSION)"
NAME="j2me-deck-$VERSION"
"$ROOT/build.sh"
"$ROOT/launcher/build.sh"
GL_ENTRIES="$(jar tf build/J2ME-Deck.jar | grep -c 'j2medeck/gl/GlSurface.class' || true)"
if [[ "$GL_ENTRIES" -lt 1 ]]; then
  echo "WARNING: this jar has no OpenGL ES output (build with JDK 21 for a full release)." >&2
  [[ "${ALLOW_NO_GL:-0}" == "1" ]] || { echo "Refusing to package without it; set ALLOW_NO_GL=1 to override." >&2; exit 1; }
fi
STAGE="$(mktemp -d)"; trap 'rm -rf "$STAGE"' EXIT
D="$STAGE/$NAME"
mkdir -p "$D/build" "$D/lib" "$D/docs"
cp build/J2ME-Deck.jar "$D/build/"
cp j2me-deck run.sh setup-java.sh make-shortcut.sh VERSION LICENSE NOTICE THIRD_PARTY.md README.md CHANGELOG.md "$D/"
cp lib/README.txt "$D/lib/"
cp docs/DEVELOPMENT_NOTES.md "$D/docs/"
chmod 755 "$D/j2me-deck" "$D/run.sh" "$D/setup-java.sh" "$D/make-shortcut.sh"
mkdir -p dist && rm -f "dist/$NAME".tar.gz "dist/$NAME".zip dist/SHA256SUMS
tar --sort=name --owner=0 --group=0 --numeric-owner -C "$STAGE" -czf "dist/$NAME.tar.gz" "$NAME"
if command -v zip >/dev/null 2>&1; then (cd "$STAGE" && zip -qr -X "$ROOT/dist/$NAME.zip" "$NAME"); fi
(cd dist && sha256sum "$NAME".tar.gz $( [[ -f "$NAME.zip" ]] && echo "$NAME.zip" ) > SHA256SUMS)
echo "Created:"; ls -la dist
