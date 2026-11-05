package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.input.pointer.PointerEvent

// Compose Desktop already supplies AWT's preciseWheelRotation (fractional steps).
internal actual fun PointerEvent.wheelScrollSteps(): Float =
    changes.firstOrNull()?.scrollDelta?.y ?: 0f
