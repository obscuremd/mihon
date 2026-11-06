package mihon.feature.home

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * Sets a source's own filters to show one genre, so browsing a genre gets what the source files under it rather
 * than titles that happen to contain the word.
 */
object GenreFilters {

    /**
     * [filters], fresh from the source, set to include genre [key], or null when the source has no filter for it.
     */
    fun include(filters: FilterList, key: String): FilterList? {
        val matched = filters.any { filter ->
            when (filter) {
                is Filter.Group<*> -> !filter.name.isExclusion() && includeInGroup(filter, key)
                is Filter.Select<*> -> filter.name.isGenreList() && select(filter, key)
                else -> false
            }
        }
        return filters.takeIf { matched }
    }

    private fun includeInGroup(group: Filter.Group<*>, key: String): Boolean {
        for (child in group.state) {
            when (child) {
                is Filter.TriState -> if (Genres.normalize(child.name) == key) {
                    child.state = Filter.TriState.STATE_INCLUDE
                    return true
                }
                is Filter.CheckBox -> if (Genres.normalize(child.name) == key) {
                    child.state = true
                    return true
                }
                is Filter.Group<*> -> if (!child.name.isExclusion() && includeInGroup(child, key)) return true
            }
        }
        return false
    }

    private fun select(filter: Filter.Select<*>, key: String): Boolean {
        val index = filter.values.indexOfFirst { Genres.normalize(it.toString()) == key }
        if (index < 0) return false
        filter.state = index
        return true
    }

    private fun String.isExclusion(): Boolean = contains("exclu", ignoreCase = true)

    private fun String.isGenreList(): Boolean =
        GENRE_LIST_NAMES.any { contains(it, ignoreCase = true) }

    private val GENRE_LIST_NAMES = listOf("genre", "categor", "tag", "theme")
}
