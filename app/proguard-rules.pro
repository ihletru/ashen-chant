# Reguły ProGuard/R8 — build release szkieletu (minify wyłączony).
# Silnik ładuje natywne metody po nazwie — nic nie zmieniać nazw w Javie/Kotlinie
# modułu app (shim JNI: org.libsdl.app.NativeLib, org.libsdl.app.SDLOpenTouch,
# com.opentouchgaming.saffal.FileJNI, com.beloko.touchcontrols.*).
