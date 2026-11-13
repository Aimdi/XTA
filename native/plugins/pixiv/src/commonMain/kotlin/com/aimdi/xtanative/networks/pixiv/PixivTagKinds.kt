package com.aimdi.xtanative.networks.pixiv

/** What a tag says about a work, in the order a work's tags are shown. */
enum class PixivTagKind { COPYRIGHT, CHARACTER, GENERAL, META }

/** A work's tag and its kind. */
data class PixivKindedTag(val tag: PixivTag, val kind: PixivTagKind)

/**
 * The kinds of a work's tags. Pixiv sends no kinds, so they are read off the
 * tags themselves: a `Name(Series)` tag is a character, the series it names a
 * copyright, ratings and bookmark milestones meta, and anything unsure general.
 */
object PixivTagKinds {
    private val metaTags = setOf("R-18", "R-18G", "AI生成")
    private const val ORIGINAL = "オリジナル"
    private val bookmarkMilestone = Regex("""\d+users入り""")
    private val characterTags = listOf(Regex("""(.+?)\((.+)\)"""), Regex("""(.+?)（(.+)）"""))

    /** The series a `Name(Series)` or `Name（Series）` tag names; null for any other tag. */
    fun characterSeries(tag: String): String? = characterTags.firstNotNullOfOrNull { pattern ->
        pattern.matchEntire(tag.trim())?.groupValues
            ?.takeIf { it[1].isNotBlank() && it[2].isNotBlank() }
            ?.let { it[2].trim() }
    }

    /** [tag]'s kind, where [series] are the series the work's character tags name. */
    fun kindOf(tag: String, series: Set<String>): PixivTagKind = when {
        tag in metaTags || bookmarkMilestone.matches(tag) -> PixivTagKind.META
        tag == ORIGINAL || tag in series -> PixivTagKind.COPYRIGHT
        characterSeries(tag) != null -> PixivTagKind.CHARACTER
        else -> PixivTagKind.GENERAL
    }

    /** [tags] with their kinds, ordered by kind and in Pixiv's own order within one. */
    fun kinded(tags: List<PixivTag>): List<PixivKindedTag> {
        val series = tags.mapNotNull { characterSeries(it.name) }.toSet()
        val kinded = tags.map { PixivKindedTag(it, kindOf(it.name, series)) }
        return PixivTagKind.entries.flatMap { kind -> kinded.filter { it.kind == kind } }
    }
}
