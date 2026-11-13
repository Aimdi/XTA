package com.aimdi.xtanative.networks

import java.time.Instant

/** A file an item carries: a podcast's audio, a video, a picture. */
data class RssEnclosure(val url: String, val type: String?, val length: Long?)

/**
 * One item of an RSS or Atom feed, as the RSS plugin follows it and a web
 * source reads one. [id] is the feed's id and the item's own guid, link or
 * title, so the same post read twice is the same item.
 */
data class RssItem(
    val id: String,
    val title: String,
    val feedId: String,
    val feedTitle: String,
    val link: String? = null,
    /** The body as plain text, at most 240 characters. */
    val excerpt: String? = null,
    /** The full body as the feed sent it: HTML, to be sanitized before it is shown ([RssHtml.sanitize]). */
    val bodyHtml: String? = null,
    val publishedAt: Instant? = null,
    val author: String? = null,
    /** The item's picture, else (once read through the RSS client) the feed's. */
    val imageUrl: String? = null,
    val categories: List<String> = emptyList(),
    val enclosures: List<RssEnclosure> = emptyList(),
) {
    /** Whether the body says more than the excerpt: what makes the reader worth opening. */
    val hasReadableBody: Boolean
        get() = bodyHtml?.trim().orEmpty().let { it.isNotEmpty() && it != excerpt?.trim() }
}

/**
 * What a feed document says about itself, and its items. A mangled feed
 * still yields whatever could be read, never a throw.
 */
data class RssChannel(
    val title: String? = null,
    val description: String? = null,
    val link: String? = null,
    val imageUrl: String? = null,
    val items: List<RssItem> = emptyList(),
)
