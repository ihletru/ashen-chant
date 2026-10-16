package com.ashenchant.game.game

import android.content.Context
import android.content.Intent
import com.ashenchant.game.HereticSettings
import com.ashenchant.game.assets.EngineAssets
import com.ashenchant.game.wad.WadEntry

/**
 * Kontrakt launchu aktywności gry (proces :game).
 *
 * Argumenty silnika MUSZĄ być zbudowane tak samo jak w stosie referencyjnym
 * (tam ten kontrakt był „byte-identical" z testem złotym):
 *
 *   -iwad <ścieżka> <mody> -savedir <base>/gzdoom_saves
 *   +set fluid_patchset gzdoom.sf2 +set midi_dmxgus 0
 *
 * SDLOpenTouch dokleja jeszcze +set vid_preferbackend/vid_fullscreen/vid_def*.
 */
object LaunchContract {

    const val EXTRA_ARGS = "args"
    const val EXTRA_GAME_PATH = "game_path"
    const val EXTRA_RES_DIV = "res_div"

    /**
     * Buduje argumenty dla wybranego IWADa. [extraArgs] to pole na przyszłe
     * argumenty z UI (póki puste).
     */
    fun buildArgs(wad: WadEntry, baseDir: String, extraArgs: String = ""): String =
        buildString {
            append(wad.iwadArgs)
            append(' ')
            // Tablica tekstów interfejsu (lump LANGUAGE) — bez tego menu pokazuje
            // surowe nazwy (MNU_NEWGAME). Silnik nie ładuje dowolnych pk3
            // z katalogu gry, więc podajemy plik wprost. Patrz EngineAssets.
            append("-file $baseDir/${EngineAssets.LANGUAGE_PK3} ")
            append(extraArgs.trim())
            if (extraArgs.isNotBlank()) append(' ')
            append("-savedir $baseDir/gzdoom_saves ")
            append("+set fluid_patchset gzdoom.sf2 ")
            append("+set midi_dmxgus 0 ")
            // ⚠️ TYMCZASOWO (16.10.2026) — podbite na czas robienia zrzutów do karty
            // Sklepu. Gra jest bardzo ciemna, a suwak jasności w menu silnika ustawia
            // PODŚWIETLENIE EKRANU, nie obraz, więc na zrzucie nie widać różnicy.
            // `vid_whitepoint` to pozycja „Brightness" z Display Options i działa
            // na obraz. DO DECYZJI: zostawić jako domyślną dla telefonów czy zdjąć.
            append("+set vid_whitepoint 0.4 ")
        }

    /** Tworzy Intent uruchamiający grę w osobnym procesie :game. */
    fun gameIntent(context: Context, wad: WadEntry, baseDir: String): Intent {
        HereticSettings.reloadSettings(context)
        return Intent(context, GameActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(EXTRA_RES_DIV, HereticSettings.getIntOption(context, HereticSettings.KEY_RES_DIV, 1))
            putExtra(EXTRA_GAME_PATH, baseDir)
            putExtra(EXTRA_ARGS, buildArgs(wad, baseDir))
        }
    }
}
