package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost

/**
 * An X post for the reader's own translation service (tweet.dart's
 * _readerTranslationText): the words shown, unescaped, with each link's
 * `t.co` address written out, the media's links left out as the card
 * leaves them out; and the translation back in the post's place.
 */
object ServiceTranslation {
    /** What is sent: the words the card shows. */
    fun text(post: XPost): String {
        val points = post.text.codePoints().toArray()
        val start = post.displayStart.coerceIn(0, points.size)
        val end = (post.displayEnd ?: points.size).coerceIn(start, points.size)
        val links = post.entities.filterIsInstance<XEntity.Link>().filter { it.start >= start && it.end <= end }.sortedBy { it.start }
        val shown = StringBuilder()
        var at = start
        links.forEach { link ->
            if (link.start < at) return@forEach
            shown.append(String(points, at, link.start - at)).append(link.expanded.ifEmpty { link.url })
            at = link.end
        }
        shown.append(String(points, at, end - at))
        return unescape(shown.toString()).trim()
    }

    /** [post] showing [translated] in place of its words, escaped as X sends text; links in it are drawn as the card finds them. */
    fun applied(post: XPost, translated: String): XPost =
        post.copy(text = escape(translated), entities = emptyList(), displayStart = 0, displayEnd = null)

    private fun unescape(text: String) = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
