package mihon.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class HomeViewModel(
    private val repository: HomeFeedRepository,
    private val libraryPreferences: LibraryPreferences,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val addTracks: AddTracks,
    private val updateManga: UpdateManga,
    private val sourceManager: SourceManager,
    private val getNextChapters: GetNextChapters,
    homePreferences: HomePreferences,
) : ViewModel() {

    val feed: StateFlow<HomeFeed?> = repository.feed
    val isLoading: StateFlow<Boolean> = repository.isLoading

    /** New chapters in the library, as the bottom bar used to show on Updates. */
    val updatesCount: StateFlow<Int> = combine(
        libraryPreferences.newShowUpdatesCount.changes(),
        libraryPreferences.newUpdatesCount.changes(),
    ) { show, count -> if (show) count else 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Entries added to the library from here, which the feed doesn't know of until it reloads. */
    private val _added = MutableStateFlow<Set<Long>>(emptySet())
    val added: StateFlow<Set<Long>> = _added.asStateFlow()

    init {
        viewModelScope.launch { repository.load() }
        // Changing what recommendations leave out rebuilds them
        viewModelScope.launch {
            combine(
                homePreferences.hideAdultContent.changes(),
                homePreferences.excludedSources.changes(),
                homePreferences.excludedGenres.changes(),
            ) { _, _, _ -> }
                .drop(1)
                .debounce(FILTER_DEBOUNCE)
                .collect { repository.load(force = true) }
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.load(force = true) }
    }

    /**
     * Adds [manga] to the library, in the default category. Returns false, adding nothing, when the reader has to
     * pick categories, which the entry's screen asks for.
     */
    suspend fun addToLibrary(manga: Manga): Boolean {
        if (manga.favorite || manga.id in _added.value) return true
        val categories = getCategories.await().filterNot { it.isSystemCategory }
        val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
        val defaultCategory = categories.find { it.id == defaultCategoryId }
        val categoryIds = when {
            defaultCategory != null -> listOf(defaultCategory.id)
            defaultCategoryId == 0L || categories.isEmpty() -> emptyList()
            else -> return false
        }
        setMangaCategories.await(manga.id, categoryIds)
        setMangaDefaultChapterFlags.await(manga)
        addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
        updateManga.await(MangaUpdate(manga.id) { favoriteAt = Clock.System.now() })
        _added.update { it + manga.id }
        return true
    }

    private companion object {
        val FILTER_DEBOUNCE = 600.milliseconds
    }

    /** The chapter to read next of [manga], if it has any known. */
    suspend fun nextChapter(manga: Manga): Chapter? = getNextChapters.await(manga.id).firstOrNull()
}
