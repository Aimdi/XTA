package com.aimdi.xtanative.networks.threads

/**
 * The `data-sjs` scripts of a threads.com page, found by scanning its text
 * once rather than building its whole document: a page is about a megabyte
 * of HTML with some fifty scripts, and only the few that carry a marker are
 * worth a copy. A script's text is taken as the page has it, as a browser
 * (and Jsoup's `data()`) takes a script's.
 */
internal object ThreadsScripts {
    private const val OPEN = "<script"
    private const val CLOSE = "</script"
    private const val ATTRIBUTE = "data-sjs"

    /** The text of every `data-sjs` script of [html] that holds [marker], in page order. */
    fun embedded(html: String, marker: String): List<String> {
        val found = ArrayList<String>()
        var markerAt = html.indexOf(marker)
        var at = tag(html, OPEN, 0)
        while (at >= 0 && markerAt >= 0) {
            val tagEnd = html.indexOf('>', at + OPEN.length)
            if (tagEnd < 0) break
            val close = tag(html, CLOSE, tagEnd + 1).let { if (it < 0) html.length else it }
            if (markerAt <= tagEnd) markerAt = html.indexOf(marker, tagEnd + 1)
            if (markerAt in tagEnd + 1..close - marker.length && isEmbedded(html, at + OPEN.length, tagEnd)) {
                found += html.substring(tagEnd + 1, close).trim()
            }
            at = tag(html, OPEN, close)
        }
        return found
    }

    /**
     * Where [name] (a tag's start such as `<script`, any case) next starts from [from]:
     * each `<` is found by a plain search and only there is the case ignored,
     * which a case-blind search of the whole page is many times slower than.
     */
    fun tag(html: String, name: String, from: Int): Int {
        var at = html.indexOf('<', from)
        while (at >= 0 && !html.regionMatches(at, name, 0, name.length, ignoreCase = true)) at = html.indexOf('<', at + 1)
        return at
    }

    /** The opening tag between [start] and [end] names `data-sjs` as an attribute of its own. */
    private fun isEmbedded(html: String, start: Int, end: Int): Boolean = (start..end - ATTRIBUTE.length).any { i ->
        html.regionMatches(i, ATTRIBUTE, 0, ATTRIBUTE.length, ignoreCase = true) &&
            html[i - 1].isWhitespace() && html.getOrNull(i + ATTRIBUTE.length)?.let { !it.isLetterOrDigit() && it != '-' } != false
    }
}
