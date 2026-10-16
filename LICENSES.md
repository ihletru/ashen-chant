# Licensing

## This app

**GNU General Public License v3.0 or later.** The app links the GZDoom engine,
which is GPL-3.0-or-later, so the combined work is distributed under the same
terms. The full text is in `app/src/main/jni/engine/gzdoom/COPYING` once the
engine submodule is checked out.

Selling a GPL-licensed program is permitted. What the licence requires is that
anyone who receives a binary can also get this source, and that no further
restriction is placed on their right to modify and redistribute it.

## Bundled game data

| What | Licence | Text |
|---|---|---|
| Blasphemer v0.1.8 — the game data shipped in `app/src/main/assets/blasphem.wad` | BSD 3-Clause | `licenses/Blasphemer-BSD-3.txt` |
| Noto fonts, inside `uzdoom.pk3` | SIL Open Font License 1.1 | with the engine sources |

The original `HERETIC.WAD` is **not** included and is not required — the app
runs on the bundled data, and a player who owns the original can import it.

## Engine and vendored libraries

Each of these ships its own licence text alongside its source; the paths below
are where to find it in a checked-out tree.

| Component | Licence | Text |
|---|---|---|
| GZDoom / UZDoom engine (`app/src/main/jni/engine/gzdoom`) | GPL-3.0-or-later | `COPYING` |
| TouchControls, by Emile Belanger (`MobileTouchControls`) | GPL-2.0 | `License.txt` |
| SDL2 and SDL2_net (`SDL2_OpenTouch`) | zlib | `COPYING.txt` |
| OpenAL (`AudioLibs_OpenTouch/openal`) | LGPL-2.0 | `COPYING` |
| FluidSynth lite (`AudioLibs_OpenTouch/fluidsynth-lite`) | LGPL-2.0 | `COPYING` |
| libsndfile (`AudioLibs_OpenTouch/libsndfile-android`) | LGPL-2.0 | `COPYING` |
| FLAC (`AudioLibs_OpenTouch/android_external_flac`) | BSD-style | `MODULE_LICENSE_BSD_LIKE` |
| libvpx, libjpeg, and the rest of the vendored tree | see each directory | per-directory |

The LGPL components are built as **shared libraries** (`.so`), which is what the
LGPL requires to keep the relinking right intact. If you change that — for
example by switching any of them to a static build — you change the licence
obligations of the whole app.

## Icons and graphics

The touch control icons and the launcher artwork were produced for this project.
The `store/` directory holds the material used on the Google Play listing.

## Trademarks

`HERETIC` is a trademark of its respective owner. This project is not affiliated
with, sponsored by, or endorsed by them.
