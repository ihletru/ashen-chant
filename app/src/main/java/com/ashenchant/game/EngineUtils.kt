package com.ashenchant.game

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Pomocniki plikowe i argumentowe współdzielone przez launcher i szew SDL
 * (org.libsdl.app.SDLOpenTouch). Wszystko statyczne, bez zależności od UI.
 */
object EngineUtils {

    private const val TAG = "EngineUtils"
    private const val BUFFER_SIZE = 8192

    /**
     * Rozbija linię poleceń silnika na tokeny. Silnik dostaje tablicę argv,
     * więc spacje w ścieżkach łamią ten kontrakt — ścieżki z spacjami trzeba
     * najpierw przenieść/cytyzować (w Heretic trzymamy pliki w katalogu bez spacji).
     */
    @JvmStatic
    fun createArgs(appArgs: String): Array<String> =
        appArgs.split(" ").filter { it.isNotEmpty() }.toTypedArray()

    /**
     * Kopiuje wszystkie PNG z katalogu głównego assets do [dir] (grafika
     * sterowania dotykowego silnika — wywoływane PRZED NativeLib.init).
     * Pojedynczy błąd kopiowania jest logowany i pomijany (grafika sterowania
     * nie jest krytyczna dla startu silnika).
     */
    @JvmStatic
    fun copyPngAssets(ctx: Context, dir: String) {
        val d = File(dir)
        if (!d.exists()) d.mkdirs()

        val files: Array<String> = try {
            ctx.assets.list("") ?: emptyArray()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to get asset file list.", e)
            emptyArray()
        }

        for (filename in files) {
            if (filename.endsWith("png", ignoreCase = true)) {
                try {
                    copyAsset(ctx, filename, dir)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to copy asset file: $filename", e)
                }
            }
        }
    }

    /**
     * Kopiuje pojedynczy plik z assets do [destdir]. Strumienie zawsze zamykane,
     * IOException idzie do wołającego (dla zasobów krytycznych: pk3/sf2/wad).
     */
    @JvmStatic
    @Throws(IOException::class)
    fun copyAsset(ctx: Context, file: String, destdir: String) {
        File(destdir).mkdirs()
        ctx.assets.open(file).use { input ->
            FileOutputStream("$destdir/$file").use { out ->
                copyFile(input, out)
            }
        }
    }

    /** Kopiuje plik już istniejący na dysku (np. z cache SAF) — z nadpisaniem. */
    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(src: File, dest: File) {
        dest.parentFile?.mkdirs()
        src.inputStream().use { input ->
            FileOutputStream(dest).use { out ->
                copyFile(input, out)
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(input: InputStream, out: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
        out.flush()
    }

    /** Rozmiar pliku albo 0 gdy brak. */
    @JvmStatic
    fun sizeOrZero(file: File): Long = if (file.isFile) file.length() else 0L
}
