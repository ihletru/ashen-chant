package com.ashenchant.game

import android.content.Context
import com.beloko.touchcontrols.TouchSettings

/**
 * Ustawienia aplikacji (launcher + proces :game).
 *
 * Jeden plik prefsów "OPTIONS" współdzielony z modułem opentouch-input — stąd
 * delegacja do TouchSettings zamiast drugiego pliku. Proces :game startuje
 * świeżo (brak statycznego stanu), więc getBoolOption czyta WPROST z dysku
 * (getSharedPreferences sam ładowuje plik przy pierwszym dotknięciu).
 */
object HereticSettings {

    /** Klucz włączający haptykę obrażeń (most COMMAND_VIBRATE w SDLOpenTouch). */
    const val KEY_VIBRATE = "vibrate"

    /** Klucz włączającego mapowanie gamepada przez ControlInterpreter. */
    const val KEY_GAMEPAD_ENABLED = "gamepad_enabled"

    /**
     * CZUŁOŚĆ GŁÓWNA rozglądania, w procentach (100 = wartość bazowa). Skaluje
     * OBA kanały — to ją zmienia tymczasowy suwak w grze.
     */
    const val KEY_LOOK_SENS = "look_sens"

    /** Domyślna czułość rozglądania (procent). */
    const val LOOK_SENS_DEFAULT = 100

    /** Zakres suwaka czułości (procent). */
    const val LOOK_SENS_MIN = 10
    const val LOOK_SENS_MAX = 300

    // ---------------------------------------------------------------------
    // Sterowanie — ustawiane na ekranie „Controls" w launcherze.
    // ⚠️ Obrót i pochylenie mają OSOBNE czułości, bo to dwie różne potrzeby:
    // szybkie obracanie się i wolne, precyzyjne celowanie w pionie. Wzorzec
    // z biblioteki sterowania (`turnSensitivity` vs `lookSensitivity`).
    // Czułość efektywna = KEY_LOOK_SENS × (ta osi).
    // ---------------------------------------------------------------------

    /** Czułość OBROTU w poziomie (procent). */
    const val KEY_TURN_SENS = "turn_sens"

    /** Czułość POCHYLENIA w pionie (procent). */
    const val KEY_PITCH_SENS = "pitch_sens"

    /** Odwrócenie pionu (dla przyzwyczajonych do odwrotnego sterowania). */
    const val KEY_INVERT_PITCH = "invert_pitch"

    /** Zwolnienie czułości podczas strzału — pomaga wycelować. */
    const val KEY_PRECISION_SHOOT = "precision_shoot"

    /** Ile procent czułości zostaje przy strzale (procent). */
    const val KEY_PRECISION_SENS = "precision_sens"

    const val PRECISION_SENS_DEFAULT = 30
    const val PRECISION_SENS_MIN = 20
    const val PRECISION_SENS_MAX = 70

    /** Martwa strefa drążka chodzenia (procent promienia). */
    const val KEY_MOVE_DEADZONE = "move_deadzone"

    const val MOVE_DEADZONE_DEFAULT = 15
    const val MOVE_DEADZONE_MAX = 40

    /** Dzielnik rozdzielczości renderowania (1 = pełna). */
    const val KEY_RES_DIV = "gzdoom_res_div"

    /** Ścieżka do katalogu bazy gry (zapisywana przy pierwszym starcie). */
    const val KEY_BASE_PATH = "base_path"

    /** Katalog bazy gry: dane gracza (+ ewentualny fallback na filesDir). */
    fun baseDir(context: Context): String {
        val external = context.getExternalFilesDir(null) ?: context.filesDir
        return "$external/Heretic"
    }

    /**
     * Ustala/odczytuje katalog bazy. Pierwsze uruchomienie zapisuje domyślny.
     * Wywoływać przy starcie launchera i w procesie :game (przed launch).
     */
    @JvmStatic
    fun reloadSettings(context: Context) {
        val base = TouchSettings.getStringOption(context, KEY_BASE_PATH, null)
        if (base == null) {
            TouchSettings.setStringOption(context, KEY_BASE_PATH, baseDir(context))
        }
        // Prefsy samego modułu (gamepad_enabled) — ten sam plik, jedna ścieżka.
        TouchSettings.reloadSettings(context)
    }

    @JvmStatic
    fun getBoolOption(context: Context, key: String, def: Boolean): Boolean =
        TouchSettings.getBoolOption(context, key, def)

    @JvmStatic
    fun setBoolOption(context: Context, key: String, value: Boolean) {
        TouchSettings.setBoolOption(context, key, value)
    }

    @JvmStatic
    fun getIntOption(context: Context, key: String, def: Int): Int =
        TouchSettings.getIntOption(context, key, def)

    @JvmStatic
    fun setIntOption(context: Context, key: String, value: Int) {
        TouchSettings.setIntOption(context, key, value)
    }

    @JvmStatic
    fun vibrateEnabled(context: Context): Boolean =
        getBoolOption(context, KEY_VIBRATE, true)
}
