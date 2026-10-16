package com.ashenchant.game.wad

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ashenchant.game.EngineUtils
import java.io.File
import java.io.IOException

/**
 * Import HERETIC.WAD przez SAF (Storage Access Framework).
 *
 * Gracz wybiera plik, my kopiujemy go do katalogu bazy. Nie potrzebujemy
 * żadnych uprawnień magazynowych (katalog specyficzny dla aplikacji).
 */
object WadImporter {

    private const val TAG = "WadImporter"

    /**
     * Mime typy dla pickera SAF — ActivityResultContracts.OpenDocument(input).
     * WAD-y nie mają sensownego mime; dajemy ogólny, picker i tak filtruje po nazwie.
     */
    val PICKER_MIME_TYPES = arrayOf("*/*")

    /**
     * Nazwa pliku z uri SAF (DisplayName). Fallback: ostatni segment ścieżki.
     */
    fun queryDisplayName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryDisplayName failed", e)
        }
        if (name.isNullOrBlank()) name = uri.lastPathSegment?.substringAfterLast('/')
        return name?.takeIf { it.isNotBlank() }
    }

    /**
     * Kopiuje wybrany plik do <baseDir>/<normalizedName>. Zwraca cel lub
     * rzuca IOException. Nazwę normalizujemy do wielkich liter z rozszerzeniem
     * .WAD (HERETIC.WAD — kontrakt z dokumentacją produktu).
     */
    @Throws(IOException::class)
    fun import(context: Context, uri: Uri, baseDir: String): File {
        val raw = queryDisplayName(context, uri)
            ?: throw IOException("Cannot read file name from the picked document")
        val normalized = normalizeWadName(raw)
        val dest = File(baseDir, normalized)

        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { out ->
                EngineUtils.copyFile(input, out)
            }
        } ?: throw IOException("Cannot open picked document")

        Log.i(TAG, "imported $raw -> ${dest.absolutePath} (${dest.length()} bytes)")
        return dest
    }

    /**
     * HERETIC.WAD / HERETIC.wad / heretic.wad → HERETIC.WAD.
     * Inne nazwy zostają jak są (gracz może mieć inne nazwy plików).
     */
    fun normalizeWadName(raw: String): String {
        val base = raw.substringBeforeLast('.', raw)
        val ext = raw.substringAfterLast('.', "WAD")
        return if (base.equals("heretic", ignoreCase = true)) {
            "HERETIC.$ext".uppercase()
        } else {
            raw
        }
    }
}
