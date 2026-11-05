package sfg.wseminar.rover.algorithms

import sfg.wseminar.rover.algorithms.backend.AlgorithmContext
import sfg.wseminar.rover.algorithms.backend.AlgorithmStep
import sfg.wseminar.rover.ui.states.SpaceMode
import sfg.wseminar.rover.ui.states.isContinuous
import sfg.wseminar.rover.ui.states.isDiscrete
import sfg.wseminar.rover.ui.states.isGlobal
import sfg.wseminar.rover.ui.states.isLocal


abstract class RoverAlgorithm {
    open val name: String = this::class.simpleName ?: "Unknown"

    fun supports(other: RoverAlgorithm): Boolean =
        ((isContinuous() && other.isContinuous()) || (isDiscrete() && other.isDiscrete()))
                && ((isLocal() && other.isLocal()) || (isGlobal() && other.isGlobal()))

    override fun toString(): String = name

    open val description: String = "Finds a shortest path on the weighted graph."
    open val costMeaning: String = "Cost from start"
    abstract fun run(context: AlgorithmContext): Sequence<AlgorithmStep>

    companion object {
        // Algorithm objects extend this class; eager initialization here can read
        // a subclass INSTANCE before its constructor has completed on the JVM.
        val algorithms: List<RoverAlgorithm> by lazy { listOf(Dijkstra, AStar, GoToWaypoint) }

        fun defaultFor(spaceMode: SpaceMode): RoverAlgorithm =
            when (spaceMode) {
                SpaceMode.DISCRETE -> Dijkstra
                SpaceMode.CONTINUOUS -> null
            } ?: algorithms.first()
    }
}
