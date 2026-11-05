package sfg.wseminar.rover.ui.states

import sfg.wseminar.rover.algorithms.ContinuousAlgorithm
import sfg.wseminar.rover.algorithms.DiscreteAlgorithm
import sfg.wseminar.rover.algorithms.RoverAlgorithm


enum class SpaceMode {
    DISCRETE,
    CONTINUOUS;

    fun nameForUI() = when (this) {
        DISCRETE -> "Discrete"
        CONTINUOUS -> "Continuous"
    }

    companion object {
        fun bothSupportedDefault() = CONTINUOUS
    }
}

fun RoverAlgorithm.supports(spaceType: SpaceMode) =
    when (spaceType) {
        SpaceMode.DISCRETE -> this is DiscreteAlgorithm
        SpaceMode.CONTINUOUS -> this is ContinuousAlgorithm
    }

fun RoverAlgorithm.isDiscrete() = supports(SpaceMode.DISCRETE)
fun RoverAlgorithm.isContinuous() = supports(SpaceMode.CONTINUOUS)


fun RoverAlgorithm.defaultSpaceMode(): SpaceMode {
    val isDiscrete = isDiscrete()
    val isContinuous = isContinuous()

    return when {
        isDiscrete && !isContinuous -> SpaceMode.DISCRETE
        isContinuous && !isDiscrete -> SpaceMode.CONTINUOUS
        else -> SpaceMode.bothSupportedDefault()
    }
}
