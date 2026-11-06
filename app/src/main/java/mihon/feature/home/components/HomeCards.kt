package mihon.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.neon.GhostButton
import eu.kanade.presentation.components.neon.NeonChip
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.theme.NeonDefaults
import eu.kanade.presentation.theme.NeonSpacing
import eu.kanade.presentation.theme.neonGlow
import eu.kanade.presentation.theme.neonOutline
import mihon.feature.home.Genres
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel

/** The cover of [manga] for loading with Coil. */
fun Manga.coverData(): MangaCoverModel = MangaCoverModel(
    mangaId = id,
    sourceId = source,
    isMangaFavorite = favorite,
    url = thumbnailUrl,
    lastModified = coverLastModified,
)

/** The first [count] genres of [manga] as shown, like "Action · Adventure", or null when it has none. */
fun Manga.genreLine(count: Int = 2): String? =
    Genres.normalizeAll(genre).take(count).joinToString(" · ") { Genres.label(it) }.ifEmpty { null }

/**
 * A cover with its title and genres under it, as rows and grids of the home and discover screens show entries.
 * [inLibrary] marks entries already saved.
 */
@Composable
fun HomeMangaCard(
    manga: Manga,
    fallbackSubtitle: String?,
    inLibrary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 128.dp,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .clip(NeonDefaults.CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box {
            MangaCover.Book(
                data = manga.coverData(),
                shape = NeonDefaults.CoverShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .neonGlow(NeonDefaults.CoverShape, primary.copy(alpha = 0.45f), 6.dp)
                    .neonOutline(NeonDefaults.CoverShape, primary.copy(alpha = 0.25f)),
            )
            if (inLibrary) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(10.dp)
                        .clip(NeonDefaults.PillShape)
                        .background(primary)
                        .align(Alignment.TopStart),
                )
            }
        }
        Text(
            text = manga.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        val subtitle = manga.genreLine() ?: fallbackSubtitle
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

/**
 * A row's title with an accent bar before it, a line under it, and a "See all" pill when there's more.
 */
@Composable
fun HomeSectionHeader(
    title: String,
    subtitle: String?,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NeonSpacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (subtitle != null) 38.dp else 22.dp)
                .clip(NeonDefaults.PillShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onSeeAll != null) {
            Spacer(Modifier.width(NeonSpacing.xs))
            GhostButton(
                text = stringResource(MR.strings.home_see_all),
                onClick = onSeeAll,
                trailingIcon = MaterialSymbols.Rounded.KeyboardArrowRight,
                contentColor = MaterialTheme.colorScheme.primary,
                compact = true,
            )
        }
    }
}

/**
 * The accent of genre [key]: each common genre has its own hue, as the reference design gives them, toned to the
 * theme so it reads on light and dark backgrounds alike. Other genres get a hue from their name.
 */
@Composable
fun genreAccent(key: String): Color {
    val hue = GENRE_HUES[key] ?: FALLBACK_HUES[Math.floorMod(key.hashCode(), FALLBACK_HUES.size)]
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) hue else lerp(hue, Color.Black, 0.3f)
}

/**
 * A genre as a small outlined chip in its accent color.
 */
@Composable
fun GenreChip(key: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    NeonChip(
        text = Genres.label(key),
        accent = genreAccent(key),
        onClick = onClick,
        modifier = modifier,
    )
}

private val GENRE_HUES = mapOf(
    "action" to Color(0xFFEF5350),
    "romance" to Color(0xFFEC407A),
    "fantasy" to Color(0xFF5C7CFA),
    "horror" to Color(0xFF8D6E63),
    "comedy" to Color(0xFFFFD54F),
    "drama" to Color(0xFFAB47BC),
    "sci-fi" to Color(0xFF26C6DA),
    "adventure" to Color(0xFFFFA726),
    "slice of life" to Color(0xFF66BB6A),
    "mystery" to Color(0xFF90A4AE),
    "isekai" to Color(0xFF7E57C2),
    "sports" to Color(0xFF29B6F6),
    "thriller" to Color(0xFFE57373),
    "psychological" to Color(0xFF9575CD),
    "supernatural" to Color(0xFF4DB6AC),
    "historical" to Color(0xFFBCAAA4),
    "school life" to Color(0xFF81C784),
    "martial arts" to Color(0xFFFF7043),
)

private val FALLBACK_HUES = listOf(
    Color(0xFF4FC3F7),
    Color(0xFFF06292),
    Color(0xFFAED581),
    Color(0xFFFFB74D),
    Color(0xFF9FA8DA),
    Color(0xFF80CBC4),
    Color(0xFFCE93D8),
    Color(0xFFFFF176),
)
