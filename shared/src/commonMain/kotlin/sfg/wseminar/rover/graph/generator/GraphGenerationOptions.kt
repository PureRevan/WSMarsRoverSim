package sfg.wseminar.rover.graph.generator

enum class GraphGenerationMode {
    GRID,
    CONTINUOUS
}

enum class EndpointPlacement {
    OPPOSITE_CORNERS, CENTER_TO_EDGE, NEARBY, RANDOM, CUSTOM
}

/** Defaults and structural bounds; budgets may be overridden per generation. */
object GenerationLimits {
    const val MaxWorkOperations = 250_000_000L
    const val MaxCandidateEdges = 1_000_000
    const val MaxEdges = 500_000
    const val MaxNodes = Int.MAX_VALUE
    const val MaxGridSide = Int.MAX_VALUE
}

@kotlinx.serialization.Serializable
data class GenerationBudget(
    val maxWorkOperations: Long = GenerationLimits.MaxWorkOperations,
    val maxCandidateEdges: Int = GenerationLimits.MaxCandidateEdges,
    val maxEdges: Int = GenerationLimits.MaxEdges,
    val workLimitEnabled: Boolean = true,
    val timeLimitEnabled: Boolean = false,
    val maxDurationMillis: Long = 120_000,
    val enabled: Boolean = true
) {
    init { require(maxWorkOperations > 0 && maxCandidateEdges > 0 && maxEdges > 0 && maxDurationMillis > 0) }
}

data class GraphGenerationOptions(
    val terrain: sfg.wseminar.rover.terrain.TerrainSettings = sfg.wseminar.rover.terrain.TerrainSettings(enabled = false),
    val mode: GraphGenerationMode = GraphGenerationMode.GRID,
    val gridColumns: Int = 6,
    val gridRows: Int = 5,
    val nodeCount: Int = 28,
    val density: Double = 0.45,
    val planar: Boolean = true,
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
    val seed: Int = 42,
    val budget: GenerationBudget = GenerationBudget()
)

internal fun checkGenerationBudget(name: String, used: Long, limit: Long, advice: String) {
    fun count(value: Long) = value.toString().reversed().chunked(3).joinToString(",").reversed()
    require(used <= limit) {
        "Graph generation stopped: $name budget exceeded (${count(used)} requested/counted; limit ${count(limit)}). $advice You can also turn on Disable all computation limits in graph generation or Settings."
    }
}
