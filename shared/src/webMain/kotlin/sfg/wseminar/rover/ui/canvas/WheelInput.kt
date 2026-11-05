package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.input.pointer.PointerEvent
import org.w3c.dom.events.WheelEvent

internal actual fun PointerEvent.wheelScrollSteps(): Float {
    // Compose Web passes through deltaY without accounting for deltaMode. Keep
    // Compose's vertical delta so horizontal/Shift scrolling does not also zoom.
    val delta = changes.firstOrNull()?.scrollDelta?.y ?: 0f
    val mode = (nativeEvent as? WheelEvent)?.deltaMode ?: 0
    return normalizeBrowserWheelDelta(delta, mode)
}
