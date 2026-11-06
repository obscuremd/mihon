package mihon.feature.home

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class RecommendationTest {

    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private fun signal(
        id: Long,
        genres: List<String>,
        favorite: Boolean = false,
        readHours: Int = 0,
        chapters: Int = 0,
        source: Long = 1,
        lastRead: Instant? = null,
    ) = ReadingSignal(id, source, genres, favorite, readHours.hours.inWholeMilliseconds, chapters, lastRead)

    @Test
    fun `genres are one key however sources spell them`() {
        Genres.normalize("Science Fiction") shouldBe "sci-fi"
        Genres.normalize(" Sci-Fi ") shouldBe "sci-fi"
        Genres.normalize("Slice-of-Life") shouldBe "slice of life"
        Genres.normalize("Manhwa") shouldBe null
        Genres.normalize("Completed") shouldBe null
        Genres.label("slice of life") shouldBe "Slice of Life"
        Genres.label("sci-fi") shouldBe "Sci-Fi"
    }

    @Test
    fun `reading time outweighs a single save`() {
        val profile = TasteProfile.build(
            listOf(
                signal(1, listOf("Romance"), favorite = true),
                signal(2, listOf("Horror"), readHours = 20, chapters = 80),
            ),
            now,
        )
        profile.topGenres(2).map { it.key } shouldContainExactly listOf("horror", "romance")
        profile.genreWeight("horror") shouldBe 1.0
    }

    @Test
    fun `recent reading counts more than old reading`() {
        val profile = TasteProfile.build(
            listOf(
                signal(1, listOf("Action"), readHours = 5, lastRead = now - 400.days),
                signal(2, listOf("Fantasy"), readHours = 5, lastRead = now - 2.days),
            ),
            now,
        )
        profile.topGenres(1).single().key shouldBe "fantasy"
    }

    @Test
    fun `saved and read time are kept per genre to explain recommendations`() {
        val profile = TasteProfile.build(
            listOf(
                signal(1, listOf("Action", "Comedy"), favorite = true, readHours = 2),
                signal(2, listOf("Action"), favorite = true),
            ),
            now,
        )
        val action = profile.genres.first { it.key == "action" }
        action.savedCount shouldBe 2
        action.readMinutes shouldBe 120
    }

    @Test
    fun `entries in loved genres rank above popular ones`() {
        val profile = TasteProfile.build(listOf(signal(1, listOf("Horror"), favorite = true, readHours = 10)), now)
        val candidates = listOf(
            Candidate("popular hit", 1, Origin.Popular, 0),
            Candidate("horror pick", 1, Origin.Genre("horror"), 0),
        )
        val ranked = Recommender(profile).rank(candidates, { it }, { null }, 10)
        ranked.map { it.item } shouldContainExactly listOf("horror pick", "popular hit")
        ranked.first().genre shouldBe "horror"
    }

    @Test
    fun `an entry in several loved genres beats one in a single genre`() {
        val profile = TasteProfile.build(
            listOf(signal(1, listOf("Horror", "Mystery"), favorite = true, readHours = 10)),
            now,
        )
        val candidates = listOf(
            Candidate("only horror", 1, Origin.Genre("horror"), 0),
            Candidate("both", 1, Origin.Genre("horror"), 1),
            Candidate("both", 1, Origin.Genre("mystery"), 0),
        )
        Recommender(profile).rank(candidates, { it }, { null }, 10).first().item shouldBe "both"
    }

    @Test
    fun `known genres that fit the reader add to the score`() {
        val profile = TasteProfile.build(listOf(signal(1, listOf("Romance"), favorite = true)), now)
        val genres = mapOf("fits" to listOf("Romance"), "doesn't" to listOf("Sports"))
        val candidates = listOf(
            Candidate("doesn't", 1, Origin.Popular, 0),
            Candidate("fits", 1, Origin.Popular, 1),
        )
        Recommender(profile).rank(candidates, { it }, { genres[it] }, 10).first().item shouldBe "fits"
    }

    @Test
    fun `a new reader gets what's popular`() {
        val candidates = listOf(
            Candidate("latest", 1, Origin.Latest, 0),
            Candidate("popular", 1, Origin.Popular, 0),
        )
        Recommender(TasteProfile.EMPTY).rank(candidates, { it }, { null }, 10).first().item shouldBe "popular"
    }

    @Test
    fun `one source doesn't fill the recommendations`() {
        val profile = TasteProfile.build(listOf(signal(1, listOf("Action"), favorite = true)), now)
        // b0 scores below a2 on its own, but a third pick from source 1 counts for less
        val candidates = (0 until 5).map { Candidate("a$it", 1, Origin.Genre("action"), it) } +
            Candidate("b0", 2, Origin.Genre("action"), 0)
        val ranked = Recommender(profile).rank(candidates, { it }, { null }, 3)
        ranked.map { it.item } shouldContainExactly listOf("a0", "a1", "b0")
    }

    @Test
    fun `the same title from two sources counts once`() {
        val candidates = listOf(
            Candidate("One Piece", 1, Origin.Popular, 0),
            Candidate("one piece!", 2, Origin.Popular, 0),
        )
        Recommender(TasteProfile.EMPTY).rank(candidates, { titleKey(it) }, { null }, 10).size shouldBe 1
    }

    @Test
    fun `rows interleave sources without repeats`() {
        interleave(listOf(listOf("a", "b", "c"), listOf("x", "b", "y")), { it }, 10) shouldContainExactly
            listOf("a", "x", "b", "c", "y")
    }

    @Test
    fun `affinity rewards matching genres with diminishing returns`() {
        val profile = TasteProfile.build(
            listOf(signal(1, listOf("Action", "Drama"), favorite = true, readHours = 4)),
            now,
        )
        profile.affinity(listOf("Action", "Drama")) shouldBeGreaterThan profile.affinity(listOf("Action"))
        profile.affinity(listOf("Sports")) shouldBe 0.0
    }

    private class Genre(name: String) : Filter.TriState(name)
    private class GenreGroup(name: String, genres: List<Genre>) : Filter.Group<Genre>(name, genres)
    private class Check(name: String) : Filter.CheckBox(name)
    private class CheckGroup(name: String, genres: List<Check>) : Filter.Group<Check>(name, genres)
    private class Types(values: Array<String>) : Filter.Select<String>("Genre", values)

    @Test
    fun `genre filters are included in a source's group`() {
        val horror = Genre("Horror")
        val filters = FilterList(GenreGroup("Genres", listOf(Genre("Action"), horror)))
        GenreFilters.include(filters, "horror") shouldBeSameInstanceAs filters
        horror.state shouldBe Filter.TriState.STATE_INCLUDE
    }

    @Test
    fun `exclusion groups are left alone`() {
        val excluded = Check("Horror")
        val included = Check("Horror")
        val filters = FilterList(CheckGroup("Exclude genres", listOf(excluded)), CheckGroup("Genres", listOf(included)))
        GenreFilters.include(filters, "horror")
        excluded.state shouldBe false
        included.state shouldBe true
    }

    @Test
    fun `genre selects pick the genre and sources without one get nothing`() {
        val select = Types(arrayOf("All", "Science Fiction", "Romance"))
        GenreFilters.include(FilterList(select), "sci-fi")
        select.state shouldBe 1
        GenreFilters.include(FilterList(GenreGroup("Genres", listOf(Genre("Action")))), "horror") shouldBe null
    }
}

class ContentFilterTest {

    private fun manga(vararg genres: String) =
        tachiyomi.domain.manga.model.Manga.create().copy(genre = genres.toList())

    @Test
    fun `adult titles are left out while hidden`() {
        val filter = ContentFilter(hideAdult = true, excludedGenres = emptySet())
        filter.allows(manga("Action", "Ecchi")) shouldBe false
        filter.allows(manga("Action")) shouldBe true
        ContentFilter(hideAdult = false, excludedGenres = emptySet()).allows(manga("Mature")) shouldBe true
    }

    @Test
    fun `excluded genres are left out however they're spelled`() {
        val filter = ContentFilter(hideAdult = false, excludedGenres = setOf("sci-fi"))
        filter.allows(manga("Science Fiction")) shouldBe false
        filter.allows(manga("Romance")) shouldBe true
    }

    @Test
    fun `excluded genres don't get rows`() {
        val profile = TasteProfile.build(
            listOf(ReadingSignal(1, 1, listOf("Horror", "Drama"), true, 0, 0, null)),
            kotlin.time.Instant.fromEpochSeconds(0),
        ).excluding(setOf("horror"))
        profile.topGenres(5).map { it.key } shouldContainExactly listOf("drama")
    }
}
