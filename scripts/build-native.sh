#!/bin/bash
# Build natywny stosu Heretic (.so) -> app/src/main/libs/<abi>/ przez ndk-build.
#
# Gradle NIE buduje kodu natywnego — tylko pakuje .so wyprodukowane tutaj
# (app/build.gradle.kts: jniLibs.srcDirs("src/main/libs")).
#
# ⚠️ WINDOWS: NDK nie ma skryptu `ndk-build` (tylko ndk-build.cmd na make) —
# używamy shima scripts/ndk-shim/ndk-build (woła make.exe NDK z SHELL=cmd).
# Bez shima build pada natychmiast, a błąd wygląda jak błąd kodu.
# (źródło: docs/stack-techniczny.md)
set -e

REPO="$(cd "$(dirname "$0")/.." && pwd)"
PROJ="$REPO/app/src/main"

# NDK: env, inaczej najwyższy zainstalowany pod SDK-em.
if   [[ -n "$ANDROID_NDK_HOME" ]]; then NDK_DIR="$ANDROID_NDK_HOME"
elif [[ -n "$ANDROID_NDK_ROOT" ]]; then NDK_DIR="$ANDROID_NDK_ROOT"
else
    SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/sdk}}"
    NDK_DIR=$(ls -d "$SDK_DIR"/ndk/* 2>/dev/null | sort -V | tail -1)
fi
[[ -d "$NDK_DIR" ]] || { echo "ERROR: NDK not found. Set ANDROID_NDK_HOME." >&2; exit 1; }

# Shim ma pierwszeństwo przed systemowym ndk-build (Linux/macOS go mają, Windows nie).
SHIM="$REPO/scripts/ndk-shim/ndk-build"
if [[ -x "$SHIM" ]]; then
    NDK_BUILD="$SHIM"
    echo "Using NDK shim: $SHIM"
else
    NDK_BUILD="$NDK_DIR/ndk-build"
fi
[[ -x "$NDK_BUILD" ]] || chmod +x "$NDK_BUILD" 2>/dev/null || true
[[ -x "$NDK_BUILD" ]] || { echo "ERROR: ndk-build not executable: $NDK_BUILD" >&2; exit 1; }

echo "Using NDK: $NDK_DIR"
JOBS="${JOBS:-$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)}"

NDK_PROJECT_PATH="$PROJ" "$NDK_BUILD" \
    NDK_APPLICATION_MK="$PROJ/jni/Application.mk" \
    -j"$JOBS" "$@"

echo "Done. .so installed under app/src/main/libs/{armeabi-v7a,arm64-v8a}/"
