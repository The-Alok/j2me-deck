# Development notes (pre-release history)

> These are the working notes kept while J2ME Deck was developed. The "v5 ... v19" numbers are **private development builds**, not
> releases; public releases start at 0.1.0 (see `CHANGELOG.md`). Some older entries describe limitations that were fixed later
> (for example 3D and LCDUI forms were added in v8/v9). "Not verified" lines were written when no display or Steam Deck was available
> in the development environment. Read this as history, not as the current feature list - the README is the current state.


## v19 patch: Steam Game Mode freeze on the first screen

Symptom: fine in Desktop Mode, but in Game Mode the game starts and stays on its first screen.

Not reproducible in the development environment (no gamescope, headless JRE), so this patch removes the three most likely causes and adds logging
that identifies the cause if the freeze remains. Please verify on the Deck.

* **OpenGL ES is no longer used by `renderer=auto` in Game Mode** (it was listed as unverified under gamescope). Game Mode uses the Swing
  renderer unless `renderer=gles` is chosen explicitly (`--renderer gles`, settings or game profile).
* **GL swap watchdog:** if `eglSwapBuffers` does not return for 2 s (compositor never completes the frame), the loader falls back to Swing
  by itself instead of freezing. In Game Mode the swap interval is 0 (gamescope paces frames), on the desktop it stays 1.
* **Auto-pause is off by default in Game Mode** (focus changes under gamescope are not a reliable "player left" signal). If a game is
  auto-paused anyway, any button press resumes it. Per game: profile `autopause=true` turns it back on.
* **Diagnostics in the game log** (`~/.local/share/j2me-deck/logs/<game>.log`): a `[j2me] session:` line (mode, renderer, display variables),
  focus gained/lost lines, `[j2me] alive` lines every 5 s for the first minute (frames published, focus, auto-pause state) and a thread dump
  when the Swing event thread stops answering for 4 s (`Diag`).

## v18: Steam Deck UI input and tab-label visibility fixes

* Set explicit Swing/Nimbus tabbed-pane foreground and background defaults to keep settings/key-mapper tab labels legible across selection and focus changes.
* Corrected the Xbox-style Steam Deck **View** button to map to Back/Clear and **Menu/Start** to map to Select/Fire in the library UI. A/B/X/Y and LB/RB retain their existing game-oriented mappings.
* Added a controller regression check for View and Menu/Start.

Build and headless controller checks pass. A real Steam Deck display/session is not available in this build environment, so please verify the visual tab issue on-device.

## v17: console-style, touch-friendly library UI

* **New UI kit (`ui/`):** `Anim` (shared 60 Hz timer, critically damped springs, a scroller with momentum and rubber-band edges),
  `Gesture` (mouse events from the touchscreen -> tap / long-press / drag / fling), `Gfx`, `Covers`, `RowList`, `Sheet`, `TopBar`, `HintBar`.
* **Screens:** `ShelfView` (hero + shelf of cover tiles, blurred tinted backdrop that cross-fades between games, focus scale + ring),
  `DetailView` (full game page with six quick-setting cards that write the game's profile), `SettingsPage` (global settings / key map /
  add games / quit), bottom `Sheet`s instead of `JOptionPane`. `LibraryWindow` is now only the controller that wires them together.
* **Library state:** `LibraryState` stores favourites, last-played time and play counts; the *Recent* and *Favorites* tabs use it.
* **Input:** one focus model for gamepad, keyboard (mapped through the user's key map) and touch. Controller layout changed:
  **LB / RB** = previous / next tab (global settings and global key map moved to the *Settings* tab), **X** = game page, **Y** = favourite,
  **B** = back (on Library it opens a quit confirmation instead of quitting at once).
* **Touch rules:** targets of 56 px or more at 1280x800, momentum scrolling, tap-to-focus then tap-to-play, long-press for options,
  tap outside a sheet to close it. Swing only sees one pointer, so there are no multi-finger gestures.
* **Speed:** cover cards, drop shadows and full-size backdrops are rendered once and blitted; profile and library-state lookups are cached
  (they used to hit the disk on every paint). Measured offscreen on one vCPU: about 18-25 ms per home frame, 8-10 ms per game page.
* **Tests:** `LibShots` renders home, game page, sheets, tabs, empty state and the 760x480 minimum size to PNGs; `TouchTest` drives
  d-pad keys and synthetic touch events (28 checks: browsing, tabs, back/quit, game page, favourites, tap, swipe, long-press, tap outside).
* **Not verified (no display or hardware in the development environment):** real touchscreen events on the Deck, fullscreen mode, drag and drop,
  the file chooser (unchanged, desktop-style), launching the child JVM from the new window, and frame rate on the Deck's own display.
  The settings form, key mapper and file chooser are still regular Swing dialogs.

## v15: sound-effect end-of-media watchdog + audio trace

* Sound-effect clips now also report "finished" from a timer (clip length + 150 ms), not only from the line's STOP event whose position
  check can fail on some backends. The game keeps a single "something is playing" slot, so one missed end event blocks all later sounds
  (including the music) until sound is toggled off and on in the game's options.
* `J2ME_DEBUG_AUDIO=1` logs player start / stop / endOfMedia and clip STOP events (`[audio]` lines).

## v14 (v13 + quiet-MIDI fix): quiet MIDI music (found with Medal of Honor Airborne)

* **Symptom:** menu / door / gun sounds (AMR) played, the background music (MIDI) seemed missing.
* **Cause:** the music *was* playing, but this game's main track (`ba`, resource 53) is written very softly (low velocities, channel
  volumes 45-100, soft strings/brass). Java's synthesizer renders it at about -33 dBFS RMS, and the game's volume request (level 60)
  was applied as a squared master-volume curve (-8 dB), giving about -41 dBFS: roughly 25-30 dB below the other tracks and the
  sound effects, i.e. inaudible on a handheld. The other two MIDI files (`aw`, `bb`) are loud and unaffected.
* **Fix:** `MidiLeveler` + `SharedMidi.openLeveled`: the synthesizer's audio is pulled by our own thread and raised towards -18 dBFS
  RMS (max +30 dB, never attenuates, per-chunk peak limit so it cannot clip, silence below -60 dBFS untouched). The game's volume is
  now applied linearly after the leveling, like for the sound-effect players. Quiet music ends up about 18-20 dB louder.
* **Needs** `--add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED` (added to `run.sh`, so the launcher has it too). Without the
  flag the old plain synthesizer is used and a line in the log says so.
* Profile keys: `midiboost=false` turns it off, `midiboost.target=-18` (dBFS, -40..-6) sets the level.
* The synthesizer open is retried after 5 s if it failed once (it used to stay silent for the whole session).
* The log now says `MIDI synthesizer ready: ... loudness leveling on/off` or `MIDI synthesizer unavailable`.
* Verified: real game through a simulated sound card (leveled path, fallback path); `MidiLevelerTest` (10 checks); SelfTest, FeatureTest,
  GlobalConfigTest, PadTest, RewriterTest still pass. **Not verified:** a real sound card / the Deck's speakers, and whether -18 dBFS
  suits your taste (change `midiboost.target`).


## v13: OpenGL ES output (lower power)

* **What:** the game picture is presented through EGL + OpenGL ES 2 instead of Java2D (`src/gl/java/j2medeck/gl`, about 900 lines).
  Java2D still renders the game into its framebuffer; the GPU does upload, scaling (shader: sharp-bilinear, nearest, integer, stretch), the LCD
  grid, toast / pause tags and vsync'd presentation. Selected by `renderer=auto|gles|swing` (default `auto`; falls back to Swing by itself).
* **How:** EGL, GLES2 and the three Xlib calls EGL needs are bound with the Java foreign-function API (`Ffm`, `Egl`, `Gles`, `X11`), so nothing
  is downloaded. `GlSurface` is a heavyweight AWT canvas inside the existing `ScreenView`; its X window id is read from the AWT peer (reflection,
  hence the `--add-opens` flags) and handed to `eglCreateWindowSurface`. One render thread owns the context and wakes only when a redraw is
  requested, so an unchanging picture costs nothing. The EGL surface is released *before* AWT destroys the window (`removeNotify`), and recreated on
  `addNotify`, which covers showing a Form and back, and the fullscreen toggle (frame dispose / show).
* **Plumbing:** `GlHost` / `GlControl` / `GlOverlay` / `GlBackend` in the main jar are plain Java 17 and load the GL classes reflectively, so a JVM
  that cannot (Java 17, missing flags, no libEGL, init error, lost window, swap failures) keeps or returns to the Swing path with a log line and
  an on-screen toast. `ScreenView` now implements `GlHost`; its input handlers attach to the GL canvas too. `run.sh`, the library's child JVM and
  `build.sh` handle the Java 21 preview flags. New: `--renderer`, *Renderer* in the settings dialog, `tools/gltest.sh`, `GlTest` (18 checks).
* **Verified here:** Xvfb + Mesa llvmpipe (software GLES 3.2): pixel-exact output per scaling mode incl. orientation, sharp-bilinear band width,
  LCD grid, overlay blending, resize, remove/re-add and dispose/show of the window; the real loader with a synthetic game (picture, keys, mouse
  coordinates, switching to a Form and back, scaling toast, pause tag, fullscreen toggle, forced GL failure -> Swing fallback, missing flags ->
  Swing). All earlier suites still pass.
* **Not verified:** a real GPU, gamescope / Steam Game Mode, and power use (a software GL driver says nothing about watts; compare battery
  draw with `renderer=swing` on the Deck). Java 22+ builds. Many different Canvases in one game each get their own GL surface and idle thread.
* **Known limits:** X11/XWayland only (as the rest of the port). Overlays are the toast and pause tags only; the Swing soft-key bar and dialogs are
  unchanged. `syncFlush` waits for the swap (up to 100 ms) on the GL path.

---

## v12: Steam Game Mode launcher

* **`j2me-deck`** (source `launcher/j2me-deck.c`, rebuild with `launcher/build.sh`): static native launcher to add to Steam.
  It restores `SYSTEM_LD_LIBRARY_PATH`, drops `LD_PRELOAD`/`LD_AUDIT` (Steam runtime + overlay break the JVM) and runs
  `bash run.sh` (works without the +x bit), found through `/proc/self/exe` (symlinks, any working directory).
* **`run.sh`** does the same cleanup when run directly, finds Java in more places, falls back to `DISPLAY=:0`, runs Java as a
  child (stop signal forwarded, exit code kept) and writes `~/.local/share/j2me-deck/launch.log` (+ `.1`), with a dialog if
  zenity/kdialog/xmessage exist.
* Verified here: a simulated Steam environment (broken library shadows + bogus overlay preload) makes plain `java` fail but
  runs through `j2me-deck`; arguments, SIGTERM, exit codes, symlinks and the missing-`run.sh` message.
  **Not verified: a real Game Mode session** (no gamescope here, and that environment's JRE cannot open windows).

## v11: global settings and global key map

* **Global defaults:** `Profile.loadGlobal()` (`~/.config/j2me-deck/global/game.properties`) sits under every game profile (a
  `java.util.Properties` default chain), and `KeyMapper.loadMerged` layers global then per-game `keyboard.properties` /
  `gamepad.properties` for `InputManager` and `GamepadReader`.
* **Buttons:** library header *Global Settings* / *Global Keymap*, controller LB / RB in the library, F7 / Shift+F8 in a game.
* **Override-aware saving:** a game's form/key map stores only values that differ from the global default (or from the built-in
  default when no global value exists), so untouched values keep following the global ones, and a game can still override a
  global value back to the built-in one. Also warns when one key is bound to two actions.
* Tests: `GlobalConfigTest` (21 checks). The dialogs themselves (modal) were only rendered offscreen / exercised through their forms.

## v10: fixes found with a real 3D game (Medal of Honor Airborne, M3G)

Fixed
* **Hero's rifle not drawn:** the rifle is a Mesh attached to a hand bone inside the SkinnedMesh's skeleton. The skeleton is a
  child of the SkinnedMesh in the scene graph, but the scene collector never descended into it, so anything attached to a bone
  was skipped. It now is.
* **Whole-screen flash when shooting:** `Sprite3D` drawing only knew ALPHA and REPLACE, so ALPHA_ADD (muzzle flash, impact sparks)
  and MODULATE (the game's full-screen tint) were painted as opaque fills. All four blend modes now use the triangle path's
  equations, and colour-write masking is honoured.
* **Scaled sprites were far too large:** size was crop pixels x scale (a 32 px muzzle flash at scale 4 became ~1160 px wide). A
  scaled sprite is now a unit square sized by the node's scale; the crop only selects the image region.
* **Negative crop sizes:** a negative width/height only mirrors the image; the rectangle still starts at (cropX, cropY) and
  extends by |w| x |h|. It used to extend the other way and sampled the wrong part of the atlas.
* `RewriterTest` created no output folder for its debug dump (test-only).

Verified: this game's 3D resources (7 `.m3g` files, 300+ meshes incl. skinned characters) load and render; the first real-content
check of the loader. Gameplay frames show the rifle and a small muzzle flash and impact spark instead of a screen-wide flash.
New regression test `M3GSpriteSkinTest` (6 checks) fails on v9 and passes on v10. `CompatTest` (`ignoreservicerepaints`) is timing
dependent and fails now and then on v9 and v10 alike.

Not verified: textures/colours against a real phone, audio (AMR needs ffmpeg), real gamepad/windowed GUI. Known approximations from
v9 still apply (SPLINE/SQUAD keyframes, simplified node alignment, no mipmaps).

---

## v9: presets, compatibility tweaks, speed control, controller, 3D

New
* Device presets and vendor key layouts (Motorola, Siemens, LG, Sagem, Sharp, Nokia/SE); `getKeyCode`/`getGameAction`/
  `GameCanvas.getKeyStates` follow the layout.
* Ten compatibility tweaks, frame-rate limit, platform/locale/encoding overrides, auto-size to the display aspect.
* Class rewriter rule table: `Thread.sleep`, `System.currentTimeMillis`, `System.gc`, `Thread.yield`, `Runtime.gc/freeMemory/totalMemory`
  give speed x0.25..x8, pause, single-step (F3-F6), ignore gc/sleep, configurable heap.
* LT, RT, right stick, thresholds and inversion in the controller code and key mapper.
* Library: log files, MIDlet chooser, collision-safe profile ids, tabbed settings, `lib/` class path.
* Mascot Capsule v3 (adapted MIT software renderer) and M3G (written from the JSR-184 spec, full public API).

Fixed
* **Rewriter bug present since v6:** the bytecode walker did not know `invokestatic` is 3 bytes, so it drifted out of
  alignment inside methods; harmless by luck before, but it hid later patches and could in principle corrupt a class.

Verified here (175 automated checks, `./tools/selftest.sh`)
* Layouts: a Motorola-layout game really receives -6/-20/-21; Siemens -59/-4. Every tweak changes what it should.
* Rewriter shims on compiled game-like classes, including a `Thread` subclass calling `sleep()` unqualified.
* Controller: raw `js_event` bytes through the real reader (triggers with either rest value, stick hysteresis, inversion).
* Mascot: triangle, quad, UV-mapped palette texture, 2D/3D mixing, fixed-point math.
* M3G: API diffed against the JSR-184 list (all 389 spec entries present; the 10 not present are `finalize()` and Image2D
  helpers that are not in the spec) and all 79 constants match; transforms, scene graph, animation, culling, depth,
  blending, alpha test, lighting, fog, textures, near-plane clipping, skinning, morphing, picking, translated/clipped
  targets, and the loader on a compressed and an uncompressed synthetic file.
* Diamond Rush and both Splinter Cell games still run on the final build (same frame rates as v8.2).

**Not verified: 3D against real content.** None of your games use 3D and I had no `.m3g` or Mascot assets, so both 3D
implementations were tested with synthetic geometry. The `.m3g` loader follows the file format from my knowledge of the
spec and has only read files written by a writer I wrote to the same understanding, so a real game may expose a
misreading. Send me one 3D game and I will verify it.

Known approximations in M3G: SPLINE/SQUAD keyframes use LINEAR/SLERP; node alignment is simplified; picking ignores
skinning/morphing; no mipmaps, antialiasing or dithering; Sprite3D is basic; external references in `.m3g` files are
not followed; two texture units. Mascot vendor wrappers (Motorola, J-Phone, Vodafone, JBlend) are not included.

Not done: Nokia ringtone (OTT) playback, because the only decoder available to me was GPL-licensed; MP3 through
`lib/` is untested (no decoder jar offline). Speed/pause hotkeys need a keyboard or a Steam Input binding.
As before, the windowed GUI, real audio and real gamepad hardware could not be exercised in the development environment.

---

## v8 (this version): library GUI, per-game profiles, broader API coverage

Built on v7 (key mapper and per-game key profiles are unchanged; `game.properties` lives in the same profile folder).

New
* **Game library window** (`ui/LibraryWindow`): import by button / folder / drag and drop, JAD support, icons, compatibility
  badges, gamepad navigation, per-game launch in a separate JVM, remove (optionally with saves).
* **Per-game profile** (`Profile`, `ui/GameSettingsDialog`): custom screen size, scaling incl. stretch, fullscreen, window scale,
  soft-key bar, network, mute, auto-pause, fonts, system-property and JAD overrides. F10 now saves per game.
* **API scanner** (`ApiScanner`): reads the constant pool of every class and reports missing classes/methods and
  unsupported feature families (3D, Bluetooth, SMS...). Also usable on the command line.
* **High-level LCDUI** (`Form`, `List`, `TextBox`, `Alert`, all standard `Item`s, `Ticker`) rendered with Swing, plus a
  soft-key command bar for Canvases that use Commands.
* **Nokia UI API** (`FullCanvas`, `DirectGraphics`, `DirectUtils`, `DeviceControl`, `Sound`).
* **I/O**: `Connector` with all overloads, `HttpConnection`/`HttpsConnection`/`SocketConnection` (opt-in), sandboxed JSR-75 files.
* **Audio**: tone sequences, optional ffmpeg conversion for MP3/AMR/AAC/ADPCM, mute, graceful silence when undecodable.
* **LCD grid shader** (F9 cycles off/low/medium/high, per-game setting): overlay lines on source-pixel boundaries, cached per output size (~1 ms/frame).
* F12 screenshots, `./run.sh` with no arguments opens the library, `make-shortcut.sh --library`.

Verified here (headless)
* 56 automated checks (`./tools/selftest.sh`): MIDP graphics and sprites, key states, scaling, HTTP GET/POST against a local
  server, file sandbox and path-traversal rejection, tone sequence length and end-of-media, Nokia pixel formats, scanner on
  synthetic supported/unsupported games, JAD merge, soft-key layout.
* LCDUI screens rendered offscreen and inspected (List, Form with every item type, Alert flow, TextBox, command bars);
  library window, cards and settings form rendered offscreen and inspected.
* Diamond Rush, Splinter Cell: Chaos Theory and Conviction boot and run on the new code; Diamond Rush plays through a
  per-game 320x480 profile.

**Not verified (no display or hardware in the development environment)**: the real windowed/fullscreen library and settings dialogs
(layout was checked offscreen only), file chooser and drag and drop, gamepad navigation inside the library, launching
the child JVM from the library, ffmpeg conversion, real audio output. Please report anything odd.

Known limits / things to know
* A custom screen size only helps games that adapt to the size they are given; most Gameloft builds draw a fixed 240x320.
* `DirectGraphics.ROTATE_90/270` direction follows the Nokia docs as remembered (counter-clockwise); verify with a game that uses it.
* 3D (M3G / Mascot) is not implemented, nor are Siemens/Samsung/Motorola/etc. vendor APIs.
* Nokia ring-tone (`Sound.FORMAT_TONE`) data is accepted but silent.

---

Java SE desktop backend, independent of the Android application UI.

## Verified (Diamond Rush 1.2.0, K800i build, headless + scripted input)

* Launches, loads suite resources, runs title -> menu -> New Game -> level; dialogs advance, arrows move the hero,
  soft keys open/close the pause menu. ~20 frames/s published (the game's own cap).
* Gamepad: real `js_event` packets (A, hat, stick, X) drove the game from title screen into play.
* `./tools/selftest.sh`: 27 checks pass (all 8 MIDP transforms, translate+clip, sprite refs/collision, key states, scaling).
* Scaling at 1280x800 costs ~6 ms/frame (sharp-bilinear), <1 ms (nearest/integer) on a single slow core.

## Changes in this version (v6) versus v5

Bugs fixed
* `Display.vibrate()` was missing -> `NoSuchMethodError` in Diamond Rush when the hero is hurt.
* `Graphics.translate()` + `setClip()` applied the offset twice; clip getters returned wrong values.
* `drawString` TOP/BOTTOM anchors were wrong (text drawn above its anchor); `Font` metrics disagreed with `Graphics`.
* `Image.createImage(w,h)` was transparent (MIDP: opaque white); mirrored-rotated transforms were wrong.
* `showNotify()/hideNotify()/sizeChanged()` were never called.
* `repaint()/serviceRepaints()` painted from the game thread through Swing; now MIDP-style paint with dirty-rect clip.
* Held keys stuttered on X11 (auto-repeat sends release+press pairs): releases are debounced, repeats become `keyRepeated`.
* `getGameAction/getKeyCode` mapped wrongly; `GameCanvas.getKeyStates` used wrong bit values and no latching.
* MIDI music could not play (no MIDI path) and `endOfMedia` was never sent, which the game's music loop needs.
* RMS: each open created a divergent copy; all games shared one store namespace; missing exception types.
* The process never exited after `notifyDestroyed()` because game threads are non-daemon.
* `startApp` ran on the UI thread (blocking painting); it now runs on its own thread like on a handset.

Steam Deck work
* Gamepad support via `/dev/input/js*`, fullscreen auto-detection for Game Mode, focus-loss auto-pause,
  three scaling modes, `setup-java.sh`, `make-shortcut.sh`, gamescope window workaround in `run.sh`.

Other
* Class rewriter now retargets every `Class.getResourceAsStream` call (previously one fragile bytecode pattern),
  preserves constant-pool strings byte-exactly, and never fails class loading.
* Complete `Sprite`, `TiledLayer`, `Layer`, `LayerManager`, `RecordEnumeration`, `Alert` stubs.

## Not verified / known limitations

* **The windowed GUI path (real X11 / gamescope window, fullscreen, focus handling, audio output) could not be run
  in the development environment**, which only has a headless JRE. Rendering, input dispatch and scaling were tested offscreen.
  Please report anything odd on the Deck.
* Gamepad button numbering assumes an Xbox-style layout (Steam's virtual controller). Remap in `gamepad.properties`.
* No LCDUI Form/List/TextBox, M3G/Mascot 3D, networking, camera or Bluetooth.


## Key mapper addition

* F8 opens a two-tab Swing mapping dialog for keyboard keys and all 16 gamepad button slots.
* Mappings are stored per game title under `~/.config/j2me-deck/profiles/<title>/`.
* Saving reloads keyboard/controller maps live; no restart required.
