package sfg.wseminar.rover.ui.graph

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import sfg.wseminar.rover.ui.components.RoverFilterChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import sfg.wseminar.rover.graph.generator.EndpointPlacement
import sfg.wseminar.rover.ui.components.ChipGroup
import sfg.wseminar.rover.ui.components.DecimalSliderRow
import sfg.wseminar.rover.ui.components.SectionHeader
import sfg.wseminar.rover.ui.states.GenerationPreset
import sfg.wseminar.rover.ui.states.GraphGenerationState
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun GenerationPresetPicker(state: GraphGenerationState, onChange: (GraphGenerationState) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val preset = GenerationPreset.entries.firstOrNull { it.applyTo(state) == state }
    SectionHeader("Scenario", Icons.Outlined.AutoAwesome)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(preset?.label ?: "Custom scenario · choose a preset")
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GenerationPreset.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                    onChange(option.applyTo(state))
                    expanded = false
                })
            }
        }
    }
    Hint(preset?.description ?: "Mix the controls below, or choose a starting point. Presets keep your map size, space and seed.")
}

@Composable
fun ScenarioGenerationControls(state: GraphGenerationState, onChange: (GraphGenerationState) -> Unit) {
    SectionHeader("Start and goal", Icons.Outlined.Flag)
    ChipGroup {
        EndpointPlacement.entries.filter { state.advanced || it != EndpointPlacement.CUSTOM || state.endpointPlacement == it }
            .forEach { placement ->
                RoverFilterChip(selected = state.endpointPlacement == placement,
                    onClick = { onChange(state.copy(endpointPlacement = placement)) },
                    label = { Text(placement.label()) })
            }
    }
    Hint(when (state.endpointPlacement) {
        EndpointPlacement.OPPOSITE_CORNERS -> "Cross the map diagonally, as in the original examples."
        EndpointPlacement.CENTER_TO_EDGE -> "Begin near the center and head right; part of the map lies behind the start."
        EndpointPlacement.NEARBY -> "A shorter trip surrounded by nodes that need not be on the route."
        EndpointPlacement.RANDOM -> "Pick two different nodes. The same seed always gives the same pair."
        EndpointPlacement.CUSTOM -> "Positions snap to the nearest distinct nodes. Left/top is 0%; right/bottom is 100%."
    })
    if (state.endpointPlacement == EndpointPlacement.CUSTOM && state.advanced) {
        FractionSlider("Start · horizontal", state.startX) { onChange(state.copy(startX = it)) }
        FractionSlider("Start · vertical", state.startY) { onChange(state.copy(startY = it)) }
        FractionSlider("Goal · horizontal", state.goalX) { onChange(state.copy(goalX = it)) }
        FractionSlider("Goal · vertical", state.goalY) { onChange(state.copy(goalY = it)) }
    }

    if (state.terrain.enabled) return

    SectionHeader("Terrain costs", Icons.Outlined.Landscape)
    if (!state.advanced) {
        ChipGroup {
            listOf("Gentle", "Varied", "Extreme").forEachIndexed { index, label ->
                val chance = listOf(0.0, 0.2, 0.4)[index]
                val factor = listOf(10.0, 5.0, 30.0)[index]
                RoverFilterChip(
                    selected = state.weightJitter == 0.22 && state.extremeWeightChance == chance &&
                        (index == 0 || (state.extremeWeightFactor == factor && state.cheapWeightShare == 0.5)),
                    onClick = { onChange(state.copy(weightJitter = 0.22,
                        extremeWeightChance = chance, extremeWeightFactor = factor, cheapWeightShare = 0.5)) },
                    label = { Text(label) }
                )
            }
        }
        Hint(if (state.extremeWeightChance == 0.0) {
            "Costs mainly follow edge length, with ±${(state.weightJitter * 100).roundToInt()}% ordinary variation."
        } else {
            "About ${(state.extremeWeightChance * 100).roundToInt()}% of edges get unusual costs, up to ${state.extremeWeightFactor.roundToInt()}× cheaper or more expensive."
        })
    } else {
        DecimalSliderRow("Ordinary variation", state.weightJitter.toFloat(), 0f..0.8f,
            "±${(state.weightJitter * 100).roundToInt()}%") { onChange(state.copy(weightJitter = it.toDouble())) }
        FractionSlider("Chance of an extreme cost", state.extremeWeightChance) {
            onChange(state.copy(extremeWeightChance = it))
        }
        if (state.extremeWeightChance > 0.0) {
            DecimalSliderRow("Extreme cost factor", log10(state.extremeWeightFactor).toFloat(), 0f..2f,
                "${state.extremeWeightFactor.roundToInt()}×") {
                onChange(state.copy(extremeWeightFactor = 10.0.pow(it.toDouble()).roundToInt().toDouble()))
            }
            FractionSlider("Cheap share of extreme edges", state.cheapWeightShare) {
                onChange(state.copy(cheapWeightShare = it))
            }
            Hint("Extreme edges divide or multiply their normal cost by the factor. The remaining ${(100 - state.cheapWeightShare * 100).roundToInt()}% of extreme edges are expensive.")
        }
    }
}

@Composable
private fun FractionSlider(label: String, value: Double, onChange: (Double) -> Unit) {
    DecimalSliderRow(label, value.toFloat(), 0f..1f, "${(value * 100).roundToInt()}%") { onChange(it.toDouble()) }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun EndpointPlacement.label(): String = when (this) {
    EndpointPlacement.OPPOSITE_CORNERS -> "Across map"
    EndpointPlacement.CENTER_TO_EDGE -> "From center"
    EndpointPlacement.NEARBY -> "Nearby"
    EndpointPlacement.RANDOM -> "Random"
    EndpointPlacement.CUSTOM -> "Custom"
}
