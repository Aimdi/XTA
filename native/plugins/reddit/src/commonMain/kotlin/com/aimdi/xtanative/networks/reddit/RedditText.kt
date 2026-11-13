package com.aimdi.xtanative.networks.reddit

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One run of a title, self text or comment: words, or a link with the short name it is shown by. */
data class RedditTextPart(val text: String, val url: String? = null) {
    val isLink: Boolean get() = url != null
}

/**
 * [raw] split into words and links (XTA's `redditTextParts`): `[label](url)`
 * keeps its label, and a bare URL is shortened to its host and first path
 * segment, so a Drive folder or an X post does not print its query string
 * across four lines.
 */
fun redditTextParts(raw: String): List<RedditTextPart> {
    if (raw.isEmpty()) return emptyList()
    val parts = mutableListOf<RedditTextPart>()
    var cursor = 0
    for (match in textLink.findAll(raw)) {
        if (match.range.first > cursor) parts += RedditTextPart(raw.substring(cursor, match.range.first))
        val label = match.groups[1]?.value
        val url = (if (label == null) match.value else match.groupValues[2]).replace(urlJunk, "")
        parts += RedditTextPart(compactRedditLinkLabel(url, label), url)
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) parts += RedditTextPart(raw.substring(cursor))
    return parts
}

private val textLink = Regex("""\[([^\]\n]{1,200})\]\((https?://[^)$SPACE]+)\)|https?://[^$SPACE\])<>]+""", RegexOption.IGNORE_CASE)
private val urlJunk = Regex("[.,;:!?]+$")
private val xHosts = setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")

/**
 * What a link is called on a card: its markdown [label] when that is a word,
 * otherwise its host, with the first path segment when that is the link's
 * identity (an X account, a community, a short page name).
 */
fun compactRedditLinkLabel(url: String, label: String? = null): String {
    val named = label?.trim()
    if (!named.isNullOrEmpty() && !named.lowercase().startsWith("http")) {
        return if (named.length > 42) named.substring(0, 41) + "…" else named
    }
    val parsed = url.toHttpUrlOrNull() ?: return url
    val host = parsed.host.removePrefix("www.")
    val segments = parsed.pathSegments.filter { it.isNotEmpty() }
    val first = segments.firstOrNull() ?: return host
    val reddit = host == "reddit.com" || host.endsWith(".reddit.com")
    return when {
        host == "drive.google.com" || host.endsWith(".google.com") -> host
        parsed.host in xHosts || host == "x.com" || host == "twitter.com" -> "$host/$first"
        reddit && first == "r" && segments.size >= 2 -> "r/${segments[1]}"
        reddit && (first == "u" || first == "user") && segments.size >= 2 -> "u/${segments[1]}"
        first.length <= 22 -> "$host/$first"
        else -> host
    }
}
