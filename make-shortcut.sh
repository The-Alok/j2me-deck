#!/usr/bin/env bash
# Creates an application-menu shortcut so Steam can add it:
#   Desktop Mode -> Steam -> Games -> Add a Non-Steam Game -> tick the new entry -> Add Selected Programs.
# Usage: ./make-shortcut.sh --library                    one entry that opens the whole game library (recommended)
#        ./make-shortcut.sh /path/to/game.jar ["Name"]   one entry that starts a single game
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
DIR="$HOME/.local/share/applications"
mkdir -p "$DIR"
if [[ "${1:-}" == "--library" ]]; then
  NAME="J2ME Deck"
  FILE="$DIR/j2me-library.desktop"
  EXEC="\"$ROOT/j2me-deck\" --fullscreen"
else
  [[ $# -ge 1 && -f "$1" ]] || { echo "Usage: $0 --library | $0 game.jar [\"Display name\"]" >&2; exit 2; }
  JAR="$(realpath "$1")"
  NAME="${2:-$(basename "$JAR" .jar)} (J2ME)"
  SLUG="$(echo "$NAME" | tr -c 'A-Za-z0-9\n' '-' | sed 's/--*/-/g; s/^-//; s/-$//')"
  FILE="$DIR/j2me-$SLUG.desktop"
  EXEC="\"$ROOT/j2me-deck\" --fullscreen \"$JAR\""
fi
cat > "$FILE" <<DESK
[Desktop Entry]
Type=Application
Name=$NAME
Comment=Play Java ME (J2ME) games on Steam Deck and Linux
Exec=$EXEC
Path=$ROOT
Terminal=false
Categories=Game;
DESK
chmod +x "$FILE"
echo "Created $FILE"
echo "Now add it in Steam (Desktop Mode): Games -> Add a Non-Steam Game -> \"$NAME\"."
