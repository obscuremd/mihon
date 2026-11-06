package eu.kanade.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Tokens of the Neon design language. See docs/design-system.md.
 */

/**
 * Rounder corners across the app, for the card-heavy Neon look.
 */
internal val NeonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Material type scale with bolder titles and headlines, so screen and section headers stand out.
 */
internal val NeonTypography: Typography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** The spacing scale. */
object NeonSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp

    /** The space between the screen's edges and its content. */
    val gutter = 20.dp
    val xl = 24.dp

    /** The space between sections of a screen. */
    val section = 32.dp
    val xxxl = 48.dp

    val gridColumnGap = 14.dp
    val gridRowGap = 22.dp
    val rowGap = 8.dp
}

object NeonDefaults {
    /** A card holding a cover and its title. */
    val CardShape: Shape = RoundedCornerShape(16.dp)

    /** A cover inside a card. */
    val CoverShape: Shape = RoundedCornerShape(12.dp)

    /** A large featured card. */
    val HeroShape: Shape = RoundedCornerShape(24.dp)

    /** A fully rounded control, like a button, chip, search field or segmented tab row. */
    val PillShape: Shape = RoundedCornerShape(percent = 50)

    /** Alpha of an accent's tint fill at rest. */
    const val TINT_ALPHA = 0.12f

    /** Alpha of an accent's tint fill when selected or pressed. */
    const val TINT_SELECTED_ALPHA = 0.2f

    /**
     * The neon stroke for [accent]: from a lighter end to the accent itself. Light themes lighten toward the
     * background rather than white, so the stroke stays visible on a light canvas.
     */
    @Composable
    @ReadOnlyComposable
    fun stroke(accent: Color = MaterialTheme.colorScheme.primary): Brush {
        val background = MaterialTheme.colorScheme.background
        val lightEnd = if (background.luminance() < 0.5f) {
            lerp(accent, Color.White, 0.55f)
        } else {
            lerp(accent, background, 0.3f)
        }
        return Brush.horizontalGradient(listOf(lightEnd, accent))
    }

    /** The neon stroke of the theme's accent. */
    val accentStroke: Brush
        @Composable
        @ReadOnlyComposable
        get() = stroke()

    /** The thin outline cards and controls get. */
    val outlineColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    /** The quiet border of cards and rows. */
    val hairline: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
}

/**
 * A soft glow of [color] around [shape]. Colored shadows show from Android 9, a neutral one before.
 */
fun Modifier.neonGlow(shape: Shape, color: Color, elevation: Dp = 10.dp): Modifier =
    shadow(elevation = elevation, shape = shape, clip = false, ambientColor = color, spotColor = color)

/**
 * A thin [color] outline along [shape].
 */
fun Modifier.neonOutline(shape: Shape, color: Color, width: Dp = 1.dp): Modifier = border(width, color, shape)

/**
 * The neon pattern: clipped to [shape], a transparent tint of [accent] behind, and its gradient stroke around.
 * Selected surfaces get a stronger tint and a thicker stroke.
 */
@Composable
fun Modifier.neonSurface(
    shape: Shape,
    accent: Color = MaterialTheme.colorScheme.primary,
    selected: Boolean = false,
): Modifier {
    val stroke = NeonDefaults.stroke(accent)
    val alpha = if (selected) NeonDefaults.TINT_SELECTED_ALPHA else NeonDefaults.TINT_ALPHA
    return this
        .clip(shape)
        .background(accent.copy(alpha = alpha))
        .border(if (selected) 1.5.dp else 1.dp, stroke, shape)
}

/**
 * A quiet card surface: clipped to [shape], [color] behind, and a hairline around.
 */
@Composable
fun Modifier.cardSurface(
    shape: Shape = NeonDefaults.CardShape,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
): Modifier = this
    .clip(shape)
    .background(color)
    .border(1.dp, NeonDefaults.hairline, shape)
