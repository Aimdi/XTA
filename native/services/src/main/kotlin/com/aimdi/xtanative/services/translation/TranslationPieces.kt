package com.aimdi.xtanative.services.translation

/** One run of a text to translate: joining every piece's [text] gives the original back. */
data class TranslationPiece(val text: String, val translate: Boolean)

/**
 * A text cut for a translation service (reader_translation_service.dart):
 * links, line breaks and the spaces around words stay as they are, only
 * runs with letters are sent, and none longer than [max] UTF-16 units, cut
 * after a sentence where one ends, else after a space, never inside a
 * surrogate pair.
 */
object TranslationPieces {
    const val CHUNK = 4000

    private val URL = Regex("""(?:https?://|www\.)[^\s<>"'“”«»]+""", RegexOption.IGNORE_CASE)
    private val LINE_BREAK = Regex("\r\n|\r|\n")
    private val LETTER = Regex("\\p{L}")
    private val SENTENCE_END = Regex("[.!?。！？…]\\s+")
    private val SPACE = Regex("\\s+")

    fun of(text: String, max: Int = CHUNK): List<TranslationPiece> = buildList {
        var start = 0
        LINE_BREAK.findAll(text).forEach { line ->
            addLine(text.substring(start, line.range.first), max)
            add(TranslationPiece(line.value, translate = false))
            start = line.range.last + 1
        }
        addLine(text.substring(start), max)
    }

    private fun MutableList<TranslationPiece>.addLine(line: String, max: Int) {
        var start = 0
        URL.findAll(line).forEach { url ->
            addWords(line.substring(start, url.range.first), max)
            add(TranslationPiece(url.value, translate = false))
            start = url.range.last + 1
        }
        addWords(line.substring(start), max)
    }

    private fun MutableList<TranslationPiece>.addWords(run: String, max: Int) {
        if (run.isEmpty()) return
        if (run.length > max) {
            var start = 0
            while (start < run.length) {
                val end = if (run.length - start > max) chunkEnd(run, start, max) else run.length
                addWords(run.substring(start, end), max)
                start = end
            }
            return
        }
        val core = run.trim()
        if (core.isEmpty()) {
            add(TranslationPiece(run, translate = false))
            return
        }
        val leading = run.substring(0, run.indexOf(core))
        val trailing = run.substring(leading.length + core.length)
        if (leading.isNotEmpty()) add(TranslationPiece(leading, translate = false))
        add(TranslationPiece(core, translate = LETTER.containsMatchIn(core)))
        if (trailing.isNotEmpty()) add(TranslationPiece(trailing, translate = false))
    }

    private fun chunkEnd(text: String, start: Int, max: Int): Int {
        val window = text.substring(start, start + max)
        SENTENCE_END.findAll(window).lastOrNull()?.takeIf { it.range.last + 1 > max / 4 }?.let { return start + it.range.last + 1 }
        SPACE.findAll(window).lastOrNull()?.takeIf { it.range.last + 1 > max / 4 }?.let { return start + it.range.last + 1 }
        val end = start + max
        return if (text[end - 1].isHighSurrogate()) end - 1 else end
    }
}
