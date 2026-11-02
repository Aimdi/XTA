package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A thread as www.reddit.com served it: what the page was, its comments,
 * whether Reddit held back top-level comments the page does not carry, and
 * where the next of them are served.
 */
internal data class WwwThread(
    val kind: WwwPageKind,
    val comments: List<RedditComment>,
    val moreAtRoot: Boolean,
    val next: RedditCommentsCursor?,
)

/**
 * A comment thread from the piece of a post's page that loads its comments
 * (`/svc/shreddit/comments/…`), which a full post page keeps behind its
 * browser check (XTA's `reddit_www_comments.dart`). Each comment is a
 * `<shreddit-comment>` holding its replies as nested ones, its words in its
 * `slot="comment"` block and everything else in its attributes.
 */
internal object RedditWwwComments {
    /**
     * The comments of a thread page. A comment's own page answers with the
     * whole thread, its ancestors first; only that comment and its replies
     * are what the page was opened for, so only they are kept.
     */
    fun thread(body: String): WwwThread {
        val kind = RedditWww.pageKind(body)
        val tree = (if (kind == WwwPageKind.READ) RedditWww.parse(body).selectFirst("shreddit-comment-tree") else null)
            ?: return WwwThread(kind, emptyList(), false, null)
        val focus = tree.attrOrNull("thingid")
        val roots = if (focus == null) childComments(tree, null) else listOfNotNull(tree.below("shreddit-comment").firstOrNull { it.attr("thingid") == focus })
        val more = if (focus == null) tree.firstBelow(TOP_LEVEL_MORE) else null
        return WwwThread(kind, roots.mapNotNull(::comment), more != null, cursorOf(more))
    }

    /** The next comments at a thread's top, as the piece serving them answers: those comments, and where the ones after them are. */
    fun more(body: String): WwwThread {
        val kind = RedditWww.pageKind(body)
        if (kind != WwwPageKind.READ) return WwwThread(kind, emptyList(), false, null)
        val page = RedditWww.parse(body)
        val more = page.selectFirst(TOP_LEVEL_MORE)
        val comments = page.select("shreddit-comment").filter { ownerOf(it) == null }.mapNotNull(::comment)
        return WwwThread(kind, comments, more != null, cursorOf(more))
    }

    private const val TOP_LEVEL_MORE = "#top-level-more-comments-partial"

    /** Where a "more comments" partial loads from, and the cursor it sends. */
    private fun cursorOf(partial: Element?): RedditCommentsCursor? {
        val path = partial?.attrOrNull("src")
        val cursor = partial?.firstBelow("input[name=cursor]")?.attrOrNull("value")
        return if (path == null || !path.startsWith("/svc/shreddit/") || cursor.isNullOrEmpty()) null else RedditCommentsCursor(path, cursor)
    }

    private fun comment(element: Element): RedditComment? {
        val id = RedditWww.bareId(element.attrOrNull("thingid"), "t1_") ?: return null
        val content = RedditCommentContent.of(ownBody(element))
        val replies = childComments(element, element).mapNotNull(::comment) + listOfNotNull(heldBack(element))
        val body = RedditWww.tidy(content.body)
        if (body.isEmpty() && content.mediaUrls.isEmpty() && replies.isEmpty()) return null
        return RedditComment(
            id = id,
            author = element.attrOrNull("author"),
            // A comment whose words are gone keeps its row for the replies under it.
            body = if (body.isEmpty() && content.mediaUrls.isEmpty()) "[deleted]" else body,
            score = intOf(element.attrOrNull("score")),
            createdAt = instantOf(element.attrOrNull("created")),
            isSubmitter = element.hasAttr("is-op"),
            mediaUrls = content.mediaUrls,
            replies = replies,
            permalink = element.attrOrNull("permalink"),
        )
    }

    /** The comments whose nearest enclosing comment is [owner]: null for the top level of [root]. */
    private fun childComments(root: Element, owner: Element?): List<Element> =
        root.below("shreddit-comment").filter { ownerOf(it) === owner }

    /** The block holding [comment]'s own words, not a reply's. */
    private fun ownBody(comment: Element): Element? = comment.below("[slot=comment]").firstOrNull { ownerOf(it) === comment }

    /** Replies Reddit held back under [comment], as the stub row that stands for them; its page is where they are. */
    private fun heldBack(comment: Element): RedditComment? {
        val partial = comment.below("faceplate-partial[slot=children]")
            .firstOrNull { ownerOf(it) === comment && "more-comments" in it.attr("src") } ?: return null
        val permalink = comment.attrOrNull("permalink") ?: return null
        val count = intOf(partial.firstBelow("faceplate-number")?.attrOrNull("number"))
        return RedditComment(id = "more-$permalink", body = "", permalink = permalink, moreCount = count ?: -1)
    }

    /** The nearest `<shreddit-comment>` around [element], or null at the top. */
    private fun ownerOf(element: Element): Element? = element.ancestorNamed("shreddit-comment")
}

/**
 * Results from www.reddit.com's search (`/svc/shreddit/search/`), XTA's
 * `reddit_www_search.dart`. Every result sits in a
 * `<search-telemetry-tracker>` whose tracking JSON names what it is, so that
 * JSON is read first and the markup only for what it leaves out: a post's
 * counts, age and thumbnail, a community's picture and description, an
 * account's karma.
 */
internal object RedditWwwSearch {
    /** Posts a search found. They name no link target, so each is its own page, which is where tapping it goes anyway. */
    fun posts(body: String): List<RedditPost> = results(body, "post").mapNotNull(::post)

    fun communities(body: String): List<RedditSubredditResult> = results(body, "subreddit").mapNotNull(::community)

    fun people(body: String): List<RedditUserResult> = results(body, "people").mapNotNull(::person)

    /** The result units of [kind] (`post`, `subreddit`, `people`) in order; none from a page that is not a search. */
    private fun results(body: String, kind: String): List<Element> =
        if (RedditWww.pageKind(body) != WwwPageKind.READ) emptyList()
        else RedditWww.parse(body).select("search-telemetry-tracker[view-events=\"search/view/$kind\"]")

    private fun contextOf(unit: Element): J = J.parse(unit.attr("data-faceplate-tracking-context"))

    private fun post(unit: Element): RedditPost? {
        val context = contextOf(unit)
        val id = RedditWww.bareId(context["post"]["id"].string, "t3_")
        val title = context["post"]["title"].string?.trim()
        val permalink = unit.firstBelow("a[data-testid=post-title]")?.attrOrNull("href")?.let(::urlOf)?.encodedPath?.takeIf { "/comments/" in it }
        if (id == null || title.isNullOrEmpty() || permalink == null) return null
        val counts = unit.below("[data-testid=search-counter-row] faceplate-number")
        return RedditPost(
            id = id,
            title = title,
            subreddit = context["subreddit"]["name"].string ?: "",
            permalink = permalink,
            author = context["profile"]["name"].string,
            score = intOf(counts.getOrNull(0)?.attrOrNull("number")) ?: 0,
            commentCount = intOf(counts.getOrNull(1)?.attrOrNull("number")) ?: 0,
            createdAt = instantOf(unit.firstBelow("faceplate-timeago")?.attrOrNull("ts")),
            isSelf = true,
            over18 = context["post"]["nsfw"].boolean ?: false,
            spoiler = context["post"]["spoiler"].boolean ?: false,
            thumbnail = unit.firstBelow("[data-testid=search_post_thumbnail]")?.attrOrNull("src"),
        )
    }

    private fun community(unit: Element): RedditSubredditResult? {
        val name = contextOf(unit)["subreddit"]["name"].string?.ifEmpty { null } ?: return null
        return RedditSubredditResult(
            name = name,
            // Reddit shows weekly visitors here now, which are not subscribers.
            subscribers = null,
            description = unit.firstBelow("[data-testid=search-subreddit-desc-text]")?.rawText()?.trim()?.ifEmpty { null },
            iconUrl = RedditMedia.communityIconUrl(communityIcon = unit.firstBelow("img")?.attrOrNull("src")),
        )
    }

    private fun person(unit: Element): RedditUserResult? {
        val name = contextOf(unit)["profile"]["name"].string?.ifEmpty { null } ?: return null
        return RedditUserResult(name, intOf(unit.firstBelow("faceplate-number")?.attrOrNull("number")))
    }
}

/**
 * A post from its embed card (`embed.reddit.com/r/…/comments/…/?embed=true`),
 * the one page of a single post Reddit still serves whole to a reader without
 * an account (XTA's `reddit_www_embed.dart`): its words in full, its pictures,
 * video, link, score and comment count. An NSFW post's card is refused (400).
 */
internal object RedditWwwEmbed {
    /**
     * The post on an embed page, or null when it names none: a post that is
     * gone answers with an empty card. [permalink] is the page it was asked
     * for, kept as the post's own: the card spells a profile post's page
     * differently (`/r/u_<name>/…`).
     */
    fun post(body: String, permalink: String): RedditPost? {
        if (RedditWww.pageKind(body) != WwwPageKind.READ) return null
        val document = RedditWww.parse(body)
        val screen = screenOf(document)
        val id = RedditWww.bareId(screen["post"]["id"].string, "t3_")
        val title = RedditWww.tidy(document.selectFirst("#embed-title")?.rawText().orEmpty())
        if (id == null || title.isEmpty()) return null
        val community = RedditWww.communityOf(permalink).orEmpty()
        val link = linkOf(screen["post"]["url"].string, id)
        val header = document.selectFirst("[data-testid=embed-header]")
        val player = document.selectFirst("shreddit-player")
        return RedditPost(
            id = id,
            title = title,
            subreddit = community,
            permalink = permalink,
            author = header?.firstBelow("a[href*=\"/user/\"]")?.rawText()?.trim(),
            score = intOf(document.selectFirst("[data-testid=upvote] faceplate-number")?.attrOrNull("number")) ?: 0,
            commentCount = countIn(document.selectFirst("[data-testid=cta]")?.rawText()) ?: 0,
            createdAt = instantOf(header?.firstBelow("faceplate-timeago")?.attrOrNull("ts")),
            url = link,
            isSelf = link == null,
            selfText = RedditWww.text(document.selectFirst("[id$=\"-post-rtjson-content\"]")),
            over18 = screen["post"]["nsfw"].boolean ?: false,
            domain = if (link == null) "self.$community" else hostOf(link)?.removePrefix("www."),
            galleryImages = RedditWww.galleryOf(document),
            previewImage = player?.attrOrNull("poster") ?: document.selectFirst("shreddit-aspect-ratio img")?.attrOrNull("src"),
            videoManifestUrl = player?.attrOrNull("src"),
        )
    }

    /** What the page reports to Reddit's analytics about the post it shows: its id, link and NSFW mark. */
    private fun screenOf(document: Document): J =
        document.selectFirst("shreddit-screenview-data")?.attrOrNull("data")?.let(J::parse) ?: J(null)

    /** Where the post points, or null for a post whose link is its own page. */
    private fun linkOf(url: String?, id: String): String? {
        val link = RedditWww.absoluteUrl(url) ?: return null
        val parsed = link.toHttpUrlOrNull() ?: return null
        val ownPage = parsed.host.endsWith("reddit.com") && "comments" in parsed.pathSegments && id in parsed.pathSegments
        return if (ownPage) null else link
    }

    /** The number in "View 15 comments". */
    private fun countIn(text: String?): Int? = countPattern.find(text.orEmpty())?.value?.replace(",", "")?.toIntOrNull()

    private val countPattern = Regex("\\d[\\d,]*")
}
