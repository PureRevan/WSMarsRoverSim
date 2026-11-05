package sfg.wseminar.rover.ui.graph

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.*
import sfg.wseminar.rover.terrain.*
import sfg.wseminar.rover.ui.components.*
import sfg.wseminar.rover.ui.states.*
import kotlin.math.round

@Composable
fun TerrainModelControls(state: GraphGenerationState, onChange: (GraphGenerationState) -> Unit) {
    val p = state.terrain
    fun update(next: TerrainSettings) = onChange(state.copy(terrain = next))
    Text("Generation model", style = MaterialTheme.typography.titleMedium)
    ChipGroup {
        RoverFilterChip(p.enabled, { update(p.copy(enabled = true)) }, label = { Text("Paper model") })
        RoverFilterChip(!p.enabled, { update(p.copy(enabled = false)) }, label = { Text("Standard generator") })
    }
    if (!p.enabled) {
        Text("Original distance-based random weights and scenario presets.", style = MaterialTheme.typography.bodySmall)
        return
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cost function · paper §2.1", style = MaterialTheme.typography.titleSmall)
            ChipGroup {
                RoverFilterChip(p.model == CostModel.DISTANCE, { update(p.copy(model = CostModel.DISTANCE)) }, label = { Text("Distance only") })
                RoverFilterChip(p.model == CostModel.TERRAIN_AND_SLIP, { update(p.copy(model = CostModel.TERRAIN_AND_SLIP)) }, label = { Text("Terrain + slip") })
            }
            FormulaText("c_0(u,v) = √[(x_v − x_u)^2 + (y_v − y_u)^2] / d_0")
            if (p.model == CostModel.TERRAIN_AND_SLIP) FormulaText("c(u,v) = c_0(u,v) [1 + α q_G(u,v) + β q_S(u,v)]")
            FormulaText("C(P) = Σ c(v_i, v_{i+1})")
            Text("Costs are dimensionless. Coordinates used for distance are in metres; qG and qS lie between 0 and 1.", style = MaterialTheme.typography.bodySmall)
            if (p.model == CostModel.TERRAIN_AND_SLIP) {
                ModelSlider("Terrain weight α", p.alpha, 0f..30f) { update(p.copy(alpha = it)) }
                ModelSlider("Slip weight β", p.beta, 0f..30f) { update(p.copy(beta = it)) }
            }
            ModelSlider("Reference distance d₀ (m)", p.referenceLength, 0.1f..10f) { update(p.copy(referenceLength = it)) }
            if (state.mode == SpaceMode.DISCRETE) ModelSlider("Grid cell size (m)", p.cellSizeMetres, 0.1f..10f) { update(p.copy(cellSizeMetres = it)) }
            else ModelSlider("Map width (m)", p.mapWidthMetres, 10f..1000f) { update(p.copy(mapWidthMetres = it)) }
        }
    }
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Hide terrain simulation" else "Terrain simulation and passability")
    }
    if (expanded) {
        Text("Synthetic, seeded terrain—not measured Mars data. Each edge is sampled along its length. Change the seed below to generate another terrain.", style = MaterialTheme.typography.bodySmall)
        FormulaText("q_G = mean(max(slope / slopeLimit, roughness / roughnessLimit, step / stepLimit))")
        FormulaText("q_S = mean(clamp(soil × field + slopeInfluence × slope / slopeLimit, 0, 1))")
        Text("These sampling rules are simulation assumptions for the paper's abstract ratings. Thresholds are checked at every sample; impassable edges are omitted even for distance-only costs. This can leave the goal unreachable.", style = MaterialTheme.typography.bodySmall)
        ModelSlider("Elevation amplitude (m)", p.elevationAmplitude, 0f..30f) { update(p.copy(elevationAmplitude = it)) }
        ModelSlider("Terrain wavelength (m)", p.terrainWavelength, 1f..200f) { update(p.copy(terrainWavelength = it)) }
        ModelSlider("Roughness amplitude (m)", p.roughnessAmplitude, 0f..1f) { update(p.copy(roughnessAmplitude = it)) }
        ModelSlider("Step amplitude (m)", p.stepAmplitude, 0f..1f) { update(p.copy(stepAmplitude = it)) }
        ModelSlider("Soil slip intensity", p.soilSlip, 0f..1f) { update(p.copy(soilSlip = it)) }
        ModelSlider("Slope influence on slip", p.slopeSlip, 0f..1f) { update(p.copy(slopeSlip = it)) }
        HorizontalDivider()
        Text("Impassable at or above", style = MaterialTheme.typography.titleSmall)
        ModelSlider("Slope limit (°)", p.maxSlopeDegrees, 1f..60f) { update(p.copy(maxSlopeDegrees = it)) }
        ModelSlider("Roughness limit (m)", p.maxRoughness, 0.01f..1f) { update(p.copy(maxRoughness = it)) }
        ModelSlider("Step limit (m)", p.maxStep, 0.01f..1f) { update(p.copy(maxStep = it)) }
        ModelSlider("Slip limit", p.maxSlip, 0.01f..1f) { update(p.copy(maxSlip = it)) }
        IntegerSettingRow("Samples per edge", p.samplesPerEdge, 2..256) { update(p.copy(samplesPerEdge = it)) }
        Text("More samples resolve smaller terrain changes but take longer. Sampling can miss hazards between points. Local exploration reveals costs only when sensing nearby connections.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { update(TerrainSettings(model = p.model)) }) { Text("Reset model parameters") }
    }
    HorizontalDivider()
}

@Composable
private fun ModelSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float>, change: (Double) -> Unit) {
    DecimalSliderRow(label, value.toFloat(), range, (round(value * 100) / 100).toString()) { change((round(it * 100) / 100).toDouble()) }
}

/** Native text layout with real subscripts/superscripts, retained in image exports. */
@Composable
private fun FormulaText(formula: String) {
    val translated = sfg.wseminar.rover.i18n.translate(formula, sfg.wseminar.rover.i18n.LocalLanguage.current)
    val expression = remember(translated) { buildAnnotatedString {
        val scripts = Regex("([_^])(?:\\{([^}]+)\\}|([A-Za-z0-9]))")
        var start = 0
        for (m in scripts.findAll(translated)) {
            append(translated.substring(start, m.range.first))
            withStyle(SpanStyle(baselineShift = if (m.groupValues[1] == "_") BaselineShift.Subscript else BaselineShift.Superscript, fontSize = 11.sp)) {
                append(m.groupValues[2].ifEmpty { m.groupValues[3] })
            }
            start = m.range.last + 1
        }
        append(translated.substring(start))
    } }
    androidx.compose.material3.Text(expression, fontFamily = FontFamily.Serif, fontSize = 17.sp,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp))
}
