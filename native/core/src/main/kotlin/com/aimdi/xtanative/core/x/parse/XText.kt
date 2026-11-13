package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.x.model.XCommunityNote
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XLiveLinks
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XTranslation
import com.aimdi.xtanative.core.x.model.XUser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
 * their display text. A link is left out when something under the text shows
 * it: the post's media, the post it quotes, an X article, a broadcast or a
 * Space, and a shared Grok conversation its card shows.
 */
object XText {
    private val xHosts = setOf("x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")

    fun runs(post: XPost): List<XRun> {
        val points = post.text.codePointCount(0, post.text.length)
        val start = post.displayStart.coerceIn(0, points)
        val end = (post.displayEnd ?: points).coerceIn(start, points)
        return runsOf(post.text, post.entities, start, end, hiddenLinks(post))
    }

    /**
     * A profile's bio as runs: its links from X's entities, and the @names and
     * #tags in it, which X does not mark in a bio.
     */
    fun bio(user: XUser): List<XRun> {
        val text = user.description ?: return emptyList()
        val links = user.descriptionLinks
        fun points(index: Int) = text.codePointCount(0, index)
        val marked = bioMarks.findAll(text).mapNotNull { match ->
            val start = points(match.range.first)
            val end = points(match.range.last + 1)
            if (links.any { start < it.end && end > it.start }) return@mapNotNull null
            val name = match.value.drop(1)
            if (match.value.startsWith("@")) XEntity.Mention(start, end, name) else XEntity.Hashtag(start, end, name)
        }.toList()
        return runsOf(text, (links + marked).sortedBy { it.start }, 0, text.codePointCount(0, text.length), emptySet())
    }

    /** A community note's text, its links drawn by their own words. */
    fun note(note: XCommunityNote): List<XRun> = runsOf(note.text, note.links, 0, note.text.codePointCount(0, note.text.length), emptySet())

    /** A translated post's text, as its own post's would be drawn. */
    fun translation(translation: XTranslation): List<XRun> =
        runsOf(translation.text, translation.entities, 0, translation.text.codePointCount(0, translation.text.length), emptySet())

    private val bioMarks = Regex("""(?<![\w@#])[@#]\w{1,50}""")

    /** The whole text, plain: for a screen reader and for copying. */
    fun plain(post: XPost): String = runs(post).joinToString("") { it.text }

    private fun runsOf(text: String, entities: List<XEntity>, start: Int, end: Int, hidden: Set<String>): List<XRun> {
        val points = text.codePoints().toArray()
        val runs = ArrayList<XRun>()
        var cursor = start
        for (entity in entities.filter { it.start >= start && it.end <= end }.sortedBy { it.start }) {
            if (entity.start < cursor) continue
            slice(points, cursor, entity.start)?.let { runs.add(XRun.Text(it)) }
            val piece = slice(points, entity.start, entity.end).orEmpty()
            when (entity) {
                is XEntity.Link -> if (entity.url !in hidden && !leftOut(entity.expanded)) runs.add(XRun.Link(entity.display, entity.expanded))
                is XEntity.Mention -> runs.add(XRun.Mention(piece, entity.screenName))
                is XEntity.Hashtag -> runs.add(XRun.Hashtag(piece, entity.tag))
                is XEntity.Cashtag -> runs.add(XRun.Cashtag(piece, entity.symbol))
            }
            cursor = entity.end
        }
        slice(points, cursor, end)?.let { runs.add(XRun.Text(it)) }
        return trimEnd(runs)
    }

    private fun hiddenLinks(post: XPost): Set<String> {
        val media = post.media.mapNotNull { it.shortUrl }.toSet()
        val links = post.entities.filterIsInstance<XEntity.Link>()
        val quoted = post.quoted?.id ?: post.quotedWithheld?.id
        val quoteLinks = if (quoted == null) emptyList() else links.filter { it.expanded.contains("/status/$quoted") }
        val grokLinks = if (post.grokShare == null) emptyList() else links.filter { isGrokShare(it.expanded) }
        return media + (quoteLinks + grokLinks).map { it.url }
    }

    /** An article, a broadcast or a Space is shown as its own block under the text. */
    private fun leftOut(url: String): Boolean = XLiveLinks.isLive(url) || isXPath(url, "article")

    private fun isGrokShare(url: String): Boolean = isXPath(url, "grok", "share")

    private fun isXPath(url: String, vararg path: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val parts = parsed.pathSegments.filter { it.isNotEmpty() }
        return parsed.host.lowercase() in xHosts && parts.size > path.size + 1 && parts[0] == "i" && parts.subList(1, path.size + 1) == path.toList()
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
