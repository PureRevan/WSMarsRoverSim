package sfg.wseminar.rover.ui.graph

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.components.IntegerSettingRow
import sfg.wseminar.rover.ui.components.SectionHeader
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.VisualizerState

@Composable
fun SeedRow(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit
) {
    val generation = state.graphGeneration

    SectionHeader(
        title = "Randomness",
        icon = Icons.Outlined.Casino
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IntegerSettingRow(
            label = "Seed",
            value = generation.seed,
            range = 0..99_999,
            modifier = Modifier.weight(1f),
            onValueChange = {
                onEvent(
                    RoverVisualizerEvent.ChangeGraphGeneration(
                        generation.copy(seed = it)
                    )
                )
            }
        )

        IconButton(
            onClick = {
                onEvent(RoverVisualizerEvent.RandomizeGraphGenerationSeed)
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Casino,
                contentDescription = "Randomize seed"
            )
        }
    }
}
