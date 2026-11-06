package mihon.feature.home

import kotlin.math.pow

/** Which of a source's lists an entry was found in. */
sealed interface Origin {
    data class Genre(val key: String) : Origin
    data object Popular : Origin
    data object Latest : Origin
}

/** An entry a source listed at [position] of the list it came from. */
data class Candidate<T>(val item: T, val sourceId: Long, val origin: Origin, val position: Int)

/** A recommended entry, with the genre that recommends it most if any. */
data class Scored<T>(val item: T, val sourceId: Long, val score: Double, val genre: String?)

/**
 * Ranks entries the reader's sources listed by how well they fit [profile]. An entry scores for each list it was
 * found in: more for the genres the reader likes most, near the top of the list, and from the sources the reader
 * uses. Genres it's known to have that the reader likes add to that. With nothing known of the reader yet, what's
 * popular comes first.
 */
class Recommender(private val profile: TasteProfile) {

    fun <T> rank(
        candidates: List<Candidate<T>>,
        keyOf: (T) -> String,
        genresOf: (T) -> List<String>?,
        limit: Int,
    ): List<Scored<T>> {
        val byKey = LinkedHashMap<String, Accumulated<T>>()
        for (candidate in candidates) {
            val key = keyOf(candidate.item)
            val accumulated = byKey.getOrPut(key) { Accumulated(candidate) }
            val contribution = originWeight(candidate.origin) *
                positionDecay(candidate.position) *
                sourceFactor(candidate.sourceId)
            accumulated.score += contribution
            val origin = candidate.origin
            if (origin is Origin.Genre && contribution > accumulated.bestGenreContribution) {
                accumulated.bestGenreContribution = contribution
                accumulated.genre = origin.key
            }
            // Keep the copy from the list where it ranked best
            if (contribution > accumulated.bestContribution) {
                accumulated.bestContribution = contribution
                accumulated.first = candidate
            }
        }

        val scored = byKey.values.map { accumulated ->
            val item = accumulated.first.item
            val score = accumulated.score + AFFINITY_BONUS * profile.affinity(genresOf(item))
            Scored(item, accumulated.first.sourceId, score, accumulated.genre)
        }
        return diversify(scored, limit)
    }

    /**
     * Picks the best [limit] of [scored], each source counting a little less for every pick it already has, so one
     * prolific source doesn't fill the list.
     */
    private fun <T> diversify(scored: List<Scored<T>>, limit: Int): List<Scored<T>> {
        val remaining = scored.sortedByDescending { it.score }.toMutableList()
        val picks = ArrayList<Scored<T>>(limit.coerceAtMost(remaining.size))
        val perSource = HashMap<Long, Int>()
        while (picks.size < limit && remaining.isNotEmpty()) {
            val index = remaining.indices.maxBy { i ->
                val candidate = remaining[i]
                candidate.score * SOURCE_REPEAT_PENALTY.pow(perSource[candidate.sourceId] ?: 0)
            }
            val pick = remaining.removeAt(index)
            perSource.merge(pick.sourceId, 1, Int::plus)
            picks += pick
        }
        return picks
    }

    private fun originWeight(origin: Origin): Double = when (origin) {
        is Origin.Genre -> if (profile.isEmpty) 0.5 else 0.25 + 0.75 * profile.genreWeight(origin.key)
        Origin.Popular -> if (profile.isEmpty) 1.0 else 0.35
        Origin.Latest -> if (profile.isEmpty) 0.6 else 0.2
    }

    private fun positionDecay(position: Int): Double = 1.0 / (1.0 + position / 8.0)

    private fun sourceFactor(sourceId: Long): Double =
        if (profile.isEmpty) 1.0 else 0.6 + 0.4 * profile.sourceWeight(sourceId)

    private class Accumulated<T>(var first: Candidate<T>) {
        var score = 0.0
        var bestContribution = 0.0
        var bestGenreContribution = 0.0
        var genre: String? = null
    }

    private companion object {
        const val AFFINITY_BONUS = 0.8
        const val SOURCE_REPEAT_PENALTY = 0.85
    }
}

/**
 * [lists] merged by taking each list's first entry in turn, then each one's second, and so on, without repeats.
 */
fun <T> interleave(lists: List<List<T>>, keyOf: (T) -> String, limit: Int): List<T> {
    val seen = HashSet<String>()
    val merged = ArrayList<T>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until longest) {
        for (list in lists) {
            val item = list.getOrNull(i) ?: continue
            if (seen.add(keyOf(item))) merged += item
            if (merged.size >= limit) return merged
        }
    }
    return merged
}

/** A title as a key, so one entry from two sources counts once. */
fun titleKey(title: String): String = title.lowercase().filter(Char::isLetterOrDigit)
