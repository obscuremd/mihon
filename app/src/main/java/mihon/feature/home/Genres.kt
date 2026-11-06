package mihon.feature.home

/**
 * Genres as one key each, however a source spells them: "Sci-Fi", "Science Fiction" and "sci fi" are all
 * "sci-fi". Formats and statuses some sources list as genres, like "Manhwa" or "Completed", aren't genres.
 */
object Genres {

    /** The key for [raw], or null when it isn't a genre. */
    fun normalize(raw: String): String? {
        val cleaned = raw.trim()
            .lowercase()
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty() || cleaned.length > MAX_LENGTH) return null
        val key = ALIASES[cleaned] ?: cleaned
        return key.takeUnless { it in NOT_GENRES }
    }

    /** The keys of [raw], without repeats. */
    fun normalizeAll(raw: List<String>?): List<String> = raw.orEmpty().mapNotNull(::normalize).distinct()

    /** [key] as shown: "slice of life" is "Slice of Life", "sci-fi" is "Sci-Fi". */
    fun label(key: String): String = key.split(' ').mapIndexed { index, word ->
        if (index > 0 && word in SMALL_WORDS) {
            word
        } else {
            word.split('-').joinToString("-") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        }
    }.joinToString(" ")

    /** Genres that mark a title as for adults only. */
    val adult: Set<String> = setOf(
        "adult",
        "mature",
        "hentai",
        "smut",
        "ecchi",
        "erotica",
        "erotic",
        "pornographic",
        "18+",
        "r-18",
        "r18",
        "nsfw",
        "sexual violence",
    )

    /** Genres most sources have, to browse before the reader's own taste is known. */
    val common: List<String> = listOf(
        "action",
        "romance",
        "fantasy",
        "horror",
        "comedy",
        "drama",
        "sci-fi",
        "adventure",
        "slice of life",
        "mystery",
        "isekai",
        "sports",
    )

    private const val MAX_LENGTH = 40

    private val SMALL_WORDS = setOf("of", "and", "the", "in", "a")

    private val ALIASES = mapOf(
        "science fiction" to "sci-fi",
        "sci fi" to "sci-fi",
        "scifi" to "sci-fi",
        "slice-of-life" to "slice of life",
        "sol" to "slice of life",
        "shonen" to "shounen",
        "shōnen" to "shounen",
        "shojo" to "shoujo",
        "shōjo" to "shoujo",
        "seinen manga" to "seinen",
        "martial art" to "martial arts",
        "super power" to "superpower",
        "super powers" to "superpower",
        "boys love" to "boys' love",
        "boy's love" to "boys' love",
        "bl" to "boys' love",
        "yaoi" to "boys' love",
        "girls love" to "girls' love",
        "girl's love" to "girls' love",
        "gl" to "girls' love",
        "yuri" to "girls' love",
        "rom-com" to "romantic comedy",
        "romcom" to "romantic comedy",
        "psychological thriller" to "psychological",
        "reincarnated" to "reincarnation",
        "school" to "school life",
        "schoollife" to "school life",
        "magic school" to "school life",
    )

    private val NOT_GENRES = setOf(
        "manga",
        "manhwa",
        "manhua",
        "webtoon",
        "webtoons",
        "comic",
        "comics",
        "web comic",
        "oneshot",
        "one shot",
        "one-shot",
        "full color",
        "full colour",
        "long strip",
        "official colored",
        "colored",
        "completed",
        "complete",
        "ongoing",
        "hiatus",
        "cancelled",
        "adaptation",
        "anthology",
        "award winning",
        "doujinshi",
        "user created",
        "fan colored",
        "web",
        "novel",
        "other",
        "none",
        "n/a",
    )
}
