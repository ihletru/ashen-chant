package com.ashenchant.game.wad

import java.io.File

/** Nazwa WAD-a pakietowego (BSD-3, z oficjalnego releasu Blasphemer v0.1.8). */
const val BUNDLED_WAD_NAME = "blasphem.wad"

/** Plik danych gry w katalogu bazy — kandydat na IWAD. */
data class WadEntry(val file: File) {
    val name: String get() = file.name
    val sizeBytes: Long get() = if (file.isFile) file.length() else 0L

    /** Pakietowy WAD dostarczany z apką — gra działa od razu, bez importu. */
    val isBundled: Boolean get() = name.equals(BUNDLED_WAD_NAME, ignoreCase = true)

    /** Etykieta dla UI (apka mówi po angielsku — treści w aplikacji). */
    val displayName: String get() = if (isBundled) "Blasphemer (bundled)" else name

    /** Argument -iwad dla linii poleceń silnika (ścieżka bezwzględna). */
    val iwadArgs: String get() = "-iwad ${file.absolutePath}"
}

/**
 * Pliki silnika, których NIGDY nie pokazujemy jako wybieralne IWAD-y.
 * (Ten sam zestaw co w stosie referencyjnym — wyklucza też brightmaps/lights,
 * które autoloaduje silnik.)
 */
val EXCLUDED_IWAD_FILES = setOf(
    "gzdoom.pk3", "gzdoom_dev.pk3",
    "lights_dt.pk3", "brightmaps_dt.pk3", "lights.pk3", "brightmaps.pk3",
    "game_widescreen_gfx.pk3", "uzdoom.pk3", "uzdoom_game_support.pk3",
    // Tablica tekstów interfejsu — nasz dodatek, nie dane gry.
    "ashen_language.pk3",
)

/**
 * Skanuje katalog bazy pod kątem wybieralnych IWAD-ów
 * (.wad/.pk3/.pk7, minus pliki silnika).
 */
object WadScanner {

    fun scan(baseDir: String): List<WadEntry> =
        File(baseDir).listFiles().orEmpty()
            .filter { it.isFile }
            .filter { f ->
                val name = f.name.lowercase()
                (name.endsWith(".wad") || name.endsWith(".pk3") || name.endsWith(".pk7")) &&
                    name !in EXCLUDED_IWAD_FILES
            }
            .sortedBy { it.name.lowercase() }
            .map { WadEntry(it) }

    /**
     * Czy plik wygląda na HERETIC.WAD oryginału/Blasphemer — rozpoznanie po
     * lumpach, bez uruchamiania silnika (HEXTITLE... w Heretic: TITLE).
     * Skanujemy pierwsze 4 znaki sygnatury i rozmiar (HERETIC.WAD ~14 MB).
     * Na razie test rozmiaru + „IWAD" w nagłówku; pełne iwadinfo robi silnik.
     */
    fun looksLikeHereticWad(file: File): Boolean {
        if (!file.isFile) return false
        if (file.length() < 1024 * 1024) return false
        val head = ByteArray(12)
        file.inputStream().use { it.read(head) }
        val magic = String(head, 0, 4, Charsets.US_ASCII).uppercase()
        // IWAD (oryginalne dane gry) lub PWAD (modyfikacja) — oba ładowalne.
        return magic == "IWAD" || magic == "PWAD"
    }
}
