package mihon.feature.home

import androidx.compose.runtime.Immutable
import tachiyomi.domain.manga.model.Manga

/**
 * What the home screen shows: featured entries up top, then rows of entries.
 */
@Immutable
data class HomeFeed(
    val hero: List<Manga>,
    val sections: List<HomeSection>,
    val notice: HomeNotice?,
    val topGenres: List<GenreStat>,
    val sourceNames: Map<Long, String> = emptyMap(),
)

/**
 * A row of the home screen. [genre] is the genre a genre row is for, [sourceId] the source a row from one source
 * opens with "See all", and [basedOn] the genres recommendations were made from.
 */
@Immutable
data class HomeSection(
    val id: String,
    val kind: SectionKind,
    val items: List<Manga>,
    val genre: String? = null,
    val sourceId: Long? = null,
    val basedOn: List<String> = emptyList(),
)

enum class SectionKind {
    /** Library entries partly read. */
    ContinueReading,

    /** Entries from the reader's sources that fit their taste. */
    Recommended,

    /** What the reader's sources updated last. */
    Latest,

    /** What's popular on the reader's sources. */
    Popular,

    /** Entries from the reader's sources in a genre they like. */
    Genre,

    /** Library entries in a genre they like, when the sources can't be reached. */
    FromLibrary,
}

/** Why the feed isn't fully personalized. */
enum class HomeNotice {
    /** No extension sources are installed, which recommendations come from. */
    NoSources,

    /** The sources couldn't be reached: rows are from the last time they could, or from the library. */
    Offline,

    /** Nothing is read or saved yet, so rows show what's popular. */
    NewReader,
}
