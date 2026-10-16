# Ashen Chant — a dark fantasy retro FPS for Android

Source code of the Android app **Ashen Chant: Retro FPS**, a paid game on Google Play.

It wraps a fork of the **GZDoom** engine (the `uz_5.0_pre` line) with touch controls
written for phones rather than mapped onto them: two sticks, two fire buttons, a
settings panel you can open without leaving the game, and no native game UI
underneath. The complete game data is bundled, so it plays the moment it is
installed, and a player who owns the original `HERETIC.WAD` can import it instead.

## What is here

| Path | What it is |
|---|---|
| `app/` | The Android app — Kotlin, plus the Java glue for SDL and the touch layer |
| `app/src/main/jni/` | Native build: the engine submodule, SDL2, the touch controls library |
| `app/src/main/assets/` | Game data and engine data shipped inside the APK |
| `scripts/` | Native build script, and an `ndk-build` shim for Windows |
| `store/` | Material used on the Google Play listing |
| `licenses/` | Third-party licence texts |

## Building

See **[BUILD.md](BUILD.md)**. Short version: build the native libraries first
(`bash scripts/build-native.sh`), then the APK or bundle with Gradle. Gradle does
not build the native code; it only packages what the script produced.

## Licensing

The app as a whole is distributed under the **GNU General Public License v3.0 or
later**, because it links the GZDoom engine, which is GPL-3.0-or-later.

Bundled game data, fonts, and the various vendored libraries have their own
licences, all of them compatible and all listed in **[LICENSES.md](LICENSES.md)**.

If you are looking for the corresponding source of the version you installed from
Google Play: this repository is it. See BUILD.md for the exact revisions.

## Trademarks

`HERETIC` is a trademark of its respective owner. This project is not affiliated
with, sponsored by, or endorsed by them. The bundled game data is
[Blasphemer](https://github.com/Blasphemer/blasphemer), an independent,
freely licensed replacement, not the original commercial game data.
