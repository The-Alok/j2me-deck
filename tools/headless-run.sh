#!/usr/bin/env bash
# Run a game without a display and capture screenshots, e.g.:
#   tools/headless-run.sh game.jar "wait 4000;shot /tmp/a.png;tap FIRE;wait 1000;shot /tmp/b.png;exit"
# Script commands: wait MS | shot FILE | tap KEY [MS] | press KEY | release KEY | fps MS | exit
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec java -Djava.awt.headless=true -Dj2me.rms.dir="${TMPDIR:-/tmp}/j2me-test-rms" \
     -jar "$ROOT/build/J2ME-Deck.jar" "$1" --script "$2"
