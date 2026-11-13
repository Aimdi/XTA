package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The mark threads.com sets beside an author in a community they belong to
 * (`text_post_app_info.community_badge.badge_type`). CI's probe saw
 * `MEMBER`; the others are the community roles Meta's relay switches name
 * (founder, champion, top contributor) and a moderator, read in case they
 * come; any other word draws nothing.
 */
enum class ThreadsCommunityBadge {
    MEMBER,
    FOUNDER,
    MODERATOR,
    CHAMPION,
    TOP_CONTRIBUTOR,
    ;

    companion object {
        /** The badge [value] names, in any case or spelling Meta writes it ("MEMBER", "community_founder"); null for a word not known. */
        fun of(value: String?): ThreadsCommunityBadge? {
            val word = value?.trim()?.lowercase()?.ifEmpty { null } ?: return null
            return when {
                "none" == word || "no_badge" == word -> null
                "founder" in word || "creator" in word -> FOUNDER
                "moderator" in word || "admin" in word -> MODERATOR
                "champion" in word -> CHAMPION
                "top" in word || "contributor" in word -> TOP_CONTRIBUTOR
                "member" in word -> MEMBER
                else -> null
            }
        }
    }
}

/**
 * What a guest is shown on a post beyond round 19's content, each read as
 * if it might be gone: the author's badge in the community the post is in,
 * a quoted post Threads no longer shows, and how often a video was played.
 */
object ThreadsGuestMarks {
    /** `community_badge.badge_type` on `text_post_app_info` (seen live) or on the post, or the badge as a bare word. */
    fun communityBadge(post: J): ThreadsCommunityBadge? = listOf(post["text_post_app_info"]["community_badge"], post["community_badge"])
        .firstNotNullOfOrNull { badge -> ThreadsCommunityBadge.of(badge["badge_type"].string ?: badge["type"].string ?: badge.string) }

    /**
     * Whether the post quotes one Threads shows as "This post is unavailable":
     * a true flag in `share_info` whose name says a quoted post is
     * unavailable (`quoted_attachment_post_unavailable`,
     * `quoted_post_unavailable`). Not seen set yet: CI's probes count only
     * keys that hold something, and a false flag holds nothing.
     */
    fun quoteUnavailable(post: J): Boolean {
        val share = post["text_post_app_info"]["share_info"]
        val keys = (share.raw as? JsonObject)?.keys.orEmpty()
        return keys.any { key -> "quot" in key && "unavailable" in key && share[key].boolean == true }
    }

    /**
     * How often the post's video was played, where Meta gives a count: a
     * number under a play key on the post, its `text_post_app_info` or one
     * of its carousel's items (the largest of them). Not seen on a post a
     * guest was given yet.
     */
    fun playCount(post: J): Long? {
        val holders = listOf(post, post["text_post_app_info"]) + post["carousel_media"].list
        return holders.flatMap { holder -> PLAY_KEYS.mapNotNull { holder[it].integer?.takeIf { count -> count >= 0 } } }.maxOrNull()
    }

    private val PLAY_KEYS = listOf("play_count", "video_play_count", "ig_play_count", "fb_play_count", "video_view_count")
}

/** A podcast a profile lists (`platform_podcast_info`): its name, where it is heard (over https) and on which platform. */
data class ThreadsPodcast(val title: String? = null, val url: String? = null, val platform: String? = null) {
    /** What its row reads: the title, else the platform, else the address's host. */
    val label: String? get() = title ?: platform ?: url?.toHttpUrlOrNull()?.host?.removePrefix("www.")
}

/** A profile that shares its posts to the fediverse, and the address the fediverse follows it at. */
data class ThreadsFediverse(val address: String)

/**
 * What a profile's user object tells a guest beside its bio and links: the
 * topics and communities it lists (`profile_tags`, seen live as
 * `profile_tags.edges[].node` with a community's favicon), the podcast it
 * names (`platform_podcast_info`, seen live, its own keys not yet), and
 * whether it shares to the fediverse (no key seen yet). Each is read under
 * the names it is likely to take, and nothing is made up where Meta says
 * nothing.
 */
object ThreadsProfileExtras {
    /** At most this many of a profile's tags are shown (threads.com shows a handful). */
    const val MAX_TAGS = 10

    /** The topics and communities [user] lists, each once, as a post's `tag_header` is read ([ThreadsCommunities]). */
    fun tags(user: J): List<ThreadsTopic> {
        val holder = user["profile_tags"]
        val items = holder["edges"].list.map { it["node"] }.ifEmpty { holder.list.map { item -> item["node"].takeIf { it.exists } ?: item } }
        return items.mapNotNull(::tag).distinctBy { it.key }.take(MAX_TAGS)
    }

    private fun tag(item: J): ThreadsTopic? {
        val node = listOf(item, item["tag"], item["tag_header"]).firstOrNull { ThreadsCommunities.name(it) != null } ?: return null
        val name = ThreadsCommunities.name(node) ?: return null
        return ThreadsTopic(
            name = name,
            emoji = node["community_emoji"].string?.trim()?.ifEmpty { null },
            iconUrl = node["community_favicon_url"].string?.trim()?.takeIf { it.startsWith("https://") },
            community = ThreadsCommunities.isCommunity(node),
            url = ThreadsCommunities.url(node),
        )
    }

    /** The podcast [user] names: a title or an https address under the names they are likely to take; null with neither. */
    fun podcast(user: J): ThreadsPodcast? {
        val info = user["platform_podcast_info"]
        if (info.raw !is JsonObject) return null
        val nodes = listOf(info) + NESTED.map { info[it] }.filter { it.raw is JsonObject } + info["podcasts"].list.take(1)
        val url = nodes.firstNotNullOfOrNull { node -> URL_KEYS.firstNotNullOfOrNull { https(node[it].string) } }
        val title = nodes.firstNotNullOfOrNull { node -> TITLE_KEYS.firstNotNullOfOrNull { said(node[it]) } }
        val platform = nodes.firstNotNullOfOrNull { node -> PLATFORM_KEYS.firstNotNullOfOrNull { platform(node[it].string) } }
        return if (title == null && url == null) null else ThreadsPodcast(title, url, platform)
    }

    /**
     * Whether [user] shares to the fediverse: a key naming the fediverse
     * (or federation, or ActivityPub) that holds true, an object saying it is
     * enabled, or an address; a key about being eligible, a prompt or what a
     * reader can do says nothing. The address is the one Meta gives, else
     * the one Threads federates every account under (`@handle@threads.net`).
     */
    fun fediverse(user: J, username: String): ThreadsFediverse? {
        val keys = (user.raw as? JsonObject)?.keys.orEmpty().filter { FEDIVERSE.containsMatchIn(it) && !ASIDE.containsMatchIn(it) }
        val values = keys.map { user[it] }
        val address = values.firstNotNullOfOrNull(::address)
        val on = address != null || values.any { it.boolean == true || ENABLED_KEYS.any { key -> it[key].boolean == true } }
        val handle = username.trim().lowercase().ifEmpty { null } ?: return null
        return if (on) ThreadsFediverse(address ?: "@$handle@threads.net") else null
    }

    private fun address(node: J): String? = (listOf(node) + ADDRESS_KEYS.map { node[it] })
        .firstNotNullOfOrNull { it.string?.trim()?.takeIf(FEDI_ADDRESS::matches) }
        ?.let { if (it.startsWith("@")) it else "@$it" }

    private fun said(node: J): String? = (node.string ?: node["text"].string)?.trim()?.ifEmpty { null }

    private fun https(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.startsWith("https://") } ?: return null
        return ThreadsLinks.unwrapOutbound(text).takeIf { it.startsWith("https://") }
    }

    /** A platform's name as people write it: "SPOTIFY" is Spotify, "apple_podcasts" Apple Podcasts; anything else is left out. */
    private fun platform(raw: String?): String? {
        val word = raw?.trim()?.takeIf(PLATFORM_WORD::matches) ?: return null
        return word.lowercase().split('_', ' ').filter { it.isNotEmpty() }.joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }
    }

    private val NESTED = listOf("podcast", "show", "podcast_info", "podcast_show")
    private val URL_KEYS = listOf("podcast_url", "show_url", "url", "link", "uri", "web_url", "external_url", "share_url", "deeplink_url")
    private val TITLE_KEYS = listOf("title", "podcast_title", "podcast_name", "show_name", "show_title", "name", "display_name")
    private val PLATFORM_KEYS = listOf("platform", "platform_name", "provider", "provider_name", "source", "app_name")
    private val PLATFORM_WORD = Regex("[A-Za-z][A-Za-z_ ]{1,30}")
    private val FEDIVERSE = Regex("fedivers|federat|activitypub", RegexOption.IGNORE_CASE)
    private val ASIDE = Regex("eligib|can_|nux|upsell|show_|seen|prompt|dialog|education", RegexOption.IGNORE_CASE)
    private val ENABLED_KEYS = listOf("enabled", "is_enabled", "is_sharing_enabled", "sharing_enabled", "is_federated", "is_on")
    private val ADDRESS_KEYS = listOf("address", "handle", "fediverse_handle", "fediverse_address", "username")
    private val FEDI_ADDRESS = Regex("@?[A-Za-z0-9_.]{1,64}@[A-Za-z0-9.-]{1,253}\\.[A-Za-z]{2,24}")
}
