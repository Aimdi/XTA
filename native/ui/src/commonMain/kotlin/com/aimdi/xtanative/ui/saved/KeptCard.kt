package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedSource

/**
 * A kept post of another network, as Saved draws it until that network's own
 * card is here: who wrote it, its title and text, its pictures, and where it
 * lives on its network, which a tap opens.
 */
data class KeptCard(
    val source: SavedSource,
    val name: String,
    val handle: String?,
    val title: String?,
    val text: String,
    val images: List<String>,
    val url: String?,
    /** A link kept from a network shown under its own name (a plugin's). */
    val linkSource: String? = null,
) {
    companion object {
        /** The card for [content]; null for an X post, which draws as a post, and for nothing kept. */
        fun of(content: SavedContent): KeptCard? {
            val post = content.post
            return when (content.kind) {
                SavedContent.Kind.LINK -> link(post)
                SavedContent.Kind.BLUESKY -> bluesky(post)
                SavedContent.Kind.THREADS -> threads(post)
                SavedContent.Kind.MASTODON -> mastodon(post)
                SavedContent.Kind.REDDIT -> reddit(post)
                SavedContent.Kind.X, SavedContent.Kind.NONE -> null
            }
        }

        private fun link(blob: J) = KeptCard(
            source = if (blob["source"].string == "threads") SavedSource.THREADS else SavedSource.OTHER,
            name = blob["author"].string.orEmpty(),
            handle = null,
            title = null,
            text = blob["text"].string.orEmpty(),
            images = strings(blob["images"]),
            url = blob["url"].string,
            linkSource = blob["source"].string,
        )

        private fun bluesky(post: J): KeptCard {
            val handle = post["handle"].string.orEmpty()
            return KeptCard(
                source = SavedSource.BLUESKY,
                name = post["authorName"].string?.takeIf { it.isNotBlank() } ?: handle,
                handle = handle.takeIf { it.isNotEmpty() }?.let { "@$it" },
                title = null,
                text = post["text"].string.orEmpty(),
                images = strings(post["images"]),
                url = blueskyUrl(handle, post["uri"].string),
            )
        }

        private fun threads(post: J): KeptCard {
            val handle = post["handle"].string.orEmpty()
            return KeptCard(
                source = SavedSource.THREADS,
                name = post["authorName"].string?.takeIf { it.isNotBlank() } ?: handle,
                handle = handle.takeIf { it.isNotEmpty() }?.let { "@$it" },
                title = null,
                text = post["text"].string.orEmpty(),
                images = strings(post["images"]),
                url = post["url"].string,
            )
        }

        private fun mastodon(post: J): KeptCard {
            val acct = post["acct"].string.orEmpty()
            val spoiler = post["spoiler"].string?.let(::plainText)?.takeIf { it.isNotBlank() }
            return KeptCard(
                source = SavedSource.MASTODON,
                name = post["name"].string?.takeIf { it.isNotBlank() } ?: acct,
                handle = "@$acct",
                title = spoiler,
                text = plainText(post["text"].string.orEmpty()),
                images = strings(post["images"]),
                url = post["url"].string,
            )
        }

        private fun reddit(post: J): KeptCard {
            val gallery = strings(post["galleryImages"])
            val picture = post["url"].string?.takeIf { url -> imageExtensions.any { url.substringBefore('?').lowercase().endsWith(it) } }
            return KeptCard(
                source = SavedSource.REDDIT,
                name = "r/" + post["subreddit"].string.orEmpty(),
                handle = post["author"].string?.let { "u/$it" },
                title = post["title"].string,
                text = post["selfText"].string.orEmpty(),
                images = gallery.ifEmpty { listOfNotNull(picture) },
                url = post["permalink"].string?.let { "https://www.reddit.com$it" },
            )
        }

        private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

        /** `at://did/app.bsky.feed.post/<key>` as the post's page on bsky.app. */
        internal fun blueskyUrl(handle: String, uri: String?): String? {
            val key = uri?.takeIf { it.startsWith("at://") }?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: return null
            val who = handle.ifEmpty { uri.removePrefix("at://").substringBefore('/') }
            return "https://bsky.app/profile/$who/post/$key"
        }

        private fun strings(json: J): List<String> = json.list.mapNotNull { it.string }
    }
}

private val breaks = Regex("(?i)<br\\s*/?>|</p>\\s*<p[^>]*>")
private val tags = Regex("<[^>]+>")

/**
 * [html] as the text it shows, Mastodon's way of writing posts: breaks and
 * paragraphs as new lines, other tags dropped, the common entities read.
 */
internal fun plainText(html: String): String = html
    .replace(breaks, "\n")
    .replace(tags, "")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .trim()
