# J2ME Deck

**Play classic Java ME (J2ME / MIDP 2.0) phone games on the Steam Deck and on Linux.**

<img width="1247" height="746" alt="image" src="https://github.com/user-attachments/assets/f0fda62c-19bb-4592-995a-09e43c767929" />


J2ME Deck is a native desktop runtime for `.jar` / `.jad` mobile games, with a console-style, controller- and touch-friendly game
library. It is plain Java SE + Swing. It is based on the architecture and API coverage of
[J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) (Apache-2.0) and is **not** an official release of that project - see
[Credits and licenses](#credits-and-licenses).

**AI (Claude) was used for development of this emulator.**

> **Status: 0.1.0, first public release.** It was developed and tested on a real Steam Deck with a handful of games
> (for example Gameloft's *Diamond Rush*, K800i build, and the 3D game *Medal of Honor Airborne*).

**No games are included.** You need your own `.jar` files of games you are entitled to use.

## Quick start (Steam Deck, Desktop Mode)

1. Download `j2me-deck-<version>.tar.gz` from the [Releases](../../releases) page, unpack it somewhere permanent (e.g. `~/j2me-deck`)
   and open a terminal (Konsole) in that folder.
2. SteamOS does not ship Java. Install a private runtime once (needs internet, touches no system files):
   ```bash
   chmod +x *.sh tools/*.sh
   ./setup-java.sh
   ```
3. Open the game library (or start one game directly):
   ```bash
   ./run.sh                                   # library window: import games, settings, play
   ./run.sh ~/Downloads/YourGame.jar          # run a single game
   ```

On a normal Linux PC with Java 17+ installed, skip step 2.

## Playing in Game Mode (with the Deck's own controls)

Add the native executable **`j2me-deck`** (not `run.sh`) to Steam. Do this once in Desktop Mode:

1. Keep the folder on the Deck's internal storage (e.g. `/home/deck/j2me-deck`) and run `./setup-java.sh`
   once (needs internet), because SteamOS has no Java.
2. Steam -> Games -> **Add a Non-Steam Game** -> **Browse...** -> set the file type to *All files* -> pick `j2me-deck` -> Add.
3. In the entry's Properties leave **Compatibility** *unticked* (Proton cannot run a Linux program) and the launch options empty.
   That opens the game library fullscreen; add games there. (To start one game directly, put its path in the launch
   options, or use `./make-shortcut.sh game.jar "Name"`, which creates a menu entry Steam can add.)
4. In Game Mode keep Steam's *Gamepad* controller layout (see below).

Why a native executable: Steam starts non-Steam games with its own runtime libraries and overlay injected into the process.
That makes Java fail to start (and can break the shell script too). `j2me-deck` is a small static program that restores
the system library path, drops the overlay preload and then runs `run.sh`. If the executable bit was lost (e.g. the folder
was copied through a USB stick), run `chmod +x j2me-deck` in Desktop Mode. Rebuild it with `launcher/build.sh` (needs `gcc`).

**Controller:** On steam deck keep Steam's default *Gamepad* layout on gamemode and gamepad layout (by holding start) in desktop mode. The loader reads the virtual Xbox pad directly, so no key
bindings are needed:

| Deck control | Sent to the game |
|---|---|
| D-pad / left stick | arrow keys (move, climb, menus) |
| A | FIRE / confirm (`-5`) |
| X, Menu (Start) | left soft key (`-6`, pause menu in Diamond Rush) |
| B, View (Back) | right soft key (`-7`, back) |
| Y | number key `5` |
| L1 / R1 | `*` / `#` |

If you prefer a keyboard-style Steam layout, these keys work too: arrows or **WASD**, **Enter/Space** = fire,
**Q / F1** = left soft, **E / F2** = right soft, **Esc/Backspace** = clear, **0-9, \*, #** (also numpad).

For manual controller remapping, the active game profile is stored at `~/.config/j2me-deck/profiles/<game-name>/gamepad.properties`:
```properties
button.0=FIRE          # names: UP DOWN LEFT RIGHT FIRE SOFT_LEFT SOFT_RIGHT CLEAR STAR POUND 0-9 NONE
button.3=5
dpad=arrows            # arrows | digits | both (digits = 2/4/6/8 for games that ignore arrow keys)
```

## Game library (console-style home)

`./run.sh` with no arguments opens the library. It is laid out like a console home screen and works with the Deck's controller,
the touchscreen, the trackpad / mouse and the keyboard.

* **Home:** a hero block for the focused game (name, vendor, compatibility, **Play**, **Details**, favourite star) over a
  colour-tinted backdrop taken from the game's icon, and a shelf of cover tiles below it. Tabs along the top: **Library**,
  **Recent**, **Favorites**, **Settings**. A hint bar at the bottom shows the buttons (controller glyphs, or touch tips after you
  touched the screen).
* **Game page** (X, or *Details*): big cover, **Play**, **Options**, favourite, and six large quick-setting cards: screen size,
  scaling, LCD grid, renderer, sound on/off and key mapping. Everything else is under *Options* (settings, key mapping,
  compatibility report, log, remove).
* **Sheets** replace dialogs for options, choices, confirmations, logs and the MIDlet chooser. (The full per-game and global
  settings forms and the key mapper are still the familiar windows.)

| Controller | Does |
| --- | --- |
| D-pad left / right | browse the shelf (up: tab bar, down: back to the shelf) |
| **A** | play the focused game (on the *Add games* tile: import) |
| **X** / Menu | game page; on the game page: Options |
| **Y** | add / remove favourite |
| **LB** / **RB** | previous / next tab |
| **B** | back; on Library: quit (asks first) |

| Touch / mouse | Does |
| --- | --- |
| swipe the shelf | scroll (with momentum); the nearest tile gets the focus |
| tap a tile | focus it; tap the focused tile again to play |
| long-press (or right-click) a tile | options sheet |
| tap a card / row | open or change it; tap outside a sheet to close it |

* **Add games:** the *Add games* tile, *Settings -> Add games*, or drag and drop `.jar`/`.jad` files or a folder onto the
  window. Games are copied to `~/.local/share/j2me-deck/games/`; a `.jad` next to a jar is picked up automatically.
* **Compatibility badge:** each game is scanned (no code is run) and marked *compatible*, *may have problems*
  (missing methods) or *unsupported* (needs 3D, Bluetooth, SMS, ...). *Options -> Compatibility report* lists exactly what is missing.
* **Play** starts the game in its own process, hides the library, and brings it back when the game exits.
* **Recent / Favorites** are kept in `~/.config/j2me-deck/library-state.properties`.
* **Remove** can also delete that game's saves.

For Steam: `./make-shortcut.sh --library`, then Desktop Mode -> Steam -> Add a Non-Steam Game -> *J2ME Deck*.
One Steam entry then gives you every game.

### Global settings and global key map

**Global settings** and **Global key mapping** (library *Settings* tab; in a game: **F7** / **Shift+F8**) set the defaults for *every* game:

* **Global Settings** has the same tabs as the per-game dialog (screen size, scaling, window, fonts, device preset, input
  quirks, speed, patches ...) except the per-game JAD overrides. Stored in `~/.config/j2me-deck/global/game.properties`.
* **Global Keymap** is the keyboard and controller map used by every game (`~/.config/j2me-deck/global/keyboard.properties`
  and `gamepad.properties`).

A game's own settings and key map only store what *you changed for that game*; everything else follows the global values,
so changing a global default later also changes every game that did not override it. In a game's dialog, **Use global
settings** (key map: **Use global map**) drops that game's overrides. Applies the next time a game starts (key maps:
immediately). Games you already configured keep their own values as overrides.

### Per-game settings

*Settings...* in the library (stored in `~/.config/j2me-deck/profiles/<game>/game.properties`, next to that game's
key mapping):

| Setting | What it does |
|---|---|
| **Screen size** | The resolution the game *sees* (presets from 128x128 to 640x360, or custom W x H). Games built for one device size usually only work at that size; others adapt. |
| LCD grid shader | Default / Off / Low / Medium / High per game (F9 cycles it in game); profile key `lcdgrid=off|low|medium|high` |
| Scaling | sharp-bilinear (default), nearest, integer, or stretch to fill. F10 cycles it in game and remembers it per game. |
| Window | fullscreen default/always/never, window scale |
| Soft-key bar | `auto` shows a Commands bar only for non-fullscreen canvases that have Commands |
| Network | HTTP/sockets are **off by default** (many old games phone home and hang); tick to allow |
| Mute / pause on focus loss | per game |
| Fonts | pixel size of small/medium/large and family, for games that use system fonts |
| System properties | e.g. `microedition.platform=NokiaN73`; some games choose code paths from this |
| JAD overrides | e.g. `MIDlet-Name=...` or custom `Some-Property=value` read through `getAppProperty` |

Command line: `--size WxH` overrides the profile for one run; `./run.sh --library` forces the library.

## What is supported (more games)

| Area | Support |
|---|---|
| LCDUI high level | `Form`, `List` (all types), `TextBox`, `Alert`, `Ticker`, `StringItem`, `TextField`, `ChoiceGroup`, `Gauge`, `ImageItem`, `DateField`, `Spacer`, commands with a soft-key bar. Navigable with gamepad or keyboard. |
| Game API | complete `Sprite`, `TiledLayer`, `LayerManager`, `GameCanvas` |
| Nokia UI API | `FullCanvas`, `DirectGraphics` / `DirectUtils` (pixel formats, polygons, flips), `DeviceControl`, `Sound` |
| Networking | `HttpConnection`, `HttpsConnection`, `SocketConnection` (per-game opt-in) |
| Files | JSR-75 `FileConnection` / `FileSystemRegistry`, sandboxed per game under `~/.local/share/j2me-deck/fs/` |
| Audio | MIDI, WAV/AU/AIFF, tone sequences (`ToneControl`); MP3/AMR/AAC/ADPCM when `ffmpeg` is installed |
| Storage | RMS with enumerations, shared store instances, per-game folders |
| Not supported | Mascot vendor wrappers (Motorola, J-Phone, Vodafone, JBlend 3D), Bluetooth, SMS/MMS, PIM, location, vendor APIs other than Nokia (Siemens, Samsung, Motorola...) |

The scanner flags unsupported features before you waste time launching a game.

## Device presets, key layouts and compatibility tweaks

*Settings -> Device & compatibility* in the library (or `~/.config/j2me-deck/profiles/<game>/game.properties`):

* **Device preset** (about 25 handsets: Sony Ericsson, Nokia, Motorola, Siemens, LG, Sagem, Sharp, Samsung and generic sizes)
  sets the screen size, the key layout and the platform name the game sees. Games often pick code paths from
  `microedition.platform`, so this is the first thing to try when a game misbehaves.
* **Key layout** matters more than it sounds: Motorola phones sent -20/-21/-22 for fire and the soft keys and unusual
  codes for the d-pad, Siemens used -59..-62, LG -202/-203. A game built for one of them ignores the Nokia codes this
  loader uses by default. A layout translates the loader's keys into the codes that family really sent, and
  `getKeyCode` / `getGameAction` follow it.
* **Input quirks:** send `keyRepeated` or turn it off, send `keyPressed` on repeats, swap the soft keys, make soft keys
  also reach `keyPressed` when they fire a Command.
* **Drawing quirks:** ignore `setFullScreenMode`, always repaint the whole screen (ignore region repaints), ignore
  `serviceRepaints()`, no automatic repaint when a canvas is shown, wait for each frame to reach the screen,
  override `hasPointerEvents()`.
* **Frame rate limit** and **game speed**, **timing patches** (ignore `System.gc()` and `Thread.sleep()`, make
  `Thread.yield()` sleep), and a **reported heap size** for games that check `Runtime.freeMemory()`.

These work because the loader rewrites calls to `Thread.sleep`, `System.currentTimeMillis`, `System.gc`, `Thread.yield`
and `Runtime.freeMemory/totalMemory/gc` inside the game's own classes; the JDK itself is never affected.

Profile keys: `preset`, `layout`, `platform`, `locale`, `encoding`, `tweak.keyrepeat|keypressonrepeat|swapsoftkeys|softkeyfix|
haspointer|ignorefullscreen|ignoreregionrepaint|ignoreservicerepaints|norepaintonsetcurrent|syncflush`, `fps`, `speed`,
`patch.ignoregc|ignoresleep|yieldsleep`, `heapmb`, `autosize`, `autosizeheight`.

**Auto-size:** *Match the display's aspect ratio at height N* gives games that adapt to their screen a canvas with the
Deck's proportions instead of a portrait phone shape.

## 3D games

Two 3D APIs are implemented in pure Java (no OpenGL, no native libraries), so they run anywhere the loader does:

* **Mascot Capsule v3** (`com.mascotcapsule.micro3d.v3`): a pure-Java software renderer; its provenance and license terms are listed in
  `THIRD_PARTY.md`. Settings: *don't mix with 2D*, *ignore the background*.
* **M3G / JSR-184** (`javax.microedition.m3g`): written for this project. The full public API (all 30 classes, every method
  and constant) plus a software rasteriser: perspective-correct texturing, Gouraud lighting (ambient, directional,
  omni, spot, specular), fog, alpha test, depth test, five blend modes, skinning, morphing, keyframe animation,
  ray picking and the `.m3g` file loader. About 390 fps for a textured lit scene at 240x320 on one slow core.

The compatibility scanner now counts both as supported. See `PORTING_STATUS.md` for what is approximated.

## Controller

Besides the d-pad and left stick, **LT, RT and the right stick** (four directions) are mappable in the key mapper, like
any button. `gamepad.properties` also accepts `stick.threshold`, `trigger.threshold` (0.1 to 0.9 of full travel),
`stick.invertx`, `stick.inverty`, `rstick.invertx`, `rstick.inverty`. Back paddles appear as extra controller buttons
when Steam Input exposes them as buttons 11 to 15.

## Library extras

* **Show log** keeps the output of the last two runs of each game (`~/.local/share/j2me-deck/logs/`), useful in Game
  Mode where there is no terminal.
* Suites with **several MIDlets** ask which one to start.
* Two different games with the same MIDlet-Name no longer share settings or key mappings.
* **`lib/`**: any `.jar` you drop there joins the class path of the loader and of the games. Put an MP3 decoder for Java
  Sound (for example mp3spi + jlayer) there to play MP3 without ffmpeg. They are not bundled because of their licenses.

## In-game key mapper

Press **F8** while the game window is focused to open the Key Mapper. It has two tabs:

- **Keyboard:** click a Nokia key, then press the physical key to assign it.
- **Controller:** choose which Nokia key each of the 16 controller buttons sends.

**Shift+F8** (or *Settings -> Global key mapping* in the library) opens the same mapper for the global map that every game starts from; a game's map only stores the bindings that differ from it. If you bind one key to two actions the mapper warns you (only one of them would work). Click **Save** to apply the mappings immediately. Profiles are saved per game under `~/.config/j2me-deck/profiles/<game-name>/`, so Diamond Rush and Splinter Cell can use different layouts. **Reset defaults** restores the built-in bindings. Keyboard mappings include directions, Fire, both soft keys, Clear, Send, End, digits 0-9, `*` and `#`.

## OpenGL ES output (lower power)

On the desktop the game picture goes to the screen through **OpenGL ES 2** by default (in Steam Game Mode `auto` currently keeps the Swing renderer; use `--renderer gles` to try GL there) (EGL + GLES from Mesa, which SteamOS has), not through Java2D.
The game still draws with Java2D into its framebuffer, exactly as before, so compatibility does not change. What moves to the GPU is
everything after that: uploading the frame as a texture, scaling (sharp-bilinear, nearest, integer, stretch), the LCD grid, the toast and
pause tags, and presenting with vsync. This is what saves power: the CPU no longer scales every frame, and a static picture (a paused game,
a menu) costs nothing because the render thread sleeps until something changes.

* **No new downloads.** EGL and GLES2 are called straight from Java through the foreign-function API; there is no LWJGL/JOGL.
* **Setting:** *Settings -> Display -> Renderer* (global or per game), profile key `renderer`, or `--renderer` on the command line.
  * `auto` (default): use OpenGL ES, and silently keep Swing if it cannot start or stops working.
  * `gles`: same, but the log says so when it had to fall back.
  * `swing`: the previous Java2D path, never touches GL. Use this if the picture is black or wrong on your device.
* **Same window.** The GL surface sits inside the normal window, so keyboard, mouse/touch, controller, fullscreen, F-keys, the soft-key bar,
  forms and dialogs all keep working unchanged. Forms/lists/dialogs are still Swing; only the game canvas is GL.
* **Needs Java 21** (what `setup-java.sh` installs). On 21 the foreign-function API is a preview feature, so `run.sh` and the library start Java
  with `--enable-preview --enable-native-access=ALL-UNNAMED --add-opens ...`. Started without those flags, or on a Java that cannot load the
  classes, the loader logs why and uses Swing. On Java 22+ run `./build.sh` with that JDK (built without the preview flag); this was not tested.
* The log line `OpenGL ES output: EGL 1.5 Mesa ..., <GPU> / OpenGL ES 3.x ...` in `launch.log` / *Show log* confirms the GPU path is in use.
* Looks the same as before: sharp-bilinear uses a sharp-bilinear shader, the LCD grid uses the same strength and thickness rules as the Swing version.
* Debugging: `J2ME_GL_DEBUG=1` prints GL errors and stack traces; `./tools/gltest.sh` runs the GL checks (needs xvfb-run or a display and Mesa).

## Window / display keys

| Key | Action |
|---|---|
| F11 or Alt+Enter | toggle fullscreen |
| F8 / Shift+F8 | open the key mapper for this game / for all games (global map) |
| F7 | open the global settings (defaults for every game; apply when a game starts) |
| F10 | cycle scaling: *sharp-bilinear* (default) -> *nearest* -> *integer* -> *stretch* (saved per game) |
| F3 / F4 | game speed slower / faster (x0.25 ... x8; the clock, sleeps and animations all scale) |
| F5 / F6 | pause / resume the game clock, and single-step one frame while paused |
| F9 | cycle the **LCD grid shader**: off -> low -> medium -> high -> off (dark lines between game pixels; needs 2x scaling or more; saved per game) |
| F12 | screenshot to `~/Pictures/j2me-screenshots/` |

The window pauses the game (`hideNotify`) if it loses focus, e.g. when you open the Steam overlay, and resumes when you
return. Disable with `--no-autopause`.

## Options

```
./run.sh [--library] [--fullscreen|--windowed] [--scale N] [--size WxH] [--renderer auto|gles|swing] [--midlet N] [--no-autopause] [game.jar] [midlet.class]
```
Game saves (RMS) live in `~/.local/share/j2me-deck/rms/<vendor>_<game>/`, one folder per game.
Music: Java's built-in synthesizer is used. For nicer instruments drop a SoundFont at
`~/.config/j2me-deck/soundfont.sf2`.

## Troubleshooting

* **Does not start in Game Mode (nothing appears)** - read `~/.local/share/j2me-deck/launch.log`. Make sure the Steam entry
  points at `j2me-deck` with Compatibility *unticked*, Java is installed (`./setup-java.sh`), and the folder is on internal
  storage.
* **Black or garbled picture with the new OpenGL ES output** - set *Renderer* to `swing` (or start with `--renderer swing`) and look at the log for the `OpenGL ES output` line.
* **Black window / no keyboard input** - click the window once. In Game Mode make sure the shortcut launches `j2me-deck` or
  `run.sh` (not `java` directly), which set the window-manager workaround gamescope needs.
* **Controller does nothing** - check `ls /dev/input/js*`. If the device has another name, start with
  `J2ME_JOYSTICK=/dev/input/by-id/<name>-joystick ./run.sh game.jar`. `J2ME_DEBUG_INPUT=1` prints every key event.
* **No sound** - run from a terminal and look for a "MIDI synthesizer unavailable" line; it means Java found no audio
  device. The game keeps running silently.
* **Sound missing or odd** - `J2ME_DEBUG_AUDIO=1 bash run.sh game.jar` logs every player start/stop/end-of-media (lines starting `[audio]`) to the terminal / launch log.
* **Music very quiet** - quiet MIDI tracks are lifted automatically (`run.sh` passes the needed
  `--add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED`). `midiboost.target=-14` is louder, `midiboost=false` turns it off.
* **Game says it needs a different screen size** - try `--size 176x208`, `--size 240x320` or `--size 320x240`.

## Building / testing

```bash
./build.sh              # needs a JDK 17+ (JDK 21 for the OpenGL ES output); produces build/J2ME-Deck.jar
./tools/selftest.sh     # headless checks: graphics, sprites, key states, scaling, networking, files, tones, Nokia API, scanner, LCDUI screens
tools/headless-run.sh game.jar "wait 4000;shot /tmp/a.png;tap FIRE;wait 800;shot /tmp/b.png;exit"
```
The last command runs a game with no display and writes screenshots, handy for regression testing.

## Known limitations (0.1.0)

* Verified on a limited set of games only; many titles are untested. The library's compatibility badge is a static scan, not a guarantee.
* Vendor APIs other than Nokia UI (Siemens, Samsung, Motorola, J-Phone/Vodafone wrappers), Bluetooth, SMS/MMS, PIM and location are not implemented.
* The settings form, key mapper and file chooser are regular Swing dialogs and are less touch-friendly than the rest of the library.
* Game Mode: the OpenGL ES renderer is opt-in there (`--renderer gles`); some Game Mode behaviour could only be verified on one device.
* Per-game audio behaviour varies; if music is missing in a game, try `J2ME_DEBUG_AUDIO=1` and attach the log to a report.

## Reporting problems

Open an issue and attach `~/.local/share/j2me-deck/launch.log` and the game's log from `~/.local/share/j2me-deck/logs/` (or use
*Options -> Show log* in the library). Please say which game you tried, which device preset/settings you used, and whether you run
Desktop Mode or Game Mode. Do **not** attach game files.

## Building and releasing

`./build.sh` builds `build/J2ME-Deck.jar`; `./tools/selftest.sh` runs the automated checks; `./tools/package.sh` creates the release
archives in `dist/` (jar + native launcher + scripts). See `CHANGELOG.md` for the version history.

## Credits and licenses

J2ME Deck is licensed under the **Apache License, Version 2.0** (see `LICENSE` and `NOTICE`).

* **[J2ME-Loader](https://github.com/nikita36078/J2ME-Loader)** by Nikita Shakarun (Apache-2.0), itself a fork of J2meLoader by
  Nickolay Savchenko, is the project this one is based on. J2ME Deck is an independent Linux/SteamOS desktop implementation; it is
  not affiliated with or endorsed by the J2ME-Loader authors.
* **JL-Mod / Mascot Capsule support** by woesss (Yury Kharchenko) and the Mascot Capsule software renderer work by Roman Lahin
  (MascotME) - see `THIRD_PARTY.md` for exactly which files and under which terms.
* *Steam Deck*, *SteamOS* and *Steam* are trademarks of Valve Corporation; *Java* and *Java ME* are trademarks of Oracle. This project
  is not affiliated with Valve, Oracle, Nokia, Sony Ericsson, Gameloft or any other game or device vendor; those names are used only to
  describe compatibility. Games such as Diamond Rush belong to their respective owners.
