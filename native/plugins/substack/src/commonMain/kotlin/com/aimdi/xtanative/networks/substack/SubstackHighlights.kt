package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A run as the reader draws it: [highlighted] where one of the reader's highlights covers it. */
data class SubstackShownRun(val run: SubstackRun, val highlighted: Boolean = false)

/**
 * The reader's highlights, by post key, as XTA's substack_highlights.dart
 * keeps them in `plugin.substack.highlights`: quotes of what was selected, at
 * most [LIMIT] in all, the oldest posts' going first.
 */
class SubstackHighlights(kept: SubstackKept) : SubstackKeptValue<Map<String, List<String>>>(kept, emptyMap(), ::decode, ::encode) {
    fun of(post: SubstackPost): List<String> = state.value[post.key].orEmpty()

    /** [quotes] added to [post]'s, each once; the post becomes the newest. */
    suspend fun add(post: SubstackPost, quotes: List<String>) {
        if (quotes.isEmpty()) return
        update { saved ->
            val kept = saved[post.key].orEmpty()
            bounded(saved - post.key + (post.key to kept + quotes.filter { it !in kept }.distinct()))
        }
    }

    suspend fun remove(post: SubstackPost, quote: String) {
        update { saved ->
            val left = saved[post.key].orEmpty().filter { it != quote }
            bounded(if (left.isEmpty()) saved - post.key else saved + (post.key to left))
        }
    }

    companion object {
        const val LIMIT = 2000
        private val spaces = Regex("\\s+")
        private val quoteMarks = Regex("^[#>\\s]+")

        /** A selection as the quotes it is saved as: one per line it spans, its spaces collapsed; a stray letter is no quote. */
        fun quotes(selected: String): List<String> =
            selected.split('\n').map { it.replace(spaces, " ").trim() }.filter { it.length >= 2 }

        /**
         * [runs] split where [quotes] occur in their text, those parts marked.
         * A quote must not start or end inside a word of a spaced script ("an"
         * is not marked in "plan"), unless it occurs nowhere else: a
         * selection dragged from inside a word still shows where it was made.
         */
        fun shown(runs: List<SubstackRun>, quotes: List<String>): List<SubstackShownRun> {
            val plain = runs.map { SubstackShownRun(it) }
            if (quotes.isEmpty()) return plain
            val text = runs.joinToString("") { it.text }
            val marked = BooleanArray(text.length)
            quotes.filter { it.isNotEmpty() }.forEach { quote -> mark(text, quote, marked) }
            if (marked.none { it }) return plain
            return split(runs, marked)
        }

        private fun mark(text: String, quote: String, marked: BooleanArray) {
            val at = generateSequence(text.indexOf(quote).takeIf { it >= 0 }) { from -> text.indexOf(quote, from + 1).takeIf { it >= 0 } }.toList()
            val whole = at.filter { wholeWords(text, it, quote) }
            (whole.ifEmpty { at }).forEach { start -> marked.fill(true, start, start + quote.length) }
        }

        private fun split(runs: List<SubstackRun>, marked: BooleanArray): List<SubstackShownRun> {
            val out = ArrayList<SubstackShownRun>()
            var offset = 0
            for (run in runs) {
                var start = 0
                while (start < run.text.length) {
                    val on = marked[offset + start]
                    var end = start + 1
                    while (end < run.text.length && marked[offset + end] == on) end++
                    out += SubstackShownRun(run.copy(text = run.text.substring(start, end)), on)
                    start = end
                }
                offset += run.text.length
            }
            return out
        }

        private fun wholeWords(text: String, at: Int, quote: String): Boolean {
            val startsInWord = spacedWordChar(quote, 0) && spacedWordChar(text, at - 1)
            val endsInWord = spacedWordChar(quote, quote.length - 1) && spacedWordChar(text, at + quote.length)
            return !startsInWord && !endsInWord
        }

        /** A letter of a script that puts spaces between words, or a digit. Scripts without spaces, such as Chinese, match anywhere. */
        private fun spacedWordChar(text: String, at: Int): Boolean {
            if (at < 0 || at >= text.length) return false
            val c = text[at]
            if (Character.isDigit(c)) return true
            return Character.UnicodeScript.of(c.code) in SPACED
        }

        private val SPACED = setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.GREEK, Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.ARMENIAN)

        /** [quotes] of [post] as Markdown: its title, where it is from, its address, then each quote. */
        fun markdown(post: SubstackPost, quotes: List<String>): String {
            val source = listOfNotNull(post.publicationName, post.authorName).filter { it.isNotBlank() }.joinToString(" · ")
            return (listOf("# ${post.title}") + listOfNotNull(source.ifEmpty { null }, post.canonicalUrl) + quotes.map { "> " + it.replace(quoteMarks, "") })
                .joinToString("\n\n")
        }

        /** [highlights] with the oldest posts dropped until the newest fit [LIMIT]; the newest post stays whatever its size. */
        fun bounded(highlights: Map<String, List<String>>): Map<String, List<String>> {
            var total = 0
            val kept = ArrayList<Pair<String, List<String>>>()
            for ((key, quotes) in highlights.entries.reversed()) {
                if (quotes.isEmpty()) continue
                total += quotes.size
                if (total > LIMIT && kept.isNotEmpty()) break
                kept += key to quotes
            }
            return LinkedHashMap<String, List<String>>().apply { kept.asReversed().forEach { (key, quotes) -> put(key, quotes) } }
        }

        private fun decode(text: String): Map<String, List<String>> {
            val root = J.parse(text)
            if (root.raw !is JsonObject) return emptyMap()
            return LinkedHashMap<String, List<String>>().apply { root.keys.forEach { key -> put(key, root[key].list.mapNotNull { it.string }) } }
        }

        private fun encode(highlights: Map<String, List<String>>): String =
            JsonObject(highlights.mapValues { (_, quotes) -> JsonArray(quotes.map(::JsonPrimitive)) }).toString()
    }
}
