package sfg.wseminar.rover.ui

import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.ui.states.VisualizerOptions


interface RoverSceneRenderer {
    fun drawScene(
        frame: SimulationFrame,
        options: VisualizerOptions
    )
}
