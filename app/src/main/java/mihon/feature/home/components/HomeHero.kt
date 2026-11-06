package mihon.feature.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.neon.NeonButton
import eu.kanade.presentation.components.neon.NeonIconButton
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.theme.NeonDefaults
import eu.kanade.presentation.theme.NeonSpacing
import eu.kanade.presentation.theme.neonGlow
import eu.kanade.presentation.theme.neonOutline
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.delay
import mihon.feature.home.Genres
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds
import mihon.icons.materialsymbols.rounded.Bookmark as BookmarkOutline
import mihon.icons.materialsymbols.roundedfilled.Bookmark as BookmarkFilled

/**
 * The featured entries across the top of the home screen: each with its cover blurred behind it, fading into the
 * theme's background, its genres, synopsis and buttons to read it or save it. It turns to the next entry every few
 * seconds until touched.
 */
@Composable
fun HomeHero(
    items: List<Manga>,
    sourceNames: Map<Long, String>,
    added: Set<Long>,
    onOpen: (Manga) -> Unit,
    onRead: (Manga) -> Unit,
    onAddToLibrary: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { items.size }
    AutoAdvance(pagerState, items.size)
    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            key = { items[it].id },
            modifier = Modifier
                .fillMaxWidth()
                .height(HERO_HEIGHT),
        ) { page ->
            val manga = items[page]
            HeroPage(
                manga = manga,
                sourceName = sourceNames[manga.source],
                inLibrary = manga.favorite || manga.id in added,
                onOpen = { onOpen(manga) },
                onRead = { onRead(manga) },
                onAddToLibrary = { onAddToLibrary(manga) },
            )
        }
        if (items.size > 1) {
            PagerDots(
                count = items.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun AutoAdvance(pagerState: PagerState, count: Int) {
    if (count < 2) return
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pagerState, dragged) {
        if (dragged) return@LaunchedEffect
        while (true) {
            delay(6.seconds)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % count)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroPage(
    manga: Manga,
    sourceName: String?,
    inLibrary: Boolean,
    onOpen: () -> Unit,
    onRead: () -> Unit,
    onAddToLibrary: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        AsyncImage(
            model = manga.coverData(),
            placeholder = ColorPainter(colors.surfaceContainer),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(18.dp),
        )
        // Dims the art and fades it into the background, so the theme carries on below
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.background.copy(alpha = 0.35f),
                        0.45f to colors.background.copy(alpha = 0.55f),
                        0.8f to colors.background.copy(alpha = 0.92f),
                        1f to colors.background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = NeonSpacing.gutter, end = NeonSpacing.gutter, bottom = 40.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            MangaCover.Book(
                data = manga.coverData(),
                shape = NeonDefaults.CoverShape,
                modifier = Modifier
                    .width(124.dp)
                    .neonGlow(NeonDefaults.CoverShape, colors.primary.copy(alpha = 0.6f), 12.dp)
                    .neonOutline(NeonDefaults.CoverShape, colors.primary.copy(alpha = 0.35f)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                val genres = Genres.normalizeAll(manga.genre).take(3)
                if (genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxLines = 1,
                    ) {
                        genres.forEach { GenreChip(it) }
                    }
                }
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val meta = listOfNotNull(sourceName, statusLabel(manga.status)).joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val description = manga.description?.trim()
                if (!description.isNullOrEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NeonButton(
                        text = stringResource(MR.strings.home_read_now),
                        icon = MaterialSymbols.RoundedFilled.PlayArrow,
                        onClick = onRead,
                        glow = true,
                    )
                    NeonIconButton(
                        icon = if (inLibrary) {
                            MaterialSymbols.RoundedFilled.BookmarkFilled
                        } else {
                            MaterialSymbols.Rounded.BookmarkOutline
                        },
                        contentDescription = stringResource(
                            if (inLibrary) MR.strings.in_library else MR.strings.add_to_library,
                        ),
                        active = inLibrary,
                        onClick = onAddToLibrary,
                    )
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: Long): String? = when (status.toInt()) {
    SManga.ONGOING -> stringResource(MR.strings.ongoing)
    SManga.COMPLETED -> stringResource(MR.strings.completed)
    SManga.ON_HIATUS -> stringResource(MR.strings.on_hiatus)
    else -> null
}

@Composable
fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                },
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(width)
                    .clip(NeonDefaults.PillShape)
                    .background(color),
            )
        }
    }
}

private val HERO_HEIGHT = 440.dp
