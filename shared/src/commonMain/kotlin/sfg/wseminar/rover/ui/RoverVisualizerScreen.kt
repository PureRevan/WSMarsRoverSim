package sfg.wseminar.rover.ui

import sfg.wseminar.rover.ui.settings.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import sfg.wseminar.rover.export.FrameImageCapture
import sfg.wseminar.rover.transfer.*
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import kotlinx.coroutines.delay
import sfg.wseminar.rover.ui.graph.GraphGenerationDialog
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.ChangeGraphGeneration
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import sfg.wseminar.rover.ui.controlpanel.ControlPanel
import sfg.wseminar.rover.ui.canvas.RoverCanvasPanel
import sfg.wseminar.rover.ui.infopanel.InfoPanel
import sfg.wseminar.rover.ui.timeline.TimelineControls
import sfg.wseminar.rover.ui.topbar.RoverTopBar
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun RoverVisualizerScreen(
    theme: ThemePreset = ThemePreset.PAPER,
    onThemeChange: (ThemePreset) -> Unit = {},
    allowCompactLayout: Boolean = false
) {
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val viewModel = remember { VisualizerViewModel(scope = scope) }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    val state = viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.noticeId, state.performance.language) {
        snackbarHostState.currentSnackbarData?.dismiss()
        state.noticeMessage?.let { reason ->
            snackbarHostState.showSnackbar(sfg.wseminar.rover.i18n.translate(reason, state.performance.language), actionLabel = sfg.wseminar.rover.i18n.translate("Dismiss", state.performance.language), duration = SnackbarDuration.Indefinite)
        }
    }
    val imageCapture = remember { FrameImageCapture() }
    var exportKind by remember { mutableStateOf<ExportKind?>(null) }
    var showImport by remember { mutableStateOf(false) }
    val exported: (String) -> Unit = { message ->
        exportKind = null
        scope.launch { snackbarHostState.currentSnackbarData?.dismiss(); snackbarHostState.showSnackbar(sfg.wseminar.rover.i18n.translate(message, state.performance.language), actionLabel = sfg.wseminar.rover.i18n.translate("Dismiss", state.performance.language)) }
    }
    var showGraphGenerationDialog by remember { mutableStateOf(false) }
    var compactPanel by remember { mutableStateOf<CompactPanel?>(null) }
    val openGeneration = {
        compactPanel = null
        viewModel.onEvent(ChangeGraphGeneration(state.graphGeneration.copy(mode = state.spaceMode)))
        showGraphGenerationDialog = true
    }
    val openImport = {
        if (state.isPlaying) viewModel.onEvent(RoverVisualizerEvent.SeekToStep(state.currentStep))
        showImport = true
    }
    val openExport: (ExportKind) -> Unit = { kind ->
        if (state.isPlaying) viewModel.onEvent(RoverVisualizerEvent.SeekToStep(state.currentStep))
        exportKind = kind
    }

    LaunchedEffect(state.isPlaying, state.playbackSpeed) {
        var previous = kotlin.time.TimeSource.Monotonic.markNow()
        var pendingSteps = 0.0
        while (state.isPlaying) {
            delay(16.milliseconds)
            pendingSteps += previous.elapsedNow().inWholeNanoseconds / 1_000_000_000.0 * state.playbackSpeed
            previous = kotlin.time.TimeSource.Monotonic.markNow()
            if (pendingSteps >= 1.0) {
                val steps = pendingSteps.coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
                viewModel.tickPlayback(steps)
                pendingSteps -= steps
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(sfg.wseminar.rover.i18n.LocalLanguage provides state.performance.language) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = allowCompactLayout && (maxWidth < 1100.dp || maxHeight < 560.dp)
            LaunchedEffect(compact) { if (!compact) compactPanel = null }
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (compact) {
                    CompactVisualizerBar(state, viewModel::onEvent, { compactPanel = it }, openGeneration,
                        openImport, openExport, { showSettings = true })
                } else RoverTopBar(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onImport = {
                        if (state.isPlaying) viewModel.onEvent(RoverVisualizerEvent.SeekToStep(state.currentStep))
                        showImport = true
                    },
                    onExport = { kind ->
                        if (state.isPlaying) viewModel.onEvent(RoverVisualizerEvent.SeekToStep(state.currentStep))
                        exportKind = kind
                    },
                    onOpenSettings = { showSettings = true },
                    onOpenGraphGeneration = {
                        viewModel.onEvent(
                            ChangeGraphGeneration(
                                state.graphGeneration.copy(
                                    mode = state.spaceMode
                                )
                            )
                        )
                        showGraphGenerationDialog = true
                    }
                )

                Row(modifier = Modifier.weight(1f)) {
                    if (!compact) ControlPanel(
                        state = state,
                        onEvent = viewModel::onEvent,
                        onOpenGraphGeneration = {
                            viewModel.onEvent(ChangeGraphGeneration(state.graphGeneration.copy(mode = state.spaceMode)))
                            showGraphGenerationDialog = true
                        },
                        modifier = Modifier.weight(0.24f)
                    )

                    RoverCanvasPanel(
                        state = state,
                        imageCapture = imageCapture,
                        onEvent = viewModel::onEvent,
                        onOpenGraphGeneration = openGeneration,
                        compact = compact,
                        modifier = Modifier.weight(if (compact) 1f else 0.52f)
                    )

                    if (!compact) InfoPanel(
                        state = state,
                        onEvent = viewModel::onEvent,
                        modifier = Modifier.weight(0.24f)
                    )
                }

                TimelineControls(
                    state = state,
                    onEvent = viewModel::onEvent,
                    compact = compact
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
            )
            if (compact) compactPanel?.let { panel ->
                CompactPanelDialog(panel, onDismiss = { compactPanel = null }) {
                    when (panel) {
                        CompactPanel.CONFIGURATION -> ControlPanel(state, viewModel::onEvent, openGeneration, showHeading = false)
                        CompactPanel.DETAILS -> InfoPanel(state, viewModel::onEvent, showHeading = false)
                    }
                }
            }
            when (val kind = exportKind) {
                ExportKind.IMAGE -> ImageExportDialog(imageCapture, state.currentStep, state.options, { exportKind = null }, exported)
                ExportKind.GRAPH, ExportKind.SETTINGS -> BundleExportDialog(kind, state, theme, { exportKind = null }, exported)
                null -> Unit
            }
            if (showImport) {
                BundleImportDialog(state, onImport = { imported ->
                    viewModel.importPrepared(imported)
                    imported.theme?.let(onThemeChange)
                }, onDismiss = { showImport = false }, onImported = {
                    showImport = false
                    scope.launch { snackbarHostState.currentSnackbarData?.dismiss(); snackbarHostState.showSnackbar(sfg.wseminar.rover.i18n.translate("Import complete", state.performance.language), actionLabel = sfg.wseminar.rover.i18n.translate("Dismiss", state.performance.language)) }
                })
            }
            if (showSettings) {
                SettingsDialog(theme, state.performance, onApply = { selectedTheme, performance ->
                    onThemeChange(selectedTheme)
                    viewModel.onEvent(RoverVisualizerEvent.ChangePerformance(performance))
                    showSettings = false
                }, onDismiss = { showSettings = false })
            }
            if (showGraphGenerationDialog) {
                GraphGenerationDialog(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onDismissRequest = {
                        showGraphGenerationDialog = false
                    }
                )
            }
        }
    }
    }
}
