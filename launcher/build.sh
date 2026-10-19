#!/usr/bin/env bash
# Rebuilds the native launcher ./j2me-deck (needs gcc; produces a static x86_64 / aarch64 binary).
set -euo pipefail
cd "$(dirname "$0")/.."
gcc -O2 -Wall -static -s -o j2me-deck launcher/j2me-deck.c
chmod +x j2me-deck
file j2me-deck 2>/dev/null || true
echo "Built $(pwd)/j2me-deck"
