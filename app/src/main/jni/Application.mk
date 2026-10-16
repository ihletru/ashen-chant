# Application.mk — własny stos natywny Heretic.
#
# TEN PLIK JEST NOŚNY. Każda flaga poniżej ma wypisane źródło i skutek zgłoszenia
# bez niej — nie usuwaj niczego bez sprawdzenia w docs/stack-techniczny.md.

APP_ABI      := armeabi-v7a arm64-v8a

# APP_PLATFORM silnika = android-23. minSdk aplikacji (33) jest osobny — to floor
# powłoki Kotlin/Compose, nie silnika.
APP_PLATFORM := android-23

# Wiele bibliotek .so (uzdoom, touchcontrols, saffal, SDL2) -> wspólny STL.
APP_STL := c++_shared

# Wyłączenie ścieżek licencyjnych Delta Touch w Clibs_OpenTouch (platny check
# w sklepie Play — nasz produkt jest płatny, licencja nie jest potrzebna).
APP_CFLAGS   += -DNO_SEC
APP_CPPFLAGS += -DNO_SEC

# NDK r27 (budujemy na 27.0.12077973) clang podnosi starsze diagnostyki do błędów;
# vendorowany kod C z lat 2010 (openal, sndfile, mpg123, jpeg) je triggeruje.
APP_CFLAGS += -Wno-error=incompatible-function-pointer-types \
              -Wno-error=incompatible-pointer-types \
              -Wno-error=implicit-function-declaration \
              -Wno-error=int-conversion \
              -Wno-error=implicit-int \
              -Wno-error=return-type

# Wyrównanie segmentów LOAD .so do 16 KB — wymóg urządzeń ze stronami 16 KB
# (Android 15+). Razem z packaging { jniLibs { useLegacyPackaging = false } }
# w app/build.gradle.kts. Bramka: check-16k-alignment.py (wzorzec z Verbigem).
APP_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384

# GZDoom rejestruje CVARy/klasy/akcje we WŁASNYCH sekcjach ELF (areg/creg/vreg/...),
# po których silnik chodzi przez symbole __start_*/__stop_*. Nowoczesne lld przy
# domyślnym --gc-sections uznaje te symbole za nieożywiające i WYCINA cicho całą
# rejestrację — silnik pada przy pierwszym dostępie do CVARa, a wygląda to jak błąd
# kodu. Wyłączone tu.
APP_LDFLAGS += -Wl,-z,nostart-stop-gc

# ⚠️ WINDOWS: link libuzdoom.so podaje setki plików .o w linii poleceń i przekracza
# limit CreateProcess (32k znaków) — make pada z "e=87: parametr jest niepoprawny",
# co WYGLĄDA jak błąd kodu, a jest błędem narzędzia. Z tą flagą NDK zapisuje listę
# plików do linker.list i woła linker przez @plik. Bez tego build się nie kończy.
APP_SHORT_COMMANDS := true
