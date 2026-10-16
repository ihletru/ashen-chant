# Master makefile — własny stos natywny Heretic (fork emileb/gzdoom @ uz_5.0_pre
# + vendored biblioteki OpenTouch; ten sam zestaw, który przeszedł zielony build
# referencji 14.10.2026 — nie zmieniaj listy include'ów bez przebudowania).
#
# Układ katalogów jest wbudowany w vendorowane makefile'i:
#  - TOP_DIR = ten katalog; biblioteki wspierające są rodzeństwem (Clibs_OpenTouch,
#    MobileTouchControls, AudioLibs_OpenTouch, SDL2_OpenTouch, SAFFAL, jpeg8d).
#  - Silnik leży o poziom niżej (engine/gzdoom) — dzięki temu ścieżki glue'u
#    ../../../Clibs_OpenTouch/... z engine/gzdoom/src wracą do TOP_DIR.
#
# engine/gzdoom to SUBMODULE -> https://github.com/ihletru/heretic-engine
# (gałąź heretic-main, bazowa uz_5.0_pre + 6 łat).

TOP_DIR := $(call my-dir)
LOCAL_PATH := $(call my-dir)

SDL_INCLUDE_PATHS := $(TOP_DIR)/SDL2_OpenTouch/SDL2-2.0.12/include \
                     $(TOP_DIR)/SDL2_OpenTouch

# --- biblioteki wspierające / third-party (static) ---
include $(TOP_DIR)/Clibs_OpenTouch/Android.mk            # logwritter + JNI glue silnika
include $(TOP_DIR)/Clibs_OpenTouch/libvpx/Android.mk     # vpx_player (prebuilt)
include $(TOP_DIR)/jpeg8d/Android.mk                     # libjpeg
include $(TOP_DIR)/Android_webp.mk                       # webpmux (z engine/gzdoom/libraries/webp)

# --- MobileTouchControls i jej zależności ---
include $(TOP_DIR)/MobileTouchControls/libpng/Android.mk
include $(TOP_DIR)/MobileTouchControls/libzip/Android.mk
include $(TOP_DIR)/MobileTouchControls/sigc++/Android.mk
include $(TOP_DIR)/MobileTouchControls/TinyXML/Android.mk
include $(TOP_DIR)/MobileTouchControls/Android_TouchControls.mk

# --- warstwa dostępu do plików SAF (wspólna .so: silnik + touchcontrols + zmusic) ---
include $(TOP_DIR)/SAFFAL/saffal/src/main/jni/Android.mk

# --- SDL2 (fork emileb, OPENTOUCH_SDL_EXTRA) + SDL2_net ---
include $(TOP_DIR)/SDL2_OpenTouch/SDL2-2.0.12/Android.mk
include $(TOP_DIR)/SDL2_OpenTouch/SDL2_net/Android.mk

# --- audio: OpenAL (source build, backend OpenSL) + kodeki ZMusic ---
include $(TOP_DIR)/AudioLibs_OpenTouch/openal/Android.mk
include $(TOP_DIR)/AudioLibs_OpenTouch/android_external_flac/Android.mk
include $(TOP_DIR)/AudioLibs_OpenTouch/libsndfile-android/jni/Android.mk
include $(TOP_DIR)/AudioLibs_OpenTouch/libmpg123/Android.mk
include $(TOP_DIR)/AudioLibs_OpenTouch/fluidsynth-lite/src/Android.mk

# --- silnik: lzma, bzip2, glslang, zwidget, ZMusic i moduł "uzdoom" ---
include $(TOP_DIR)/engine/gzdoom/mobile/Android.mk
