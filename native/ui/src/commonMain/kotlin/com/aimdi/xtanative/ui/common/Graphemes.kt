package com.aimdi.xtanative.ui.common

/**
 * What a reader sees as one character, the same on every JVM:
 * java.text.BreakIterator joins emoji sequences only from JDK 20 on, and the
 * desktop app runs on 17. A grapheme here takes in combining marks, variation
 * selectors, skin tones, tag sequences, a pair of flag letters and whatever a
 * zero-width joiner joins.
 */
object Graphemes {
    private const val ZWJ = 0x200D
    private const val CR = 0x0D

    /** The end of the grapheme that starts at [start] in [text]. */
    fun end(text: String, start: Int): Int {
        if (start >= text.length) return text.length
        val first = text.codePointAt(start)
        var end = start + Character.charCount(first)
        if (first == CR && end < text.length && text[end] == '\n') return end + 1
        if (isFlagLetter(first) && end < text.length && isFlagLetter(text.codePointAt(end))) {
            end += Character.charCount(text.codePointAt(end))
        }
        var joined = false
        while (end < text.length) {
            val next = text.codePointAt(end)
            if (!joined && next != ZWJ && !extends(next)) break
            joined = next == ZWJ
            end += Character.charCount(next)
        }
        return end
    }

    /** The first grapheme of [text], or "" for an empty text. */
    fun first(text: String): String = text.substring(0, end(text, 0))

    /** Every grapheme of [text], in order. */
    fun of(text: String): List<String> =
        generateSequence(0) { start -> end(text, start).takeIf { it < text.length } }
            .takeWhile { it < text.length }
            .map { start -> text.substring(start, end(text, start)) }
            .toList()

    private fun extends(codePoint: Int): Boolean =
        Character.getType(codePoint).toByte().let {
            it == Character.NON_SPACING_MARK || it == Character.ENCLOSING_MARK || it == Character.COMBINING_SPACING_MARK
        } ||
            codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF ||
            codePoint in 0x1F3FB..0x1F3FF || codePoint in 0xE0020..0xE007F

    private fun isFlagLetter(codePoint: Int) = codePoint in 0x1F1E6..0x1F1FF
}
