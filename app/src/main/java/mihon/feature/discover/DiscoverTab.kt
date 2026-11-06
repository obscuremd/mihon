package mihon.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.theme.NeonDefaults
import eu.kanade.presentation.theme.NeonSpacing
import eu.kanade.presentation.theme.cardSurface
import eu.kanade.presentation.theme.neonSurface
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.feature.home.GenreBrowseScreen
import mihon.feature.home.Genres
import mihon.feature.home.HomeFeedRepository
import mihon.feature.home.HomePreferences
import mihon.feature.home.SectionKind
import mihon.feature.home.components.genreAccent
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.TrendingUp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The Discover page of the Discover tab: search across the reader's sources, recent and trending searches, and
 * genres to browse, the reader's favourite genres first.
 */
@Composable
fun discoverTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<DiscoverViewModel>()
    return TabContent(
        titleRes = MR.strings.label_discover,
        content = { contentPadding, _ ->
            val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
            val trending by viewModel.trending.collectAsStateWithLifecycle()
            val genres by viewModel.genres.collectAsStateWithLifecycle()
            DiscoverContent(
                contentPadding = contentPadding,
                recentSearches = recent,
                trending = trending,
                genres = genres,
                onSearch = { query ->
                    viewModel.remember(query)
                    navigator.push(GlobalSearchScreen(query))
                },
                onRemoveRecent = viewModel::forget,
                onClearRecent = viewModel::clearRecent,
                onGenre = { navigator.push(GenreBrowseScreen(it)) },
            )
        },
    )
}

/** A genre to browse, and whether it's one of the reader's favourites. */
data class DiscoverGenre(val key: String, val forYou: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverContent(
    contentPadding: PaddingValues,
    recentSearches: List<String>,
    trending: List<String>,
    genres: List<DiscoverGenre>,
    onSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onGenre: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = NeonSpacing.gutter, vertical = NeonSpacing.xs),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(MR.strings.discover_search_hint), maxLines = 1) },
            leadingIcon = { Icon(MaterialSymbols.Rounded.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.clickable { query = "" },
                    )
                }
            } else {
                null
            },
            singleLine = true,
            shape = NeonDefaults.PillShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = NeonDefaults.outlineColor,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch(query.trim()) }),
        )

        if (recentSearches.isNotEmpty()) {
            SectionTitle(MR.strings.discover_recent_searches) {
                Text(
                    text = stringResource(MR.strings.discover_clear),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(NeonDefaults.PillShape)
                        .clickable(role = Role.Button, onClick = onClearRecent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recentSearches.forEach { search ->
                    SearchChip(
                        text = search,
                        leading = { Icon(MaterialSymbols.Rounded.Schedule, null, Modifier.size(16.dp)) },
                        onClick = { onSearch(search) },
                        onRemove = { onRemoveRecent(search) },
                    )
                }
            }
        }

        if (trending.isNotEmpty()) {
            SectionTitle(MR.strings.discover_trending)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                trending.forEach { title ->
                    SearchChip(
                        text = title,
                        accent = true,
                        leading = { Icon(MaterialSymbols.Rounded.TrendingUp, null, Modifier.size(16.dp)) },
                        onClick = { onSearch(title) },
                    )
                }
            }
        }

        SectionTitle(MR.strings.discover_browse_genres)
        Column(verticalArrangement = Arrangement.spacedBy(NeonSpacing.sm)) {
            genres.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(NeonSpacing.sm)) {
                    row.forEach { genre ->
                        GenreTile(genre, onClick = { onGenre(genre.key) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: StringResource,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NeonSpacing.section, bottom = NeonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
private fun SearchChip(
    text: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    accent: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (accent) colors.primary else colors.onSurface
    Row(
        modifier = Modifier
            .then(
                if (accent) {
                    Modifier.neonSurface(NeonDefaults.PillShape)
                } else {
                    Modifier.cardSurface(NeonDefaults.PillShape, colors.surfaceContainerHigh)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            leading()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onRemove != null) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onRemove),
                )
            }
        }
    }
}

@Composable
private fun GenreTile(genre: DiscoverGenre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = genreAccent(genre.key)
    Box(
        modifier = modifier
            .height(60.dp)
            .neonSurface(NeonDefaults.CoverShape, accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(NeonDefaults.PillShape)
                    .background(accent),
            )
            Text(
                text = Genres.label(genre.key),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (genre.forYou) {
            Text(
                text = stringResource(MR.strings.discover_for_you),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(NeonDefaults.PillShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class DiscoverViewModel(
    private val repository: HomeFeedRepository,
    private val homePreferences: HomePreferences,
) : ViewModel() {

    val recentSearches: StateFlow<List<String>> = homePreferences.recentSearches.changes()
        .map { homePreferences.recentSearchList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, homePreferences.recentSearchList())

    /** Titles popular on the reader's sources right now. */
    val trending: StateFlow<List<String>> = repository.feed
        .map { feed ->
            val sections = feed?.sections.orEmpty()
            val popular = sections.find { it.kind == SectionKind.Popular }
                ?: sections.find { it.kind == SectionKind.Latest }
            popular?.items.orEmpty().take(TRENDING_COUNT).map { it.title }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _genres = MutableStateFlow(Genres.common.map { DiscoverGenre(it, forYou = false) })
    val genres: StateFlow<List<DiscoverGenre>> = _genres.asStateFlow()

    init {
        viewModelScope.launch { repository.load() }
        viewModelScope.launch {
            val favourites = repository.profile().topGenres(FAVOURITE_GENRES).map { it.key }
            val rest = Genres.common.filterNot { it in favourites }
            _genres.value = favourites.map { DiscoverGenre(it, forYou = true) } +
                rest.take((GENRE_TILES - favourites.size).coerceAtLeast(0)).map { DiscoverGenre(it, forYou = false) }
        }
    }

    fun remember(query: String) = homePreferences.addRecentSearch(query)

    fun forget(query: String) = homePreferences.removeRecentSearch(query)

    fun clearRecent() = homePreferences.clearRecentSearches()

    private companion object {
        const val TRENDING_COUNT = 8
        const val FAVOURITE_GENRES = 6
        const val GENRE_TILES = 14
    }
}
