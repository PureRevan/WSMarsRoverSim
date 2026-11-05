package sfg.wseminar.rover.terrain

import kotlinx.serialization.Serializable
import sfg.wseminar.rover.graph.Graph
import kotlin.math.*
import kotlin.random.Random

@Serializable
enum class CostModel { DISTANCE, TERRAIN_AND_SLIP }

/** Equations (2) and (3) of section 2.1 of the paper. Simulation parameters are illustrative. */
@Serializable
data class TerrainSettings(
    val enabled: Boolean = true,
    val model: CostModel = CostModel.TERRAIN_AND_SLIP,
    val alpha: Double = 10.0,
    val beta: Double = 10.0,
    val referenceLength: Double = 1.0,
    val mapWidthMetres: Double = 100.0,
    val cellSizeMetres: Double = 1.0,
    val elevationAmplitude: Double = 3.0,
    val terrainWavelength: Double = 40.0,
    val roughnessAmplitude: Double = 0.15,
    val stepAmplitude: Double = 0.12,
    val soilSlip: Double = 0.3,
    val slopeSlip: Double = 0.25,
    val maxSlopeDegrees: Double = 30.0,
    val maxRoughness: Double = 0.5,
    val maxStep: Double = 0.4,
    val maxSlip: Double = 0.85,
    val samplesPerEdge: Int = 16
) {
    fun validate() {
        require(listOf(alpha, beta, referenceLength, mapWidthMetres, cellSizeMetres, elevationAmplitude,
            terrainWavelength, roughnessAmplitude, stepAmplitude, soilSlip, slopeSlip,
            maxSlopeDegrees, maxRoughness, maxStep, maxSlip).all { it.isFinite() }) { "Terrain parameters must be finite." }
        require(alpha in 0.0..100.0 && beta in 0.0..100.0 && referenceLength in 0.01..100.0)
        require(mapWidthMetres in 1.0..10000.0 && cellSizeMetres in 0.01..100.0 && terrainWavelength in 0.1..10000.0)
        require(elevationAmplitude in 0.0..1000.0 && roughnessAmplitude in 0.0..5.0 && stepAmplitude in 0.0..5.0)
        require(soilSlip in 0.0..1.0 && slopeSlip in 0.0..1.0 && maxSlip in 0.01..1.0)
        require(maxSlopeDegrees in 1.0..89.0 && maxRoughness in 0.001..5.0 && maxStep in 0.001..5.0 && samplesPerEdge in 2..256)
    }
    fun cost(distanceMetres: Double, geometry: Double, slip: Double): Double {
        val distanceCost = distanceMetres / referenceLength
        return if (model == CostModel.DISTANCE) distanceCost else distanceCost * (1 + alpha * geometry + beta * slip)
    }
}

data class TerrainEvaluation(val distanceMetres: Double, val geometry: Double, val slip: Double,
    val maximumSlopeDegrees: Double, val passable: Boolean, val cost: Double)

/** Seeded, spatially coherent synthetic terrain. No claim of calibrated rover physics. */
class TerrainSimulation(val settings: TerrainSettings, seed: Int, private val metresPerUnit: Double) {
    // A separate seeded stream uses every seed bit without changing node placement.
    private val phase = Random(seed xor 0x5F3759DF).nextDouble() * 2 * PI
    init { settings.validate(); require(metresPerUnit.isFinite() && metresPerUnit > 0) }
    private fun field(x: Double, y: Double, channel: Double): Double {
        val k = 2 * PI / settings.terrainWavelength
        return (0.5 + 0.25 * sin(x * k + phase + channel) +
            0.25 * cos(y * k * 0.73 - phase + channel * 1.7)).coerceIn(0.0, 1.0)
    }
    private fun height(x: Double, y: Double) = settings.elevationAmplitude * (2 * field(x, y, 0.0) - 1)

    fun evaluate(from: Graph.Node, to: Graph.Node): TerrainEvaluation {
        val ax = from.x * metresPerUnit; val ay = from.y * metresPerUnit
        val bx = to.x * metresPerUnit; val by = to.y * metresPerUnit
        val length = hypot(bx - ax, by - ay)
        var geometry = 0.0; var slip = 0.0; var maxSlope = 0.0; var passable = true
        var previousHeight = height(ax, ay)
        val n = settings.samplesPerEdge
        repeat(n) { i ->
            val t = (i + 1.0) / n
            val x = ax + (bx - ax) * t; val y = ay + (by - ay) * t
            val nextHeight = height(x, y)
            val slope = if (length > 0) atan2(abs(nextHeight - previousHeight), length / n) * 180 / PI else 0.0
            previousHeight = nextHeight
            val mid = (i + 0.5) / n
            val mx = ax + (bx - ax) * mid; val my = ay + (by - ay) * mid
            val roughness = settings.roughnessAmplitude * field(mx, my, 2.0)
            val step = settings.stepAmplitude * field(mx, my, 4.0)
            val q = maxOf(slope / settings.maxSlopeDegrees, roughness / settings.maxRoughness, step / settings.maxStep)
            val s = (settings.soilSlip * field(mx, my, 6.0) + settings.slopeSlip * slope / settings.maxSlopeDegrees).coerceIn(0.0, 1.0)
            if (slope >= settings.maxSlopeDegrees || roughness >= settings.maxRoughness || step >= settings.maxStep || s >= settings.maxSlip) passable = false
            geometry += q.coerceIn(0.0, 1.0); slip += s
            maxSlope = maxOf(maxSlope, slope)
        }
        geometry /= n; slip /= n
        return TerrainEvaluation(length, geometry, slip, maxSlope, passable, settings.cost(length, geometry, slip))
    }
}
