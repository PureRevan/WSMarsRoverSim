package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.input.pointer.PointerEvent

/** Scroll in mouse-wheel steps, with the platform's native units accounted for. */
internal expect fun PointerEvent.wheelScrollSteps(): Float
