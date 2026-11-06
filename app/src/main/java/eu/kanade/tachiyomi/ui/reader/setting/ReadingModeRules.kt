package eu.kanade.tachiyomi.ui.reader.setting

/**
 * Opens series in a reading mode by what they are: a series with a tag, or in a library category, that a rule is
 * for opens in its [mode], unless the series has a reading mode of its own. Rules are tried in order, so the first
 * that matches wins.
 *
 * A [Kind.Tag] rule's [value] is the tag, matched ignoring case. A [Kind.Category] rule's [value] is the category's
 * id, so renaming the category keeps the rule.
 */
data class ReadingModeRule(val kind: Kind, val value: String, val mode: ReadingMode) {

    enum class Kind { Tag, Category }

    val categoryId: Long?
        get() = if (kind == Kind.Category) value.toLongOrNull() else null

    companion object {
        fun tag(tag: String, mode: ReadingMode) = ReadingModeRule(Kind.Tag, tag.trim(), mode)

        fun category(id: Long, mode: ReadingMode) = ReadingModeRule(Kind.Category, id.toString(), mode)
    }
}

object ReadingModeRules {

    /** Rules that fit how most manga, comics and manhwa are made, to start from. */
    val suggested = listOf(
        ReadingModeRule.tag("Manga", ReadingMode.BOOK_RIGHT_TO_LEFT),
        ReadingModeRule.tag("Comic", ReadingMode.BOOK_LEFT_TO_RIGHT),
        ReadingModeRule.tag("Manhwa", ReadingMode.WEBTOON),
        ReadingModeRule.tag("Manhua", ReadingMode.WEBTOON),
        ReadingModeRule.tag("Webtoon", ReadingMode.WEBTOON),
    )

    /**
     * [raw] as stored, one "kind<TAB>value<TAB>mode" per line, as rules. Lines from before rules had kinds,
     * "tag<TAB>mode", are tag rules. Lines that don't parse are skipped.
     */
    fun parse(raw: String): List<ReadingModeRule> = raw.lines().mapNotNull { line ->
        val parts = line.split('\t')
        val (kind, value, flag) = when (parts.size) {
            3 -> Triple(parts[0].toKind() ?: return@mapNotNull null, parts[1], parts[2])
            2 -> Triple(ReadingModeRule.Kind.Tag, parts[0], parts[1])
            else -> return@mapNotNull null
        }
        val mode = ReadingMode.fromPreference(flag.toIntOrNull())
        ReadingModeRule(kind, value.trim(), mode).takeIf { value.isNotBlank() && mode != ReadingMode.DEFAULT }
    }

    fun serialize(rules: List<ReadingModeRule>): String = rules.joinToString("\n") {
        "${it.kind.name.lowercase()}\t${it.value.replace('\t', ' ').trim()}\t${it.mode.flagValue}"
    }

    /**
     * The mode of the first of [rules] for one of [tags] or [categoryIds], or null.
     */
    fun match(rules: List<ReadingModeRule>, tags: List<String>?, categoryIds: Collection<Long>): ReadingMode? {
        val tagNames = tags.orEmpty().mapTo(HashSet()) { it.trim().lowercase() }
        return rules.firstOrNull { rule ->
            when (rule.kind) {
                ReadingModeRule.Kind.Tag -> rule.value.trim().lowercase() in tagNames
                ReadingModeRule.Kind.Category -> rule.categoryId?.let { it in categoryIds } == true
            }
        }?.mode
    }

    private fun String.toKind() = ReadingModeRule.Kind.entries.find { it.name.equals(this, ignoreCase = true) }
}
