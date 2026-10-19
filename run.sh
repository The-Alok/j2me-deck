#!/usr/bin/env bash
# Usage: ./run.sh                              open the game library (import, settings, play)
#        ./run.sh [options] game.jar [midlet]   start one game directly (./run.sh --help lists the options)
#
# In Steam Game Mode start it through the native ./j2me-deck executable instead (see README, "Steam Deck Game Mode").
# Every start writes ~/.local/share/j2me-deck/launch.log (the previous one is kept as launch.log.1): look there first
# when something does not start.
set -u
SELF="$(readlink -f "${BASH_SOURCE[0]}")"
ROOT="$(dirname "$SELF")"

# ---- HOME can be missing in Game Mode
if [[ -z "${HOME:-}" ]]; then
  HOME="$(getent passwd "$(id -u)" 2>/dev/null | cut -d: -f6 || true)"
  [[ -n "$HOME" ]] || HOME=/home/deck
  export HOME
fi
STATE="$HOME/.local/share/j2me-deck"

# ---- one-time migration of data from the pre-release name (j2me-loader): games, saves, profiles, key maps. Old folders are left in place.
migrate_dir() {   # migrate_dir OLD NEW : copy what NEW does not have yet
  [[ -d "$1" ]] || return 0
  mkdir -p "$2" 2>/dev/null || return 0
  local item
  for item in "$1"/*; do
    [[ -e "$item" && ! -e "$2/$(basename "$item")" ]] && cp -a "$item" "$2/" 2>/dev/null
  done
  return 0
}
if [[ ! -e "$HOME/.config/j2me-deck/.migrated" && ( -d "$HOME/.local/share/j2me-loader" || -d "$HOME/.config/j2me-loader" ) ]]; then
  migrate_dir "$HOME/.local/share/j2me-loader" "$STATE"
  migrate_dir "$HOME/.config/j2me-loader" "$HOME/.config/j2me-deck"
  mkdir -p "$HOME/.config/j2me-deck" 2>/dev/null; : > "$HOME/.config/j2me-deck/.migrated" 2>/dev/null
fi

# ---- Steam starts non-Steam games with its own runtime libraries and overlay injected; both break the JVM's native
#      libraries (AWT, fonts, audio). Restore the system library path and drop the overlay preload.
if [[ -n "${SYSTEM_LD_LIBRARY_PATH:-}" ]]; then export LD_LIBRARY_PATH="$SYSTEM_LD_LIBRARY_PATH"; else unset LD_LIBRARY_PATH; fi
unset LD_PRELOAD LD_AUDIT

# ---- log file
mkdir -p "$STATE" 2>/dev/null || true
LOG="$STATE/launch.log"
[[ -f "$LOG" ]] && mv -f "$LOG" "$LOG.1" 2>/dev/null
{
  echo "== J2ME Deck $(tr -d '[:space:]' < "$ROOT/VERSION" 2>/dev/null) launch $(date)"
  echo "args: $*"
  echo "root: $ROOT   user: $(id -un 2>/dev/null)   kernel: $(uname -sr)"
  env | grep -E '^(DISPLAY|WAYLAND_DISPLAY|XAUTHORITY|XDG_[A-Z_]*|Steam[A-Za-z]*|STEAM[A-Z_]*|GAMESCOPE[A-Z_]*|LD_[A-Z_]*|SDL_[A-Z_]*|J2ME[A-Z_]*|JAVA[A-Z_]*|PATH)=' | sort
  echo "--"
} > "$LOG" 2>/dev/null || LOG=/dev/null
if [[ -t 2 ]]; then exec > >(tee -a "$LOG") 2>&1; else exec >>"$LOG" 2>&1; fi

# ---- tell the user when something is wrong (a dialog if a tool is around; Game Mode shows none, so the log is the record)
notify() {
  local msg="$1"
  echo "ERROR: $msg" >&2
  if command -v zenity >/dev/null 2>&1; then timeout 60 zenity --error --no-markup --title "J2ME Deck" --text "$msg" >/dev/null 2>&1 && return
  elif command -v kdialog >/dev/null 2>&1; then timeout 60 kdialog --error "$msg" --title "J2ME Deck" >/dev/null 2>&1 && return
  elif command -v xmessage >/dev/null 2>&1; then timeout 60 xmessage -center "$msg" >/dev/null 2>&1 && return
  fi
  return 0
}

# ---- find a Java 17+ runtime (private one from setup-java.sh first, then the system's)
find_java() {
  shopt -s nullglob
  local c v
  for c in "${J2ME_JAVA:-}" "$ROOT/jre/bin/java" "$STATE/jre/bin/java" "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
           "$(command -v java 2>/dev/null || true)" /usr/lib/jvm/*/bin/java /usr/lib/jvm/*/jre/bin/java \
           /usr/lib64/jvm/*/bin/java /opt/*/bin/java /run/host/usr/lib/jvm/*/bin/java; do
    [[ -n "$c" && -x "$c" ]] || continue
    v="$("$c" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version =/ {print $2; exit}')"
    if [[ "$v" =~ ^[0-9]+$ ]] && (( v >= 17 )); then echo "$c"; return 0; fi
    echo "skipping $c (Java ${v:-?})" >&2
  done
  return 1
}
if ! JAVA="$(find_java)"; then
  notify "Java 17 or newer was not found. Open Desktop Mode once and run ./setup-java.sh in the J2ME Deck folder, then start it again. Log: $LOG"
  exit 127
fi
echo "java: $JAVA"

JAR="$ROOT/build/J2ME-Deck.jar"
if [[ ! -f "$JAR" ]]; then
  echo "jar missing, building"
  "$ROOT/build.sh" || { notify "Could not build $JAR (needs a full JDK with javac). Log: $LOG"; exit 1; }
fi

# ---- display: Game Mode runs inside gamescope; its Xwayland is normally :0
if [[ -z "${DISPLAY:-}" ]]; then
  for s in /tmp/.X11-unix/X*; do [[ -S "$s" ]] && { export DISPLAY=":${s##*/X}"; echo "DISPLAY was unset, using $DISPLAY"; break; }; done
fi

# Java's window code gets confused by gamescope (Steam Game Mode) without this.
export _JAVA_AWT_WM_NONREPARENTING=1
export AWT_TOOLKIT="${AWT_TOOLKIT:-}"
[[ -z "$AWT_TOOLKIT" ]] && unset AWT_TOOLKIT

# OpenGL ES output (low power): needs the foreign-function API. Java 21 has it as a preview feature (the shipped jar is built that way),
# Java 22+ has it final. If these flags do not fit this Java, the loader notices and keeps the Swing renderer.
JAVA_FEATURE="$("$JAVA" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version =/ {print $2; exit}')"
GL_FLAGS=()
if [[ "$JAVA_FEATURE" =~ ^[0-9]+$ ]] && (( JAVA_FEATURE >= 21 )); then
  (( JAVA_FEATURE == 21 )) && GL_FLAGS+=(--enable-preview)
  GL_FLAGS+=(--enable-native-access=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED)
fi
echo "java feature version: ${JAVA_FEATURE:-?}; gl flags: ${GL_FLAGS[*]:-none}"

# lib/*.jar are optional extras (e.g. an MP3 decoder for Java Sound); a missing lib/ folder is fine.
# Run Java as a child so a failed start can be reported, and pass Steam's stop signal on to it.
"$JAVA" -Xmx512m -XX:+UseSerialGC -Xshare:auto "${GL_FLAGS[@]}" \
     --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED \
     -Dsun.java2d.opengl=false -Dawt.useSystemAAFontSettings=on \
     -cp "$JAR:$ROOT/lib/*" j2medeck.LinuxLoader "$@" &
JPID=$!
trap 'kill -TERM "$JPID" 2>/dev/null' TERM INT HUP
wait "$JPID"; rc=$?
while kill -0 "$JPID" 2>/dev/null; do wait "$JPID"; rc=$?; done     # a signal interrupts the first wait
trap - TERM INT HUP
echo "exit code: $rc after ${SECONDS}s"
if (( rc != 0 && rc != 130 && rc != 143 && SECONDS < 20 )); then
  notify "J2ME Deck stopped right after starting (exit code $rc). See $LOG"
fi
exit "$rc"
