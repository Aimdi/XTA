package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind

/**
 * A caption's tappable runs, as XTA's threads_rich_text.dart finds them:
 * Meta's own `text_fragments` when the post carried them, otherwise the
 * @handles, http(s) addresses and #tags a pattern can find.
 */
object ThreadsCaption {
    /** One tappable run inside a caption: [start] and [end] index into the text in UTF-16 units. */
    data class Hit(val start: Int, val end: Int, val value: String, val kind: Kind)

    private val mention = Regex("""(?<![A-Za-z0-9_])@([A-Za-z0-9._]{1,30})""")
    private val address = Regex("""(?U)https?://[^\s<>]+""", RegexOption.IGNORE_CASE)
    private val tag = Regex("""(?<![\p{L}\p{N}_&/#])#([\p{L}\p{N}_]{1,50})""")
    private const val trailingPunctuation = ".,);]!?\"'"

    /** The caption as runs to draw: [fragments] when Meta sent them, else plain text around the hits a pattern finds. */
    fun runs(text: String, fragments: List<ThreadsTextFragment>): List<ThreadsTextFragment> {
        if (fragments.isNotEmpty()) return fragments
        val runs = mutableListOf<ThreadsTextFragment>()
        var cursor = 0
        // A hit inside an earlier one, such as a `#` in a link, is not a run of its own.
        for (hit in hits(text)) {
            if (hit.start < cursor) continue
            if (hit.start > cursor) runs += ThreadsTextFragment(Kind.TEXT, text.substring(cursor, hit.start))
            runs += ThreadsTextFragment(hit.kind, text.substring(hit.start, hit.end), hit.value)
            cursor = hit.end
        }
        if (cursor < text.length) runs += ThreadsTextFragment(Kind.TEXT, text.substring(cursor))
        return runs
    }

    /** Every mention, address and #tag in [text], left to right; one may lie inside another. */
    fun hits(text: String): List<Hit> = (
        mention.findAll(text).map { Hit(it.range.first, it.range.last + 1, it.groupValues[1], Kind.MENTION) } +
            address.findAll(text).map { linkHit(it.range.first, trimUrl(it.value)) } +
            tag.findAll(text).map { Hit(it.range.first, it.range.last + 1, it.groupValues[1], Kind.TAG) }
        ).sortedBy { it.start }.toList()

    /** [raw] without the punctuation a sentence puts after an address. */
    fun trimUrl(raw: String): String = raw.trimEnd { it in trailingPunctuation }

    private fun linkHit(start: Int, url: String) = Hit(start, start + url.length, url, Kind.LINK)
}
