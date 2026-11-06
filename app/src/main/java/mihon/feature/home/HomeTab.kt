package mihon.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.launch
import mihon.feature.home.components.HomeContent
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Home
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The first tab: featured entries and rows from the reader's library and sources, recommended to their taste.
 */
data object HomeTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = stringResource(MR.strings.label_home),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Home),
        )

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalSearchScreen())
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val viewModel = metroViewModel<HomeViewModel>()
        val feed by viewModel.feed.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val updatesCount by viewModel.updatesCount.collectAsStateWithLifecycle()
        val added by viewModel.added.collectAsStateWithLifecycle()

        val open = { manga: Manga -> navigator.push(MangaScreen(manga.id, fromSource = !manga.favorite)) }

        HomeContent(
            feed = feed,
            isLoading = isLoading,
            updatesCount = updatesCount,
            added = added,
            onRefresh = viewModel::refresh,
            onSearch = { navigator.push(GlobalSearchScreen()) },
            onUpdates = { navigator.push(UpdatesTab) },
            onRecommendationSettings = { navigator.push(RecommendationSettingsScreen()) },
            onOpen = open,
            onRead = { manga ->
                scope.launch {
                    val chapter = viewModel.nextChapter(manga)
                    if (chapter != null) {
                        context.startActivity(ReaderActivity.newIntent(context, manga.id, chapter.id))
                    } else {
                        // Never opened, so its chapters aren't known yet
                        open(manga)
                    }
                }
            },
            onAddToLibrary = { manga ->
                scope.launch {
                    if (!viewModel.addToLibrary(manga)) open(manga)
                }
            },
            onSeeAll = { section ->
                when (section.kind) {
                    SectionKind.ContinueReading, SectionKind.FromLibrary -> scope.launch {
                        HomeScreen.openTab(HomeScreen.Tab.Library())
                    }
                    SectionKind.Latest -> section.sourceId?.let {
                        navigator.push(BrowseSourceScreen(it, GetRemoteManga.QUERY_LATEST))
                    }
                    SectionKind.Popular -> section.sourceId?.let {
                        navigator.push(BrowseSourceScreen(it, GetRemoteManga.QUERY_POPULAR))
                    }
                    SectionKind.Genre -> section.genre?.let { navigator.push(GenreBrowseScreen(it)) }
                    SectionKind.Recommended -> navigator.push(HomeSectionScreen(section.id))
                }
            },
            onGenre = { navigator.push(GenreBrowseScreen(it)) },
            onAddExtensions = {
                scope.launch { HomeScreen.openTab(HomeScreen.Tab.Browse(toExtensions = true)) }
            },
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
