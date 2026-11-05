package sfg.wseminar.rover.computation.wire

import sfg.wseminar.rover.algorithms.backend.SimulationLimits
import sfg.wseminar.rover.graph.generator.*

fun PacketWriter.write(value: SimulationLimits) {
    int(value.maxFrames)
    long(value.maxEstimatedBytes)
    bool(value.frameLimitEnabled)
    bool(value.timeLimitEnabled)
    long(value.maxDurationMillis)
    bool(value.enabled)
}
fun PacketReader.readSimulationLimits() = SimulationLimits(
    maxFrames = int(),
    maxEstimatedBytes = long(),
    frameLimitEnabled = bool(),
    timeLimitEnabled = bool(),
    maxDurationMillis = long(),
    enabled = bool(),
)

fun PacketWriter.write(value: GenerationBudget) {
    long(value.maxWorkOperations)
    int(value.maxCandidateEdges)
    int(value.maxEdges)
    bool(value.workLimitEnabled)
    bool(value.timeLimitEnabled)
    long(value.maxDurationMillis)
    bool(value.enabled)
}
fun PacketReader.readGenerationBudget() = GenerationBudget(
    maxWorkOperations = long(),
    maxCandidateEdges = int(),
    maxEdges = int(),
    workLimitEnabled = bool(),
    timeLimitEnabled = bool(),
    maxDurationMillis = long(),
    enabled = bool(),
)

fun PacketWriter.write(value: GraphGenerationOptions) {
    int(value.mode.ordinal)
    int(value.gridColumns)
    int(value.gridRows)
    int(value.nodeCount)
    double(value.density)
    bool(value.planar)
    double(value.connectionRadius)
    bool(value.includeGridDiagonals)
    bool(value.allowDisconnected)
    bool(value.allowNodeOverlap)
    double(value.nodeClearanceMultiplier)
    double(value.nodeSpacing)
    double(value.weightJitter)
    double(value.extremeWeightChance)
    double(value.extremeWeightFactor)
    double(value.cheapWeightShare)
    int(value.endpointPlacement.ordinal)
    double(value.startX)
    double(value.startY)
    double(value.goalX)
    double(value.goalY)
    bool(value.bidirectional)
    int(value.seed)
    write(value.budget)
    write(value.terrain)
}
fun PacketReader.readGraphGenerationOptions() = GraphGenerationOptions(
    mode = GraphGenerationMode.entries[int()],
    gridColumns = int(),
    gridRows = int(),
    nodeCount = int(),
    density = double(),
    planar = bool(),
    connectionRadius = double(),
    includeGridDiagonals = bool(),
    allowDisconnected = bool(),
    allowNodeOverlap = bool(),
    nodeClearanceMultiplier = double(),
    nodeSpacing = double(),
    weightJitter = double(),
    extremeWeightChance = double(),
    extremeWeightFactor = double(),
    cheapWeightShare = double(),
    endpointPlacement = EndpointPlacement.entries[int()],
    startX = double(),
    startY = double(),
    goalX = double(),
    goalY = double(),
    bidirectional = bool(),
    seed = int(),
    budget = readGenerationBudget(),
    terrain = readTerrainSettings(),
)


fun PacketWriter.write(value: sfg.wseminar.rover.terrain.TerrainSettings) {
    bool(value.enabled); int(value.model.ordinal)
    double(value.alpha)
    double(value.beta)
    double(value.referenceLength)
    double(value.mapWidthMetres)
    double(value.cellSizeMetres)
    double(value.elevationAmplitude)
    double(value.terrainWavelength)
    double(value.roughnessAmplitude)
    double(value.stepAmplitude)
    double(value.soilSlip)
    double(value.slopeSlip)
    double(value.maxSlopeDegrees)
    double(value.maxRoughness)
    double(value.maxStep)
    double(value.maxSlip)
    int(value.samplesPerEdge)
}
fun PacketReader.readTerrainSettings() = sfg.wseminar.rover.terrain.TerrainSettings(
    enabled = bool(), model = sfg.wseminar.rover.terrain.CostModel.entries[int()],
    alpha = double(),
    beta = double(),
    referenceLength = double(),
    mapWidthMetres = double(),
    cellSizeMetres = double(),
    elevationAmplitude = double(),
    terrainWavelength = double(),
    roughnessAmplitude = double(),
    stepAmplitude = double(),
    soilSlip = double(),
    slopeSlip = double(),
    maxSlopeDegrees = double(),
    maxRoughness = double(),
    maxStep = double(),
    maxSlip = double(),
    samplesPerEdge = int()
).also { it.validate() }
