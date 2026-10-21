# Changelog

## 0.1.1

Added
* **Vendor Mascot Capsule 3D wrappers**: `com.vodafone.v10.graphics.j3d` (Vodafone / J-Phone V10), `com.motorola.graphics.j3d` and
  `com.jblend.graphics.j3d` (JBlend, incl. command lists, `renderFigure`/`renderPrimitives`/`flush` and `Texture(Image, ...)`). Games
  cast their `Graphics` to the vendor `Graphics3D` interface, as on the handsets; calls are forwarded to the built-in Mascot v3
  software renderer. The compatibility scanner now lists these games as supported instead of unsupported. Not included:
  `com.j_phone.amuse.j3d` and the separate Motorola iDEN API `com.motorola.iden.micro3d`.

Fixed
* **White bar at the bottom of some games.** Games built for one fixed canvas size (for example the Diamond Rush build that hard-codes
  240x304) leave the rest of the 240x320 virtual screen undrawn, and MIDP defines a fresh canvas as white. Those never-drawn rows (or
  columns at the right edge) are now shown black. Only an area the game has never touched is affected, and only when it sits directly
  below/next to a region the game paints edge to edge, so games that fill the whole screen, or draw sparsely on the default white
  background, look exactly as before. Set `J2ME_KEEP_UNPAINTED=1` (or `-Dj2me.keepunpainted=true`) to switch it off.

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
