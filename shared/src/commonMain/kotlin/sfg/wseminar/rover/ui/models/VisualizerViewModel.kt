package sfg.wseminar.rover.ui.models

import sfg.wseminar.rover.computation.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.algorithms.GoToWaypoint
import sfg.wseminar.rover.ui.states.defaultExplorationMode
import sfg.wseminar.rover.algorithms.backend.AlgorithmRunner
import sfg.wseminar.rover.algorithms.backend.KnowledgeMode
import sfg.wseminar.rover.ui.states.ExplorationMode
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.Graph.Companion.StartGoalSelector
import sfg.wseminar.rover.ui.CanvasConstants.MaxZoom
import sfg.wseminar.rover.ui.CanvasConstants.MinZoom
import sfg.wseminar.rover.ui.canvas.calculateFitViewport
import sfg.wseminar.rover.simulation.InitialSimulationFrameFactory
import sfg.wseminar.rover.graph.generator.GeneratedGraphCache
import sfg.wseminar.rover.graph.generator.GraphGenerationMode
import sfg.wseminar.rover.ui.states.SpaceMode
import sfg.wseminar.rover.ui.states.ViewportState
import sfg.wseminar.rover.ui.states.VisualizerState
import sfg.wseminar.rover.ui.states.waypointCommandIssue
import sfg.wseminar.rover.ui.states.supports
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.TimeSource
import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.simulation.FrameHistory
import sfg.wseminar.rover.algorithms.backend.SimulationLimits


class VisualizerViewModel(
    private val graphCache: GeneratedGraphCache = GeneratedGraphCache(),
    private val scope: CoroutineScope? = null,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val limits: SimulationLimits = SimulationLimits(),
    private val checkpoint: suspend () -> Unit = { sfg.wseminar.rover.computation.computationCheckpoint() }
) {
    var state by mutableStateOf(VisualizerState(replayBudgetBytes = limits.maxEstimatedBytes, performance = sfg.wseminar.rover.ui.settings.PerformanceSettings(replay = limits)))
        private set

    private val graphRecipes = mutableMapOf<SpaceMode, Pair<Graph, sfg.wseminar.rover.ui.states.GraphGenerationState?>>()
    private var workId = 0L
    private var job: Job? = null
    private var activeWorker: WorkerClient? = null
    private var idleWorker: WorkerClient? = null
    private var pendingRecording: FrameHistory? = null
    private var pendingReplayBytes = 0L
    private var pendingPeakAlgorithmBytes = 0L
    private var runClock: kotlin.time.TimeMark? = null

    private fun finishRunTiming() {
        runClock?.let { state = state.copy(runElapsedNanos = it.elapsedNow().inWholeNanoseconds) }
        runClock = null
    }

    fun close() {
        cancelWork()
        idleWorker?.close()
        idleWorker = null
    }

    internal suspend fun importPrepared(imported: sfg.wseminar.rover.transfer.PreparedImport) {
        cancelWork()
        val id = workId
        val performance = imported.performance ?: state.performance
        val recipe = imported.recipe
        val graph = imported.graph ?: recipe?.let {
            val options = it.toOptions().copy(budget = performance.generation.copy(enabled = performance.limitsEnabled))
            compute { graphCache.build(options) { cooperate() } }
        }
        if (id != workId) return
        var next = state.copy(
            options = imported.options ?: state.options,
            performance = performance,
            graphGeneration = imported.generation ?: recipe ?: state.graphGeneration,
            playbackSpeed = state.playbackSpeed.coerceIn(performance.playback.range),
            viewport = imported.view?.viewport ?: state.viewport,
            legendEdgePosition = imported.view?.legendPosition ?: state.legendEdgePosition,
            noticeMessage = null
        )
        imported.setup?.let { setup ->
            next = next.copy(algorithm = RoverAlgorithm.algorithms.first { it.name == setup.algorithm },
                spaceMode = setup.space, explorationMode = setup.exploration, sensingRadius = setup.sensingRadius)
        }
        if (graph != null) {
            next = next.copy(generatedRecipe = recipe).withFreshSimulation(graph)
            if (imported.view == null) next = next.copy(viewport = fitViewport(graph))
        } else if (imported.setup != null && (next.algorithm != state.algorithm || next.explorationMode != state.explorationMode || next.spaceMode != state.spaceMode || next.sensingRadius != state.sensingRadius)) next = next.withFreshSimulation()
        imported.replay?.let { replay ->
            val g = checkNotNull(graph)
            next = next.copy(frames = checkNotNull(imported.frames), currentStep = replay.currentStep,
                selectedNode = replay.selectedNode?.let { g.nodes[it] }, selectedEdge = replay.selectedEdge?.let { g.edges[it] },
                hasCompletedRecording = replay.completed, hasRunAlgorithm = replay.hasRun,
                peakAlgorithmBytes = replay.peakAlgorithmBytes, runElapsedNanos = replay.elapsedNanos, estimatedReplayBytes = replay.estimatedBytes,
                recordingStopReason = replay.stopReason, playbackSpeed = replay.speed.coerceIn(performance.playback.range))
        }
        if (graph != null) graphRecipes[next.spaceMode] = graph to recipe
        state = next.copy(isPlaying = false, isComputing = false, statusMessage = "Import complete", replayBudgetBytes = performance.replay.maxEstimatedBytes)
        revealFinalCostIfViewed()
    }

    private fun notifyUser(message: String) {
        state = state.copy(noticeMessage = message, noticeId = state.noticeId + 1)
    }

    private fun revealFinalCostIfViewed() {
        val frame = state.currentFrame ?: return
        if (!state.isComputing && state.hasCompletedRecording && state.currentStep == state.maxStep) {
            state = state.copy(revealedFinalPathCost = if (frame.finalPathNodes.isNotEmpty()) frame.finalPathEdges.sumOf { it.weight } else Double.POSITIVE_INFINITY)
        }
    }

    private fun publishPendingRecording() {
        pendingRecording?.let { state = state.copy(frames = it, estimatedReplayBytes = pendingReplayBytes, peakAlgorithmBytes = pendingPeakAlgorithmBytes) }
        pendingRecording = null
    }

    private fun cancelWork() {
        publishPendingRecording()
        activeWorker?.close()
        activeWorker = null
        finishRunTiming()
        workId++
        job?.cancel()
        job = null
        state = state.copy(isComputing = false, workMessage = "", isPlaying = false)
    }

    private fun task(message: String, play: Boolean = false, block: suspend (Long) -> Unit) {
        cancelWork()
        val id = workId
        state = state.copy(isComputing = true, isPlaying = play, workMessage = message, recordingStopReason = null)
        val action: suspend () -> Unit = {
            var succeeded = false
            try {
                block(id)
                succeeded = true
            } catch (cancelled: CancellationException) {
                if (id == workId) {
                    publishPendingRecording()
                    state = state.copy(isComputing = false, isPlaying = false, workMessage = "")
                }
                throw cancelled
            } catch (failure: Exception) {
                if (id == workId) state = state.copy(isComputing = false, isPlaying = false,
                    workMessage = "", statusMessage = failure.message ?: "Computation failed")
                if (id == workId) notifyUser(state.statusMessage)
            } finally {
                if (id == workId) {
                    publishPendingRecording()
                    activeWorker?.let {
                        if (succeeded) { idleWorker?.close(); idleWorker = it }
                        else it.close()
                    }
                    activeWorker = null
                    finishRunTiming()
                }
            }
        }
        if (scope != null) job = scope.launch { action() }
        else action.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
        }) // Deterministic headless execution; production always supplies the screen scope.
    }

    private suspend fun cooperate() {
        if (scope != null) { currentCoroutineContext().ensureActive(); checkpoint() }
    }

    private suspend fun <T> compute(block: suspend () -> T): T =
        if (scope == null) block() else withContext(worker) { block() }

    private suspend fun openWorker(settings: WorkerSettings, estimate: Long): WorkerClient? {
        if (!browserWorkerSettingsAvailable || scope == null || !settings.useWorker(estimate)) return null
        idleWorker?.let { idleWorker = null; activeWorker = it; return it }
        state = state.copy(workMessage = "Starting Web Worker…")
        return try {
            WorkerClient.open().also { activeWorker = it }
        } catch (failure: WorkerStartupException) {
            if (settings.mode == WorkerMode.ALWAYS) throw failure
            notifyUser("Web Worker unavailable; continuing on the UI thread. ${failure.message}")
            null
        }
    }

    private fun generateGraph() {
        val options = state.graphGeneration.toOptions().copy(budget = state.performance.generation.copy(enabled = state.performance.limitsEnabled))
        val recipe = state.graphGeneration
        val generationMode = recipe.mode
        val compatible = state.algorithm.takeIf { it.supports(generationMode) } ?: RoverAlgorithm.defaultFor(generationMode)
        val cached = graphCache.cached(options)
        val workerSettings = state.performance.browserWorker
        task("Generating graph…") { id ->
            val remote = if (cached == null) openWorker(workerSettings, estimateGenerationWork(options)) else null
            val graph = cached ?: remote?.generate(options) ?: compute { graphCache.build(options) { cooperate() } }
            if (id == workId) {
                graphCache.store(options, graph)
                graphRecipes[generationMode] = graph to recipe
                state = state.copy(algorithm = compatible, spaceMode = generationMode, generatedRecipe = recipe,
                    explorationMode = if (compatible.supports(state.explorationMode)) state.explorationMode else compatible.defaultExplorationMode(),
                    viewport = fitViewport(graph), isComputing = false, workMessage = "",
                    statusMessage = "Graph ready (${graph.nodes.size} nodes, ${graph.edges.size} edges; generator v${graph.generatorVersion})${if (remote != null) " · Web Worker" else ""}"
                ).withFreshSimulation(graph)
            }
        }
    }

    private fun startSimulation(play: Boolean = false, firstStep: Int = 0, waypoint: Boolean = false) {
        val limits = state.performance.replay.copy(enabled = state.performance.limitsEnabled)
        val workerSettings = state.performance.browserWorker
        if (state.algorithm === GoToWaypoint || waypoint) {
            if (!waypoint) {
                val message = "Select a destination and choose Send waypoint. Playback controls only replay existing commands."
                state = state.copy(statusMessage = message, isPlaying = false)
                notifyUser(message)
                return
            }
            state.waypointCommandIssue()?.let { message ->
                state = state.copy(statusMessage = message)
                notifyUser(message)
                return
            }
        }
        val initial = state.currentFrame
        val graph = if (waypoint) initial?.graph else state.generatedGraph
        if (graph == null) { state = state.copy(statusMessage = "Generate a graph before running ${state.algorithm.name}"); return }
        val algorithm = state.algorithm
        if (!algorithm.supports(state.spaceMode) || !algorithm.supports(state.explorationMode)) {
            state = state.copy(statusMessage = "${algorithm.name} does not support the selected space or exploration mode"); return
        }
        val start = if (waypoint) initial?.roverNode else StartGoalSelector.selectStart(graph)
        val goal = if (waypoint) state.selectedNode else StartGoalSelector.selectGoal(graph)
        if (start == null || goal == null || graph.nodes.getOrNull(goal.id) !== goal ||
            (waypoint && algorithm !== GoToWaypoint)) {
            state = state.copy(statusMessage = "Select a waypoint on the current graph first."); return
        }
        val knowledge = state.explorationMode.knowledgeMode()
        val sensingRadius = state.sensingRadius
        val history = if (waypoint) FrameHistory().append(state.frames.take(state.currentStep + 1)) else FrameHistory()
        val remainingFrames = if (limits.enabled && limits.frameLimitEnabled) limits.maxFrames - history.size else Int.MAX_VALUE
        if (limits.enabled && (remainingFrames <= 0 || (waypoint && state.estimatedReplayBytes >= limits.maxEstimatedBytes))) {
            state = state.copy(recordingStopReason = "Replay budget reached. Reset the simulation to start a new recording.")
            notifyUser(state.recordingStopReason!!)
            return
        }
        val previousBytes = if (waypoint) state.estimatedReplayBytes else 0L
        task("Preparing ${algorithm.name}…", play = play) { id ->
            runClock = TimeSource.Monotonic.markNow()
            state = state.copy(replayBudgetBytes = limits.maxEstimatedBytes, peakAlgorithmBytes = 0L, runElapsedNanos = 0L, estimatedReplayBytes = previousBytes, revealedFinalPathCost = null, hasCompletedRecording = false)
            val jobLimits = limits.copy(maxFrames = remainingFrames, maxEstimatedBytes = (limits.maxEstimatedBytes - previousBytes).coerceAtLeast(1))
            val remote = openWorker(workerSettings, estimateAlgorithmWork(graph))
            val timeline = if (remote == null) compute {
                AlgorithmRunner.createTimeline(algorithm, graph, start, goal, knowledge,
                    seed = graph.seed, initialKnowledge = if (waypoint) initial else null, limits = jobLimits, retainHistory = false, sensingRadius = sensingRadius)
            } else null
            var published = history
            var first = true
            var finished = false
            var stopReason: String? = null
            var lastPublication = TimeSource.Monotonic.markNow()
            while (!finished) {
                val result = if (remote != null) {
                    if (first) remote.start(algorithm, graph, start, goal, knowledge, jobLimits, if (waypoint) initial else null, sensingRadius)
                    else remote.next()
                } else compute {
                    val local = checkNotNull(timeline)
                    val frames = mutableListOf<SimulationFrame>()
                    val clock = TimeSource.Monotonic.markNow()
                    // Publish a small first batch promptly; subsequent slices are time based.
                    while (frames.size < (if (first) 32 else 2048) &&
                        (frames.isEmpty() || clock.elapsedNow().inWholeMilliseconds < 8)) {
                        currentCoroutineContext().ensureActive()
                        frames += local.generateNext() ?: break
                    }
                    WorkerBatch(frames, local.isExhausted, local.estimatedBytes, local.stopReason, local.peakAlgorithmBytes)
                }
                val batch = if (history.isEmpty()) result.frames else result.frames.map { it.copy(stepIndex = history.size + it.stepIndex) }
                finished = result.done
                stopReason = result.stopReason
                if (id != workId) return@task
                published = published.append(batch)
                pendingRecording = published
                pendingPeakAlgorithmBytes = result.peakAlgorithmBytes
                pendingReplayBytes = previousBytes + result.estimatedBytes
                if (first || finished || lastPublication.elapsedNow().inWholeMilliseconds >= 50) {
                    state = state.copy(frames = published,
                        currentStep = if (first) (history.size + firstStep).coerceAtMost(published.lastIndex.coerceAtLeast(0)) else state.currentStep,
                        hasRunAlgorithm = published.isNotEmpty(),
                        selectedNode = if (first) null else state.selectedNode,
                        selectedEdge = if (first) null else state.selectedEdge,
                        peakAlgorithmBytes = result.peakAlgorithmBytes,
                        estimatedReplayBytes = previousBytes + result.estimatedBytes,
                        runElapsedNanos = runClock?.elapsedNow()?.inWholeNanoseconds,
                        workMessage = "${algorithm.name}: ${published.size} frames recorded${if (remote != null) " · Web Worker" else ""}")
                    lastPublication = TimeSource.Monotonic.markNow()
                    pendingRecording = null
                }
                first = false
                if (!finished) {
                    if (remote == null) cooperate() else yield()
                }
            }
            if (id == workId) state = state.copy(isComputing = false, workMessage = "",
                recordingStopReason = stopReason,
                hasCompletedRecording = stopReason == null,
                statusMessage = stopReason ?: "${algorithm.name} recorded ${published.size - history.size} steps${if (remote != null) " · Web Worker" else ""}")
            if (id == workId) {
                stopReason?.let(::notifyUser)
                revealFinalCostIfViewed()
            }
        }
    }

    fun onEvent(event: RoverVisualizerEvent) {
        if (event == RoverVisualizerEvent.GenerateGraph) {
            state.graphGeneration.validationMessage()?.let { message ->
                state = state.copy(statusMessage = message)
                notifyUser(message)
                return
            }
        }
        when (event) {
            RoverVisualizerEvent.GenerateGraph -> { generateGraph(); return }
            RoverVisualizerEvent.RunAlgorithm -> {
                if (state.algorithm === GoToWaypoint) startSimulation(play = true, waypoint = true) else startSimulation()
                return
            }
            RoverVisualizerEvent.SendWaypoint -> { startSimulation(play = true, waypoint = true); return }
            RoverVisualizerEvent.CancelWork -> { cancelWork(); state = state.copy(statusMessage = "Cancelled. Recorded history is preserved."); return }
            RoverVisualizerEvent.PlayPause -> if (!state.hasRunAlgorithm) {
                if (!state.isComputing) startSimulation(play = true)
                else state = state.copy(isPlaying = !state.isPlaying)
                return
            }
            RoverVisualizerEvent.StepForward -> if (!state.hasRunAlgorithm) { if (!state.isComputing) startSimulation(firstStep = 1); return }
            RoverVisualizerEvent.Restart -> if (!state.hasRunAlgorithm) { if (!state.isComputing) startSimulation(); return }
            else -> Unit
        }
        if ((event is RoverVisualizerEvent.ChangeAlgorithm && event.algorithm != state.algorithm) ||
            (event is RoverVisualizerEvent.ChangeSpaceMode && event.mode != state.spaceMode) ||
            (event is RoverVisualizerEvent.ChangeExplorationMode && event.mode != state.explorationMode) ||
            (event is RoverVisualizerEvent.ChangeSensingRadius && event.radius.coerceIn(sfg.wseminar.rover.simulation.SensingRadius.range) != state.sensingRadius) ||
            event == RoverVisualizerEvent.ResetSimulation) cancelWork()
        state = when (event) {
            is RoverVisualizerEvent.ChangePerformance -> state.copy(performance = event.settings, playbackSpeed = state.playbackSpeed.coerceIn(event.settings.playback.range))
            is RoverVisualizerEvent.ChangeAlgorithm -> {
                if (event.algorithm == state.algorithm) state else state.copy(
                    algorithm = event.algorithm,
                    explorationMode = event.algorithm.defaultExplorationMode(),
                    statusMessage = "Algorithm changed to ${event.algorithm.name}"
                ).withFreshSimulation()
            }

            is RoverVisualizerEvent.ChangeSpaceMode -> {
                if (event.mode == state.spaceMode) state else {
                    val stored = graphRecipes[event.mode]
                    val graph = stored?.first ?: graphCache.latest(event.mode.generationMode())
                    state.copy(
                        spaceMode = event.mode,
                        generatedRecipe = stored?.second,
                        graphGeneration = state.graphGeneration.copy(mode = event.mode),
                        viewport = graph?.let(::fitViewport) ?: state.viewport,
                        statusMessage = if (graph == null) "Generate a graph for ${event.mode.name.lowercase()} space"
                            else "Restored ${event.mode.name.lowercase()} graph"
                    ).withFreshSimulation(graph)
                }
            }

            is RoverVisualizerEvent.ChangeExplorationMode -> {
                if (event.mode == state.explorationMode) state else state.copy(
                    explorationMode = event.mode,
                    statusMessage = "Exploration mode changed to ${event.mode}"
                ).withFreshSimulation()
            }

            is RoverVisualizerEvent.ChangeSensingRadius -> {
                val radius = event.radius.coerceIn(sfg.wseminar.rover.simulation.SensingRadius.range)
                if (radius == state.sensingRadius) state else state.copy(
                    sensingRadius = radius,
                    statusMessage = "Sensing radius changed; exploration restarted."
                ).withFreshSimulation()
            }

            RoverVisualizerEvent.GenerateGraph, RoverVisualizerEvent.RunAlgorithm,
            RoverVisualizerEvent.SendWaypoint, RoverVisualizerEvent.CancelWork -> state

            is RoverVisualizerEvent.ChangeGraphGeneration -> {
                state.copy(
                    graphGeneration = event.generation,
                    statusMessage = "Graph generation settings updated"
                )
            }

            is RoverVisualizerEvent.ChangeGraphAppearance -> state.copy(
                options = state.options.copy(appearance = event.appearance.constrained())
            )

            RoverVisualizerEvent.RandomizeGraphGenerationSeed -> {
                state.copy(
                    graphGeneration = state.graphGeneration.copy(
                        seed = Random.nextInt(0, 100_000)
                    ),
                    statusMessage = "Randomized graph seed"
                )
            }

            RoverVisualizerEvent.PlayPause -> {
                if (!state.hasRunAlgorithm) {
                    onEvent(RoverVisualizerEvent.RunAlgorithm)
                }

                state.copy(isPlaying = state.hasRunAlgorithm && !state.isPlaying)
            }

            RoverVisualizerEvent.StepForward -> {
                if (!state.hasRunAlgorithm) {
                    onEvent(RoverVisualizerEvent.RunAlgorithm)
                }

                state.copy(
                    isPlaying = false,
                    currentStep = (state.currentStep + 1).coerceAtMost(state.maxStep)
                )
            }

            RoverVisualizerEvent.StepBackward -> {
                state.copy(
                    isPlaying = false,
                    currentStep = (state.currentStep - 1).coerceAtLeast(0)
                )
            }

            RoverVisualizerEvent.Restart -> {
                if (!state.hasRunAlgorithm) {
                    onEvent(RoverVisualizerEvent.RunAlgorithm)
                }

                state.copy(
                    isPlaying = false,
                    currentStep = 0
                )
            }

            is RoverVisualizerEvent.SeekToStep -> {
                state.copy(
                    isPlaying = false,
                    currentStep = event.step.coerceIn(0, state.maxStep)
                )
            }

            is RoverVisualizerEvent.SetPlaybackSpeed -> {
                state.copy(
                    playbackSpeed = if (event.speed.isFinite()) event.speed.coerceIn(state.performance.playback.range) else state.playbackSpeed
                )
            }

            is RoverVisualizerEvent.ToggleNodeIds -> {
                state.copy(options = state.options.copy(showNodeIds = event.enabled))
            }

            is RoverVisualizerEvent.ToggleEdgeWeights -> {
                state.copy(options = state.options.copy(showEdgeWeights = event.enabled))
            }

            is RoverVisualizerEvent.ToggleCosts -> {
                state.copy(options = state.options.copy(showCosts = event.enabled))
            }

            is RoverVisualizerEvent.ToggleCostsInsideNodes -> {
                state.copy(
                    options = state.options.copy(showCostsInsideNodes = event.enabled),
                    statusMessage = if (event.enabled) {
                        "Costs are shown inside graph nodes"
                    } else {
                        "Costs are shown next to graph nodes"
                    }
                )
            }

            is RoverVisualizerEvent.ChangeNodeColoringMode -> {
                state.copy(
                    options = state.options.copy(nodeColoringMode = event.mode),
                    statusMessage = "Node coloring changed to ${event.mode.label.lowercase()}"
                )
            }

            is RoverVisualizerEvent.ToggleHeuristics -> {
                state.copy(options = state.options.copy(showHeuristics = event.enabled))
            }

            is RoverVisualizerEvent.ToggleFrontier -> {
                state.copy(options = state.options.copy(showFrontier = event.enabled))
            }

            is RoverVisualizerEvent.ToggleVisited -> {
                state.copy(options = state.options.copy(showVisited = event.enabled))
            }

            is RoverVisualizerEvent.SetFinalPathScale -> {
                if (event.scale.isFinite()) state.copy(options = state.options.copy(finalPathScale = event.scale.coerceIn(0f, 5f))) else state
            }

            is RoverVisualizerEvent.ToggleFinalPath -> {
                state.copy(options = state.options.copy(showFinalPath = event.enabled))
            }

            is RoverVisualizerEvent.ToggleSensorRadius -> {
                state.copy(options = state.options.copy(showSensorRadius = event.enabled))
            }

            is RoverVisualizerEvent.ToggleGridLines -> {
                state.copy(options = state.options.copy(showGridLines = event.enabled))
            }

            is RoverVisualizerEvent.ToggleCoordinates -> {
                state.copy(options = state.options.copy(showCoordinates = event.enabled))
            }

            is RoverVisualizerEvent.ToggleLegend -> {
                state.copy(options = state.options.copy(showLegend = event.enabled))
            }

            is RoverVisualizerEvent.ToggleNodeLegend -> {
                state.copy(options = state.options.copy(showNodeLegend = event.enabled))
            }

            is RoverVisualizerEvent.TogglePathLegend -> {
                state.copy(options = state.options.copy(showPathLegend = event.enabled))
            }

            is RoverVisualizerEvent.ToggleKnowledgeLegend -> {
                state.copy(options = state.options.copy(showKnowledgeLegend = event.enabled))
            }

            is RoverVisualizerEvent.SetLegendEdgePosition -> {
                state.copy(
                    legendEdgePosition = event.position.coerceIn(-1f, 1f)
                )
            }

            RoverVisualizerEvent.ResetLegendPosition -> {
                state.copy(
                    legendEdgePosition = 0f,
                    statusMessage = "Legend position reset"
                )
            }

            is RoverVisualizerEvent.ToggleUnknownArea -> {
                state.copy(options = state.options.copy(showUnknownArea = event.enabled))
            }

            is RoverVisualizerEvent.ExportCurrentFrame -> {
                state // Handled by the screen, which owns the canvas renderer and save dialog.
            }
            is RoverVisualizerEvent.ChangeViewport -> {
                state.copy(
                    viewport = state.viewport.copy(
                        zoom = event.zoom.coerceIn(MinZoom, MaxZoom),
                        panX = event.panX,
                        panY = event.panY
                    )
                )
            }

            is RoverVisualizerEvent.CanvasSizeChanged -> {
                state.copy(
                    canvasSize = event.size
                )
            }

            RoverVisualizerEvent.ResetSimulation -> state.withFreshSimulation().copy(statusMessage = "Simulation reset")

            is RoverVisualizerEvent.ResetViewport -> {
                state.copy(
                    viewport = ViewportState(),
                    statusMessage = "Viewport reset"
                )
            }

            RoverVisualizerEvent.FitViewportToGraph -> {
                val frame = state.currentFrame

                if (frame == null || state.canvasSize.width == 0 || state.canvasSize.height == 0) {
                    state.copy(
                        statusMessage = "Cannot fit view: no graph loaded"
                    )
                } else {
                    state.copy(
                        viewport = calculateFitViewport(
                            graph = frame.graph,
                            canvasSize = state.canvasSize
                        ),
                        statusMessage = "Fit graph to view"
                    )
                }
            }

            is RoverVisualizerEvent.SelectNode -> {
                state.copy(
                    selectedNode = event.node,
                    selectedEdge = null,
                    isPlaying = if (state.algorithm === GoToWaypoint) false else state.isPlaying,
                    statusMessage = if (event.node == null) {
                        "Node selection cleared"
                    } else {
                        "Selected node ${event.node.label}"
                    }
                )
            }

            is RoverVisualizerEvent.SelectEdge -> {
                state.copy(
                    selectedNode = null,
                    selectedEdge = event.edge,
                    statusMessage = if (event.edge == null) {
                        "Edge selection cleared"
                    } else {
                        "Selected edge ${event.edge.label}"
                    }
                )
            }

            RoverVisualizerEvent.ClearSelection -> {
                state.copy(
                    selectedNode = null,
                    selectedEdge = null,
                    statusMessage = "Selection cleared"
                )
            }
        }
        revealFinalCostIfViewed()
    }

    /** Reset transient run data without losing topology, endpoints, or the viewport. */
    private fun VisualizerState.withFreshSimulation(graph: Graph? = generatedGraph): VisualizerState = copy(
        generatedGraph = graph,
        frames = graph?.let {
            val ready = InitialSimulationFrameFactory.create(it, explorationMode.knowledgeMode(), sensingRadius)
            listOf(if (algorithm === GoToWaypoint) ready.copy(
                goalNode = null,
                message = "Waiting for a waypoint. Select a destination on the canvas, then choose Send waypoint."
            ) else ready)
        } ?: emptyList(),
        currentStep = 0,
        isPlaying = false,
        hasRunAlgorithm = false,
        recordingStopReason = null,
        estimatedReplayBytes = 0,
        peakAlgorithmBytes = null,
        runElapsedNanos = null,
        revealedFinalPathCost = null,
        hasCompletedRecording = false,
        selectedNode = null,
        selectedEdge = null
    )

    private fun fitViewport(graph: Graph): ViewportState =
        if (state.canvasSize.width > 0 && state.canvasSize.height > 0) {
            calculateFitViewport(graph, state.canvasSize)
        } else state.viewport

    fun tickPlayback(steps: Int = 1) {
        if (!state.isPlaying) return

        if (state.currentStep >= state.maxStep) {
            if (!state.isComputing) state = state.copy(isPlaying = false)
        } else {
            state = state.copy(currentStep = (state.currentStep.toLong() + steps.coerceAtLeast(0)).coerceAtMost(state.maxStep.toLong()).toInt())
        }
        revealFinalCostIfViewed()
    }
}

private fun SpaceMode.generationMode(): GraphGenerationMode = when (this) {
    SpaceMode.DISCRETE -> GraphGenerationMode.GRID
    SpaceMode.CONTINUOUS -> GraphGenerationMode.CONTINUOUS
}

private fun ExplorationMode.knowledgeMode(): KnowledgeMode = when (this) {
    ExplorationMode.GLOBAL -> KnowledgeMode.GLOBAL
    ExplorationMode.LOCAL -> KnowledgeMode.LOCAL
}
