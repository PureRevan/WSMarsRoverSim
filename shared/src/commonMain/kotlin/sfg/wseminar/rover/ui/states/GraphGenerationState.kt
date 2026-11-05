package sfg.wseminar.rover.ui.states

import sfg.wseminar.rover.graph.generator.GraphGenerationMode
import sfg.wseminar.rover.graph.generator.GraphGenerationOptions
import sfg.wseminar.rover.graph.generator.EndpointPlacement
import sfg.wseminar.rover.graph.generator.GenerationLimits
import kotlin.math.roundToInt
import kotlin.random.Random

@kotlinx.serialization.Serializable
data class GraphGenerationState(
    val terrain: sfg.wseminar.rover.terrain.TerrainSettings = sfg.wseminar.rover.terrain.TerrainSettings(),
    val mode: SpaceMode = SpaceMode.CONTINUOUS,
    val advanced: Boolean = false,
    val sizeFactor: Double = 0.42,
    val density: Double = 0.45,
    val planar: Boolean = true,
    val gridColumns: Int = 6,
    val gridRows: Int = 5,
    val nodeCount: Int = 28,
    val simpleNodeCount: Int? = null,
    val connectionRadius: Double = 0.28,
    val includeGridDiagonals: Boolean = false,
    val allowDisconnected: Boolean = false,
    val allowNodeOverlap: Boolean = false,
    val nodeClearanceMultiplier: Double = 1.0,
    val nodeSpacing: Double = 0.5,
    val weightJitter: Double = 0.22,
    val extremeWeightChance: Double = 0.0,
    val extremeWeightFactor: Double = 10.0,
    val cheapWeightShare: Double = 0.5,
    val endpointPlacement: EndpointPlacement = EndpointPlacement.CENTER_TO_EDGE,
    val startX: Double = 0.5,
    val startY: Double = 0.5,
    val goalX: Double = 0.9,
    val goalY: Double = 0.5,
    val bidirectional: Boolean = true,
    val seed: Int = Random.nextInt(0, 100_000)
) {
    fun resolvedGridColumns(): Int =
        if (advanced) {
            gridColumns
        } else {
            (3 + sizeFactor * 9.0).roundToInt().coerceIn(3, 12)
        }

    fun resolvedGridRows(): Int =
        if (advanced) {
            gridRows
        } else {
            (3 + sizeFactor * 8.0).roundToInt().coerceIn(3, 11)
        }

    fun resolvedNodeCount(): Int =
        if (advanced) {
            nodeCount
        } else {
            simpleNodeCount ?: (10 + sizeFactor * 38.0).roundToInt().coerceIn(10, 48)
        }

    fun toOptions(): GraphGenerationOptions =
        GraphGenerationOptions(
            terrain = terrain,
            mode = when (mode) {
                SpaceMode.DISCRETE -> GraphGenerationMode.GRID
                SpaceMode.CONTINUOUS -> GraphGenerationMode.CONTINUOUS
            },
            gridColumns = resolvedGridColumns(),
            gridRows = resolvedGridRows(),
            nodeCount = resolvedNodeCount(),
            density = density,
            planar = planar,
            connectionRadius = connectionRadius,
            includeGridDiagonals = advanced && includeGridDiagonals,
            allowDisconnected = advanced && allowDisconnected,
            allowNodeOverlap = advanced && allowNodeOverlap,
            nodeClearanceMultiplier = if (advanced) nodeClearanceMultiplier else 1.0,
            nodeSpacing = nodeSpacing,
            weightJitter = weightJitter,
            extremeWeightChance = extremeWeightChance,
            extremeWeightFactor = extremeWeightFactor,
            cheapWeightShare = cheapWeightShare,
            endpointPlacement = endpointPlacement,
            startX = startX,
            startY = startY,
            goalX = goalX,
            goalY = goalY,
            bidirectional = bidirectional,
            seed = seed
        )

    fun requestedNodeCount(): Long = when (mode) {
        SpaceMode.DISCRETE -> resolvedGridColumns().toLong() * resolvedGridRows()
        SpaceMode.CONTINUOUS -> resolvedNodeCount().toLong()
    }

    fun validationMessage(): String? = when {
        mode == SpaceMode.DISCRETE && (resolvedGridColumns() !in 2..GenerationLimits.MaxGridSide ||
            resolvedGridRows() !in 2..GenerationLimits.MaxGridSide) ->
            "Choose 2–${GenerationLimits.MaxGridSide} rows and columns."
        requestedNodeCount() !in 2L..GenerationLimits.MaxNodes.toLong() ->
            "Choose 2–${GenerationLimits.MaxNodes} nodes in total. This map has ${requestedNodeCount()}."
        else -> null
    }

    fun withAdvanced(enabled: Boolean): GraphGenerationState =
        if (enabled && !advanced) copy(
            advanced = true,
            gridColumns = resolvedGridColumns(),
            gridRows = resolvedGridRows(),
            nodeCount = resolvedNodeCount()
        ) else copy(advanced = enabled)
}
