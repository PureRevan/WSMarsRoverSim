package sfg.wseminar.rover.ui.states

import sfg.wseminar.rover.algorithms.GlobalAlgorithm
import sfg.wseminar.rover.algorithms.LocalAlgorithm
import sfg.wseminar.rover.algorithms.RoverAlgorithm


enum class ExplorationMode {
    LOCAL,
    GLOBAL;

    fun nameForUI() = when (this) {
        LOCAL -> "Local"
        GLOBAL -> "Global"
    }

    companion object {
        fun bothSupportedDefault() = LOCAL
    }
}

fun RoverAlgorithm.supports(infoType: ExplorationMode) =
    when (infoType) {
        ExplorationMode.LOCAL -> this is LocalAlgorithm
        ExplorationMode.GLOBAL -> this is GlobalAlgorithm
    }

fun RoverAlgorithm.isLocal() = supports(ExplorationMode.LOCAL)
fun RoverAlgorithm.isGlobal() = supports(ExplorationMode.GLOBAL)


fun RoverAlgorithm.defaultExplorationMode(): ExplorationMode {
    val isLocal = isLocal()
    val isGlobal = isGlobal()

    return when {
        isLocal && !isGlobal -> ExplorationMode.LOCAL
        isGlobal && !isLocal -> ExplorationMode.GLOBAL
        else -> ExplorationMode.bothSupportedDefault()
    }
}
