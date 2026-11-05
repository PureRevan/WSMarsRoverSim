package sfg.wseminar.rover.terrain

import sfg.wseminar.rover.graph.*
import sfg.wseminar.rover.graph.generator.*
import sfg.wseminar.rover.ui.states.GraphGenerationState
import sfg.wseminar.rover.algorithms.*
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.computation.wire.*
import kotlin.math.sqrt
import kotlin.test.*

class TerrainModelTest {
    @Test fun paperExampleChangesPreferredRouteWithSlipWeight() {
        val model = TerrainSettings(alpha = 10.0, beta = 10.0)
        assertEquals(6.0, model.cost(2.0, 0.0, 0.2), 1e-12)
        val upper = 2 * sqrt(5.0) + model.cost(2.0, 0.0, 0.2)
        val lower = 2 * sqrt(8.0) + 2
        assertTrue(lower < upper)
        assertTrue(2 * sqrt(5.0) + model.copy(beta = 1.0).cost(2.0, 0.0, 0.2) < lower)
        assertEquals(2.0, model.copy(model = CostModel.DISTANCE).cost(2.0, 1.0, 1.0))
        assertEquals(1.0, model.copy(referenceLength = 2.0, alpha = 0.0, beta = 0.0).cost(2.0, 1.0, 1.0))
    }
    @Test fun sampledTerrainIsDeterministicSymmetricAndBounded() {
        val a = Graph.Node(0.1, 0.2, 0); val b = Graph.Node(0.8, 0.6, 1)
        val simulator = TerrainSimulation(TerrainSettings(), 42, 100.0)
        val forward = simulator.evaluate(a, b)
        assertEquals(forward, simulator.evaluate(a, b))
        assertEquals(forward.cost, simulator.evaluate(b, a).cost, 1e-8)
        assertTrue(forward.geometry in 0.0..1.0 && forward.slip in 0.0..1.0)
        assertTrue(forward.cost >= forward.distanceMetres)
        assertNotEquals(forward.cost, TerrainSimulation(TerrainSettings(), 43, 100.0).evaluate(a, b).cost)
    }
    @Test fun blockedEdgesAreRemovedWithoutNegativeOrInfiniteWeights() {
        val p = TerrainSettings(elevationAmplitude = 0.0, soilSlip = 1.0, maxSlip = 0.01)
        val graph = GraphGenerator.generate(GraphGenerationOptions(terrain = p, seed = 42))
        assertEquals(30, graph.nodes.size)
        assertTrue(graph.edges.isEmpty())
        val reachable = GraphGenerator.generate(GraphGenerationOptions(terrain = TerrainSettings(model = CostModel.DISTANCE,
            elevationAmplitude = 0.0, roughnessAmplitude = 0.0, stepAmplitude = 0.0, soilSlip = 0.0, slopeSlip = 0.0)))
        assertTrue(reachable.edges.all { it.weight == 1.0 })
    }
    @Test fun workerOptionsPreserveEveryTerrainParameter() {
        val state = GraphGenerationState(terrain = TerrainSettings(beta = 2.5, samplesPerEdge = 32))
        val options = state.toOptions()
        val writer = PacketWriter().apply { write(options) }
        assertEquals(options, PacketReader(writer.bytes()).readGraphGenerationOptions())
        assertTrue(GraphGenerationState().terrain.enabled)
        assertFalse(GraphGenerationOptions().terrain.enabled) // Legacy programmatic API is unchanged.
    }
    @Test fun paperCoordinatesAndWeightsUseTheSameMetreScale() {
        val settings = TerrainSettings(model = CostModel.DISTANCE, cellSizeMetres = 2.5,
            referenceLength = 0.5, elevationAmplitude = 0.0, roughnessAmplitude = 0.0,
            stepAmplitude = 0.0, soilSlip = 0.0, slopeSlip = 0.0)
        val grid = GraphGenerator.generate(GraphGenerationOptions(terrain = settings))
        assertTrue(grid.nodes.all { it.x % 2.5 == 0.0 && it.y % 2.5 == 0.0 })
        assertTrue(grid.edges.all { it.weight == 5.0 })
        val options = GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS, terrain = settings, seed = 42)
        val small = GraphGenerator.generate(options.copy(terrain = settings.copy(mapWidthMetres = 10.0)))
        val large = GraphGenerator.generate(options.copy(terrain = settings.copy(mapWidthMetres = 100.0)))
        small.nodes.zip(large.nodes).forEach { (a, b) ->
            assertEquals(a.x * 10.0, b.x, 1e-8)
            assertEquals(a.y * 10.0, b.y, 1e-8)
        }
        small.edges.zip(large.edges).forEach { (a, b) -> assertEquals(a.weight * 10.0, b.weight, 1e-8) }
    }
    @Test fun astarAndDijkstraAgreeOnTerrainGraph() {
        val graph = GraphGenerator.generate(GraphGenerationOptions(terrain = TerrainSettings(), seed = 51))
        val start = graph.nodes.first(); val goal = graph.nodes.last()
        fun cost(a: RoverAlgorithm) = AlgorithmRunner.createTimeline(a, graph, start, goal).generateAll().last().nodeCosts.last()
        assertEquals(cost(Dijkstra), cost(AStar), 1e-8)
        assertFailsWith<IllegalArgumentException> { TerrainSettings(alpha = Double.NaN).validate() }
        assertFailsWith<IllegalArgumentException> { TerrainSettings(referenceLength = 0.0).validate() }
    }
}
