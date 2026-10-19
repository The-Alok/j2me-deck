# Third-party code and credits

J2ME Deck itself is licensed under the Apache License 2.0 (`LICENSE`). This file lists code that came from elsewhere.

## Mascot Capsule v3 (`src/main/java/com/mascotcapsule/micro3d/v3/`)

A pure-Java software implementation of the Mascot Capsule Micro3D v3 API. These 13 files were taken from the micro3d software
renderer used by the KEmulator "nnmod" project and adapted to this runtime (resource loading, screen size, two settings flags,
reading back the target pixels, removal of a debug accessor). **Each file keeps its original header unchanged**:

| Files | Header in the file |
|---|---|
| `Graphics3D`, `Rasterizer`, `MascotME` | "MIT License", Copyright (c) 2026 Roman Lahin |
| `Figure`, `Texture`, `ActionTable`, `AffineTrans`, `Effect3D`, `FigureLayout`, `Light`, `Loader`, `Util3D`, `Vector3D` | "MIT License", Copyright (c) 2026 Yury Kharchenko (some files also list Roman Lahin) |

Background for readers: the same class set and the Mascot Capsule work of Yury Kharchenko ("woesss") is also part of
[J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) and [JL-Mod](https://github.com/woesss/JL-Mod), both Apache-2.0, and Roman Lahin's
software renderer is published as [MascotME](https://github.com/rmn20/MascotME). `Rasterizer.java` was produced by its author's code
generator, which is not included.

The MIT License named in those headers reads:

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

If you are an author of these files and the attribution or license terms above are wrong or incomplete, please open an issue and it
will be corrected or the files removed.

## M3G / JSR-184 (`src/main/java/javax/microedition/m3g/`)

Written for this project from the public JSR-184 specification. The API signatures and constants (names and values, which the
specification defines) were cross-checked against the KEmulator sources; no KEmulator implementation code was copied.

## J2ME-Loader

The overall design (MIDlet loading, LCDUI/Game API/MMAPI/RMS coverage, per-game settings) follows
[J2ME-Loader](https://github.com/nikita36078/J2ME-Loader), Copyright 2017-2024 Nikita Shakarun, Apache-2.0. See `NOTICE`.

## Everything else

All other code (loader, LCDUI, media, I/O, library GUI, Nokia UI API, device presets, OpenGL ES output) is original to this project
and marked `SPDX-License-Identifier: Apache-2.0`.

## Optional libraries (not bundled)

`lib/` can hold extra jars you download yourself, for example an MP3 decoder for Java Sound (mp3spi / jlayer / tritonus-share, LGPL).
They are not part of this repository or its releases.
