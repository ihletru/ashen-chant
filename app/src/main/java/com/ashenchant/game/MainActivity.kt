package com.ashenchant.game

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ashenchant.game.assets.EngineAssets
import com.ashenchant.game.game.LaunchContract
import com.ashenchant.game.ui.HereticTheme
import com.ashenchant.game.ui.LauncherScreen
import com.ashenchant.game.ui.LauncherUiState
import com.ashenchant.game.wad.WadImporter
import com.ashenchant.game.wad.WadScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launcher (proces główny). Compose UI: import WADa, rozpakowanie zasobów
 * silnika, launch gry w osobnym procesie :game.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        HereticSettings.reloadSettings(applicationContext)

        setContent {
            HereticTheme {
                val baseDir = remember { HereticSettings.baseDir(this@MainActivity) }
                var state by remember {
                    mutableStateOf(
                        LauncherUiState(
                            baseDir = baseDir,
                            vibrate = HereticSettings.getBoolOption(this@MainActivity, HereticSettings.KEY_VIBRATE, true),
                        )
                    )
                }
                val scope = rememberCoroutineScope()

                // Skan + rozpakowanie zasobów; ponów po powrocie z gry
                // (import mógł się zmienić w innym procesie).
                fun refresh() {
                    // ⚠️ Prefsy czytamy od nowa: suwak czułości w grze (proces :game)
                    // pisze ten sam plik, więc po powrocie z gry stan w pamięci jest
                    // nieaktualny (pisze go proces :game).
                    HereticSettings.reloadSettings(this@MainActivity)
                    state = state.copy(
                        vibrate = HereticSettings.getBoolOption(this@MainActivity, HereticSettings.KEY_VIBRATE, true),
                    )
                    scope.launch {
                        val wads = withContext(Dispatchers.IO) { WadScanner.scan(baseDir) }
                        val ready = withContext(Dispatchers.IO) { EngineAssets.isInstalled(baseDir) }
                        state = state.copy(
                            wads = wads,
                            // Kolejność: własny HERETIC.WAD gracza → pakietowy
                            // Blasphemer (gra działa od razu) → cokolwiek innego.
                            selectedWad = wads.firstOrNull { it.name.equals("HERETIC.WAD", ignoreCase = true) }
                                ?: wads.firstOrNull { it.isBundled }
                                ?: wads.firstOrNull(),
                            assetsReady = ready,
                        )
                        if (!ready) {
                            state = state.copy(unpacking = "engine data")
                            try {
                                withContext(Dispatchers.IO) {
                                    EngineAssets.ensureInstalled(this@MainActivity, baseDir) { name ->
                                        // Postęp w UI — z wątku IO wraca się przez launch(Main).
                                        scope.launch { state = state.copy(unpacking = name) }
                                    }
                                }
                                state = state.copy(unpacking = null, assetsReady = true)
                            } catch (e: Exception) {
                                state = state.copy(
                                    unpacking = null,
                                    error = "Engine data unpack failed: ${e.message}",
                                )
                            }
                        }
                    }
                }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

                val pickWad = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        try {
                            val dest = withContext(Dispatchers.IO) {
                                WadImporter.import(this@MainActivity, uri, baseDir)
                            }
                            Toast.makeText(
                                this@MainActivity,
                                "Imported ${dest.name}",
                                Toast.LENGTH_SHORT,
                            ).show()
                            refresh()
                        } catch (e: Exception) {
                            state = state.copy(error = "Import failed: ${e.message}")
                        }
                    }
                }

                LauncherScreen(
                    state = state,
                    onImportWad = { pickWad.launch(WadImporter.PICKER_MIME_TYPES) },
                    onPlay = {
                        val wad = state.selectedWad ?: return@LauncherScreen
                        startActivity(LaunchContract.gameIntent(this@MainActivity, wad, baseDir))
                    },
                    onSelectWad = { wad -> state = state.copy(selectedWad = wad) },
                    onVibrateChange = { v ->
                        HereticSettings.setBoolOption(this@MainActivity, HereticSettings.KEY_VIBRATE, v)
                        state = state.copy(vibrate = v)
                    },
                    // ⚠️ Ustawienia sterowania zmienia WYŁĄCZNIE panel „SET"
                    // w grze — launcher ich nie dotyka (decyzja Milosza
                    // 15.10.2026). Tu zostaje sam launcher.
                )
            }
        }
    }
}
