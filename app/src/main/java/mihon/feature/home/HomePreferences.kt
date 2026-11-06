package mihon.feature.home

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class HomePreferences(preferenceStore: PreferenceStore) {

    /** Discover searches, newest first, one per line. */
    val recentSearches: Preference<String> = preferenceStore.getString("discover_recent_searches", "")

    /** The last feed that loaded, as entry ids, shown while a new one loads or when it can't. */
    val feedCache: Preference<String> = preferenceStore.getString(Preference.appStateKey("home_feed_cache"), "")

    /** Leaves extensions marked 18+, and titles tagged as adult, out of recommendations. */
    val hideAdultContent: Preference<Boolean> = preferenceStore.getBoolean("recommendations_hide_adult", true)

    /** Ids of sources recommendations don't come from. */
    val excludedSources: Preference<Set<String>> =
        preferenceStore.getStringSet("recommendations_excluded_sources", emptySet())

    /** Genre keys recommendations leave out. */
    val excludedGenres: Preference<Set<String>> =
        preferenceStore.getStringSet("recommendations_excluded_genres", emptySet())

    fun recentSearchList(): List<String> = recentSearches.get().lines().filter { it.isNotBlank() }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + recentSearchList().filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_RECENT_SEARCHES)
        recentSearches.set(updated.joinToString("\n"))
    }

    fun removeRecentSearch(query: String) {
        recentSearches.set(recentSearchList().filterNot { it == query }.joinToString("\n"))
    }

    fun clearRecentSearches() {
        recentSearches.set("")
    }

    private companion object {
        const val MAX_RECENT_SEARCHES = 10
    }
}
