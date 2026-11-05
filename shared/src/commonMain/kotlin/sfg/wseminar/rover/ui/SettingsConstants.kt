package sfg.wseminar.rover.ui

object CanvasConstants {
    // Higher -> More.
    const val PanSpeed = 3.5f
    // Exponent per wheel step; browser pixel/line deltas are normalized first.
    const val WheelZoomSensitivity = 0.045f
    const val MaxWheelStepsPerEvent = 3f

    // Target distance between visible major grid lines in pixels.
    const val TargetMajorGridSpacingPx = 96f
    // Minimum distance before minor grid lines are hidden.
    const val MinimumMinorGridSpacingPx = 12f

    const val MinZoom: Float = 0.000001f
    const val MaxZoom: Float = 100f
}
