package mihon.feature.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.neon.NeonButton
import eu.kanade.presentation.components.neon.NeonIconButton
import eu.kanade.presentation.theme.NeonDefaults
import eu.kanade.presentation.theme.NeonSpacing
import eu.kanade.presentation.theme.neonOutline
import eu.kanade.presentation.theme.neonSurface
import eu.kanade.tachiyomi.R
import mihon.feature.home.GenreStat
import mihon.feature.home.Genres
import mihon.feature.home.HomeFeed
import mihon.feature.home.HomeNotice
import mihon.feature.home.HomeSection
import mihon.feature.home.SectionKind
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CloudOff
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.Notifications
import mihon.icons.materialsymbols.rounded.RocketLaunch
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.roundedfilled.Extension
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The home screen: featured entries, then rows from the reader's library and sources, under a bar with search and
 * library updates.
 */
@Composable
fun HomeContent(
    feed: HomeFeed?,
    isLoading: Boolean,
    updatesCount: Int,
    added: Set<Long>,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onUpdates: () -> Unit,
    onRecommendationSettings: () -> Unit,
    onOpen: (Manga) -> Unit,
    onRead: (Manga) -> Unit,
    onAddToLibrary: (Manga) -> Unit,
    onSeeAll: (HomeSection) -> Unit,
    onGenre: (String) -> Unit,
    onAddExtensions: () -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // The bar fills in as the featured entries scroll under it
    val barOpacity by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val fadeOver = with(density) { BAR_FADE_DISTANCE.toPx() }
                (listState.firstVisibleItemScrollOffset / fadeOver).coerceIn(0f, 1f)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullRefresh(
            refreshing = isLoading && feed != null,
            enabled = true,
            onRefresh = onRefresh,
            indicatorPadding = PaddingValues(top = 72.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = NeonSpacing.section),
            ) {
                val hero = feed?.hero.orEmpty()
                item(key = "hero", contentType = "hero") {
                    if (hero.isNotEmpty()) {
                        HomeHero(
                            items = hero,
                            sourceNames = feed?.sourceNames.orEmpty(),
                            added = added,
                            onOpen = onOpen,
                            onRead = onRead,
                            onAddToLibrary = onAddToLibrary,
                        )
                    } else {
                        Spacer(
                            Modifier
                                .statusBarsPadding()
                                .height(TOP_BAR_HEIGHT),
                        )
                    }
                }

                feed?.notice?.let { notice ->
                    item(key = "notice", contentType = "notice") {
                        NoticeCard(notice, onAddExtensions = onAddExtensions, onRetry = onRefresh)
                    }
                }

                val topGenres = feed?.topGenres.orEmpty()
                if (topGenres.isNotEmpty()) {
                    item(key = "top_genres", contentType = "top_genres") {
                        TopGenres(topGenres, onGenre)
                    }
                }

                feed?.sections.orEmpty().forEach { section ->
                    item(key = section.id, contentType = "section") {
                        SectionRow(
                            section = section,
                            feed = feed!!,
                            added = added,
                            onOpen = onOpen,
                            onSeeAll = { onSeeAll(section) },
                        )
                    }
                }

                when {
                    feed == null && isLoading -> item(key = "loading") { LoadingRows() }
                    feed != null && feed.sections.isEmpty() && feed.hero.isEmpty() && !isLoading -> {
                        item(key = "empty") {
                            Text(
                                text = stringResource(MR.strings.home_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }
        }

        HomeTopBar(
            opacity = if (feed?.hero.isNullOrEmpty()) 1f else barOpacity,
            updatesCount = updatesCount,
            onSearch = onSearch,
            onUpdates = onUpdates,
            onRecommendationSettings = onRecommendationSettings,
        )
    }
}

@Composable
private fun HomeTopBar(
    opacity: Float,
    updatesCount: Int,
    onSearch: () -> Unit,
    onUpdates: () -> Unit,
    onRecommendationSettings: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        background.copy(alpha = 0.75f + 0.25f * opacity),
                        background.copy(alpha = opacity),
                    ),
                ),
            )
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = NeonSpacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(NeonDefaults.CoverShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_mihon),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(26.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = stringResource(MR.strings.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(MR.strings.home_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NeonIconButton(
            icon = MaterialSymbols.Rounded.FilterList,
            contentDescription = stringResource(MR.strings.recommendation_settings),
            onClick = onRecommendationSettings,
        )
        Spacer(Modifier.width(NeonSpacing.sm))
        NeonIconButton(
            icon = MaterialSymbols.Rounded.Search,
            contentDescription = stringResource(MR.strings.action_search),
            onClick = onSearch,
        )
        Spacer(Modifier.width(NeonSpacing.sm))
        NeonIconButton(
            icon = MaterialSymbols.Rounded.Notifications,
            contentDescription = stringResource(MR.strings.home_updates),
            onClick = onUpdates,
            active = updatesCount > 0,
            badge = if (updatesCount > 0) {
                { Badge { Text(updatesCount.coerceAtMost(99).toString()) } }
            } else {
                null
            },
        )
    }
}

@Composable
private fun NoticeCard(notice: HomeNotice, onAddExtensions: () -> Unit, onRetry: () -> Unit) {
    val (icon, title, body) = when (notice) {
        HomeNotice.NoSources -> Triple(
            MaterialSymbols.RoundedFilled.Extension,
            MR.strings.home_notice_no_sources,
            MR.strings.home_notice_no_sources_body,
        )
        HomeNotice.Offline -> Triple(
            MaterialSymbols.Rounded.CloudOff,
            MR.strings.home_notice_offline,
            MR.strings.home_notice_offline_body,
        )
        HomeNotice.NewReader -> Triple(
            MaterialSymbols.Rounded.RocketLaunch,
            MR.strings.home_notice_new_reader,
            MR.strings.home_notice_new_reader_body,
        )
    }
    val action = when (notice) {
        HomeNotice.NoSources -> MR.strings.home_notice_add_extensions to onAddExtensions
        HomeNotice.Offline -> MR.strings.action_retry to onRetry
        HomeNotice.NewReader -> null
    }
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .padding(horizontal = NeonSpacing.gutter, vertical = NeonSpacing.sm)
            .fillMaxWidth()
            .neonSurface(NeonDefaults.CardShape)
            .padding(NeonSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(NeonDefaults.CoverShape)
                .background(primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = primary)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (action != null) {
                NeonButton(
                    text = stringResource(action.first),
                    onClick = action.second,
                    modifier = Modifier.padding(top = NeonSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun TopGenres(genres: List<GenreStat>, onGenre: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = NeonSpacing.md)) {
        Text(
            text = stringResource(MR.strings.home_top_genres),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = NeonSpacing.gutter),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = NeonSpacing.gutter, vertical = NeonSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(NeonSpacing.xs),
        ) {
            items(genres, key = { it.key }) { genre ->
                GenreChip(genre.key, onClick = { onGenre(genre.key) })
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: HomeSection,
    feed: HomeFeed,
    added: Set<Long>,
    onOpen: (Manga) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = NeonSpacing.section)) {
        HomeSectionHeader(
            title = sectionTitle(section),
            subtitle = sectionSubtitle(section, feed),
            onSeeAll = onSeeAll,
        )
        LazyRow(
            contentPadding = PaddingValues(
                start = NeonSpacing.gutter,
                end = NeonSpacing.gutter,
                top = NeonSpacing.sm,
            ),
            horizontalArrangement = Arrangement.spacedBy(NeonSpacing.gridColumnGap),
        ) {
            items(section.items, key = { it.id }) { manga ->
                HomeMangaCard(
                    manga = manga,
                    fallbackSubtitle = feed.sourceNames[manga.source],
                    inLibrary = manga.favorite || manga.id in added,
                    onClick = { onOpen(manga) },
                )
            }
        }
    }
}

@Composable
fun sectionTitle(section: HomeSection): String {
    val genre = section.genre?.let(Genres::label)
    return when (section.kind) {
        SectionKind.ContinueReading -> stringResource(MR.strings.library_continue_reading)
        SectionKind.Recommended -> stringResource(MR.strings.home_recommended)
        SectionKind.Latest -> stringResource(MR.strings.home_latest)
        SectionKind.Popular -> stringResource(MR.strings.home_popular)
        SectionKind.Genre -> stringResource(MR.strings.home_genre, genre.orEmpty())
        SectionKind.FromLibrary -> if (genre != null) {
            stringResource(MR.strings.home_from_library_genre, genre)
        } else {
            stringResource(MR.strings.home_from_library)
        }
    }
}

@Composable
private fun sectionSubtitle(section: HomeSection, feed: HomeFeed): String = when (section.kind) {
    SectionKind.ContinueReading -> stringResource(MR.strings.home_continue_reading_subtitle)
    SectionKind.Recommended -> if (section.basedOn.isEmpty()) {
        stringResource(MR.strings.home_recommended_subtitle_new)
    } else {
        stringResource(MR.strings.home_recommended_subtitle, section.basedOn.joinToString(", ") { Genres.label(it) })
    }
    SectionKind.Latest -> stringResource(MR.strings.home_latest_subtitle)
    SectionKind.Popular -> stringResource(MR.strings.home_popular_subtitle)
    SectionKind.Genre -> {
        val stat = feed.topGenres.find { it.key == section.genre }
        when {
            stat != null && stat.readMinutes >= 30 ->
                stringResource(MR.strings.home_genre_subtitle_time, formatReadTime(stat.readMinutes))
            stat != null && stat.savedCount > 0 -> stringResource(MR.strings.home_genre_subtitle_saved, stat.savedCount)
            else -> stringResource(MR.strings.home_genre_subtitle)
        }
    }
    SectionKind.FromLibrary -> stringResource(MR.strings.home_from_library_subtitle)
}

/** [minutes] read as "45 min" or "12 h". */
private fun formatReadTime(minutes: Long): String = if (minutes < 60) "$minutes min" else "${minutes / 60} h"

@Composable
private fun LoadingRows() {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier = Modifier.padding(top = 8.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .size(width = 180.dp, height = 22.dp)
                    .clip(NeonDefaults.PillShape)
                    .background(placeholder),
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(128.dp)
                            .aspectRatio(2f / 3f)
                            .clip(NeonDefaults.CoverShape)
                            .background(placeholder),
                    )
                }
            }
        }
    }
}

private val TOP_BAR_HEIGHT = 64.dp
private val BAR_FADE_DISTANCE = 240.dp
