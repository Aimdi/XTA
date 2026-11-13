package com.aimdi.xtanative.networks.reddit

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A subreddit's name out of whatever was pasted: `dartlang`, `r/dartlang`,
 * `/r/dartlang/` or a reddit.com URL. Null for anything that is not one, so
 * a name Reddit would not have never leaves the device.
 */
fun normaliseSubreddit(raw: String): String? {
    val trimmed = raw.trim().ifEmpty { return null }
    val named = if (trimmed.toHttpUrlOrNull() != null) nameAfter(trimmed, "r") ?: return null else trimmed
    return named.replace(leadingR, "").replace(edgeSlashes, "").takeIf(subredditName::matches)
}

private val leadingR = Regex("^/?r/", RegexOption.IGNORE_CASE)
private val edgeSlashes = Regex("^/+|/+$")
private val subredditName = Regex("^[A-Za-z0-9_]{2,21}$")

/** The hosts XTA reads Reddit through. */
internal object RedditHosts {
    /** The website, which still serves pieces of its pages to a reader without an account. */
    const val WWW = "https://www.reddit.com"

    /** The API, for a reader with an account or a client id. */
    const val OAUTH = "https://oauth.reddit.com"
    const val OAUTH_HOST = "oauth.reddit.com"

    /** The old website, served and throttled apart from [WWW]. */
    const val OLD = "https://old.reddit.com"

    /** The cards other sites embed posts with. */
    const val EMBED = "https://embed.reddit.com"

    /** The pieces of [WWW]'s pages the site loads as it scrolls. */
    const val WWW_PIECES = "$WWW/svc/shreddit"
}

/** [base] with [query] added, in order; a null value is left out. */
internal fun redditUrl(base: String, query: Map<String, String?> = emptyMap()): HttpUrl =
    base.toHttpUrl().newBuilder().apply { query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) } }.build()

/** The parts of a post's or a comment's page. */
internal data class PostPage(
    /** `r/FlutterDev` or `user/spez`. */
    val owner: String,
    /** The community www.reddit.com files the post under: `u_spez` for a profile post. */
    val community: String,
    val post: String,
    val comment: String?,
)

internal object RedditLinks {
    /**
     * The parts of a post's or comment's page, or null for anything else or
     * for a part that does not look like Reddit's own. Both spellings of a
     * comment's page are read: old.reddit's `…/comments/<post>/<slug>/<comment>/`
     * and www's `…/comments/<post>/comment/<comment>/`.
     */
    fun postPageOf(permalink: String): PostPage? {
        val segments = segmentsOf(permalink)
        val at = segments.indexOf("comments")
        if (at < 2 || at + 1 >= segments.size) return null
        val kind = if (segments[at - 2] == "r") "r" else "user"
        val name = segments[at - 1]
        val community = if (kind == "r") name else "u_$name"
        val post = segments[at + 1]
        val comment = segments.drop(at + 2).getOrNull(1)
        if (!wwwName.matches(community) || !listOfNotNull(post, comment).all(wwwId::matches)) return null
        return PostPage("$kind/$name", community, post, comment)
    }

    private val wwwName = Regex("^[A-Za-z0-9_]{2,24}$")
    private val wwwId = Regex("^[a-z0-9]{1,16}$")

    /**
     * Where www.reddit.com serves [permalink]'s comments, or null when it is
     * not a post's or comment's page. A comment's page gets that comment and
     * its replies; [sort] rides along in the site's spelling.
     */
    fun wwwCommentsUrl(permalink: String, sort: RedditCommentSort = RedditCommentSort.BEST): HttpUrl? {
        val page = postPageOf(permalink) ?: return null
        val path = listOfNotNull("comments/r/${page.community}/t3_${page.post}", page.comment?.let { "t1_$it" }).joinToString("/")
        return redditUrl("${RedditHosts.WWW_PIECES}/$path", mapOf("sort" to sort.query.uppercase().ifEmpty { null }))
    }

    /** Where Reddit serves the embed card of [permalink]'s post. A comment's page embeds the comment, so its post's card is asked for. */
    fun wwwEmbedUrl(permalink: String): HttpUrl? =
        postPageOf(permalink)?.let { redditUrl("${RedditHosts.EMBED}/${it.owner}/comments/${it.post}/", mapOf("embed" to "true")) }

    /** [permalink]'s thread as JSON: its path, without a query or a last slash, ending `.json`. */
    fun commentsJsonPath(permalink: String): String {
        val path = permalink.substringBefore('?').trim().let { if (it.startsWith("/")) it else "/$it" }.removeSuffix("/")
        return if (path.endsWith(".json")) path else "$path.json"
    }
}
