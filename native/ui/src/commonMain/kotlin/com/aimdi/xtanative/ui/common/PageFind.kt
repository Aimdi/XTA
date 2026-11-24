package com.aimdi.xtanative.ui.common

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * A text of the page the find bar looks through ([PageFind]): [key] names it
 * where it is drawn ([foundIn]), [item] is the row of the page's lazy list it
 * is drawn in (null on a page that scrolls as a whole, where every text is
 * laid out already).
 */
data class FindBlock(val key: Any, val text: String, val item: Int? = null)

/** A match of the find bar's words: the [ordinal]th in the text [key], over [range] of it, in the list's row [item]. */
data class FoundMatch(val key: Any, val ordinal: Int, val range: IntRange, val item: Int?)

/**
 * Find in page, as a browser's Ctrl+F finds (the reading screens' find bar):
 * what is typed is found anywhere in the page's texts whatever its case, and
 * accents and other marks count for nothing ("cafe" finds "Café", as the
 * library search's do). Folding keeps every character
 * in its place, so a match's range is a range of the text as drawn.
 */
object PageFind {
    private val folded = ConcurrentHashMap<Char, Char>()

    /** The marks Latin, Greek and Cyrillic letters carry once decomposed; a script's own signs stay. */
    private val marks = Regex("[\u0300-\u036f]")

    /** Letters whose stroke is part of them rather than a mark. */
    private val strokes = mapOf('ł' to 'l', 'ø' to 'o', 'đ' to 'd', 'ħ' to 'h', 'ı' to 'i')

    /** [c] as matching compares it: in lower case and without its marks, where that leaves one character. */
    fun fold(c: Char): Char = folded.getOrPut(c) {
        val lower = c.lowercaseChar()
        val plain = Normalizer.normalize(Normalizer.normalize(lower.toString(), Normalizer.Form.NFKD).replace(marks, ""), Normalizer.Form.NFC)
        strokes[lower] ?: plain.singleOrNull()?.lowercaseChar() ?: lower
    }

    /** [text] folded a character at a time ([fold]): as long as [text], each character where it was. */
    fun fold(text: String): String = buildString(text.length) { text.forEach { append(fold(it)) } }

    /** Where [query] is found in [text], in order and not overlapping; none for a blank query. */
    fun ranges(query: String, text: String): List<IntRange> {
        if (query.isBlank() || text.length < query.length) return emptyList()
        val needle = fold(query)
        val haystack = fold(text)
        return generateSequence(haystack.indexOf(needle).takeIf { it >= 0 }) { at -> haystack.indexOf(needle, at + needle.length).takeIf { it >= 0 } }
            .map { it until it + needle.length }
            .toList()
    }

    /** Every match of [query] in [blocks], in the page's order. */
    fun matches(query: String, blocks: List<FindBlock>): List<FoundMatch> = blocks.flatMap { block ->
        ranges(query, block.text).mapIndexed { ordinal, range -> FoundMatch(block.key, ordinal, range, block.item) }
    }

    /** The match [by] steps on from [current] of [count], round past either end; -1 with none. */
    fun step(current: Int, count: Int, by: Int): Int = when {
        count == 0 -> -1
        current !in 0 until count -> if (by < 0) count - 1 else 0
        else -> Math.floorMod(current + by, count)
    }

    /**
     * The match the bar starts on as what is typed changes: the first at or
     * past the row [firstItem] the list shows at its top, as a browser finds
     * from where the reader is; else the first. -1 with none.
     */
    fun startAt(matches: List<FoundMatch>, firstItem: Int?): Int = when {
        matches.isEmpty() -> -1
        firstItem == null -> 0
        else -> matches.indexOfFirst { (it.item ?: Int.MAX_VALUE) >= firstItem }.takeIf { it >= 0 } ?: 0
    }

    /** "3 / 12": the match shown (from one) and how many there are; null with none. */
    fun count(current: Int, count: Int): String? = if (count == 0 || current !in 0 until count) null else "${current + 1} / $count"
}
