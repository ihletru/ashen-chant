package com.ashenchant.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ashenchant.game.R
import com.ashenchant.game.wad.WadEntry

/** Stan launchera renderowany przez [LauncherScreen]. */
data class LauncherUiState(
    val baseDir: String = "",
    val wads: List<WadEntry> = emptyList(),
    val selectedWad: WadEntry? = null,
    val assetsReady: Boolean = false,
    val unpacking: String? = null,
    val error: String? = null,
    val vibrate: Boolean = true,
    // ⚠️ Ustawień sterowania tu NIE MA i nie będzie (Milosz 15.10.2026:
    // „skoro controls masz w grze to po co o nie w launcherze walczysz?").
    // Jedyne miejsce do ich zmiany to panel „SET" w grze — jedno źródło prawdy,
    // bez dwóch ekranów, które trzeba trzymać w zgodzie.
)

@Composable
fun LauncherScreen(
    state: LauncherUiState,
    onImportWad: () -> Unit,
    onPlay: () -> Unit,
    onSelectWad: (WadEntry) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // ⚠️ Bez tego przycisk „Play" wchodzi w strefę gestów Androida
            // i tapnięcie kończy się powrotem do ekranu głównego (złapane
            // 15.10.2026 na urządzeniu). Insety bezpieczne trzymają UI w kadrze.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            // ⚠️ Odstępy 12 dp, nie 16 — zmierzone: przy 16 dp zawartość była
            // o ~260 px wyższa od okna i przycisk „Play" przykrywał „Controls".
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.launch_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.launch_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- dane silnika ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Stan w JEDNYM wierszu z etykietą (prośba Milosza 15.10.2026) —
                // karta jest o połowę niższa, więc „Play" zostaje w kadrze.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Engine data",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    when {
                        state.unpacking != null -> {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp).width(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Preparing ${state.unpacking}…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.assetsReady -> Text(
                            text = "Ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = ReadyGreen,
                        )
                        else -> Text(
                            text = "Missing",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // --- WAD ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.action_import_wad),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.wad_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    if (state.wads.isEmpty()) {
                        Text(
                            text = stringResource(R.string.wad_status_missing),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Column(Modifier.selectableGroup()) {
                            state.wads.forEach { wad ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = wad == state.selectedWad,
                                            onClick = { onSelectWad(wad) },
                                        )
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = wad.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "%.1f MB".format(wad.sizeBytes / 1_048_576.0),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onImportWad) {
                        Text(stringResource(R.string.action_import_wad))
                    }
                }
            }

            // --- ustawienia ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Vibration", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.vibrate,
                            onCheckedChange = onVibrateChange,
                        )
                    }
                    Text(
                        text = "Touch controls are set inside the game: tap " +
                            "SET in the top-right corner, next to MENU.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
            }

            state.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ⚠️ Miejsce na przycisk Play przyklejony do dołu ekranu.
            Spacer(Modifier.height(80.dp))
        }

        // „Play" jest PRZYKLEJONY do dołu, a nie na końcu przewijanej listy:
        // to główna akcja i po dodaniu insetów bezpiecznych wypadała pod
        // krawędzią (trzeba było przewijać, żeby zacząć grę).
        Button(
            onClick = onPlay,
            enabled = state.assetsReady && state.selectedWad != null && state.unpacking == null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Text(
                text = if (state.selectedWad == null || !state.assetsReady)
                    stringResource(R.string.action_play_disabled)
                else stringResource(R.string.action_play),
            )
        }
    }
}
