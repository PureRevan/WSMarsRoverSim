package sfg.wseminar.rover.transfer

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import sfg.wseminar.rover.ui.models.*
import sfg.wseminar.rover.ui.states.*
import sfg.wseminar.rover.ui.settings.*
import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.i18n.AppLanguage
import sfg.wseminar.rover.terrain.TerrainSettings
import kotlin.test.*

class ArchiveTest {
    @Test fun projectRenamePreservesPublishedPaperBundles() {
        val sections = mapOf("theme" to JsonPrimitive("PAPER"))
        val current = Archive(sections = sections)
        assertEquals("WSMarsRoverSim", current.format)
        val legacy = current.copy(format = "WSRoverSim", generatorVersion = 3)
        for (archive in listOf(current, legacy)) {
            val bytes = archiveJson.encodeToString(archive).encodeToByteArray()
            val restored = readArchive(bytes)
            assertEquals(ThemePreset.PAPER, prepareImport(restored, sections.keys, VisualizerState()).theme)
        }
        assertFails { readArchive(archiveJson.encodeToString(current.copy(format = "another-app")).encodeToByteArray()) }
    }

    private fun model() = VisualizerViewModel().also {
        it.onEvent(RoverVisualizerEvent.ChangeGraphGeneration(GraphGenerationState(mode = SpaceMode.DISCRETE, advanced = true, gridColumns = 4, gridRows = 4, seed = 42)))
        it.onEvent(RoverVisualizerEvent.GenerateGraph)
    }

    @Test fun replayRoundTripRestoresGraphIdentityFrameCostsSelectionAndView() = runTest {
        val source = model()
        source.onEvent(RoverVisualizerEvent.RunAlgorithm)
        source.onEvent(RoverVisualizerEvent.SeekToStep(source.state.maxStep / 2))
        source.onEvent(RoverVisualizerEvent.SelectNode(source.state.generatedGraph!!.nodes[3]))
        source.onEvent(RoverVisualizerEvent.ChangeViewport(2f, 15f, -20f))
        val before = source.state
        val bytes = exportArchive(before, ThemePreset.MARS, availableSections(before), true)
        val archive = readArchive(bytes)
        val target = VisualizerViewModel()
        val imported = prepareImport(archive, archive.sections.keys, target.state)
        target.importPrepared(imported)
        assertEquals(ThemePreset.MARS, imported.theme)
        assertEquals(before.frames.size, target.state.frames.size)
        assertEquals(before.currentStep, target.state.currentStep)
        assertEquals(before.viewport, target.state.viewport)
        assertFalse(target.state.isPlaying)
        val graph = target.state.generatedGraph!!
        assertSame(graph.nodes[3], target.state.selectedNode)
        before.frames.zip(target.state.frames).forEach { (a, b) ->
            assertSame(graph, b.graph)
            assertEquals(a.message, b.message)
            assertEquals(a.nodeCosts, b.nodeCosts)
            assertEquals(a.edgeCosts, b.edgeCosts)
            assertEquals(a.finalPathEdges.map { it.id }, b.finalPathEdges.map { it.id })
            assertEquals(a.graph.nodes.filter { it in a.knownNodes }.map { it.id }, b.graph.nodes.filter { it in b.knownNodes }.map { it.id })
        }
    }

    @Test fun sensingRadiusRoundTripsAndOlderSettingsDefaultToOneHop() = runTest {
        val source = model()
        source.onEvent(RoverVisualizerEvent.ChangeAlgorithm(sfg.wseminar.rover.algorithms.GoToWaypoint))
        source.onEvent(RoverVisualizerEvent.ChangeSensingRadius(3))
        for (format in BundleFormat.entries) {
            val archive = readArchive(exportBundle(source.state, ThemePreset.PAPER, setOf("setup", "topology"), format))
            val target = model()
            target.importPrepared(prepareImport(archive, archive.sections.keys, target.state))
            assertEquals(3, target.state.sensingRadius)
            assertEquals(ExplorationMode.LOCAL, target.state.explorationMode)
            assertEquals(source.state.currentFrame!!.knownNodes.size, target.state.currentFrame!!.knownNodes.size)
            val setup = archive.sections.getValue("setup").jsonObject
            val old = archive.copy(sections = archive.sections + ("setup" to JsonObject(setup - "sensingRadius")))
            assertEquals(1, prepareImport(old, old.sections.keys, target.state).setup!!.sensingRadius)
            val invalid = archive.copy(sections = archive.sections + ("setup" to JsonObject(setup + ("sensingRadius" to JsonPrimitive(0)))))
            assertFails { prepareImport(invalid, invalid.sections.keys, target.state) }
        }
    }

    @Test fun partialSettingsImportPreservesEverythingElse() = runTest {
        val source = model()
        source.onEvent(RoverVisualizerEvent.SetFinalPathScale(5f))
        source.onEvent(RoverVisualizerEvent.ToggleCosts(false))
        val archive = readArchive(exportArchive(source.state, ThemePreset.DEEP_SPACE, setOf("overlays", "theme"), false))
        val target = model(); val graph = target.state.generatedGraph
        target.importPrepared(prepareImport(archive, setOf("overlays"), target.state))
        assertEquals(5f, target.state.options.finalPathScale)
        assertTrue(target.state.options.showCosts)
        assertSame(graph, target.state.generatedGraph)
        assertNull(prepareImport(archive, setOf("overlays"), target.state).theme)
    }

    @Test fun recipeRecreatesOriginalGraphAfterGenerationControlsChange() = runTest {
        val source = model(); val original = source.state.generatedGraph!!
        source.onEvent(RoverVisualizerEvent.ChangeGraphGeneration(source.state.graphGeneration.copy(seed = 99, gridRows = 9)))
        val archive = readArchive(exportArchive(source.state, ThemePreset.PAPER, setOf("recipe"), false))
        assertFalse("topology" in archive.sections)
        val target = VisualizerViewModel()
        target.importPrepared(prepareImport(archive, archive.sections.keys, target.state))
        val restored = target.state.generatedGraph!!
        assertEquals(original.seed, restored.seed)
        assertEquals(original.nodes.map { it.x to it.y }, restored.nodes.map { it.x to it.y })
        assertEquals(original.edges.map { it.weight }, restored.edges.map { it.weight })
        assertEquals(1, target.state.frames.size)
    }

    @Test fun switchingSpaceRestoresTheMatchingGenerationRecipe() {
        val source = model()
        source.onEvent(RoverVisualizerEvent.ChangeGraphGeneration(GraphGenerationState(mode = SpaceMode.CONTINUOUS, simpleNodeCount = 8, seed = 99)))
        source.onEvent(RoverVisualizerEvent.GenerateGraph)
        source.onEvent(RoverVisualizerEvent.ChangeSpaceMode(SpaceMode.DISCRETE))
        assertEquals(42, source.state.generatedRecipe?.seed)
        assertEquals(SpaceMode.DISCRETE, source.state.generatedRecipe?.mode)
        assertEquals(16, source.state.generatedGraph?.nodes?.size)
        source.onEvent(RoverVisualizerEvent.ChangeSpaceMode(SpaceMode.CONTINUOUS))
        assertEquals(99, source.state.generatedRecipe?.seed)
        assertEquals(8, source.state.generatedGraph?.nodes?.size)
    }

    @Test fun importingUnchangedSetupDoesNotEraseExistingReplay() = runTest {
        val target = model()
        target.onEvent(RoverVisualizerEvent.RunAlgorithm)
        val frames = target.state.frames
        val archive = readArchive(exportArchive(target.state, ThemePreset.PAPER, setOf("setup", "nodeSlider"), false))
        assertFalse("split" in archive.sections.getValue("nodeSlider").jsonObject)
        target.importPrepared(prepareImport(archive, archive.sections.keys, target.state))
        assertSame(frames, target.state.frames)
    }

    @Test fun invalidVersionMissingDependenciesAndInvalidTopologyAreRejected() {
        assertFails { readArchive("{}".encodeToByteArray()) }
        assertFails { readArchive(archiveJson.encodeToString(Archive(version = 99, sections = mapOf("theme" to JsonPrimitive("PAPER")))).encodeToByteArray()) }
        assertFails { readArchive(archiveJson.encodeToString(Archive(sections = mapOf("replay" to JsonObject(emptyMap())))).encodeToByteArray()) }
        val graph = GraphData(listOf(NodeData(0.0, 0.0, "A")), listOf(EdgeData(0, 2, 1.0, true, "bad")), 0, 0, 1, 2)
        assertFails { graph.graph() }
        val invalid = Archive(sections = mapOf("overlays" to buildJsonObject { put("finalPathScale", -3) }))
        assertFails { prepareImport(invalid, invalid.sections.keys, VisualizerState()) }
    }

    @Test fun replayAutomaticallyIncludesItsTopologyAndAlgorithm() {
        val source = model()
        val archive = readArchive(exportArchive(source.state, ThemePreset.PAPER, setOf("replay"), false))
        assertTrue(archive.sections.keys.containsAll(setOf("replay", "topology", "setup")))
    }

    @Test fun binaryAndJsonBundlesRestoreIdenticalGraphReplayAndSettings() = runTest {
        val source = model()
        source.onEvent(RoverVisualizerEvent.RunAlgorithm)
        source.onEvent(RoverVisualizerEvent.SeekToStep(source.state.maxStep / 2))
        source.onEvent(RoverVisualizerEvent.SelectNode(source.state.generatedGraph!!.nodes[2]))
        val state = source.state
        val keys = availableSections(state)
        val expected = readArchive(exportArchive(state, ThemePreset.MARS, keys, false))
        for (format in BundleFormat.entries) {
            val bytes = exportBundle(state, ThemePreset.MARS, keys, format)
            val restored = readArchive(bytes)
            assertEquals(expected, restored)
            val prepared = prepareImport(restored, keys, state)
            assertEquals(state.frames.size, prepared.frames!!.size)
            assertEquals(state.currentStep, prepared.replay!!.currentStep)
            assertEquals(2, prepared.replay.selectedNode)
        }
        val binary = exportBundle(state, ThemePreset.MARS, keys, BundleFormat.BINARY)
        assertTrue(binary.size < exportBundle(state, ThemePreset.MARS, keys, BundleFormat.COMPACT_JSON).size)
        assertFails { readArchive(binary.copyOf(12)) }
        assertFails { readArchive(binary.copyOf().also { it[4] = 2 }) }
        val damaged = binary.copyOf(binary.size - 3)
        assertFails { prepareImport(readArchive(damaged), keys, state) }
    }

    @Test fun topologyEstimatesAndDefaultSelectionRespectRecipeFallback() {
        val state = model().state
        val estimate = estimateBundle(state, ThemePreset.MARS, BundleFormat.BINARY)
        assertEquals(binaryGraphSize(state), estimate.sections["topology"])
        assertTrue("topology" in defaultBundleSelection(state, true))
        assertTrue("topology" in defaultBundleSelection(state, false))
        assertTrue(estimate.total(setOf("topology", "setup")) > binaryGraphSize(state))
        val label = "x".repeat(2000)
        val large = state.copy(generatedGraph = sfg.wseminar.rover.graph.Graph(List(6000) { id ->
            sfg.wseminar.rover.graph.Graph.Node(id.toDouble(), 0.0, id, label)
        }, emptyList()))
        assertTrue(binaryGraphSize(large) > DEFAULT_GRAPH_BYTES)
        assertFalse("topology" in defaultBundleSelection(large, true))
        assertTrue("recipe" in defaultBundleSelection(large, true))
        assertTrue("topology" in defaultBundleSelection(large.copy(generatedRecipe = null), true))
        val noGraph = VisualizerState()
        val settings = exportBundle(noGraph, ThemePreset.MARS, defaultBundleSelection(noGraph, false), BundleFormat.BINARY)
        assertFalse("topology" in readArchive(settings).sections)
        assertFalse("replay" in readArchive(settings).sections)
    }
    @Test fun emphasisAcceptsZeroAndClampsLegacyBundles() {
        val current = VisualizerState()
        assertEquals(1f, current.options.finalPathScale)
        for ((value, expected) in listOf(0 to 0f, 5 to 5f, 6 to 5f)) {
            val archive = Archive(sections = mapOf("overlays" to buildJsonObject { put("finalPathScale", value) }))
            assertEquals(expected, prepareImport(archive, setOf("overlays"), current).options!!.finalPathScale)
        }
    }

    @Test fun languageTerrainAndAlgorithmMemorySurviveEveryBundleFormat() = runTest {
        val source = model()
        source.onEvent(RoverVisualizerEvent.ChangePerformance(source.state.performance.copy(language = AppLanguage.GERMAN)))
        source.onEvent(RoverVisualizerEvent.RunAlgorithm)
        val before = source.state
        assertTrue(checkNotNull(before.peakAlgorithmBytes) > 0)
        for (format in BundleFormat.entries) {
            val archive = readArchive(exportBundle(before, ThemePreset.PAPER, availableSections(before), format))
            assertEquals(3, archive.generatorVersion)
            val target = VisualizerViewModel()
            target.importPrepared(prepareImport(archive, archive.sections.keys, target.state))
            assertEquals(AppLanguage.GERMAN, target.state.performance.language)
            assertEquals(before.generatedRecipe!!.terrain, target.state.generatedRecipe!!.terrain)
            assertEquals(before.peakAlgorithmBytes, target.state.peakAlgorithmBytes)
        }
    }

    @Test fun recipesWithoutTerrainKeepTheOriginalGenerator() = runTest {
        val source = model()
        val legacy = source.state.graphGeneration.copy(terrain = TerrainSettings(enabled = false))
        source.onEvent(RoverVisualizerEvent.ChangeGraphGeneration(legacy))
        source.onEvent(RoverVisualizerEvent.GenerateGraph)
        val archive = readArchive(exportArchive(source.state, ThemePreset.PAPER, setOf("recipe", "generation"), false))
        val old = archive.copy(sections = archive.sections.mapValues { (_, value) -> JsonObject(value.jsonObject - "terrain") })
        val target = VisualizerViewModel()
        target.importPrepared(prepareImport(old, old.sections.keys, target.state))
        assertFalse(target.state.generatedRecipe!!.terrain.enabled)
        assertFalse(target.state.graphGeneration.terrain.enabled)
        assertEquals(2, target.state.generatedGraph!!.generatorVersion)
        assertEquals(source.state.generatedGraph!!.nodes.map { it.x to it.y }, target.state.generatedGraph!!.nodes.map { it.x to it.y })
        assertEquals(source.state.generatedGraph!!.edges.map { it.weight }, target.state.generatedGraph!!.edges.map { it.weight })
    }
}
