package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.services.util.WebLink
import java.net.URLEncoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The profile tab an X profile link names (`/with_replies`, `/media`). */
enum class XProfileTab { POSTS_AND_REPLIES, MEDIA }

/** What an X link points at, as XTA's `parseUri` reads it. */
sealed interface XLink {
    data class Profile(val screenName: String, val tab: XProfileTab? = null) : XLink

    /**
     * A post. [direct] marks FxEmbed's direct-media links (`…/status/1.jpg`),
     * whose suffix is cut from [id]; [photo] is the `/photo/N` a link opened.
     */
    data class Post(val screenName: String?, val id: String, val photo: Int? = null, val direct: Boolean = false) : XLink

    data class XList(val id: String) : XLink

    /** A community, or its `/about` page. */
    data class Community(val id: String, val about: Boolean = false) : XLink

    /** A broadcast or a Space; [url] is the canonical watch address. */
    data class Live(val url: String, val isSpace: Boolean) : XLink

    /** A shared Grok conversation, which only X's web page shows in full. */
    data class GrokShare(val url: String) : XLink

    data object Unknown : XLink
}

/**
 * The rules XTA reads X links by (utils/urls.dart): article, broadcast,
 * Space and Grok ids, which X page a link is, tracking parameters stripped
 * before a link leaves the app, and the address a post is shared as.
 */
object XLinks {
    /** Every host an X link is written with. */
    val HOSTS = setOf("x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")

    private val trackingParams = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "twclid", "ttclid", "yclid", "igshid", "igsh", "mc_eid", "mc_cid",
        "mkt_tok", "li_fat_id", "gad_source", "gclsrc", "gbraid", "wbraid", "srsltid", "ocid", "_hsenc", "_hsmi", "wickedid",
    )
    private val trackingPrefixes = listOf("utm_", "mtm_", "pk_", "hsa_")

    /** Share ids X appends to copied links; stripped on X hosts only, where they cannot change the page. */
    private val xTrackingParams = setOf("s", "t", "ref_src", "ref_url")

    private fun xLink(url: String?): WebLink? = url?.let(WebLink::parse)?.takeIf { it.host in HOSTS }

    /** The article id in an `x.com/i/article/…` link, or null if it is not one. */
    fun articleId(url: String?): String? {
        val segments = xLink(url?.ifEmpty { null })?.segments ?: return null
        return segments.takeIf { it.size >= 3 && it[0] == "i" && it[1] == "article" }?.get(2)
    }

    /** The first article link among a post's expanded links, which the post shows as a card. */
    fun firstArticleLink(expandedUrls: Iterable<String?>): String? = expandedUrls.firstOrNull { articleId(it) != null }

    /** The conversation id in an `x.com/i/grok/share/…` link. */
    fun grokShareId(url: String?): String? {
        val segments = xLink(url?.trim())?.segments ?: return null
        return segments.takeIf { it.size >= 4 && it[0] == "i" && it[1] == "grok" && it[2] == "share" }?.get(3)
    }

    /**
     * The broadcast id in an `x.com/i/broadcasts/…` (or `/i/broadcast/…`,
     * `pscp.tv/w/…`) link. A scheme-less display URL counts; a truncated one
     * (`…`) does not, since its id would be wrong.
     */
    fun broadcastId(url: String?): String? {
        val live = liveLink(url) ?: return null
        return live.id.takeIf { live.pscp || live.kind == "broadcasts" || live.kind == "broadcast" }
    }

    /** The Space id in an `x.com/i/spaces/…` (or `/i/space/…`) link. */
    fun spaceId(url: String?): String? {
        val live = liveLink(url) ?: return null
        return live.id.takeIf { !live.pscp && (live.kind == "spaces" || live.kind == "space") }
    }

    fun broadcastUrl(id: String): String = "https://x.com/i/broadcasts/$id"

    fun spaceUrl(id: String): String = "https://x.com/i/spaces/$id"

    /** The first broadcast id in free text, such as a post body whose entities left the link out. */
    fun broadcastIdInText(text: String?): String? = firstIdInText(text, ::broadcastId, broadcastPattern)

    fun spaceIdInText(text: String?): String? = firstIdInText(text, ::spaceId, spacePattern)

    /** A broadcast, Periscope watch link or Space: the live player plays it. */
    fun isLiveWatchUrl(url: String?): Boolean = spaceId(url) != null || broadcastId(url) != null

    /**
     * An X page XTA has no screen for and must leave to a browser by name: a
     * plain VIEW of it would land back in XTA, which claims x.com links.
     */
    fun needsNamedBrowser(url: String): Boolean = isLiveWatchUrl(url) || grokShareId(url) != null

    /**
     * Whether a link in a post's text is left out of it: an article, broadcast
     * or Space link shows as its own block under the text, and [shownByCard]
     * one as the card below, so the URL would say the same thing twice.
     */
    fun leftOutOfText(expandedUrl: String?, shownByCard: Boolean = false): Boolean =
        shownByCard || articleId(expandedUrl) != null || broadcastId(expandedUrl) != null || spaceId(expandedUrl) != null

    /** [url] without the tracking parameters known to ride along (utm_*, fbclid, X's `s`/`t`, …). */
    fun clean(url: String): String {
        val link = WebLink.parse(url) ?: return url
        val onX = link.host in HOSTS
        return link.withoutQuery { key ->
            trackingPrefixes.any { key.startsWith(it) } || key in trackingParams || (onX && key in xTrackingParams)
        }
    }

    /** [clean] unless the reader turned that off; an unset switch strips, as XTA always has. */
    fun prepare(url: String, cleanLinks: Boolean?): String = if (cleanLinks != false) clean(url) else url

    /** The screen name an X profile link points at, without resolving anything; null for posts, lists, other hosts. */
    fun profileScreenName(url: String?): String? = xLink(url)?.segments?.let(::profile)?.screenName

    /** An X list id from what the reader typed: a bare number or a list URL. */
    fun listId(input: String): String? {
        val text = input.trim()
        if (digits.matches(text)) return text
        return WebLink.parse(text)?.segments?.let(::list)?.id
    }

    /**
     * The Wayback Machine's captures of a post, or null when [screenName] or
     * [id] is no real handle or post id. twitter.com, since most captures
     * predate x.com; the star also matches addresses that carried a query.
     */
    fun waybackUrl(screenName: String, id: String): String? =
        if (handle.matches(screenName) && digits.matches(id)) "https://web.archive.org/web/*/twitter.com/$screenName/status/$id*" else null

    /**
     * The address [post] is shared as, under [baseUrl] (the reader's share
     * host, x.com by default). A post X sent without its author is still
     * shareable, under `i`.
     */
    fun shareUrl(post: XPost, baseUrl: String = "https://x.com"): String? {
        val id = post.id.trim().ifEmpty { return null }
        val handle = post.author.screenName.trim()
        val author = if (handle.isEmpty()) "i" else encode(handle)
        return "${baseUrl.trimEnd('/')}/$author/status/${encode(id)}"
    }

    /**
     * What [url] points at on X, as `parseUri` reads it without resolving
     * t.co: a list, community, broadcast or Space, Grok share, profile or
     * post, in that order.
     */
    fun parse(url: String): XLink {
        val link = WebLink.parse(url.trim()) ?: return XLink.Unknown
        val segments = link.segments
        return list(segments)
            ?: community(segments)
            ?: live(link.text)
            ?: grokShareId(link.text)?.let { XLink.GrokShare(link.text) }
            ?: profile(segments)
            ?: post(segments)
            ?: XLink.Unknown
    }

    /**
     * [parse] after following a `t.co` short link by one request that does
     * not follow the redirect, as `parseUri` does; a short link that leads
     * nowhere, or to another short link, is unknown.
     */
    suspend fun resolve(url: String, http: XHttp): XLink {
        if (WebLink.parse(url.trim())?.host != "t.co") return parse(url)
        val target = redirect(url.trim(), http) ?: return XLink.Unknown
        return if (WebLink.parse(target)?.host == "t.co") XLink.Unknown else parse(target)
    }

    /** Where [url] redirects, from one request that does not follow it; null when it does not. */
    internal suspend fun redirect(url: String, http: XHttp): String? {
        val target = url.toHttpUrlOrNull() ?: return null
        val response = http.get(target, emptyMap(), followRedirects = false)
        val location = response.header("location")?.takeIf { response.code in 300..399 } ?: return null
        return WebLink.parse(url)?.resolve(location)?.text
    }

    private val digits = Regex("^\\d+$")
    private val handle = Regex("^[A-Za-z0-9_]{1,15}$")

    private val profileSubpaths = mapOf(
        "with_replies" to XProfileTab.POSTS_AND_REPLIES,
        "media" to XProfileTab.MEDIA,
        // Valid for an account link, though XTA has no tab for them.
        "highlights" to null, "affiliates" to null, "about" to null, "topics" to null, "lists" to null,
    )

    private fun profile(parts: List<String>): XLink.Profile? = when {
        parts.isEmpty() || parts[0] == "i" -> null
        parts.size == 1 -> XLink.Profile(parts[0])
        parts[1] in profileSubpaths -> XLink.Profile(parts[0], profileSubpaths[parts[1]])
        else -> null
    }

    private fun list(parts: List<String>): XLink.XList? =
        if (parts.size == 3 && parts[0] == "i" && parts[1] == "lists" && digits.matches(parts[2])) XLink.XList(parts[2]) else null

    private fun community(parts: List<String>): XLink.Community? {
        if (parts.size < 3 || parts[0] != "i" || parts[1] != "communities" || !digits.matches(parts[2])) return null
        return when (parts.drop(3)) {
            emptyList<String>() -> XLink.Community(parts[2])
            listOf("about") -> XLink.Community(parts[2], about = true)
            else -> null
        }
    }

    private fun post(parts: List<String>): XLink.Post? = when {
        parts.size >= 3 && parts[1] == "status" -> postOf(parts[0], parts[2], photoNumber(parts, 3))
        parts.size >= 4 && parts[0] == "i" && parts[1] == "topics" && parts[2] == "tweet" -> postOf(null, parts[3], photoNumber(parts, 4))
        else -> null
    }

    private fun postOf(screenName: String?, raw: String, photo: Int?): XLink.Post {
        val direct = raw.endsWith(".jpg") || raw.endsWith(".mp4")
        return XLink.Post(screenName, raw.replace(Regex("\\.(?:jpg|mp4)$"), ""), photo, direct)
    }

    private fun photoNumber(parts: List<String>, index: Int): Int? =
        if (parts.size >= index + 2 && parts[index] == "photo") parts[index + 1].toIntOrNull() else null

    private fun live(url: String): XLink.Live? {
        spaceId(url)?.let { return XLink.Live(spaceUrl(it), isSpace = true) }
        return broadcastId(url)?.let { XLink.Live(broadcastUrl(it), isSpace = false) }
    }

    private data class LiveLink(val id: String, val kind: String, val pscp: Boolean = false)

    private val pscpHosts = setOf("pscp.tv", "www.pscp.tv", "periscope.tv", "www.periscope.tv")

    private fun liveLink(url: String?): LiveLink? {
        var text = url?.trim()?.ifEmpty { null } ?: return null
        if (text.contains('…') || text.lowercase().contains("%e2%80%a6") || text.contains("...")) return null
        text = text.replace(Regex("[.,);]+$"), "")
        if (!text.contains("://")) text = "https://$text"
        val link = WebLink.parse(text) ?: return null
        val parts = link.segments
        if (link.host in pscpHosts) {
            return if (parts.size >= 2 && parts[0] == "w") LiveLink(parts[1], "pscp", pscp = true) else null
        }
        if (link.host !in HOSTS || parts.size < 3 || parts[0] != "i") return null
        return LiveLink(parts[2], parts[1])
    }

    private val broadcastPattern = Regex(
        "(?:https?://)?(?:(?:www|mobile)\\.)?(?:x\\.com|twitter\\.com)/i/broadcasts?/\\S+|https?://(?:www\\.)?(?:pscp|periscope)\\.tv/w/\\S+",
        RegexOption.IGNORE_CASE,
    )
    private val spacePattern = Regex("(?:https?://)?(?:(?:www|mobile)\\.)?(?:x\\.com|twitter\\.com)/i/spaces?/\\S+", RegexOption.IGNORE_CASE)

    private fun firstIdInText(text: String?, parse: (String?) -> String?, pattern: Regex): String? =
        text?.let { pattern.findAll(it).firstNotNullOfOrNull { match -> parse(match.value) } }

    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}
