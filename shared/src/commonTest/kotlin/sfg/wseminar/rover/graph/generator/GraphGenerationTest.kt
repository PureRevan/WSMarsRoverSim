package sfg.wseminar.rover.graph.generator

import kotlin.test.*
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.Graph.Companion.StartGoalSelector
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.states.GenerationPreset
import sfg.wseminar.rover.ui.states.GraphGenerationState
import sfg.wseminar.rover.ui.states.SpaceMode

class GraphGenerationTest {
    private fun topology(graph: Graph) = graph.edges.map { it.from.id to it.to.id }
    private fun coordinates(graph: Graph) = graph.nodes.map { it.x to it.y }

    @Test fun placementDoesNotChangeTheMapAndLeavesNodesBehindTheStart() {
        for (mode in GraphGenerationMode.entries) {
            val options = GraphGenerationOptions(mode = mode, seed = 47)
            val classic = GraphGenerator.generate(options.copy(endpointPlacement = EndpointPlacement.OPPOSITE_CORNERS))
            val centered = GraphGenerator.generate(options)
            assertEquals(coordinates(classic), coordinates(centered))
            assertEquals(topology(classic), topology(centered))
            assertEquals(classic.edges.map { it.weight }, centered.edges.map { it.weight })
            val start = assertNotNull(StartGoalSelector.selectStart(centered))
            val goal = assertNotNull(StartGoalSelector.selectGoal(centered))
            assertTrue(centered.nodes.any {
                (it.x - start.x) * (goal.x - start.x) + (it.y - start.y) * (goal.y - start.y) < 0.0
            })
            assertNotEquals(start.id, goal.id)
            assertEquals(classic.nodes.minBy { it.x + it.y }, StartGoalSelector.selectStart(classic))
        }
    }

    @Test fun allPlacementsAreDeterministicAndCustomSnapsToDistinctNodes() {
        for (mode in GraphGenerationMode.entries) for (placement in EndpointPlacement.entries) {
            val options = GraphGenerationOptions(mode = mode, endpointPlacement = placement,
                startX = 0.5, startY = 0.5, goalX = 0.5, goalY = 0.5)
            val first = GraphGenerator.generate(options)
            val second = GraphGenerator.generate(options)
            assertEquals(first.startNodeId, second.startNodeId)
            assertEquals(first.goalNodeId, second.goalNodeId)
            assertNotEquals(first.startNodeId, first.goalNodeId)
        }
        val graph = GraphGenerator.generate(GraphGenerationOptions(endpointPlacement = EndpointPlacement.CUSTOM,
            startX = 1.0, startY = 0.0, goalX = 0.0, goalY = 1.0))
        assertEquals(5, graph.startNodeId)
        assertEquals(24, graph.goalNodeId)
    }

    @Test fun weightExtremesRespectChanceFactorAndCheapShare() {
        val options = GraphGenerationOptions(gridColumns = 15, gridRows = 10, seed = 2026)
        val base = GraphGenerator.generate(options)
        val cheap = GraphGenerator.generate(options.copy(extremeWeightChance = 1.0, extremeWeightFactor = 100.0, cheapWeightShare = 1.0))
        val expensive = GraphGenerator.generate(options.copy(extremeWeightChance = 1.0, extremeWeightFactor = 100.0, cheapWeightShare = 0.0))
        val unchanged = GraphGenerator.generate(options.copy(extremeWeightChance = 0.0, extremeWeightFactor = 100.0))
        assertEquals(topology(base), topology(cheap))
        assertEquals(base.edges.map { it.weight }, unchanged.edges.map { it.weight })
        base.edges.indices.forEach { index ->
            assertEquals(base.edges[index].weight / 100.0, cheap.edges[index].weight, 0.00001)
            assertEquals(base.edges[index].weight * 100.0, expensive.edges[index].weight, 0.00001)
            assertTrue(cheap.edges[index].weight > 0.0)
        }
        val mixed = GraphGenerator.generate(options.copy(extremeWeightChance = 0.5, extremeWeightFactor = 10.0))
        assertTrue(mixed.edges.indices.any { mixed.edges[it].weight < base.edges[it].weight })
        assertTrue(mixed.edges.indices.any { mixed.edges[it].weight > base.edges[it].weight })
        assertTrue(mixed.edges.indices.any { mixed.edges[it].weight == base.edges[it].weight })
    }

    @Test fun largerMapsHaveTheRequestedSizeDenseIdsAndConnectivity() {
        val continuous = GraphGenerator.generate(GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS,
            nodeCount = 2_000, seed = 42))
        val grid = GraphGenerator.generate(GraphGenerationOptions(gridColumns = 50, gridRows = 40))
        for (graph in listOf(continuous, grid)) {
            assertEquals(2_000, graph.nodes.size)
            assertEquals(graph.nodes.indices.toList(), graph.nodes.map { it.id })
            assertEquals(graph.edges.indices.toList(), graph.edges.map { it.id })
            assertTrue(graph.edges.all { it.weight.isFinite() && it.weight > 0.0 })
            val visited = mutableSetOf(graph.nodes.first())
            val queue = ArrayDeque<Graph.Node>().apply { add(graph.nodes.first()) }
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (neighbor in graph.neighbors(current)) if (visited.add(neighbor)) queue.addLast(neighbor)
            }
            assertEquals(graph.nodes.size, visited.size)
        }
    }

    @Test fun presetsWorkInBothModesAndKeepSizeAndSeed() {
        val initial = GraphGenerationState(seed = 736, advanced = true, nodeCount = 220,
            gridColumns = 15, gridRows = 12)
        for (preset in GenerationPreset.entries) {
            val next = preset.applyTo(initial)
            assertEquals(initial.seed, next.seed)
            assertEquals(initial.nodeCount, next.nodeCount)
            assertEquals(initial.advanced, next.advanced)
            assertEquals(initial.gridColumns, next.gridColumns)
            assertEquals(next.toOptions(), next.copy(advanced = false).toOptions().copy(
                gridColumns = next.gridColumns, gridRows = next.gridRows, nodeCount = next.nodeCount))
        }
        assertNull(initial.validationMessage())
        assertNull(initial.copy(nodeCount = 100_001).validationMessage())
        assertNotNull(initial.copy(nodeCount = 0).validationMessage())
        assertNull(initial.copy(mode = SpaceMode.DISCRETE, gridColumns = 1001, gridRows = 100).validationMessage())
        assertNotNull(initial.copy(mode = SpaceMode.DISCRETE, gridColumns = Int.MAX_VALUE, gridRows = 2).validationMessage())
        assertFailsWith<IllegalArgumentException> {
            GraphGenerator.generate(GraphGenerationOptions(gridColumns = Int.MAX_VALUE, gridRows = Int.MAX_VALUE))
        }
        val simple = GraphGenerationState(sizeFactor = 0.8)
        assertEquals(simple.toOptions(), simple.withAdvanced(true).toOptions())
    }

    @Test fun simulationUsesTheGeneratedEndpointsEvenAfterEditingSettings() {
        val model = VisualizerViewModel()
        model.onEvent(RoverVisualizerEvent.ChangeGraphGeneration(GenerationPreset.SIDE_BRANCHES.applyTo(
            GraphGenerationState(mode = SpaceMode.DISCRETE, seed = 99))))
        model.onEvent(RoverVisualizerEvent.GenerateGraph)
        val initial = assertNotNull(model.state.currentFrame)
        model.onEvent(RoverVisualizerEvent.ChangeGraphGeneration(model.state.graphGeneration.copy(
            endpointPlacement = EndpointPlacement.OPPOSITE_CORNERS)))
        model.onEvent(RoverVisualizerEvent.RunAlgorithm)
        assertEquals(initial.startNode, model.state.frames.last().startNode)
        assertEquals(initial.goalNode, model.state.frames.last().goalNode)
        assertEquals(initial.startNode, model.state.frames.last().finalPathNodes.first())
        assertEquals(initial.goalNode, model.state.frames.last().finalPathNodes.last())
    }
}
