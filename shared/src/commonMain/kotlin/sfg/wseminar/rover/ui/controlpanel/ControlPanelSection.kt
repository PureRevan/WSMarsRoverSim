package sfg.wseminar.rover.ui.controlpanel

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.components.SectionHeader

@Composable
fun ControlPanelSection(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val language = sfg.wseminar.rover.i18n.LocalLanguage.current
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180))
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(Modifier.padding(horizontal = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .semantics { stateDescription = sfg.wseminar.rover.i18n.translate(if (expanded) "Expanded" else "Collapsed", language) }
                .clickable(
                    role = Role.Button,
                    onClickLabel = sfg.wseminar.rover.i18n.translate(if (expanded) "Collapse $title" else "Expand $title", language)
                ) { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                SectionHeader(title = title, icon = icon, modifier = Modifier.weight(1f))
            } else {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                modifier = Modifier.rotate(arrowRotation),
                contentDescription = null
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180), expandFrom = Alignment.Top) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(160), shrinkTowards = Alignment.Top) + fadeOut(tween(100))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                content()
            }
        }
    }
}
