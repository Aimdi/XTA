package com.aimdi.xtanative.networks.reddit

import java.time.Instant
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * What old.reddit.com served when a listing was asked for. Its refusals are
 * pages too, so "did this work" cannot be read off the status alone; naming
 * them is what lets the client stop asking (a banned community is not served
 * by the next route either) and report an empty community as empty.
 */
internal enum class OldPageKind { LISTING, OVER18_GATE, LOGIN_WALL, PRIVATE, BANNED, QUARANTINED, UNREADABLE }

/** One read of a listing page: what it was, its posts, the next cursor, and the community's picture when the page carried one. */
internal data class OldListingPage(val kind: OldPageKind, val posts: List<RedditPost>, val after: String?, val icon: String?)

/** What the old sidebar says about a community. */
internal data class OldSidebar(val title: String?, val description: String?, val subscribers: Int?, val activeUsers: Int?)

/** What a post's own page says it points at: its outbound link and the pictures its expando leaves. */
internal data class PostMedia(val url: String?, val images: List<String>)

/**
 * A listing out of old.reddit.com's HTML (XTA's `reddit_html.dart`), the
 * route that worked without an account until Reddit put old.reddit behind its
 * login page in 2026, and still does where that has not reached. It reads the
 * `data-*` attributes old.reddit puts on every post, stable for a decade,
 * and skips what it cannot read rather than guessing.
 */
internal object RedditOldListing {
    /** One listing page, read once: what it is, its posts and cursor, and the community's picture. */
    fun page(body: String): OldListingPage {
        val document = Jsoup.parse(body)
        val things = document.select("div.thing")
        val posts = things.mapNotNull(::post)
        val icon = iconIn(document)
        return if (posts.isNotEmpty()) {
            OldListingPage(OldPageKind.LISTING, posts, afterFrom(document), icon)
        } else {
            OldListingPage(kindWithoutPosts(document, body, hasThings = things.isNotEmpty()), emptyList(), null, icon)
        }
    }

    private fun post(thing: Element): RedditPost? {
        // Promoted posts are adverts, not what a reader asked a community for.
        if (flag(thing.attrOrNull("data-promoted"))) return null
        val id = thing.attrOrNull("data-fullname")?.removePrefix("t3_")
        val permalink = thing.attrOrNull("data-permalink")
        val title = thing.firstBelow("a.title")?.rawText()?.trim()
        if (id.isNullOrEmpty() || permalink == null || title.isNullOrEmpty()) return null
        // A self post's data-url is its own permalink, and its domain reads `self.<subreddit>`.
        val url = thing.attrOrNull("data-url")
        val domain = thing.attrOrNull("data-domain").orEmpty()
        val self = domain.startsWith("self.") || url == null || url == permalink
        return RedditPost(
            id = id,
            title = title,
            subreddit = thing.attrOrNull("data-subreddit").orEmpty(),
            permalink = permalink,
            author = thing.attrOrNull("data-author"),
            score = intOf(thing.attrOrNull("data-score")) ?: 0,
            commentCount = intOf(thing.attrOrNull("data-comments-count")) ?: 0,
            // old.reddit stamps data-timestamp in milliseconds.
            createdAt = thing.attrOrNull("data-timestamp")?.trim()?.toLongOrNull()?.let(Instant::ofEpochMilli),
            url = if (self) null else url,
            isSelf = self,
            selfText = thing.firstBelow(".expando .md")?.rawText()?.trim(),
            over18 = flag(thing.attrOrNull("data-nsfw")),
            spoiler = flag(thing.attrOrNull("data-spoiler")) || "spoiler" in thing.classNames(),
            // old.reddit marks a pinned post with a class rather than an attribute.
            stickied = "stickied" in thing.classNames(),
            thumbnail = thing.firstBelow("a.thumbnail img")?.attrOrNull("src")?.let(::absolute),
            flair = flairOf(thing),
            domain = domain.ifEmpty { null },
        )
    }

    private fun flag(value: String?) = value?.lowercase() == "true"

    /** The flair in the title line; its `title` holds the full label where the visible text is abbreviated. */
    private fun flairOf(thing: Element): String? {
        val label = thing.firstBelow(".linkflairlabel") ?: return null
        val full = label.attrOrNull("title")?.trim()
        return (if (!full.isNullOrEmpty()) full else label.rawText().trim()).ifEmpty { null }
    }

    /** Reddit serves protocol-relative pictures; `self`, `default` and `nsfw` are placeholders, not pictures. */
    fun absolute(src: String): String? = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        else -> null
    }

    /** Hosts Reddit serves community artwork from: anything else on the page (the site logo, a pixel, an advert) is not it. */
    private val iconHosts = setOf(
        "styles.redditmedia.com",
        "a.thumbs.redditmedia.com",
        "b.thumbs.redditmedia.com",
        "f.thumbs.redditmedia.com",
        "preview.redd.it",
        "i.redd.it",
    )

    /**
     * A community's own picture from its old.reddit page: the header image it
     * sets, which for most is the same artwork, else the page's `og:image`.
     */
    fun subredditIcon(body: String): String? = iconIn(Jsoup.parse(body))

    private fun iconIn(document: Document): String? {
        // Old themes make the header an `<img id="header-img">`; others put the id on a link around it.
        val header = document.selectFirst("#header-img")?.attrOrNull("src")
            ?: document.selectFirst("#header-img img")?.attrOrNull("src")
            ?: document.selectFirst(".subreddit-icon img")?.attrOrNull("src")
        val og = document.selectFirst("meta[property=\"og:image\"]")?.attrOrNull("content")
        return listOfNotNull(header, og).firstNotNullOfOrNull { candidate ->
            absolute(candidate)?.takeIf { hostOf(it)?.lowercase() in iconHosts }
        }
    }

    /** Whether [body] is the over-18 gate, which wants a cookie rather than an account. */
    fun isOver18Gate(body: String): Boolean = "over18" in body && isGate(Jsoup.parse(body))

    private fun isGate(document: Document) =
        document.selectFirst("form[action*=over18]") != null || document.selectFirst(".content [name=over18]") != null

    /**
     * What a page with nothing readable on it is. Order matters: an empty
     * community still renders the sidebar's login form, so the listing is
     * recognised before the login wall.
     */
    private fun kindWithoutPosts(document: Document, body: String, hasThings: Boolean): OldPageKind {
        val text = body.lowercase()
        return when {
            isGate(document) -> OldPageKind.OVER18_GATE
            document.selectFirst("form[action*=quarantine]") != null || quarantined.containsMatchIn(text) -> OldPageKind.QUARANTINED
            banned.containsMatchIn(text) -> OldPageKind.BANNED
            document.selectFirst(".private-subreddit") != null || privateCommunity.containsMatchIn(text) -> OldPageKind.PRIVATE
            // Posts that all failed to parse mean markup this reader no longer understands.
            hasThings -> OldPageKind.UNREADABLE
            document.selectFirst("#siteTable") != null || nothingHere.containsMatchIn(text) -> OldPageKind.LISTING
            document.selectFirst("#login_login-main") != null || document.selectFirst("form[action*=\"/post/login\"]") != null ||
                "you must be logged in" in text -> OldPageKind.LOGIN_WALL
            else -> OldPageKind.UNREADABLE
        }
    }

    /** Reddit's own sentences, matched loosely enough to survive a reword and tightly enough not to fire on a sidebar rule. */
    private val nothingHere = Regex("there doesn.t seem to be anything here")
    private val banned = Regex("(subreddit|community) (was|has been) banned|banned due to a violation")
    private val privateCommunity = Regex("private (community|subreddit)|community is private|must be invited")
    private val quarantined = Regex("quarantined (community|subreddit)|(community|subreddit) is quarantined")

    /** The `after` of the "next" link, so pages go on as the JSON ones did. */
    private fun afterFrom(document: Document): String? {
        val next = document.selectFirst(".next-button a")
            ?: document.select("a[rel]").firstOrNull { "next" in it.attr("rel").split(whitespace) }
        return next?.attrOrNull("href")?.let(::urlOf)?.queryParameter("after")?.ifEmpty { null }
    }

    private val whitespace = Regex("\\s+")

    /** What the old sidebar says about a community: its description and the two counts beside it. */
    fun sidebar(body: String): OldSidebar {
        val box = Jsoup.parse(body).selectFirst(".side .titlebox")
        fun number(css: String): Int? = box?.firstBelow(css)?.rawText()?.replace(",", "")?.let { digits.find(it)?.value?.toIntOrNull() }
        return OldSidebar(
            title = box?.firstBelow("h1.redditname a")?.rawText()?.trim(),
            description = box?.firstBelow(".usertext-body .md")?.rawText()?.trim()?.ifEmpty { null },
            subscribers = number(".subscribers .number"),
            activeUsers = number(".users-online .number"),
        )
    }

    private val digits = Regex("\\d+")
}

/**
 * A comment thread out of old.reddit.com's HTML (XTA's `reddit_comments.dart`).
 * Nesting comes from the markup's own shape, each comment holding its replies
 * in a `.child` block, so the tree is read rather than guessed at.
 */
internal object RedditOldComments {
    /**
     * The comment tree of a post page; none from a page it cannot read. The
     * post's own permalink stands in as the parent of the top level, so a
     * "load more comments (500)" at the bottom keeps its row.
     */
    fun comments(body: String, postPermalink: String? = null): List<RedditComment> {
        val document = Jsoup.parse(body)
        val area = document.selectFirst(".commentarea .sitetable") ?: document.selectFirst(".nestedlisting") ?: return emptyList()
        return commentsIn(area, postPermalink)
    }

    private fun commentsIn(listing: Element, parentPermalink: String?): List<RedditComment> =
        listing.children().filter { "thing" in it.classNames() }.mapNotNull { comment(it) ?: stub(it, parentPermalink) }

    private fun comment(thing: Element): RedditComment? {
        val fullname = thing.attrOrNull("data-fullname")
        if (fullname == null || !fullname.startsWith("t1_")) return stub(thing, null)
        // This comment's own entry, never a reply's: a selector would search the whole subtree.
        val entry = thing.children().firstOrNull { "entry" in it.classNames() }
        val content = RedditCommentContent.of(entry?.firstBelow(".usertext-body .md"))
        // A comment that is only a picture is still a comment.
        if (content.body.isEmpty() && content.mediaUrls.isEmpty()) return null
        return RedditComment(
            id = fullname.substring(3),
            author = thing.attrOrNull("data-author") ?: entry?.firstBelow("a.author")?.rawText()?.trim(),
            body = content.body,
            score = entry?.let(::score),
            createdAt = instantOf(entry?.firstBelow("time")?.attrOrNull("datetime")),
            isSubmitter = entry?.firstBelow(".author.submitter") != null,
            authorFlair = entry?.firstBelow(".tagline .flair")?.rawText()?.trim()?.ifEmpty { null },
            mediaUrls = content.mediaUrls,
            replies = repliesOf(thing),
            permalink = thing.attrOrNull("data-permalink"),
        )
    }

    /** "42 points", "1 point", or nothing when Reddit is hiding it. */
    private fun score(entry: Element): Int? {
        val text = (entry.firstBelow(".score.unvoted") ?: entry.firstBelow(".score"))?.rawText() ?: return null
        return signedNumber.find(text.replace(",", ""))?.value?.toIntOrNull()
    }

    private val signedNumber = Regex("-?\\d+")

    private fun repliesOf(thing: Element): List<RedditComment> {
        val child = thing.children().firstOrNull { "child" in it.classNames() } ?: return emptyList()
        val listing = child.children().firstOrNull { "sitetable" in it.classNames() } ?: return emptyList()
        return commentsIn(listing, thing.attrOrNull("data-permalink"))
    }

    /**
     * A "load more comments (N replies)" or "continue this thread" control,
     * kept as a row. The first goes nowhere without old.reddit's script, so
     * it points at [parentPermalink], the page that holds the replies; a
     * continuation carries its target in its link.
     */
    private fun stub(thing: Element, parentPermalink: String?): RedditComment? = when {
        "morechildren" in thing.classNames() -> parentPermalink?.let { parent ->
            val count = heldBackCount.find(thing.rawText().replace(",", ""))?.groupValues?.get(1)?.toIntOrNull()
            RedditComment(id = "more-$parent", body = "", permalink = parent, moreCount = count ?: -1)
        }
        "morerecursion" in thing.classNames() -> thing.firstBelow("a")?.attrOrNull("href")?.ifEmpty { null }?.let { href ->
            RedditComment(id = "continue-$href", body = "", permalink = href, moreCount = -1)
        }
        else -> null
    }

    private val heldBackCount = Regex("""\((\d[\d,]*)""")

    /**
     * What the post points at, read off its own page: a post from search
     * carries only its title, so the thread page is where its link and
     * pictures come from.
     */
    fun postMedia(body: String): PostMedia {
        val document = Jsoup.parse(body)
        val url = document.selectFirst("#siteTable .thing")?.attrOrNull("data-url")?.takeIf { it.startsWith("http") }
        val images = document.select("#siteTable .expando img, #siteTable .media-gallery img").mapNotNull { img ->
            (img.attrOrNull("src") ?: img.attrOrNull("data-lazy-src"))?.replace("&amp;", "&")
                ?.takeIf { hostOf(it) == "preview.redd.it" || hostOf(it) == "i.redd.it" }
        }
        return PostMedia(url, RedditMedia.collapseImageUrls(images))
    }

    /** The post's own words on its comment page, for a self post whose listing did not carry them. */
    fun selfText(body: String): String? {
        val document = Jsoup.parse(body)
        val block = document.selectFirst("#siteTable .expando .usertext-body .md") ?: document.selectFirst("#siteTable .usertext-body .md")
        return block?.rawText()?.trim()?.ifEmpty { null }
    }
}

/**
 * Search results out of old.reddit.com's HTML (XTA's `reddit_search_html.dart`):
 * `.search-result-*` blocks rather than a listing's `div.thing`, with both
 * shapes old.reddit has shipped tried, and anything unreadable skipped.
 */
internal object RedditOldSearch {
    /** Posts from a search page; one rendered as a listing is read as a listing. */
    fun posts(body: String): List<RedditPost> =
        RedditOldListing.page(body).posts.ifEmpty { Jsoup.parse(body).select(".search-result-link").mapNotNull(::post) }

    private fun post(result: Element): RedditPost? {
        val title = result.firstBelow(".search-title") ?: return null
        val href = title.attrOrNull("href") ?: return null
        val permalink = urlOf(href)?.encodedPath?.ifEmpty { null } ?: return null
        val fullname = result.attrOrNull("data-fullname")
        // The comments path is `/r/x/comments/<id>/slug/`, so the id is there when the attribute is not.
        val id = (if (fullname != null && fullname.startsWith("t3_")) fullname.substring(3) else nameAfter(href, "comments"))
            ?.ifEmpty { null } ?: return null
        return RedditPost(
            id = id,
            title = title.rawText().trim(),
            subreddit = nameAfter(result.firstBelow(".search-subreddit-link")?.attrOrNull("href"), "r") ?: nameAfter(href, "r") ?: "",
            permalink = permalink,
            author = result.firstBelow(".author")?.rawText()?.trim(),
            score = number(result.firstBelow(".search-score")?.rawText()) ?: 0,
            commentCount = number(result.firstBelow(".search-comments")?.rawText()) ?: 0,
            createdAt = instantOf(result.firstBelow("time")?.attrOrNull("datetime")),
            // A result names no link target of its own, so the post is its own page.
            isSelf = true,
            over18 = "over18" in result.classNames() || result.firstBelow(".nsfw-stamp") != null,
            spoiler = "spoiler" in result.classNames() || result.firstBelow(".spoiler-stamp") != null,
        )
    }

    /** Communities from `/subreddits/search` or a search page's community block, each once. */
    fun subreddits(body: String): List<RedditSubredditResult> {
        val seen = HashSet<String>()
        return Jsoup.parse(body).select(".search-result-subreddit, div.subreddit.thing")
            .mapNotNull(::subreddit).filter { seen.add(it.name.lowercase()) }
    }

    private fun subreddit(element: Element): RedditSubredditResult? {
        val link = element.firstBelow("a.search-subreddit-link") ?: element.firstBelow("a.title") ?: element.firstBelow("a[href*=\"/r/\"]")
        val name = nameAfter(link?.attrOrNull("href"), "r") ?: normaliseSubreddit(link?.rawText().orEmpty())
        if (name.isNullOrEmpty()) return null
        val description = element.firstBelow(".search-result-body")?.rawText()?.trim() ?: element.firstBelow(".description")?.rawText()?.trim()
        return RedditSubredditResult(
            name = name,
            subscribers = number(element.firstBelow(".search-subscribers")?.rawText() ?: element.firstBelow(".subscribers")?.rawText()),
            description = description?.ifEmpty { null },
        )
    }

    /** Accounts from `/search?q=…&type=user`, each once. */
    fun users(body: String): List<RedditUserResult> {
        val seen = HashSet<String>()
        return Jsoup.parse(body).select(".search-result-user").mapNotNull { element ->
            val name = nameAfter(element.firstBelow("a[href*=\"/user/\"]")?.attrOrNull("href"), "user")
            if (name.isNullOrEmpty() || !seen.add(name.lowercase())) null
            else RedditUserResult(name, number(element.firstBelow(".search-result-user-karma")?.rawText()))
        }
    }

    /** The first number in `1,234 subscribers`. */
    private fun number(text: String?): Int? = text?.replace(separators, "")?.let { signed.find(it)?.value?.toIntOrNull() }

    private val separators = Regex("[,.$SPACE]")
    private val signed = Regex("-?\\d+")
}
