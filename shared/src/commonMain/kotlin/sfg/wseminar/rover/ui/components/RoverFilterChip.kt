package sfg.wseminar.rover.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Standard Material chip with a persistent outline and restrained accent-colored feedback. */
@Composable
fun RoverFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration(
        color = colors.primary,
        rippleAlpha = RippleAlpha(draggedAlpha = 0.12f, focusedAlpha = 0.12f, hoveredAlpha = 0.05f, pressedAlpha = 0.09f)
    )) {
        FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled, leadingIcon = leadingIcon,
            border = BorderStroke(1.dp, when {
                !enabled -> colors.outline.copy(alpha = 0.38f)
                selected -> colors.primary.copy(alpha = 0.75f)
                else -> colors.outlineVariant
            }),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.primaryContainer,
                selectedLabelColor = colors.onPrimaryContainer
            ))
    }
}
