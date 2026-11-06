package mihon.feature.home

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.extension.model.ContentWarning
import mihon.domain.manga.model.toDomainManga
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Builds the home feed: the reader's taste from their library and reading history, then entries from their
 * installed sources ranked against it.
 *
 * When there's less to go on it falls back step by step: popular entries for a new reader, the last feed that
 * loaded and the library when the sources can't be reached, and the library alone without any sources.
 */
@Inject
@SingleIn(AppScope::class)
class HomeFeedRepository(
    private val getLibraryManga: GetLibraryManga,
    private val getHistory: GetHistory,
    private val historyRepository: HistoryRepository,
    private val getManga: GetManga,
    private val sourceManager: SourceManager,
    private val sourcePreferences: SourcePreferences,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val homePreferences: HomePreferences,
    private val extensionManager: ExtensionManager,
    private val json: Json,
) {

    private val _feed = MutableStateFlow<HomeFeed?>(null)
    val feed: StateFlow<HomeFeed?> = _feed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val mutex = Mutex()
    private var loadedAt: Instant? = null

    /**
     * Loads the feed, unless one loaded recently and not [force]d.
     */
    suspend fun load(force: Boolean = false) = mutex.withLock {
        val last = loadedAt
        if (!force && _feed.value != null && last != null && Clock.System.now() - last < REFRESH_AFTER) return
        _isLoading.value = true
        try {
            if (_feed.value == null) _feed.value = restoreCache()?.withSourceNames()
            val fresh = withContext(Dispatchers.IO) { build(cached = _feed.value).withSourceNames() }
            _feed.value = fresh
            loadedAt = Clock.System.now()
            if (fresh.notice != HomeNotice.Offline && fresh.notice != HomeNotice.NoSources) saveCache(fresh)
            _feed.value = withContext(Dispatchers.IO) { withHeroDetails(fresh) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Couldn't build the home feed" }
        } finally {
            _isLoading.value = false
        }
    }

    /** The reader's taste as it stands, for browsing by genre. */
    suspend fun profile(): TasteProfile = withContext(Dispatchers.IO) {
        TasteProfile.build(signals(getLibraryManga.await(), getHistory.subscribe("").first()), Clock.System.now())
    }

    /**
     * Entries in genre [key] from the reader's sources that have a filter for it, best used sources first, or
     * from searching its name when none do.
     */
    suspend fun browseGenre(key: String): GenreResults = withContext(Dispatchers.IO) {
        val filter = contentFilter()
        val profile = profile()
        val sources = sources(profile).take(GENRE_BROWSE_SOURCES)
        if (sources.isEmpty()) return@withContext GenreResults(emptyList(), usedSearch = false, noSources = true)
        val failures = AtomicInteger()
        val filtered = coroutineScope {
            sources.mapNotNull { source ->
                val filters = genreFilters(source, key) ?: return@mapNotNull null
                async { request(source, failures) { source.getSearchManga(1, "", filters) } }
            }.awaitAll().filterNotNull()
        }
        if (filtered.isNotEmpty()) {
            val allowed = filtered.map { it.filter(filter::allows) }
            return@withContext GenreResults(interleave(allowed, { titleKey(it.title) }, GENRE_BROWSE_SIZE), false)
        }
        // No source files titles under this genre: search for it by name on the best used ones.
        val searched = coroutineScope {
            sources.take(2).map { source ->
                async { request(source, failures) { source.getSearchManga(1, Genres.label(key), FilterList()) } }
            }.awaitAll().filterNotNull()
        }
        GenreResults(
            items = interleave(searched.map { it.filter(filter::allows) }, { titleKey(it.title) }, GENRE_BROWSE_SIZE),
            usedSearch = true,
            failed = searched.isEmpty() && failures.get() > 0,
        )
    }

    private suspend fun build(cached: HomeFeed?): HomeFeed {
        val now = Clock.System.now()
        val library = getLibraryManga.await()
        val history = getHistory.subscribe("").first()
        val filter = contentFilter()
        val profile = TasteProfile.build(signals(library, history), now).excluding(filter.excludedGenres)
        val readIds = history.mapTo(HashSet()) { it.mangaId }
        val continueReading = library
            .filter { it.unreadCount > 0 && it.lastRead != null }
            .sortedByDescending { it.lastRead }
            .take(ROW_SIZE)
            .map { it.manga }
        val sources = sources(profile)

        if (sources.isEmpty()) {
            return libraryFeed(library, continueReading, profile, HomeNotice.NoSources, cachedSections = emptyList())
        }

        val fetched = fetch(sources, profile).filtered(filter)
        val excluded: (Manga) -> Boolean = { it.favorite || it.id in readIds }
        val keyOf: (Manga) -> String = { titleKey(it.title) }

        val candidates = buildList {
            fetched.genres.forEach { (key, lists) ->
                lists.forEach { (sourceId, items) ->
                    items.filterNot(excluded).forEachIndexed { i, manga ->
                        add(Candidate(manga, sourceId, Origin.Genre(key), i))
                    }
                }
            }
            fetched.popular.forEach { (sourceId, items) ->
                items.filterNot(excluded).forEachIndexed { i, manga ->
                    add(Candidate(manga, sourceId, Origin.Popular, i))
                }
            }
            fetched.latest.forEach { (sourceId, items) ->
                items.filterNot(excluded).forEachIndexed { i, manga ->
                    add(Candidate(manga, sourceId, Origin.Latest, i))
                }
            }
        }
        val recommended = Recommender(profile).rank(candidates, keyOf, { it.genre }, RECOMMENDED_SIZE).map { it.item }
        val latest = interleave(fetched.latest.map { it.second }, keyOf, ROW_SIZE)
        val popular = interleave(fetched.popular.map { it.second }, keyOf, ROW_SIZE)

        val networkSections = buildList {
            if (recommended.isNotEmpty()) {
                add(
                    HomeSection(
                        id = "recommended",
                        kind = SectionKind.Recommended,
                        items = recommended,
                        basedOn = profile.topGenres(3).map { it.key },
                    ),
                )
            }
            if (latest.isNotEmpty()) {
                add(HomeSection("latest", SectionKind.Latest, latest, sourceId = fetched.latest.first().first))
            }
            if (popular.isNotEmpty()) {
                add(HomeSection("popular", SectionKind.Popular, popular, sourceId = fetched.popular.first().first))
            }
            fetched.genres.forEach { (key, lists) ->
                val items = interleave(lists.map { it.second.filterNot(excluded) }, keyOf, ROW_SIZE)
                if (items.size >= MIN_ROW_SIZE) {
                    add(HomeSection("genre:$key", SectionKind.Genre, items, genre = key))
                }
            }
        }

        if (networkSections.isEmpty()) {
            // Nothing came back: show what loaded last time, and the library.
            val cachedSections = cached?.sections.orEmpty().filter { it.kind.isFromSources() }
            return libraryFeed(library, continueReading, profile, HomeNotice.Offline, cachedSections)
        }

        val sections = buildList {
            if (continueReading.isNotEmpty()) add(HomeSection("continue", SectionKind.ContinueReading, continueReading))
            addAll(networkSections)
        }
        val hero = recommended.take(HERO_SIZE).ifEmpty { popular.take(HERO_SIZE) }
        return HomeFeed(
            hero = hero,
            sections = sections,
            notice = if (profile.isEmpty) HomeNotice.NewReader else null,
            topGenres = profile.topGenres(GENRE_CHIPS),
        )
    }

    /**
     * A feed from the library alone, and [cachedSections] from sources if any, for when sources can't help.
     */
    private fun libraryFeed(
        library: List<LibraryManga>,
        continueReading: List<Manga>,
        profile: TasteProfile,
        notice: HomeNotice,
        cachedSections: List<HomeSection>,
    ): HomeFeed {
        val libraryGenres = profile.topGenres(2).mapNotNull { stat ->
            val items = library
                .filter { stat.key in Genres.normalizeAll(it.manga.genre) }
                .sortedWith(compareByDescending<LibraryManga> { it.unreadCount > 0 }.thenByDescending { it.lastRead })
                .take(ROW_SIZE)
                .map { it.manga }
            items.takeIf { it.size >= MIN_ROW_SIZE }?.let {
                HomeSection("library:${stat.key}", SectionKind.FromLibrary, it, genre = stat.key)
            }
        }
        val recentlyAdded = library.sortedByDescending { it.manga.favoriteAt }.take(ROW_SIZE).map { it.manga }
        val sections = buildList {
            if (continueReading.isNotEmpty()) add(HomeSection("continue", SectionKind.ContinueReading, continueReading))
            addAll(cachedSections)
            addAll(libraryGenres)
            if (libraryGenres.isEmpty() && recentlyAdded.isNotEmpty()) {
                add(HomeSection("library", SectionKind.FromLibrary, recentlyAdded))
            }
        }
        val hero = cachedSections.firstOrNull()?.items?.take(HERO_SIZE)
            ?: continueReading.take(HERO_SIZE).ifEmpty { recentlyAdded.take(HERO_SIZE) }
        return HomeFeed(hero, sections, notice, profile.topGenres(GENRE_CHIPS))
    }

    private suspend fun fetch(sources: List<Source>, profile: TasteProfile): Fetched = coroutineScope {
        val top = sources.take(QUERY_SOURCES)
        val failures = AtomicInteger()
        val popular = top.map { source ->
            async { request(source, failures) { source.getPopularManga(1) }?.let { source.id to it } }
        }
        val latest = sources.filter { it.supportsLatest }.take(LATEST_SOURCES).map { source ->
            async { request(source, failures) { source.getLatestUpdates(1) }?.let { source.id to it } }
        }
        val genres = profile.topGenres(GENRE_ROWS).map { it.key }.associateWith { key ->
            top.mapNotNull { source ->
                val filters = genreFilters(source, key) ?: return@mapNotNull null
                async { request(source, failures) { source.getSearchManga(1, "", filters) }?.let { source.id to it } }
            }
        }
        Fetched(
            popular = popular.awaitAll().filterNotNull(),
            latest = latest.awaitAll().filterNotNull(),
            genres = genres.mapValues { (_, jobs) -> jobs.awaitAll().filterNotNull() }.filterValues { it.isNotEmpty() },
        )
    }

    /**
     * One page from [source], with the entries saved so they open, or null when it failed or took too long.
     */
    private suspend fun request(
        source: Source,
        failures: AtomicInteger,
        call: suspend () -> MangasPage,
    ): List<Manga>? = requests.withPermit {
        try {
            val page = withTimeoutOrNull(REQUEST_TIMEOUT) { call() }
            if (page == null) {
                failures.incrementAndGet()
                return@withPermit null
            }
            page.mangas
                .map { it.toDomainManga(source.id) }
                .distinctBy { it.url }
                .let { networkToLocalManga(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Home feed couldn't load from ${source.name}" }
            failures.incrementAndGet()
            null
        }
    }

    private fun genreFilters(source: Source, key: String): FilterList? = try {
        GenreFilters.include(source.getFilterList(), key)
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "Couldn't read the filters of ${source.name}" }
        null
    }

    /**
     * The reader's installed and enabled sources, the ones they use most first, then pinned and last used ones.
     */
    private suspend fun sources(profile: TasteProfile): List<Source> {
        val languages = sourcePreferences.enabledLanguages.get()
        val disabled = sourcePreferences.disabledSources.get() + homePreferences.excludedSources.get()
        val adultSources = if (homePreferences.hideAdultContent.get()) adultSourceIds() else emptySet()
        val pinned = sourcePreferences.pinnedSources.get()
        val lastUsed = sourcePreferences.lastUsedSource.get()
        return sourceManager.getAll()
            .filter { !it.isLocal() && it !is StubSource && it.lang in languages && "${it.id}" !in disabled }
            .filterNot { it.id in adultSources }
            .sortedWith(
                compareByDescending<Source> { profile.sourceWeight(it.id) }
                    .thenByDescending { "${it.id}" in pinned }
                    .thenByDescending { it.id == lastUsed }
                    .thenBy { it.name.lowercase() },
            )
    }

    /**
     * What the reader showed about each entry they saved or read: the library, and the most recently read others.
     */
    private suspend fun signals(library: List<LibraryManga>, history: List<HistoryWithRelations>): List<ReadingSignal> {
        val libraryById = library.associateBy { it.id }
        val others = history.asSequence()
            .map { it.mangaId }
            .filter { it !in libraryById }
            .distinct()
            .take(MAX_HISTORY_ENTRIES)
            .toList()
        val ids = library.map { it.id } + others
        // Chunked to stay under older SQLite's limit on query parameters
        val histories = HashMap<Long, List<History>>()
        ids.chunked(QUERY_CHUNK).forEach { histories.putAll(historyRepository.getHistoryByMangaIds(it)) }
        return ids.mapNotNull { id ->
            val libraryManga = libraryById[id]
            val manga = libraryManga?.manga ?: getManga.await(id) ?: return@mapNotNull null
            val entries = histories[id].orEmpty()
            ReadingSignal(
                mangaId = id,
                sourceId = manga.source,
                genres = manga.genre,
                favorite = manga.favorite,
                readDurationMillis = entries.sumOf { it.readDuration },
                chaptersRead = entries.count { it.readAt != null },
                lastReadAt = entries.mapNotNull { it.readAt }.maxOrNull() ?: libraryManga?.lastRead,
            )
        }
    }

    /**
     * [feed] with the details of its featured entries fetched, as source lists rarely include synopses.
     */
    private suspend fun withHeroDetails(feed: HomeFeed): HomeFeed {
        val missing = feed.hero.filter { it.description.isNullOrBlank() && !it.initialized }
        if (missing.isEmpty()) return feed
        val updated = coroutineScope {
            missing.map { manga ->
                async {
                    requests.withPermit {
                        withTimeoutOrNull(REQUEST_TIMEOUT) { updateMangaFromRemote(manga, fetchDetails = true) }
                    }
                    getManga.await(manga.id)
                }
            }.awaitAll().filterNotNull().associateBy { it.id }
        }
        return feed.copy(hero = feed.hero.map { updated[it.id] ?: it })
    }

    /** Sources of extensions marked 18+. */
    private suspend fun adultSourceIds(): Set<Long> = extensionManager.getLoadedExtensions()
        .filter { it.contentWarning == ContentWarning.NSFW }
        .flatMapTo(HashSet()) { extension -> extension.sources.map { it.id } }

    /** What the reader asked recommendations to leave out. */
    private fun contentFilter() = ContentFilter(
        hideAdult = homePreferences.hideAdultContent.get(),
        excludedGenres = homePreferences.excludedGenres.get(),
    )

    private fun Fetched.filtered(filter: ContentFilter) = Fetched(
        popular = popular.map { (id, items) -> id to items.filter(filter::allows) },
        latest = latest.map { (id, items) -> id to items.filter(filter::allows) },
        genres = genres.mapValues { (_, lists) -> lists.map { (id, items) -> id to items.filter(filter::allows) } },
    )

    private suspend fun HomeFeed.withSourceNames(): HomeFeed =
        copy(sourceNames = sourceManager.getAll().associate { it.id to it.name })

    private suspend fun restoreCache(): HomeFeed? = withContext(Dispatchers.IO) {
        val raw = homePreferences.feedCache.get().takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            val cached = json.decodeFromString<CachedFeed>(raw)
            val byId = HashMap<Long, Manga?>()
            suspend fun manga(id: Long) = byId.getOrPut(id) { getManga.await(id) }
            HomeFeed(
                hero = cached.hero.mapNotNull { manga(it) },
                sections = cached.sections.mapNotNull { section ->
                    val kind = SectionKind.entries.find { it.name == section.kind } ?: return@mapNotNull null
                    HomeSection(
                        id = section.id,
                        kind = kind,
                        items = section.items.mapNotNull { manga(it) },
                        genre = section.genre,
                        sourceId = section.sourceId,
                        basedOn = section.basedOn,
                    ).takeIf { it.items.isNotEmpty() }
                },
                notice = null,
                topGenres = emptyList(),
            )
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Couldn't read the cached home feed" }
            null
        }
    }

    private fun saveCache(feed: HomeFeed) {
        val cached = CachedFeed(
            hero = feed.hero.map { it.id },
            sections = feed.sections.filter { it.kind.isFromSources() }.map {
                CachedSection(it.id, it.kind.name, it.items.map(Manga::id), it.genre, it.sourceId, it.basedOn)
            },
        )
        homePreferences.feedCache.set(json.encodeToString(CachedFeed.serializer(), cached))
    }

    private fun SectionKind.isFromSources(): Boolean =
        this != SectionKind.ContinueReading && this != SectionKind.FromLibrary

    private class Fetched(
        val popular: List<Pair<Long, List<Manga>>>,
        val latest: List<Pair<Long, List<Manga>>>,
        val genres: Map<String, List<Pair<Long, List<Manga>>>>,
    )

    @Serializable
    private data class CachedFeed(val hero: List<Long>, val sections: List<CachedSection>)

    @Serializable
    private data class CachedSection(
        val id: String,
        val kind: String,
        val items: List<Long>,
        val genre: String? = null,
        val sourceId: Long? = null,
        val basedOn: List<String> = emptyList(),
    )

    /** Shared by every request, so loading the feed doesn't flood the network. */
    private val requests = Semaphore(PARALLEL_REQUESTS)

    private companion object {
        const val QUERY_SOURCES = 3
        const val LATEST_SOURCES = 2
        const val GENRE_ROWS = 3
        const val GENRE_CHIPS = 6
        const val GENRE_BROWSE_SOURCES = 4
        const val GENRE_BROWSE_SIZE = 60
        const val PARALLEL_REQUESTS = 4
        const val RECOMMENDED_SIZE = 30
        const val ROW_SIZE = 20
        const val MIN_ROW_SIZE = 3
        const val HERO_SIZE = 6
        const val MAX_HISTORY_ENTRIES = 300
        const val QUERY_CHUNK = 500
        val REQUEST_TIMEOUT = 20.seconds
        val REFRESH_AFTER = 15.minutes
    }
}

/**
 * Leaves titles out of recommendations: adult ones when [hideAdult], and ones in any of [excludedGenres].
 */
data class ContentFilter(val hideAdult: Boolean, val excludedGenres: Set<String>) {
    fun allows(manga: Manga): Boolean {
        val genres = Genres.normalizeAll(manga.genre)
        if (hideAdult && genres.any { it in Genres.adult }) return false
        return genres.none { it in excludedGenres }
    }
}

/** Entries in a genre, from its filters or, when [usedSearch], from searching its name. */
data class GenreResults(
    val items: List<Manga>,
    val usedSearch: Boolean,
    val noSources: Boolean = false,
    val failed: Boolean = false,
)
