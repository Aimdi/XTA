package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.services.util.WebLink

/** What a piece of text shared to the app from another one asks to open. */
sealed interface SharedTarget {
    /** An X post, profile or short link — or a Pixiv link while Pixiv is on — opened through the app's own link handling. */
    data class AppLink(val url: String) : SharedTarget

    /** Any other web page: a network's screen when one reads it, else the browser. */
    data class Web(val url: String) : SharedTarget

    /** A bare number while Pixiv is on: a work's id. */
    data class PixivId(val id: String) : SharedTarget

    /** Text without a link, searched for on X. */
    data class Search(val query: String) : SharedTarget

    /** An empty share. */
    data object Empty : SharedTarget
}

/**
 * The rules for text shared to the app (shared_links.dart): an X link wins
 * over any other link in it, since a caption often quotes a page the post
 * is about; links with credentials, ports or look-alike hosts are refused;
 * text without a link is a search.
 */
object SharedLinks {
    private val shareHosts = setOf(
        "x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "t.co",
        "fxtwitter.com", "www.fxtwitter.com", "vxtwitter.com", "www.vxtwitter.com", "fixupx.com", "www.fixupx.com",
    )
    private val links = Regex("""(?:https?|pixiv)://[^\s<>"​]+""", RegexOption.IGNORE_CASE)
    private val trailing = Regex("""[)\]}>.,!?;:'"]+$""")

    /** X caps a search query at 500 characters. */
    private const val MAX_QUERY = 500

    /** Every web or Pixiv app link in [text], in order, trailing punctuation trimmed. */
    private fun sharedLinks(text: String): Sequence<WebLink> =
        links.findAll(text).mapNotNull { WebLink.parse(it.value.replace(trailing, "")) }

    /** Every http(s) link in shared text, in order. */
    fun urls(text: String): List<String> = sharedLinks(text).filter { it.scheme == "http" || it.scheme == "https" }.map { it.text }.toList()

    /** The first X link in a share — or Pixiv link, when [pixiv] is on. */
    fun appLink(text: String, pixiv: Boolean = false): String? = sharedLinks(text).firstOrNull { supported(it, pixiv) }?.text

    /**
     * Routes a share: an X (or, with [pixiv] on, Pixiv) link first, a bare
     * number as a Pixiv work, any other web link, else a search with its
     * whitespace collapsed and cut to what X search takes.
     */
    fun target(text: String, pixiv: Boolean = false): SharedTarget {
        appLink(text, pixiv)?.let { return SharedTarget.AppLink(it) }
        pixivId(text)?.takeIf { pixiv }?.let { return SharedTarget.PixivId(it) }
        sharedLinks(text).firstOrNull { it.isWeb && it.userInfo == null }?.let { return SharedTarget.Web(it.text) }
        val query = text.trim().replace(Regex("\\s+"), " ")
        if (query.isEmpty()) return SharedTarget.Empty
        val points = query.codePoints().limit(MAX_QUERY.toLong()).toArray()
        return SharedTarget.Search(String(points, 0, points.size).trim())
    }

    /** A share that is only a number, which Pixiv reads as a work's id. */
    fun pixivId(text: String): String? = text.trim().takeIf { Regex("^\\d{1,12}$").matches(it) }

    /**
     * Whether X's link reader should read [url], which no network opened: a
     * Pixiv page should not, as X would take `pixiv.net/en/` for the profile
     * `@en`; it goes to the browser instead.
     */
    fun readsAsXLink(url: String): Boolean = WebLink.parse(url)?.let { !isPixivShare(it) } ?: true

    /**
     * [appLink] with an X short link followed, one request at a time without
     * following redirects to other hosts, at most three hops; null when it
     * leads off X or nowhere.
     */
    suspend fun resolve(text: String, http: XHttp, pixiv: Boolean = false): String? {
        var url = appLink(text, pixiv) ?: return null
        repeat(3) {
            if (WebLink.parse(url)?.host != "t.co") return url
            url = XLinks.redirect(url, http) ?: return null
            if (WebLink.parse(url)?.let { supported(it, pixiv = false) } != true) return null
        }
        return url.takeIf { WebLink.parse(it)?.host != "t.co" }
    }

    private fun supported(link: WebLink, pixiv: Boolean): Boolean {
        val web = link.scheme == "https" || link.scheme == "http"
        return (web || link.scheme == "pixiv") && link.userInfo == null && link.port == -1 &&
            ((web && link.host in shareHosts) || (pixiv && isPixivShare(link)))
    }

    /** Pixiv's pages, its image files (whose names carry the work's id) and its app links. */
    private fun isPixivShare(link: WebLink): Boolean = link.scheme == "pixiv" || PixivHosts.family(link.host)
}
