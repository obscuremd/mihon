package eu.kanade.tachiyomi.ui.reader.setting

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReadingModeRulesTest {

    private val rules = listOf(
        ReadingModeRule.tag("Manga", ReadingMode.BOOK_RIGHT_TO_LEFT),
        ReadingModeRule.tag("Comic", ReadingMode.BOOK_LEFT_TO_RIGHT),
        ReadingModeRule.tag("Manhwa", ReadingMode.WEBTOON),
    )

    @Test
    fun `a tag picks its rule's mode, ignoring case`() {
        ReadingModeRules.match(rules, listOf("Action", "MANHWA"), emptyList<Long>()) shouldBe ReadingMode.WEBTOON
    }

    @Test
    fun `the first matching rule wins`() {
        ReadingModeRules.match(rules, listOf("Manhwa", "Manga"), emptyList<Long>()) shouldBe
            ReadingMode.BOOK_RIGHT_TO_LEFT
    }

    @Test
    fun `no match leaves the default`() {
        ReadingModeRules.match(rules, listOf("Romance"), emptyList<Long>()) shouldBe null
        ReadingModeRules.match(rules, null, emptyList<Long>()) shouldBe null
    }

    @Test
    fun `rules survive being stored`() {
        ReadingModeRules.parse(ReadingModeRules.serialize(rules)) shouldBe rules
        ReadingModeRules.parse("broken line\nManga\t2") shouldBe
            listOf(ReadingModeRule.tag("Manga", ReadingMode.RIGHT_TO_LEFT))
    }
}

class CategoryRulesTest {

    @Test
    fun `category rules match the series' categories by id`() {
        val rules = listOf(
            ReadingModeRule.tag("Manhwa", ReadingMode.WEBTOON),
            ReadingModeRule.category(7, ReadingMode.BOOK_LEFT_TO_RIGHT),
        )
        ReadingModeRules.match(rules, listOf("Action"), listOf(3L, 7L)) shouldBe ReadingMode.BOOK_LEFT_TO_RIGHT
        ReadingModeRules.match(rules, listOf("Action"), listOf(3L)) shouldBe null
    }

    @Test
    fun `a tag rule doesn't match a category of the same name`() {
        val rules = listOf(ReadingModeRule.tag("7", ReadingMode.WEBTOON))
        ReadingModeRules.match(rules, emptyList(), listOf(7L)) shouldBe null
    }

    @Test
    fun `both kinds survive being stored, and old rules read as tag rules`() {
        val rules = listOf(
            ReadingModeRule.category(12, ReadingMode.BOOK_RIGHT_TO_LEFT),
            ReadingModeRule.tag("Comic", ReadingMode.BOOK_LEFT_TO_RIGHT),
        )
        ReadingModeRules.parse(ReadingModeRules.serialize(rules)) shouldBe rules
        ReadingModeRules.parse("Manhwa\t4") shouldBe listOf(ReadingModeRule.tag("Manhwa", ReadingMode.WEBTOON))
    }
}
