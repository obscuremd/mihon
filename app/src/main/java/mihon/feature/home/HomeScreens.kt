package mihon.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.NeonTabRow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.feature.home.components.HomeMangaCard
import mihon.feature.home.components.sectionTitle
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.roundedfilled.Extension
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * Every entry of a home row, by its [sectionId], as a grid.
 */
class HomeSectionScreen(private val sectionId: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<HomeViewModel>()
        val feed by viewModel.feed.collectAsStateWithLifecycle()
        val section = feed?.sections?.find { it.id == sectionId }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = section?.let { sectionTitle(it) },
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (section == null) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }
            MangaGrid(
                items = section.items,
                sourceNames = feed?.sourceNames.orEmpty(),
                contentPadding = contentPadding,
                onClick = { navigator.push(MangaScreen(it.id, fromSource = !it.favorite)) },
            )
        }
    }
}

/**
 * Entries in genre [genreKey] from the reader's sources, filterable by publishing status.
 */
class GenreBrowseScreen(private val genreKey: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<GenreBrowseViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val feed by metroViewModel<HomeViewModel>().feed.collectAsStateWithLifecycle()
        var status by rememberSaveable { mutableStateOf(StatusFilter.All) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(genreKey) { viewModel.load(genreKey) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = Genres.label(genreKey),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val results = state.results
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                results?.noSources == true -> EmptyScreen(
                    message = stringResource(MR.strings.home_notice_no_sources),
                    modifier = Modifier.padding(contentPadding),
                    actions = listOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.home_notice_add_extensions,
                            icon = MaterialSymbols.RoundedFilled.Extension,
                            onClick = {
                                navigator.popUntilRoot()
                                scope.launch { HomeScreen.openTab(HomeScreen.Tab.Browse(toExtensions = true)) }
                            },
                        ),
                    ),
                )
                results == null || results.items.isEmpty() -> EmptyScreen(
                    message = stringResource(
                        if (results?.failed == true) {
                            MR.strings.genre_browse_failed
                        } else {
                            MR.strings.genre_browse_empty
                        },
                    ),
                    modifier = Modifier.padding(contentPadding),
                    actions = listOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.action_retry,
                            icon = MaterialSymbols.Rounded.Refresh,
                            onClick = { viewModel.load(genreKey, force = true) },
                        ),
                    ),
                )
                else -> {
                    val hasStatus = remember(results) { results.items.any { it.status != SManga.UNKNOWN.toLong() } }
                    val shown = remember(results, status) { results.items.filter { status.matches(it) } }
                    Column(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
                        if (hasStatus) {
                            NeonTabRow(
                                selectedTabIndex = status.ordinal,
                                tabCount = StatusFilter.entries.size,
                                onTabClick = { status = StatusFilter.entries[it] },
                            ) { index, _ ->
                                Text(stringResource(StatusFilter.entries[index].titleRes), maxLines = 1)
                            }
                        }
                        if (results.usedSearch) {
                            Text(
                                text = stringResource(MR.strings.genre_browse_search_note, Genres.label(genreKey)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        MangaGrid(
                            items = shown,
                            sourceNames = feed?.sourceNames.orEmpty(),
                            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                            onClick = { navigator.push(MangaScreen(it.id, fromSource = !it.favorite)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaGrid(
    items: List<Manga>,
    sourceNames: Map<Long, String>,
    contentPadding: PaddingValues,
    onClick: (Manga) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items, key = { it.id }) { manga ->
            HomeMangaCard(
                manga = manga,
                fallbackSubtitle = sourceNames[manga.source],
                inLibrary = manga.favorite,
                onClick = { onClick(manga) },
                width = null,
            )
        }
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(MR.strings.genre_browse_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class StatusFilter(val titleRes: StringResource) {
    All(MR.strings.all),
    Ongoing(MR.strings.ongoing),
    Completed(MR.strings.completed),
    Hiatus(MR.strings.on_hiatus),
    ;

    fun matches(manga: Manga): Boolean = when (this) {
        All -> true
        Ongoing -> manga.status == SManga.ONGOING.toLong()
        Completed -> manga.status.toInt() in setOf(SManga.COMPLETED, SManga.PUBLISHING_FINISHED)
        Hiatus -> manga.status == SManga.ON_HIATUS.toLong()
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class GenreBrowseViewModel(private val repository: HomeFeedRepository) : ViewModel() {

    data class State(val isLoading: Boolean = true, val results: GenreResults? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadedKey: String? = null

    fun load(key: String, force: Boolean = false) {
        if (!force && loadedKey == key) return
        loadedKey = key
        _state.value = State(isLoading = true)
        viewModelScope.launch {
            _state.value = State(isLoading = false, results = repository.browseGenre(key))
        }
    }
}
