package sfg.wseminar.rover.ui

import kotlin.test.*
import sfg.wseminar.rover.algorithms.AStar
import sfg.wseminar.rover.algorithms.Dijkstra
import sfg.wseminar.rover.graph.generator.*
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.*
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.states.*

class GraphReuseTest {
    private class Fixture {
        var generations = 0
        val model = VisualizerViewModel(GeneratedGraphCache { options ->
            generations++
            GraphGenerator.generate(options)
        })
        init {
            model.onEvent(ChangeGraphGeneration(GraphGenerationState(seed = 47)))
        }
    }

    @Test fun algorithmSwitchReusesGraphAndEndpointsButResetsPlayback() {
        val fixture = Fixture()
        val model = fixture.model
        model.onEvent(GenerateGraph)
        val initial = assertNotNull(model.state.currentFrame)
        model.onEvent(RunAlgorithm)
        model.onEvent(SeekToStep(model.state.maxStep))
        model.onEvent(SelectNode(initial.startNode))
        model.onEvent(ChangeViewport(2f, 20f, -30f))
        val viewport = model.state.viewport
        model.onEvent(PlayPause)
        model.onEvent(ChangeAlgorithm(AStar))
        assertEquals(1, fixture.generations)
        assertSame(initial.graph, model.state.generatedGraph)
        val reset = assertNotNull(model.state.currentFrame)
        assertSame(initial.graph, reset.graph)
        assertSame(initial.startNode, reset.startNode)
        assertSame(initial.goalNode, reset.goalNode)
        assertTrue(reset.finalPathNodes.isEmpty())
        assertEquals(0, reset.visitedNodes.size)
        assertEquals(1, model.state.frames.size)
        assertEquals(0, model.state.currentStep)
        assertFalse(model.state.hasRunAlgorithm)
        assertFalse(model.state.isPlaying)
        assertNull(model.state.selectedNode)
        assertEquals(viewport, model.state.viewport)
        model.onEvent(RunAlgorithm)
        assertTrue(model.state.hasRunAlgorithm)
        assertSame(initial.graph, model.state.frames.last().graph)
        model.onEvent(ChangeAlgorithm(Dijkstra))
        assertEquals(1, fixture.generations)
    }

    @Test fun explorationChangesRebuildVisibilityWithoutLeakingPriorKnowledge() {
        val fixture = Fixture()
        val model = fixture.model
        model.onEvent(GenerateGraph)
        val graph = assertNotNull(model.state.generatedGraph)
        model.onEvent(RunAlgorithm)
        model.onEvent(ChangeExplorationMode(ExplorationMode.LOCAL))
        val local = assertNotNull(model.state.currentFrame)
        assertSame(graph, local.graph)
        assertTrue(local.knownNodes.size < graph.nodes.size)
        assertEquals(graph.outgoingEdges(local.startNode!!).size, local.knownEdges.size)
        assertTrue(local.finalPathNodes.isEmpty())
        assertFalse(model.state.hasRunAlgorithm)
        model.onEvent(PlayPause) // Current algorithms do not support local exploration.
        assertFalse(model.state.isPlaying)
        assertTrue(model.state.statusMessage.contains("does not support"))
        model.onEvent(ChangeExplorationMode(ExplorationMode.GLOBAL))
        assertSame(graph, model.state.generatedGraph)
        assertEquals(graph.nodes.size, model.state.currentFrame!!.knownNodes.size)
        assertTrue(local.knownNodes.size < graph.nodes.size) // Old frames remain independent.
        assertEquals(1, fixture.generations)
    }

    @Test fun draftEditsOnlyRegenerateOnExplicitRequestAndIdenticalOptionsReuseCache() {
        val fixture = Fixture()
        val model = fixture.model
        model.onEvent(GenerateGraph)
        val original = model.state.generatedGraph
        model.onEvent(ChangeGraphGeneration(model.state.graphGeneration.copy(seed = 48, density = 0.8)))
        model.onEvent(ChangeAlgorithm(AStar))
        model.onEvent(ChangeExplorationMode(ExplorationMode.LOCAL))
        assertSame(original, model.state.generatedGraph)
        assertEquals(1, fixture.generations)
        model.onEvent(GenerateGraph)
        val replacement = model.state.generatedGraph
        assertNotSame(original, replacement)
        assertEquals(2, fixture.generations)
        model.onEvent(GenerateGraph)
        assertSame(replacement, model.state.generatedGraph)
        assertEquals(2, fixture.generations)
        model.onEvent(ChangeGraphGeneration(model.state.graphGeneration.withAdvanced(true)))
        model.onEvent(GenerateGraph) // UI complexity is not part of the resolved key.
        assertSame(replacement, model.state.generatedGraph)
        assertEquals(2, fixture.generations)
    }

    @Test fun spaceSwitchRestoresLastGraphForEachModeWithoutGenerating() {
        val fixture = Fixture()
        val model = fixture.model
        model.onEvent(GenerateGraph)
        val continuous = model.state.generatedGraph
        model.onEvent(ChangeSpaceMode(SpaceMode.DISCRETE))
        assertNull(model.state.generatedGraph)
        assertTrue(model.state.frames.isEmpty())
        assertEquals(1, fixture.generations)
        model.onEvent(GenerateGraph)
        val grid = model.state.generatedGraph
        assertNotSame(continuous, grid)
        model.onEvent(ChangeSpaceMode(SpaceMode.CONTINUOUS))
        assertSame(continuous, model.state.generatedGraph)
        assertSame(continuous, model.state.currentFrame!!.graph)
        model.onEvent(ChangeSpaceMode(SpaceMode.DISCRETE))
        assertSame(grid, model.state.generatedGraph)
        assertEquals(2, fixture.generations)
    }

    @Test fun repeatedSelectionsAreNoOpsAndResetKeepsTopology() {
        val fixture = Fixture()
        val model = fixture.model
        model.onEvent(GenerateGraph)
        model.onEvent(RunAlgorithm)
        model.onEvent(SeekToStep(2))
        val state = model.state
        model.onEvent(ChangeAlgorithm(state.algorithm))
        model.onEvent(ChangeExplorationMode(state.explorationMode))
        model.onEvent(ChangeSpaceMode(state.spaceMode))
        assertSame(state, model.state)
        model.onEvent(ResetSimulation)
        assertSame(state.generatedGraph, model.state.generatedGraph)
        assertEquals(1, model.state.frames.size)
        assertFalse(model.state.hasRunAlgorithm)
        model.onEvent(StepForward)
        assertTrue(model.state.hasRunAlgorithm)
        model.onEvent(GenerateGraph) // A cache hit must still clear old run results.
        assertSame(state.generatedGraph, model.state.generatedGraph)
        assertFalse(model.state.hasRunAlgorithm)
        assertEquals(1, model.state.frames.size)
        assertEquals(1, fixture.generations)
    }

    @Test fun changesWithoutGraphDoNotTriggerGenerationAndInvalidDraftsDoNotLoseGraph() {
        val fixture = Fixture()
        val model = fixture.model
        model.onEvent(ChangeAlgorithm(AStar))
        model.onEvent(ChangeExplorationMode(ExplorationMode.LOCAL))
        model.onEvent(ResetSimulation)
        model.onEvent(PlayPause)
        assertEquals(0, fixture.generations)
        assertNull(model.state.generatedGraph)
        assertFalse(model.state.isPlaying)
        model.onEvent(GenerateGraph)
        val graph = model.state.generatedGraph
        model.onEvent(ChangeGraphGeneration(model.state.graphGeneration.copy(advanced = true, nodeCount = 0)))
        model.onEvent(GenerateGraph)
        assertSame(graph, model.state.generatedGraph)
        assertEquals(1, fixture.generations)
    }

    @Test fun cacheKeepsOnlyLatestPerModeAndDoesNotReplaceSuccessfulEntriesOnFailure() {
        var calls = 0
        val cache = GeneratedGraphCache { options ->
            calls++
            require(options.seed != -1) { "Generation failed" }
            GraphGenerator.generate(options)
        }
        val options = GraphGenerationOptions(seed = 1)
        val first = cache.getOrGenerate(options)
        assertFailsWith<IllegalArgumentException> { cache.getOrGenerate(options.copy(seed = -1)) }
        assertSame(first, cache.latest(options.mode))
        assertSame(first, cache.getOrGenerate(options))
        cache.getOrGenerate(options.copy(seed = 2))
        val rebuilt = cache.getOrGenerate(options)
        assertNotSame(first, rebuilt)
        assertEquals(4, calls)
    }
}
