package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost

/** A piece of a post's text as it is drawn. */
sealed interface XRun {
    val text: String

    data class Text(override val text: String) : XRun
    data class Link(override val text: String, val url: String) : XRun
    data class Mention(override val text: String, val screenName: String) : XRun
    data class Hashtag(override val text: String, val tag: String) : XRun
    data class Cashtag(override val text: String, val symbol: String) : XRun
}

/**
 * A post's text as runs to draw, as XTA's rich_text.dart builds it: cut by
 * code points within X's display range, each piece unescaped, links shown by
 * their display text. The `t.co` links X appends for the post's media and for
 * the post it quotes are left out: the media and the quote show themselves.
 */
object XText {
    fun runs(post: XPost): List<XRun> {
        val points = post.text.codePoints().toArray()
        val start = post.displayStart.coerceIn(0, points.size)
        val end = (post.displayEnd ?: points.size).coerceIn(start, points.size)
        val hidden = hiddenLinks(post)
        val runs = ArrayList<XRun>()
        var cursor = start
        for (entity in post.entities.filter { it.start >= start && it.end <= end }.sortedBy { it.start }) {
            if (entity.start < cursor) continue
            slice(points, cursor, entity.start)?.let { runs.add(XRun.Text(it)) }
            val text = slice(points, entity.start, entity.end).orEmpty()
            when (entity) {
                is XEntity.Link -> if (entity.url !in hidden) runs.add(XRun.Link(entity.display, entity.expanded))
                is XEntity.Mention -> runs.add(XRun.Mention(text, entity.screenName))
                is XEntity.Hashtag -> runs.add(XRun.Hashtag(text, entity.tag))
                is XEntity.Cashtag -> runs.add(XRun.Cashtag(text, entity.symbol))
            }
            cursor = entity.end
        }
        slice(points, cursor, end)?.let { runs.add(XRun.Text(it)) }
        return trimEnd(runs)
    }

    /** The whole text, plain: for a screen reader and for copying. */
    fun plain(post: XPost): String = runs(post).joinToString("") { it.text }

    private fun hiddenLinks(post: XPost): Set<String> {
        val media = post.media.mapNotNull { it.shortUrl }.toSet()
        val quoted = post.quoted?.id
        val quoteLinks = if (quoted == null) emptySet() else post.entities.filterIsInstance<XEntity.Link>()
            .filter { it.expanded.contains("/status/$quoted") }.map { it.url }
        return media + quoteLinks
    }

    private fun trimEnd(runs: List<XRun>): List<XRun> {
        val last = runs.lastOrNull() as? XRun.Text ?: return runs
        val trimmed = last.text.trimEnd()
        return if (trimmed.isEmpty()) runs.dropLast(1) else runs.dropLast(1) + XRun.Text(trimmed)
    }

    private fun slice(points: IntArray, from: Int, to: Int): String? {
        if (to <= from) return null
        return unescape(String(points, from, to - from))
    }

    /** `&lt;`, `&gt;` and `&amp;` back to what they stand for, as X escapes them. */
    fun unescape(text: String): String =
        text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
}
