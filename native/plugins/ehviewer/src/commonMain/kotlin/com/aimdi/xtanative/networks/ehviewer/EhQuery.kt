package com.aimdi.xtanative.networks.ehviewer

/** A search as the site takes it: the words with any language added, and the `f_*` parameters, in order. */
data class EhSearchRequest(val query: String, val params: Map<String, String>)

/** A span of a typed query a tag suggestion would replace, from [start] to the end, and what to ask the site to complete for it. */
data class EhSuggestionTerm(val start: Int, val prefix: String)

/**
 * Building and editing E-Hentai searches. Tag names have spaces in them
 * (`big breasts`), so the term being typed runs from the end of the last
 * finished term (one closed by `$` or a quote) to the end of the text.
 */
object EhQuery {
    /** [query] with `language:[language]` added, and the site's filters: the [catMask] to leave out and a minimum rating from 2 stars. */
    fun build(query: String, catMask: Int, minRating: Int = 0, language: String = ""): EhSearchRequest {
        val words = language.trim().let { if (it.isEmpty()) query.trim() else "${query.trim()} language:$it".trim() }
        val params = buildMap {
            put("f_search", words)
            put("f_cats", catMask.toString())
            put("f_apply", "Apply Filter")
            if (minRating >= 2) {
                put("f_sr", "on")
                put("f_srdd", minRating.toString())
            }
        }
        return EhSearchRequest(words, params)
    }

    /** The search for an uploader's galleries. */
    fun uploader(name: String): String = "uploader:\"$name\""

    /** Where the term being typed starts: after the last finished term. */
    fun lastTermStart(text: String): Int {
        var start = 0
        var quoted = false
        text.forEachIndexed { i, char ->
            if (char == '"') quoted = !quoted
            if (char == ' ' && !quoted && i > 0 && (text[i - 1] == '"' || text[i - 1] == '$')) start = i + 1
        }
        return start
    }

    /**
     * What to ask the site for, longest first: the whole term, its last two
     * words, its last word. A finished (`$`) term asks for nothing.
     */
    fun suggestionTerms(text: String): List<EhSuggestionTerm> {
        val start = lastTermStart(text)
        val term = text.substring(start)
        if ('$' in term) return emptyList()
        val words = listOf(0) + (0 until term.length - 1).filter { term[it] == ' ' }.map { it + 1 }
        val offsets = linkedSetOf(0).apply {
            if (words.size > 2) add(words[words.size - 2])
            add(words.last())
        }
        return offsets.mapNotNull { offset -> prefixAt(term.substring(offset))?.let { EhSuggestionTerm(start + offset, it) } }
    }

    /** [text] with [term] replaced by [tag]'s exact search, keeping a `-` or `~` typed before it, ready for the next term. */
    fun insertSuggestion(text: String, term: EhSuggestionTerm, tag: EhTag): String =
        text.substring(0, term.start) + operatorOf(text.substring(term.start)) + tag.query + " "

    /** The operator a term starts with: `-` excludes, `~` is either-of. */
    private fun operatorOf(term: String): String = if (term.startsWith('-') || term.startsWith('~')) term.substring(0, 1) else ""

    private fun prefixAt(span: String): String? =
        span.substring(operatorOf(span).length).replace("\"", "").trim().takeIf { it.length >= 2 }
}
