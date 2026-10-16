package com.ashenchant.game.assets

import android.content.Context
import android.util.Log
import com.ashenchant.game.EngineUtils
import java.io.File
import java.io.IOException

/**
 * Rozpakowanie zasobów silnika przy pierwszym uruchomieniu.
 *
 * Układ katalogu bazy (kontrakt z silnika UZDoom 5.0 __MOBILE__):
 *  - <base>/res/uzdoom.pk3               — BASEWAD (dane samego silnika)
 *  - <base>/res/uzdoom_game_support.pk3  — OPTIONALWAD, niesie też iwadinfo.txt
 *  - <base>/game_widescreen_gfx.pk3      — autoload (szukany przez $PROGDIR)
 *  - <base>/lights.pk3, brightmaps.pk3   — autoload
 *  - <base>/gzdoom.sf2                   — soundfont; fluid_patchset gzdoom.sf2
 *                                         rozwiązuje się względem katalogu gry
 *  - <base>/soundfonts/gzdoom.sf2        — kopia dla menu soundfontów silnika
 *  - <base>/user_files/                  — save'e, ustawienia, log
 *  - <base>/gzdoom_saves/                — -savedir z linii poleceń
 *  - <base>/blasphem.wad                 — pakietowy IWAD (BSD-3, Blasphemer
 *                                         v0.1.8) — gra działa bez importu
 *
 * Kopiowanie jest idempotentne: plik istnieje i ma rozmiar > 0 → pomijamy.
 */
object EngineAssets {

    private const val TAG = "EngineAssets"

    /**
     * Nasz dodatek z tablicą tekstów interfejsu (lump LANGUAGE). Leży w katalogu
     * gry i jest podawany silnikowi przez `-file` — GZDoom NIE ładuje dowolnych
     * pk3 z katalogu gry (autoload obejmuje tylko pliki z listy `iwadinfo`:
     * lights/brightmaps/widescreen), więc samo położenie pliku nie wystarcza.
     *
     * ⚠️ Nazwa mówi „language", ale plik wozi DWA nadpisania:
     *  1. `LANGUAGE` — brakującą tablicę tekstów interfejsu,
     *  2. `graphics/CREDIT.png` — nadpisanie tekstury `CREDIT` z WADu
     *     Blasphemera (patrz docs/stack-techniczny.md „EKRAN INFORMACYJNY").
     * Plików ładuje się PO IWADzie, więc późniejsze wygrywa — dlatego to działa.
     */
    const val LANGUAGE_PK3 = "ashen_language.pk3"

    /** Pliki krytyczne — bez nich silnik nie wstał; błąd kopiowania = błąd launchu. */
    private val REQUIRED = listOf(
        "uzdoom.pk3" to "res",
        "uzdoom_game_support.pk3" to "res",
        "game_widescreen_gfx.pk3" to "",
        "lights.pk3" to "",
        "brightmaps.pk3" to "",
        "gzdoom.sf2" to "",
        "blasphem.wad" to "",
        // ⚠️ Tablica tekstów interfejsu (lump LANGUAGE). Fork NIE MA plików
        // `wadsrc/static/language.csv`, na które wskazują kroki
        // `generate_language_files` w CMake — dlatego w `uzdoom.pk3` jest tylko
        // `language.def` i menu pokazywało surowe nazwy: MNU_NEWGAME, MNU_OPTIONS…
        // Ten pk3 uzupełnia brakujące teksty (1684 wpisy, en + pl).
        // UWAGA: samo położenie pliku NIE wystarcza — silnik dostaje go przez
        // `-file` z LaunchContract (patrz LANGUAGE_PK3).
        LANGUAGE_PK3 to "",
    )

    /** Opcjonalne kopie (menu soundfontów) — błąd jest logowany, nie fatalny. */
    private val OPTIONAL = listOf(
        "gzdoom.sf2" to "soundfonts",
    )

    /**
     * Rozpakowuje brakujące zasoby. Zwraca listę (nazwa → rozmiar) faktycznie
     * skopiowanych plików, żeby UI mogło pokazać postęp.
     *
     * @throws IOException gdy plik krytyczny nie może być skopiowany
     */
    @Throws(IOException::class)
    fun ensureInstalled(context: Context, baseDir: String, onProgress: (String) -> Unit = {}): List<String> {
        val copied = ArrayList<String>()
        for ((name, subdir) in REQUIRED) {
            val destDir = if (subdir.isEmpty()) baseDir else "$baseDir/$subdir"
            val dest = File(destDir, name)
            if (dest.exists() && dest.length() > 0) continue
            onProgress(name)
            EngineUtils.copyAsset(context, name, destDir)
            copied.add(name)
            Log.i(TAG, "unpacked $name -> $destDir")
        }
        for ((name, subdir) in OPTIONAL) {
            val destDir = if (subdir.isEmpty()) baseDir else "$baseDir/$subdir"
            val dest = File(destDir, name)
            if (dest.exists() && dest.length() > 0) continue
            try {
                EngineUtils.copyAsset(context, name, destDir)
                copied.add("$subdir/$name")
            } catch (e: IOException) {
                Log.w(TAG, "optional asset copy failed: $name", e)
            }
        }

        // Katalogi robocze silnika.
        File("$baseDir/user_files").mkdirs()
        File("$baseDir/gzdoom_saves").mkdirs()

        return copied
    }

    /** Czy wszystkie krytyczne zasoby są na miejscu (szybki test bez kopiowania). */
    fun isInstalled(baseDir: String): Boolean =
        REQUIRED.all { (name, subdir) ->
            val dir = if (subdir.isEmpty()) baseDir else "$baseDir/$subdir"
            val f = File(dir, name)
            f.isFile && f.length() > 0
        }
}
