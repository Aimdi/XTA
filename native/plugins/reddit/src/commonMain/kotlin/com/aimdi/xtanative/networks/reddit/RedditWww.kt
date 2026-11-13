package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import java.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** What www.reddit.com answered with. */
internal enum class WwwPageKind {
    /** Something to read. It may hold nothing: an empty community, a missing one and a feed's end look alike. */
    READ,

    /** The script Reddit puts in front of a full page to check for a browser. */
    BROWSER_CHECK,

    /** "You've been blocked by network security." */
    BLOCKED,

    /** Not a page of Reddit's site this reader knows: an endpoint that moved. */
    UNREADABLE,
}

/** One page of a feed, a community's or a profile's: what it was, its posts, and the fullname the next page starts after. */
internal data class WwwListing(val kind: WwwPageKind, val posts: List<RedditPost>, val after: String?)

/**
 * Reading www.reddit.com, the one part of Reddit still served to a reader
 * without an account (XTA's `reddit_www.dart`). Through 2026 Reddit closed
 * every other door: the `.json` endpoints answer 403, old.reddit.com sends a
 * logged-out visitor to its login page, and full pages open with a browser
 * check. What it still serves are the pieces of its pages the site loads as
 * it scrolls (`/svc/shreddit/…`). A post there is a `<shreddit-post>` whose
 * attributes hold its id, title, author, counts, time, link and kind, so the
 * reader reads attributes rather than layout and skips what it cannot read.
 */
internal object RedditWww {
    /** What [body] is, before anything is read out of it. A page with posts or comments is read, whatever a title on it says. */
    fun pageKind(body: String): WwwPageKind = when {
        content.containsMatchIn(body) -> WwwPageKind.READ
        "blocked by network security" in body.lowercase() -> WwwPageKind.BLOCKED
        "name=\"solution\"" in body -> WwwPageKind.BROWSER_CHECK
        "<shreddit-app" in body && "Page not found" !in body -> WwwPageKind.READ
        else -> WwwPageKind.UNREADABLE
    }

    private val content = Regex("""<shreddit-(?:post|comment)[\s>]|view-events="search/view/""")

    /** The posts of a feed page and where the next one starts. */
    fun listing(body: String): WwwListing {
        val kind = pageKind(body)
        if (kind != WwwPageKind.READ) return WwwListing(kind, emptyList(), null)
        val document = parse(body)
        // A post quoted inside another, a crosspost's original, is not a post of the feed.
        val posts = document.select("shreddit-post").filter { it.ancestorNamed("shreddit-post") == null }.mapNotNull(::post)
        return WwwListing(kind, posts, afterOf(document, posts))
    }

    /** One post from its `<shreddit-post>`, or null when it lacks what every post has. */
    fun post(element: Element): RedditPost? {
        val id = bareId(element.attrOrNull("id"), "t3_")
        val permalink = element.attrOrNull("permalink")
        val title = element.attrOrNull("post-title")?.trim()
        if (id == null || permalink == null || title.isNullOrEmpty()) return null
        val href = absoluteUrl(element.attrOrNull("content-href"))
        val self = isSelf(element.attrOrNull("domain"), href, permalink)
        val telemetry = telemetryOf(element)
        return RedditPost(
            id = id,
            title = title,
            subreddit = element.attrOrNull("subreddit-name") ?: element.attrOrNull("subreddit-prefixed-name")?.removePrefix("r/") ?: "",
            permalink = permalink,
            author = element.attrOrNull("author"),
            score = intOf(element.attrOrNull("score")) ?: 0,
            commentCount = intOf(element.attrOrNull("comment-count")) ?: 0,
            createdAt = instantOf(element.attrOrNull("created-timestamp")),
            url = if (self) null else linkOf(element, href),
            isSelf = self,
            selfText = text(element.firstBelow("shreddit-post-text-body .md")),
            over18 = element.hasAttr("nsfw") || element.hasAttr("subreddit-nsfw") || blurredFor(element, "nsfw") ||
                telemetry["post"]["nsfw"].boolean == true || telemetry["subreddit"]["nsfw"].boolean == true,
            spoiler = element.hasAttr("spoiler") || blurredFor(element, "spoiler"),
            stickied = telemetry["post"]["pinned"].boolean ?: false,
            thumbnail = element.firstBelow("[data-testid=post-thumbnail] img")?.attrOrNull("src"),
            flair = element.firstBelow("shreddit-post-flair .flair-content")?.rawText()?.trim()?.ifEmpty { null },
            domain = element.attrOrNull("domain"),
            galleryImages = galleryOf(element),
            previewImage = previewOf(element),
            videoManifestUrl = element.firstBelow("shreddit-player")?.attrOrNull("src"),
        )
    }

    /** Whether Reddit hides the post's media behind a blur, for [reason]: `nsfw` or `spoiler`. */
    private fun blurredFor(post: Element, reason: String) = post.firstBelow("shreddit-blurred-container[reason=$reason]") != null

    /**
     * What a post's media tells Reddit's analytics about it, whether it is NSFW
     * or pinned and its community's NSFW mark, which a post with pictures
     * carries and its attributes may not.
     */
    private fun telemetryOf(post: Element): J = post.firstBelow("[telemetry]")?.attrOrNull("telemetry")?.let(J::parse) ?: J(null)

    /** A self post's domain reads `self.<subreddit>`, and its link is its own page. */
    private fun isSelf(domain: String?, href: String?, permalink: String) =
        domain?.startsWith("self.") == true || href == null || href.toHttpUrlOrNull()?.encodedPath == permalink

    /** What the post points at. A crosspost points at the original's page; when that original is one picture, the picture is the better link. */
    private fun linkOf(post: Element, href: String?): String? {
        val url = href?.toHttpUrlOrNull()
        val isPostPage = url != null && url.host.endsWith("reddit.com") && "/comments/" in url.encodedPath
        if (!isPostPage || post.firstBelow("gallery-carousel") != null) return href
        return originalPicture(post) ?: href
    }

    /** The full-size file the lightbox opens, when the post has a single picture. */
    private fun originalPicture(post: Element): String? =
        post.below("shreddit-media-lightbox-listener img").mapNotNull { it.attrOrNull("src") }.firstOrNull { hostOf(it) == "i.redd.it" }

    /** A gallery's pictures in the author's order: Reddit's 640px copy of each, which its own feed shows. */
    fun galleryOf(scope: Element): List<String> = scope.below("gallery-carousel li[slot^=page-]").mapNotNull { page ->
        page.firstBelow("img")?.let { it.attrOrNull("src") ?: it.attrOrNull("data-lazy-src") }
    }

    /** Reddit's own preview of what the post shows: a video's poster frame, or the scaled copy of its picture. */
    private fun previewOf(post: Element): String? =
        post.firstBelow("shreddit-player")?.attrOrNull("poster")
            ?: post.firstBelow("img[data-post-media-primary]")?.attrOrNull("src")
            ?: post.firstBelow("img[slot=poster]")?.attrOrNull("src")

    /** The fullname the next page starts after: a community names it in the URL of the piece that loads it, a profile on its posts. */
    private fun afterOf(document: Document, posts: List<RedditPost>): String? {
        val next = document.selectFirst("faceplate-partial[slot=load-after]")?.attrOrNull("src")
        val after = next?.let(::urlOf)?.queryParameter("after")
        if (after != null) return cursorName(after)
        val cursors = document.select("shreddit-post[more-posts-cursor]")
        return if (cursors.isEmpty() || posts.isEmpty()) null else cursorName(cursors.last()?.attr("more-posts-cursor"))
    }

    /** The fullname in one of Reddit's paging cursors: `dDNfMXg3OXFvdA==` is `t3_1x79qot`, with or without its padding. */
    fun cursorName(cursor: String?): String? {
        if (cursor.isNullOrEmpty()) return null
        val padded = cursor.padEnd((cursor.length + 3) / 4 * 4, '=').replace('-', '+').replace('_', '/')
        val name = runCatching { Base64.getDecoder().decode(padded).toString(Charsets.UTF_8) }.getOrNull()
        return name?.takeIf(fullname::matches)
    }

    private val fullname = Regex("^t[0-9]_[a-z0-9]+$")

    /** [after] as the cursor Reddit's paging URLs carry. */
    fun cursorParam(after: String): String = Base64.getEncoder().encodeToString(after.toByteArray(Charsets.UTF_8))

    /** [body] parsed, without what a reader never needs. */
    fun parse(body: String): Document = Jsoup.parse(strip(body))

    /**
     * [body] without its head and its inline styles, scripts and icons: a
     * piece of the site arrives as a whole document of which those are most
     * of the weight. Cutting twice cuts nothing more.
     */
    fun strip(body: String): String {
        val start = body.indexOf("<body")
        return withoutTemplates((if (start < 0) body else body.substring(start)).replace(noise, ""))
    }

    private val noise = Regex("""<script\b[\s\S]*?</script>|<style\b[\s\S]*?</style>|<svg\b[\s\S]*?</svg>""")

    /**
     * [html] without the templates a reader never needs, the action row and
     * menus each comment carries, but with the content of those holding
     * Reddit's own: a big thread renders its comments after the fourth inside
     * a template the site fills in as the reader scrolls, along with the
     * partial that loads the rest. Templates nest, so each is cut whole.
     */
    private fun withoutTemplates(html: String): String = buildString {
        var at = 0
        var open = templateAt(html, 0)
        while (open >= 0) {
            append(html, at, open)
            val close = closingOf(html, open)
            val tagEnd = html.indexOf('>', open)
            val inner = html.substring(if (tagEnd < 0) close else minOf(tagEnd + 1, close), close)
            if (holdsContent(inner)) append(withoutTemplates(inner))
            at = minOf(close + TEMPLATE_CLOSE.length, html.length)
            open = templateAt(html, at)
        }
        append(html, at, html.length)
    }

    private const val TEMPLATE_CLOSE = "</template>"
    private val templateOpen = Regex("""<template[\s>]""")
    private val commentOpen = Regex("""<shreddit-comment[\s>]""")

    private fun templateAt(html: String, from: Int): Int =
        if (from >= html.length) -1 else templateOpen.find(html, from)?.range?.first ?: -1

    /** Where the `</template>` closing the template opened at [open] starts, past any nested in it; the end of [html] when it never closes. */
    private fun closingOf(html: String, open: Int): Int {
        var depth = 0
        var at = open + 1
        while (true) {
            val close = html.indexOf(TEMPLATE_CLOSE, at)
            if (close < 0) return html.length
            val nested = templateAt(html, at)
            when {
                nested in 0 until close -> { depth++; at = nested + 1 }
                depth == 0 -> return close
                else -> { depth--; at = close + 1 }
            }
        }
    }

    private fun holdsContent(html: String) = commentOpen.containsMatchIn(html) || "<shreddit-post" in html || "<faceplate-partial" in html

    /** A post's or comment's words: its rendered markdown as text, links kept as `[label](url)`, without the markup's indentation. */
    fun text(markdown: Element?): String? = markdown?.let { tidy(RedditCommentContent.of(it).body).ifEmpty { null } }

    /** [text] with whitespace-only lines emptied and runs of blank lines folded to one. */
    fun tidy(text: String): String = text.split('\n').joinToString("\n") { it.trimEnd() }.replace(blankLines, "\n\n").trim()

    private val blankLines = Regex("\n{3,}")

    /** The community a post's page belongs to: `FlutterDev` for `/r/FlutterDev/comments/…`, the account's own `u_spez` for `/user/spez/comments/…`. */
    fun communityOf(permalink: String): String? {
        val segments = segmentsOf(permalink)
        val at = segments.indexOf("comments")
        if (at < 2) return null
        val owner = segments[at - 1]
        return when (segments[at - 2]) {
            "r" -> owner
            "u", "user" -> "u_$owner"
            else -> null
        }
    }

    /** The id in a fullname: `abc123` from `t3_abc123`. */
    fun bareId(fullname: String?, prefix: String): String? =
        fullname?.takeIf { it.startsWith(prefix) }?.substring(prefix.length)?.ifEmpty { null }

    /** A link as an app can open it: Reddit writes its own pages relative. */
    fun absoluteUrl(href: String?): String? = href?.ifEmpty { null }?.let { if (it.startsWith("/")) "https://www.reddit.com$it" else it }
}
