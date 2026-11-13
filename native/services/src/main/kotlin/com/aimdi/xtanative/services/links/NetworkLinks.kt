package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.services.util.WebLink

/**
 * A web link one of XTA's other networks opens on its own screen. [network]
 * is the network's id, the one its on-switch in settings is kept under.
 */
sealed interface NetworkLink {
    val network: String

    data class BlueskyProfile(val actor: String) : NetworkLink {
        override val network get() = BLUESKY
    }

    data class BlueskyPost(val actor: String, val rkey: String) : NetworkLink {
        override val network get() = BLUESKY
        val atUri: String get() = "at://$actor/app.bsky.feed.post/$rkey"
    }

    data class ThreadsProfile(val handle: String) : NetworkLink {
        override val network get() = THREADS
    }

    /** A post; [handle] is empty for a `/t/CODE` share link, whose author is known once the page is read. */
    data class ThreadsPost(val url: String, val handle: String) : NetworkLink {
        override val network get() = THREADS
    }

    data class InstagramProfile(val handle: String) : NetworkLink {
        override val network get() = INSTAGRAM
    }

    data class TikTokProfile(val handle: String) : NetworkLink {
        override val network get() = TIKTOK
    }

    data class TikTokVideo(val id: String, val handle: String?) : NetworkLink {
        override val network get() = TIKTOK
    }

    data class RedditSubreddit(val name: String) : NetworkLink {
        override val network get() = REDDIT
    }

    data class RedditUser(val name: String) : NetworkLink {
        override val network get() = REDDIT
    }

    /** A thread; [title] is the slug of its link, underscores read as spaces. */
    data class RedditThread(val id: String, val subreddit: String, val permalink: String, val title: String? = null) : NetworkLink {
        override val network get() = REDDIT
    }

    data class MastodonProfile(val acct: String) : NetworkLink {
        override val network get() = MASTODON
    }

    data class MastodonStatus(val acct: String, val statusId: String, val url: String) : NetworkLink {
        override val network get() = MASTODON
    }

    data class HnStory(val id: Long) : NetworkLink {
        override val network get() = HACKERNEWS
    }

    data class HnUser(val id: String) : NetworkLink {
        override val network get() = HACKERNEWS
    }

    /**
     * A link a network's own link reader claimed (Pixiv, a booru, Substack):
     * that network reads the rest of it, so it is not read twice here.
     */
    data class Claimed(override val network: String, val url: String) : NetworkLink

    companion object {
        const val BLUESKY = "bluesky"
        const val THREADS = "threads"
        const val INSTAGRAM = "instagram"
        const val TIKTOK = "tiktok"
        const val REDDIT = "reddit"
        const val MASTODON = "mastodon"
        const val HACKERNEWS = "hackernews"
        const val PIXIV = "pixiv"
        const val BOORU = "booru"
        const val SUBSTACK = "substack"
    }
}

/**
 * Which network reads a web link (plugin_url.dart): each network's reader
 * is tried in XTA's order — Bluesky, Threads, Instagram, TikTok, Reddit,
 * Hacker News, Pixiv, a booru, then Mastodon on [mastodonHosts] (host
 * names, not URLs). Pixiv's and the boorus' links are read by those
 * networks' own readers, passed in as [pixiv] and [booru]; each says whether
 * it reads a link.
 */
class NetworkLinks(
    private val mastodonHosts: Set<String> = emptySet(),
    private val pixiv: (String) -> Boolean = { false },
    private val booru: (String) -> Boolean = { false },
) {
    fun parse(url: String): NetworkLink? = bluesky(url)
        ?: threads(url)
        ?: instagram(url)
        ?: tiktok(url)
        ?: reddit(url)
        ?: hackerNews(url)
        ?: pixivLink(url)
        ?: url.takeIf { WebLink.web(it) != null && booru(it) }?.let { NetworkLink.Claimed(NetworkLink.BOORU, it) }
        ?: mastodon(url)

    /** A pixiv.net, pixiv.me, pixivision.net or i.pximg.net page, or a `pixiv://` app link, that Pixiv's reader reads. */
    private fun pixivLink(url: String): NetworkLink? {
        val appLink = WebLink.parse(url.trim())?.scheme == "pixiv"
        if (!appLink && WebLink.web(url)?.host?.let(PixivHosts::family) != true) return null
        return if (pixiv(url)) NetworkLink.Claimed(NetworkLink.PIXIV, url) else null
    }

    /** A `/@user` or `/users/user` link on one of [mastodonHosts]. */
    fun mastodon(url: String): NetworkLink? {
        val link = WebLink.web(url) ?: return null
        if (mastodonHosts.isEmpty() || link.host !in mastodonHosts || link.segments.isEmpty()) return null
        val parts = link.segments
        return when {
            parts.size >= 4 && parts[0].lowercase() == "users" && parts[2].lowercase() == "statuses" && digits.matches(parts[3]) ->
                NetworkLink.MastodonStatus("${parts[1]}@${link.host}", parts[3], link.text)
            parts.size >= 2 && parts[0].lowercase() == "users" -> NetworkLink.MastodonProfile("${parts[1]}@${link.host}")
            !parts[0].startsWith("@") -> null
            else -> atHandle(parts[0])?.let { user ->
                if (parts.size >= 2 && digits.matches(parts[1])) {
                    NetworkLink.MastodonStatus("$user@${link.host}", parts[1], link.text)
                } else {
                    NetworkLink.MastodonProfile("$user@${link.host}")
                }
            }
        }
    }

    companion object {
        /** The hosts XTA reads as Mastodon: the domains of the reader's instances and the defaults. */
        fun mastodonHosts(instances: Iterable<String>): Set<String> =
            instances.mapNotNull { instance -> WebLink.parse(instance.trim())?.host?.ifEmpty { null } }.toSet()

        /** The instances XTA knows without being told (mastodon_models.dart). */
        val MASTODON_DEFAULT_INSTANCES = listOf(
            "https://mastodon.social", "https://mastodon.online", "https://mstdn.social", "https://mas.to", "https://mastodon.world",
        )

        private val digits = Regex("^\\d+$")

        fun bluesky(url: String): NetworkLink? {
            val link = WebLink.web(url)?.takeIf { hostIs(it.host, "bsky.app") } ?: return null
            val parts = link.segments
            if (parts.size < 2 || parts[0] != "profile" || parts[1].isEmpty()) return null
            return if (parts.size >= 4 && parts[2] == "post" && parts[3].isNotEmpty()) {
                NetworkLink.BlueskyPost(parts[1], parts[3])
            } else {
                NetworkLink.BlueskyProfile(parts[1])
            }
        }

        fun threads(url: String): NetworkLink? {
            val link = WebLink.web(url)?.takeIf { hostIs(it.host, "threads.net", "threads.com") } ?: return null
            val parts = link.segments.ifEmpty { return null }
            // Share sheets hand out /t/CODE; Threads redirects it to the post.
            if (parts[0].lowercase() == "t") {
                return parts.getOrNull(1)?.ifEmpty { null }?.let { NetworkLink.ThreadsPost("https://www.threads.com/t/$it", "") }
            }
            val handle = atHandle(parts[0]) ?: return null
            return if (parts.size >= 3 && parts[1] == "post" && parts[2].isNotEmpty()) {
                NetworkLink.ThreadsPost(link.text, handle)
            } else {
                NetworkLink.ThreadsProfile(handle)
            }
        }

        private val instagramReserved = setOf(
            "p", "reel", "reels", "stories", "explore", "accounts", "about", "legal", "direct", "tv", "share", "emails", "web",
            "lite", "developer", "directory", "privacy", "terms",
        )
        private val instagramHandle = Regex("^[A-Za-z0-9._]{1,30}$")

        fun instagram(url: String): NetworkLink? {
            val link = WebLink.web(url)?.takeIf { hostIs(it.host, "instagram.com") } ?: return null
            val first = link.segments.firstOrNull()?.lowercase() ?: return null
            return if (first in instagramReserved || !instagramHandle.matches(first)) null else NetworkLink.InstagramProfile(first)
        }

        private val tiktokReserved = setOf(
            "foryou", "following", "live", "music", "tag", "search", "explore", "discover", "upload", "messages", "activity", "about", "legal",
        )

        fun tiktok(url: String): NetworkLink? {
            val link = WebLink.web(url) ?: return null
            // Short links (vm., vt.) only say where they go once followed.
            if (link.host == "vm.tiktok.com" || link.host == "vt.tiktok.com") return null
            if (link.host != "tiktok.com" && !link.host.endsWith(".tiktok.com")) return null
            val parts = link.segments.ifEmpty { return null }
            val handle = atHandle(parts[0])?.takeIf { it !in tiktokReserved } ?: return null
            return if (parts.size >= 3 && parts[1] == "video" && digits.matches(parts[2])) {
                NetworkLink.TikTokVideo(parts[2], handle)
            } else {
                NetworkLink.TikTokProfile(handle)
            }
        }

        private val redditId = Regex("^[A-Za-z0-9]{5,10}$")
        private val subredditName = Regex("^[A-Za-z0-9_]{2,50}$")

        fun reddit(url: String): NetworkLink? {
            val link = WebLink.web(url) ?: return null
            val host = link.host
            // Media CDNs: the picture or the video, not a thread.
            if (host == "i.redd.it" || host == "v.redd.it" || host.startsWith("preview.") || host.startsWith("external-preview.")) return null
            val parts = link.segments
            if (host == "redd.it" || host == "www.redd.it") {
                if (parts.size != 1 || !redditId.matches(parts[0])) return null
                val id = parts[0].lowercase()
                return NetworkLink.RedditThread(id, "", "/comments/$id/")
            }
            if (host != "reddit.com" && !host.endsWith(".reddit.com")) return null
            return when {
                parts.size >= 2 && (parts[0] == "r" || parts[0] == "R") && subredditName.matches(parts[1]) -> redditCommunity(parts)
                parts.size >= 2 && (parts[0] == "u" || parts[0] == "user") -> NetworkLink.RedditUser(parts[1])
                else -> null
            }
        }

        private fun redditCommunity(parts: List<String>): NetworkLink {
            val subreddit = parts[1]
            if (parts.size >= 4 && parts[2] == "comments" && redditId.matches(parts[3])) {
                val id = parts[3].lowercase()
                return NetworkLink.RedditThread(id, subreddit, "/r/$subreddit/comments/$id/", parts.getOrNull(4)?.replace('_', ' '))
            }
            return NetworkLink.RedditSubreddit(subreddit)
        }

        fun hackerNews(url: String): NetworkLink? {
            val link = WebLink.web(url)?.takeIf { hostIs(it.host, "news.ycombinator.com") } ?: return null
            val first = link.segments.firstOrNull() ?: return null
            val id = link.query("id")?.trim()?.ifEmpty { null } ?: return null
            return when (first) {
                "item" -> id.toLongOrNull()?.let { NetworkLink.HnStory(it) }
                "user" -> NetworkLink.HnUser(id)
                else -> null
            }
        }

        private val handleChars = Regex("^[A-Za-z0-9._]+$")

        /** `@name` or `name` as a lower-case handle; null for anything else. */
        private fun atHandle(segment: String): String? =
            segment.removePrefix("@").takeIf { it.isNotEmpty() && handleChars.matches(it) }?.lowercase()

        private fun hostIs(host: String, vararg names: String): Boolean = host in names || host.removePrefix("www.") in names
    }
}

/** The hosts of Pixiv's family, as pixiv_links.dart names them. */
object PixivHosts {
    /** The claimed hosts XTA's Android app opens pixiv.net links for. */
    val CLAIMED = setOf("pixiv.net", "www.pixiv.net", "pixiv.me")

    fun web(host: String): Boolean = host.lowercase().let { it == "pixiv.net" || it.endsWith(".pixiv.net") || it == "pixiv.me" || it.endsWith(".pixiv.me") }

    fun image(host: String): Boolean = host.lowercase().let { it == "pximg.net" || it.endsWith(".pximg.net") }

    fun pixivision(host: String): Boolean = host.lowercase().let { it == "pixivision.net" || it.endsWith(".pixivision.net") }

    fun family(host: String): Boolean = web(host) || image(host) || pixivision(host)
}
