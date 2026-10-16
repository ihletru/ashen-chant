# Building Ashen Chant

Two stages, in this order: **native libraries first**, then the Android package.
Gradle does not compile any C or C++ here — it only packages what the native
build produced.

## What you need

- **JDK 17** (the project is compiled for Java 17)
- **Android SDK** with platform 36 and build-tools for it
- **Android NDK r27** (`27.0.12077973`) — the native build is calibrated against it
- **Gradle 9.4.1** — the wrapper downloads it
- On Windows: **Git Bash** or another POSIX shell, and `make` from the NDK

## 1. Get the sources

```bash
git clone --recurse-submodules <this repository>
cd <this repository>
```

The engine is a **submodule** pointing at `github.com/ihletru/heretic-engine`,
branch `heretic-main`. Without `--recurse-submodules` the native build has
nothing to compile.

## 2. Build the native libraries

```bash
bash scripts/build-native.sh
```

This runs `ndk-build` and writes `app/src/main/libs/<abi>/` for `armeabi-v7a`
and `arm64-v8a`. A clean build of both ABIs takes roughly two hours; after
touching a single native file it is incremental and takes about a minute.

Point it at your NDK with `ANDROID_NDK_HOME` if it is not under `ANDROID_HOME`.
It also needs `local.properties` to point at the SDK:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

⚠️ **Double the backslashes.** A single backslash is eaten by the Java
properties parser, and the failure surfaces as a confusing `Invalid file path`
error from the Android Gradle plugin, which looks like an SDK problem and is not.

## 3. Build the app

```bash
./gradlew :app:assembleDebug          # APK for testing
./gradlew :app:bundleRelease          # AAB for Google Play
```

Both are unsigned in a fresh clone. For a signed release, create
`keystore.properties` in the repository root — it is gitignored on purpose:

```properties
storeFile=app/release-keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the release build still compiles, it is simply not signed.

## Notes on the native side

`app/src/main/jni/Application.mk` carries the flags that make this build work,
each with a comment explaining what breaks without it. Two are worth knowing
about before you change anything:

- **`APP_SHORT_COMMANDS := true`** — linking `libuzdoom.so` passes hundreds of
  object files on one command line and overflows the Windows `CreateProcess`
  limit. The failure looks like a broken parameter from `make`, not like a
  toolchain limit.
- **`-Wl,-z,nostart-stop-gc`** — the engine registers its cvars, classes and
  actions in custom ELF sections reached through `__start_*` / `__stop_*`
  symbols. Modern `lld` treats those as dead and silently drops the whole
  registration, which shows up much later as a crash on the first cvar access.

On Windows the NDK ships no `ndk-build` shell script, only `ndk-build.cmd`.
`scripts/ndk-shim/ndk-build` bridges that; `build-native.sh` prefers it
automatically when it is present.

## Versions this was built with

| | |
|---|---|
| Android Gradle Plugin | 9.2.1 |
| Gradle | 9.4.1 |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.05.01 |
| `compileSdk` / `targetSdk` | 36 |
| `minSdk` | 33 |
| NDK | 27.0.12077973 |
| Engine | `heretic-engine`, branch `heretic-main` (fork of the `uz_5.0_pre` line) |
