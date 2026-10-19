#!/usr/bin/env bash
# Installs a private Java runtime (Eclipse Temurin 21 JRE) under ~/.local/share/j2me-deck/jre.
# SteamOS has a read-only root file system, so this avoids touching system packages. Needs internet + curl.
set -euo pipefail
DEST="$HOME/.local/share/j2me-deck/jre"
if [[ -x "$DEST/bin/java" ]]; then echo "Already installed: $DEST"; "$DEST/bin/java" -version; exit 0; fi
ARCH="$(uname -m)"
case "$ARCH" in x86_64) A=x64;; aarch64) A=aarch64;; *) echo "Unsupported CPU: $ARCH" >&2; exit 1;; esac
URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/$A/jre/hotspot/normal/eclipse"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
echo "Downloading Temurin 21 JRE ($A) ..."
curl -fL --retry 3 -o "$TMP/jre.tar.gz" "$URL"
mkdir -p "$DEST"
tar -xzf "$TMP/jre.tar.gz" -C "$DEST" --strip-components=1
"$DEST/bin/java" -version
echo "Done. ./run.sh will now find this runtime automatically."
