package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

val topSmallPaddingValues = PaddingValues(top = MaterialTheme.padding.small)

const val DISABLED_ALPHA = .38f
const val SECONDARY_ALPHA = .78f

/**
 * The spacing scale of the Neon design language: roomier than Material's, so screens breathe.
 * See docs/design-system.md, section 3.
 */
class Padding {

    val extraLarge = 40.dp

    val large = 28.dp

    val medium = 20.dp

    val small = 12.dp

    val extraSmall = 6.dp
}

val MaterialTheme.padding: Padding
    get() = Padding()
