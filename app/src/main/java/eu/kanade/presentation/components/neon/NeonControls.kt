package eu.kanade.presentation.components.neon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.NeonDefaults
import eu.kanade.presentation.theme.cardSurface
import eu.kanade.presentation.theme.neonGlow
import eu.kanade.presentation.theme.neonSurface

/*
 * The buttons and chips of the Neon design language. See docs/design-system.md, section 8.
 */

/**
 * The main action of a surface: a pill with a transparent tint of [accent] and its neon stroke. Only the one
 * primary action of a screen should [glow].
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glow: Boolean = false,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .then(
                if (glow &&
                    enabled
                ) {
                    Modifier.neonGlow(NeonDefaults.PillShape, accent.copy(alpha = 0.35f))
                } else {
                    Modifier
                },
            )
            .neonSurface(NeonDefaults.PillShape, accent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 18.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A secondary action: a pill on the surface with a hairline border.
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .cardSurface(NeonDefaults.PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = if (compact) 34.dp else 44.dp)
            .padding(start = if (compact) 14.dp else 18.dp, end = if (trailingIcon != null) 10.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * An icon action: a rounded square on the surface, with the neon stroke and accent icon when [active].
 */
@Composable
fun NeonIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
) {
    val shape = NeonDefaults.CoverShape
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(
                if (active) {
                    colors.primary.copy(
                        alpha = NeonDefaults.TINT_ALPHA,
                    )
                } else {
                    colors.surfaceContainerHigh.copy(alpha = 0.85f)
                },
            )
            .then(
                if (active) {
                    Modifier.border(1.5.dp, NeonDefaults.accentStroke, shape)
                } else {
                    Modifier.border(1.dp, NeonDefaults.hairline, shape)
                },
            )
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) colors.primary else colors.onSurface
        if (badge != null) {
            BadgedBox(badge = { badge() }) {
                Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
            }
        } else {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * A chip in [accent]: its tint and stroke, stronger when [selected].
 */
@Composable
fun NeonChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .neonSurface(NeonDefaults.PillShape, accent, selected)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides accent) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
