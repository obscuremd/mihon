package mihon.feature.home

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * What the reader has shown about an entry: saving it to the library, time spent reading it, chapters read and
 * when they last read it.
 */
data class ReadingSignal(
    val mangaId: Long,
    val sourceId: Long,
    val genres: List<String>?,
    val favorite: Boolean,
    val readDurationMillis: Long,
    val chaptersRead: Int,
    val lastReadAt: Instant?,
)

/**
 * How a genre came to be in the profile, to say why something is recommended.
 */
data class GenreStat(
    val key: String,
    val score: Double,
    val savedCount: Int,
    val readMinutes: Long,
) {
    val label: String get() = Genres.label(key)
}

/**
 * The reader's taste, from what they saved, how long they read each genre and how recently. Genres and sources
 * score higher the more the reader engaged with entries in them, and the more recently.
 */
class TasteProfile private constructor(
    val genres: List<GenreStat>,
    private val sourceScores: Map<Long, Double>,
) {

    private val genreScores = genres.associate { it.key to it.score }
    private val topScore = genres.firstOrNull()?.score ?: 0.0
    private val topSourceScore = sourceScores.values.maxOrNull() ?: 0.0

    /** This profile without genres [keys], so they aren't recommended. Their weight elsewhere stays. */
    fun excluding(keys: Set<String>): TasteProfile =
        if (keys.isEmpty()) this else TasteProfile(genres.filterNot { it.key in keys }, sourceScores)

    /** Whether nothing is known of the reader yet. */
    val isEmpty: Boolean get() = genres.isEmpty()

    /** The genres the reader likes most, best first. */
    fun topGenres(count: Int): List<GenreStat> = genres.take(count)

    /** How much the reader likes genre [key], 0 to 1. */
    fun genreWeight(key: String): Double = if (topScore > 0) (genreScores[key] ?: 0.0) / topScore else 0.0

    /** How much the reader uses source [sourceId], 0 to 1. */
    fun sourceWeight(sourceId: Long): Double =
        if (topSourceScore > 0) (sourceScores[sourceId] ?: 0.0) / topSourceScore else 0.0

    /** How well [genres] match the reader's taste, 0 to 1: the best few matching genres count most. */
    fun affinity(genres: List<String>?): Double {
        val weights = Genres.normalizeAll(genres).map(::genreWeight).filter { it > 0 }.sortedDescending()
        if (weights.isEmpty()) return 0.0
        // Diminishing returns, so an entry tagged with everything doesn't win on volume
        val total = weights.take(AFFINITY_GENRES).mapIndexed { index, weight -> weight / (index + 1) }.sum()
        return (total / AFFINITY_NORMALIZER).coerceAtMost(1.0)
    }

    companion object {

        private const val AFFINITY_GENRES = 4

        /** The most [affinity] can add up to before it's capped: 1 + 1/2 + 1/3 + 1/4. */
        private const val AFFINITY_NORMALIZER = 2.0833

        val EMPTY = TasteProfile(emptyList(), emptyMap())

        fun build(signals: List<ReadingSignal>, now: Instant): TasteProfile {
            val scores = HashMap<String, Double>()
            val saved = HashMap<String, Int>()
            val minutes = HashMap<String, Long>()
            val sources = HashMap<Long, Double>()

            for (signal in signals) {
                val weight = engagement(signal) * recency(signal.lastReadAt, now)
                if (weight <= 0.0) continue
                sources.merge(signal.sourceId, weight, Double::plus)

                val genres = Genres.normalizeAll(signal.genres)
                if (genres.isEmpty()) continue
                // Shared among the entry's genres, so entries tagged with many don't count for more
                val share = weight / sqrt(genres.size.toDouble())
                val readMinutes = signal.readDurationMillis / 60_000
                for (genre in genres) {
                    scores.merge(genre, share, Double::plus)
                    if (signal.favorite) saved.merge(genre, 1, Int::plus)
                    minutes.merge(genre, readMinutes, Long::plus)
                }
            }

            val stats = scores.map { (key, score) ->
                GenreStat(key, score, saved[key] ?: 0, minutes[key] ?: 0)
            }
                .sortedWith(compareByDescending<GenreStat> { it.score }.thenBy { it.key })
            return TasteProfile(stats, sources)
        }

        /**
         * Saving counts once, reading time on a log scale so one long binge doesn't drown out the rest, and
         * chapters read up to a point.
         */
        internal fun engagement(signal: ReadingSignal): Double {
            val saved = if (signal.favorite) 1.0 else 0.0
            val minutes = signal.readDurationMillis / 60_000.0
            val time = 0.8 * ln(1 + minutes / 20)
            val chapters = 0.5 * signal.chaptersRead.coerceAtMost(100) / 25.0
            return saved + time + chapters
        }

        /** Recently read entries say more about what the reader wants now. */
        internal fun recency(lastReadAt: Instant?, now: Instant): Double {
            lastReadAt ?: return 1.0
            val age = now - lastReadAt
            return when {
                age <= 7.days -> 1.6
                age <= 30.days -> 1.3
                age <= 90.days -> 1.1
                else -> 1.0
            }
        }
    }
}
