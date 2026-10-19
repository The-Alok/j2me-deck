# Changelog

## 0.1.0 - first public release

First public release of J2ME Deck: a native Java SE / Swing runtime for Java ME (MIDP 2.0) games on Steam Deck and Linux, based on
the architecture of J2ME-Loader (see `NOTICE`).

Highlights
* Console-style, controller- and touch-friendly game library (shelf, game page, favourites, recent, sheets); drag and drop import,
  JAD support, compatibility scanner.
* Per-game and global settings, device presets (~25 handsets), key layouts, compatibility tweaks, speed control, rewriter shims.
* Full keyboard and Steam Deck controller support, in-game key mapper (F8), global key map.
* LCDUI (high and low level), Game API, Nokia UI API, MMAPI (MIDI/WAV/tones, optional ffmpeg), RMS, JSR-75 files, optional networking.
* 3D: Mascot Capsule v3 and M3G (JSR-184) software renderers.
* OpenGL ES output (EGL + GLES2 via the foreign-function API) with automatic fallback to Swing; LCD-grid shader.
* Steam Game Mode: native launcher `j2me-deck`, `setup-java.sh` (private Temurin 21 JRE), `make-shortcut.sh`, launch/game logs.

Changes since the private development builds
* Project renamed to **J2ME Deck**. Data moved from `~/.config/j2me-loader` and `~/.local/share/j2me-loader` to
  `~/.config/j2me-deck` and `~/.local/share/j2me-deck`; existing data is copied automatically on first start (old folders are left alone).
  The native launcher is now `j2me-deck` (re-add it to Steam). The platform string games see is `J2ME-Deck`.
* `./run.sh --version`, version in the jar manifest and in `launch.log`.
* `tools/selftest.sh` now exits non-zero on failures; a flaky repaint test was made deterministic; GitHub Actions CI and a release workflow added.
* Gradle build files removed (the supported build is `./build.sh`).

Known limitations: see the README.
