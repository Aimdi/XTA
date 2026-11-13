package com.aimdi.xtanative.ui.subscriptions

import java.net.URI

/**
 * Which networks what was typed into a group's add box could be on, best
 * guess first, as XTA's group_add_sources.dart reads it: the plugins to ask
 * what it names there ([com.aimdi.xtanative.ui.plugins.XtaPlugin.followable]).
 * A pasted address names one network; a bare word could be a subreddit, a
 * Threads handle or a newsletter, and all three are offered.
 */
object GroupAddSources {
    const val REDDIT = "reddit"
    const val THREADS = "threads"
    const val SUBSTACK = "substack"
    const val BLUESKY = "bluesky"
    const val MASTODON = "mastodon"
    const val RSS = "rss"

    /** The plugin ids [raw] could belong to, best guess first; none for blank text. */
    fun pluginsFor(raw: String): List<String> {
        val query = raw.trim()
        if (query.isEmpty()) return emptyList()
        val address = addressOf(query)
        return if (address == null) fromTypedName(query) else fromAddress(query, address)
    }

    /** Only a real scheme makes an address: `alice.bsky.social` and `r/flutter` are typed names. */
    private fun addressOf(query: String): URI? {
        if ("://" !in query) return null
        val uri = runCatching { URI(query) }.getOrNull() ?: return null
        return uri.takeIf { !it.host.isNullOrEmpty() }
    }

    private fun fromAddress(query: String, uri: URI): List<String> {
        val host = uri.host.lowercase()
        val first = uri.path.orEmpty().split('/').firstOrNull { it.isNotEmpty() }
        return when {
            "reddit." in host -> listOf(REDDIT)
            "threads." in host -> listOf(THREADS)
            host == "bsky.app" || host == "www.bsky.app" || host.endsWith(".bsky.social") -> listOf(BLUESKY)
            // Every Fediverse instance has its own domain: a profile is told by its path.
            first != null && (first.startsWith("@") || first == "users") -> listOf(MASTODON)
            looksLikeFeed(uri) -> listOf(RSS)
            else -> listOf(SUBSTACK)
        }
    }

    private fun fromTypedName(query: String): List<String> {
        val bare = query.replaceFirst(leadingAts, "")
        return when {
            subredditPrefix.containsMatchIn(query) -> listOf(REDDIT)
            query.lowercase().startsWith("did:plc:") -> listOf(BLUESKY)
            '@' in bare -> listOf(MASTODON)
            '.' in bare -> listOf(BLUESKY, SUBSTACK)
            else -> listOf(REDDIT, THREADS, SUBSTACK)
        }
    }

    private fun looksLikeFeed(uri: URI): Boolean = feedPath.containsMatchIn(uri.path.orEmpty().lowercase())

    private val leadingAts = Regex("^@+")
    private val subredditPrefix = Regex("^/?r/", RegexOption.IGNORE_CASE)
    private val feedPath = Regex("(?:^|/)(?:feed|rss|atom)(?:[./]|$)|\\.(?:xml|rss|atom)$")
}
